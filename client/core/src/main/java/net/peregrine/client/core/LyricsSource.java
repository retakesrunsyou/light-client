package net.peregrine.client.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Timed lyrics for the song you're playing (from NowPlaying), looked up on LRCLIB
 * (lrclib.net, a free public lyrics service) the first time a song plays, then kept on disk so
 * the same song isn't asked for again. Looked up on a background thread: never in a frame.
 */
public final class LyricsSource {

    /** One timed line: when it starts (ms into the song) and what it says. */
    public static final class Line {
        public final long at;
        public final String text;

        Line(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    /** The lyrics of one song: timed lines, or none (with why). */
    public static final class Song {
        public final String key;
        public final List<Line> lines;
        public final String status;   // "" when there are lines, else what happened

        Song(String key, List<Line> lines, String status) {
            this.key = key;
            this.lines = lines;
            this.status = status;
        }
    }

    private static final String API = "https://lrclib.net/api/";
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static volatile Song current;
    private static volatile String asking;

    private LyricsSource() {
    }

    /** The lyrics for this track if they're ready (null while looking them up). Starts the lookup. */
    public static Song get(NowPlaying.Track t) {
        if (t == null || t.title.isEmpty()) {
            return null;
        }
        String key = t.title + "\u0000" + t.artist;
        Song s = current;
        if (s != null && s.key.equals(key)) {
            return s;
        }
        if (!key.equals(asking)) {
            asking = key;
            final String title = t.title, artist = t.artist;
            final long length = t.length;
            Thread th = new Thread(() -> {
                Song got = load(key, title, artist, length);
                if (key.equals(asking)) {
                    current = got;
                }
            }, "Light Client lyrics");
            th.setDaemon(true);
            th.start();
        }
        return null;
    }

    /** Parses LRC text ("[01:23.45] words") into lines in time order; untimed lines are skipped. */
    public static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) {
            return out;
        }
        for (String raw : lrc.split("\r?\n")) {
            Matcher m = STAMP.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {   // a line can start with more than one time
                long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
                String frac = m.group(3);
                if (frac != null) {
                    ms += frac.length() == 1 ? Long.parseLong(frac) * 100 : frac.length() == 2 ? Long.parseLong(frac) * 10
                            : Long.parseLong(frac);
                }
                times.add(ms);
                end = m.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = raw.substring(end).trim();
            for (long ms : times) {
                out.add(new Line(ms, text));
            }
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    /** The line being sung at ms (index into lines), or -1 before the first. */
    public static int lineAt(List<Line> lines, long ms) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).at <= ms) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    // ---- looking up

    private static Path cacheDir() {
        String data = System.getProperty("peregrine.data");
        return data == null ? null : Path.of(data, "lyrics");
    }

    private static String fileName(String key) {
        return Integer.toHexString(key.toLowerCase().hashCode()) + ".lrc";
    }

    private static Song load(String key, String title, String artist, long length) {
        Path dir = cacheDir();
        Path file = dir == null ? null : dir.resolve(fileName(key));
        try {
            if (file != null && Files.isRegularFile(file)) {
                String lrc = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                return lrc.isEmpty() ? new Song(key, Collections.<Line>emptyList(), "No timed lyrics for this song")
                        : new Song(key, parse(lrc), "");
            }
        } catch (Exception ignored) {
            // look it up again
        }
        String lrc;
        try {
            lrc = lookUp(title, artist, length);
        } catch (Exception e) {
            return new Song(key, Collections.<Line>emptyList(), "Lyrics couldn't be looked up (offline?)");
        }
        try {
            if (file != null) {
                Files.createDirectories(dir);
                Files.write(file, (lrc == null ? "" : lrc).getBytes(StandardCharsets.UTF_8));   // "" remembers "none"
            }
        } catch (Exception ignored) {
            // just not kept
        }
        List<Line> lines = parse(lrc);
        return lines.isEmpty() ? new Song(key, lines, "No timed lyrics for this song") : new Song(key, lines, "");
    }

    /** The synced (LRC) lyrics LRCLIB has for the song, or null if it has none. */
    private static String lookUp(String title, String artist, long length) throws Exception {
        String t = clean(title), a = artist == null ? "" : artist.trim();
        if (!a.isEmpty()) {
            String q = "get?track_name=" + enc(t) + "&artist_name=" + enc(a)
                    + (length > 0 ? "&duration=" + Math.round(length / 1000.0) : "");
            JsonElement got = fetch(q);
            if (got != null && got.isJsonObject()) {
                String lrc = synced(got.getAsJsonObject());
                if (lrc != null) {
                    return lrc;
                }
            }
        }
        JsonElement found = fetch("search?track_name=" + enc(t) + (a.isEmpty() ? "" : "&artist_name=" + enc(a)));
        if (found != null && found.isJsonArray()) {
            JsonArray arr = found.getAsJsonArray();
            JsonObject best = null;
            long bestOff = Long.MAX_VALUE;
            for (JsonElement e : arr) {   // the closest length with timed lyrics
                if (!e.isJsonObject() || synced(e.getAsJsonObject()) == null) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                long dur = o.has("duration") && !o.get("duration").isJsonNull() ? Math.round(o.get("duration").getAsDouble() * 1000) : 0;
                long off = length > 0 && dur > 0 ? Math.abs(dur - length) : 0;
                if (off < bestOff) {
                    bestOff = off;
                    best = o;
                }
            }
            if (best != null && (length <= 0 || bestOff < 8000)) {
                return synced(best);
            }
        }
        return null;
    }

    private static String synced(JsonObject o) {
        JsonElement s = o.get("syncedLyrics");
        return s == null || s.isJsonNull() || s.getAsString().isBlank() ? null : s.getAsString();
    }

    /** Titles often carry extras the lyrics service doesn't: " - Remastered 2011", "(Official Video)". */
    static String clean(String title) {
        String t = title == null ? "" : title;
        t = t.replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(official|video|audio|lyrics?|visuali[sz]er|remaster|hd|4k)[^\\)\\]]*[\\)\\]]", "");
        t = t.replaceAll("(?i)\\s+-\\s+.*(remaster|version|edit|mix|live).*$", "");
        return t.trim();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static JsonElement fetch(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(API + path).toURL().openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Light Client (https://github.com/retakesrunsyou/peregrine-launcher)");
        int code = c.getResponseCode();
        if (code == 404) {
            return null;
        }
        if (code != 200) {
            throw new java.io.IOException("lyrics service answered " + code);
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < 1 << 20) {
                b.write(buf, 0, n);
            }
            return JsonParser.parseString(new String(b.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
