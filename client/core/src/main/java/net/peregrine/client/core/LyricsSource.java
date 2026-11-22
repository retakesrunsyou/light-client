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
        /** Word by word timing when the lyrics have it (enhanced LRC): where each word starts in text, and when. */
        public final int[] wordStart;
        public final long[] wordAt;

        Line(long at, String text) {
            this(at, text, null, null);
        }

        Line(long at, String text, int[] wordStart, long[] wordAt) {
            this.at = at;
            this.text = text;
            this.wordStart = wordStart;
            this.wordAt = wordAt;
        }

        /**
         * How many characters of this line are sung at ms (fractional), given when the next line
         * starts: from the word timings when there are any, otherwise spread over the line by
         * letters, so long words take longer than short ones, like singing does.
         */
        public float sungChars(long ms, long next) {
            int len = text.length();
            if (len == 0 || ms <= at) {
                return 0;
            }
            if (wordAt != null && wordAt.length > 0) {
                for (int j = wordAt.length - 1; j >= 0; j--) {
                    if (ms >= wordAt[j]) {
                        int from = wordStart[j];
                        int to = j + 1 < wordStart.length ? wordStart[j + 1] : len;
                        long end = j + 1 < wordAt.length ? wordAt[j + 1] : Math.max(wordAt[j] + 300, Math.min(next, wordAt[j] + 1500));
                        float f = (float) Math.min(1, (ms - wordAt[j]) / (double) Math.max(1, end - wordAt[j]));
                        return from + (to - from) * f;
                    }
                }
                return 0;
            }
            // no word timing: the line's time spread over its letters (a gap at the end is held)
            long span = Math.max(1, Math.min(next - at, 600L + 140L * len));
            return (float) Math.min(len, len * (ms - at) / (double) span);
        }
    }

    /** The lyrics of one song: timed lines, or none (with why). */
    public static final class Song {
        public final String key;
        public final List<Line> lines;
        public final String status;   // "" when there are lines, else what happened
        /** True when the lyrics had no timing and the lines are spread evenly over the song. */
        public final boolean estimated;

        Song(String key, List<Line> lines, String status) {
            this(key, lines, status, false);
        }

        Song(String key, List<Line> lines, String status, boolean estimated) {
            this.key = key;
            this.lines = lines;
            this.status = status;
            this.estimated = estimated;
        }
    }

    private static final String API = "https://lrclib.net/api/";
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    /** A word's time inside a line (enhanced LRC): <01:23.45>. */
    private static final Pattern WORD = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    /** Whether lyrics without timing are spread over the song (set by the Lyrics module). */
    public static volatile boolean allowUntimed = true;
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
            String rest = raw.substring(end);
            // word timings: <mm:ss.xx> before each word
            List<Integer> starts = new ArrayList<>();
            List<Long> ats = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            Matcher w = WORD.matcher(rest);
            int last = 0;
            while (w.find()) {
                text.append(rest, last, w.start());
                long ms = ms(w.group(1), w.group(2), w.group(3));
                int at = text.length();
                while (at < text.length() && text.charAt(at) == ' ') {
                    at++;
                }
                starts.add(at);
                ats.add(ms);
                last = w.end();
            }
            text.append(rest.substring(last));
            String clean = text.toString();
            int lead = 0;
            while (lead < clean.length() && clean.charAt(lead) == ' ') {
                lead++;
            }
            String line = clean.trim();
            int[] ws = null;
            long[] wa = null;
            if (!starts.isEmpty()) {
                ws = new int[starts.size()];
                wa = new long[ats.size()];
                for (int k = 0; k < ws.length; k++) {
                    ws[k] = Math.max(0, Math.min(line.length(), starts.get(k) - lead));
                    wa[k] = ats.get(k);
                }
            }
            for (long ms : times) {
                out.add(new Line(ms, line, ws, wa));
            }
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    private static long ms(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            ms += frac.length() == 1 ? Long.parseLong(frac) * 100 : frac.length() == 2 ? Long.parseLong(frac) * 10
                    : Long.parseLong(frac);
        }
        return ms;
    }

    /**
     * Lyrics without timing spread over the song: the first line a little in, the last a
     * little before the end, each line's share by its length (a blank line is a short pause).
     */
    public static List<Line> spread(String plain, long length) {
        List<Line> out = new ArrayList<>();
        if (plain == null || length <= 0) {
            return out;
        }
        List<String> rows = new ArrayList<>();
        for (String r : plain.split("\r?\n")) {
            rows.add(r.trim());
        }
        while (!rows.isEmpty() && rows.get(0).isEmpty()) {
            rows.remove(0);
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
            rows.remove(rows.size() - 1);
        }
        if (rows.isEmpty()) {
            return out;
        }
        long start = Math.min(15000, length / 10), end = length - Math.min(10000, length / 15);
        double total = 0;
        for (String r : rows) {
            total += r.isEmpty() ? 6 : 8 + r.length();
        }
        double t = start;
        for (String r : rows) {
            out.add(new Line(Math.round(t), r));
            t += (end - start) * (r.isEmpty() ? 6 : 8 + r.length()) / total;
        }
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
        return Integer.toHexString(key.toLowerCase().hashCode()) + ".v2.lrc";   // v2: also keeps untimed lyrics
    }

    private static Song load(String key, String title, String artist, long length) {
        Path dir = cacheDir();
        Path file = dir == null ? null : dir.resolve(fileName(key));
        try {
            if (file != null && Files.isRegularFile(file)) {
                String lrc = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                return song(key, lrc, length);
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
        return song(key, lrc, length);
    }

    /** What's kept for a song: LRC text, PLAIN + untimed text, or "" for none. */
    private static Song song(String key, String kept, long length) {
        if (kept == null || kept.isEmpty()) {
            return new Song(key, Collections.<Line>emptyList(), "No lyrics found for this song");
        }
        if (kept.startsWith(PLAIN)) {
            if (!allowUntimed) {
                return new Song(key, Collections.<Line>emptyList(), "Only untimed lyrics for this song");
            }
            List<Line> lines = spread(kept.substring(PLAIN.length()), length);
            return lines.isEmpty() ? new Song(key, lines, "Only untimed lyrics for this song") : new Song(key, lines, "", true);
        }
        List<Line> lines = parse(kept);
        return lines.isEmpty() ? new Song(key, lines, "No timed lyrics for this song") : new Song(key, lines, "");
    }

    private static final String PLAIN = "#untimed\n";

    /**
     * The synced (LRC) lyrics LRCLIB has for the song; failing that its untimed lyrics (as
     * PLAIN + text); or null if it has neither. Tries the exact song first, then a search.
     */
    private static String lookUp(String title, String artist, long length) throws Exception {
        String[] ta = tidy(title, artist);
        String t = ta[0], a = ta[1];
        String plain = null;
        if (!a.isEmpty()) {
            String q = "get?track_name=" + enc(t) + "&artist_name=" + enc(a)
                    + (length > 0 ? "&duration=" + Math.round(length / 1000.0) : "");
            JsonElement got = fetch(q);
            if (got != null && got.isJsonObject()) {
                String lrc = synced(got.getAsJsonObject());
                if (lrc != null) {
                    return lrc;
                }
                plain = plain(got.getAsJsonObject());
            }
        }
        for (String q : new String[] {
                "search?track_name=" + enc(t) + (a.isEmpty() ? "" : "&artist_name=" + enc(a)),
                "search?q=" + enc((a + " " + t).trim())}) {
            JsonElement found = fetch(q);
            if (found == null || !found.isJsonArray()) {
                continue;
            }
            JsonObject best = null, bestPlain = null;
            long bestOff = Long.MAX_VALUE, plainOff = Long.MAX_VALUE;
            for (JsonElement e : found.getAsJsonArray()) {   // the closest length, timed lyrics first
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                long dur = o.has("duration") && !o.get("duration").isJsonNull() ? Math.round(o.get("duration").getAsDouble() * 1000) : 0;
                long off = length > 0 && dur > 0 ? Math.abs(dur - length) : 0;
                if (synced(o) != null && off < bestOff) {
                    bestOff = off;
                    best = o;
                } else if (synced(o) == null && plain(o) != null && off < plainOff) {
                    plainOff = off;
                    bestPlain = o;
                }
            }
            if (best != null && (length <= 0 || bestOff < 8000)) {
                return synced(best);
            }
            if (plain == null && bestPlain != null && (length <= 0 || plainOff < 8000)) {
                plain = plain(bestPlain);
            }
        }
        return plain == null ? null : PLAIN + plain;
    }

    private static String plain(JsonObject o) {
        JsonElement s = o.get("plainLyrics");
        return s == null || s.isJsonNull() || s.getAsString().isBlank() ? null : s.getAsString();
    }

    /**
     * Title and artist as the lyrics service knows them. Video sites often give the channel as
     * the artist ("Artist - Topic", "ArtistVEVO") and "Artist - Song (Official Video)" as the title.
     */
    static String[] tidy(String title, String artist) {
        String a = artist == null ? "" : artist.trim();
        a = a.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").trim();
        String t = title == null ? "" : title.trim();
        int dash = t.indexOf(" - ");
        if (dash > 0) {
            String left = t.substring(0, dash).trim(), right = t.substring(dash + 3).trim();
            boolean leftIsArtist = !a.isEmpty() && (left.equalsIgnoreCase(a) || left.toLowerCase().contains(a.toLowerCase())
                    || a.toLowerCase().contains(left.toLowerCase()));
            if (leftIsArtist || a.isEmpty()) {
                t = right;
                if (a.isEmpty() || !leftIsArtist) {
                    a = left;
                }
            }
        }
        t = clean(t);
        t = t.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|with)\\s[^\\)\\]]*[\\)\\]]", "")
                .replaceAll("(?i)\\s+(feat\\.?|ft\\.?)\\s.*$", "").trim();
        a = a.replaceAll("(?i)\\s*(,|&|\\bx\\b|feat\\.?|ft\\.?).*$", "").trim();   // the first artist
        return new String[] {t.isEmpty() ? (title == null ? "" : title.trim()) : t, a};
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
