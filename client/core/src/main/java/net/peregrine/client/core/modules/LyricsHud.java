package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.LyricsSource;
import net.peregrine.client.core.NowPlaying;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.ColorSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Lyrics for the song you're playing (Spotify, YouTube, any music app: what Music activity
 * sees), line by line in time with it: on screen, floating in 3D in the world, or both.
 * Looked up on LRCLIB, a free public lyrics service, the first time each song plays.
 */
public final class LyricsHud extends HudModule {

    static final int SCREEN = 0, WORLD = 1, BOTH = 2;
    static final int FOLLOW = 0, STAY = 1;

    public final ChoiceSetting where = add(new ChoiceSetting("where", "Show", SCREEN, "On screen", "In the world (3D)", "Both"));
    public final SliderSetting lines = add(new SliderSetting("lines", "Lines shown", 1f, 5f, 2f, 3f, "%.0f"));
    public final BoolSetting fill = add(new BoolSetting("fill", "Fill each line as it's sung", true));
    public final BoolSetting songName = add(new BoolSetting("song", "Song name above", false));
    public final ColorSetting sung = add(new ColorSetting("sung", "Sung colour", -1, false));
    public final ColorSetting unsung = add(new ColorSetting("unsung", "Coming up colour", 0xFFFFFFFF, false));
    public final SliderSetting width = add(new SliderSetting("width", "Width on screen", 120f, 400f, 10f, 220f, "%.0f px"));
    public final SliderSetting timing = add(new SliderSetting("timing", "Timing (if the words are early or late)", -3000f, 3000f, 50f, 0f, "%+.0f ms"));
    // in the world
    public final ChoiceSetting place = add(new ChoiceSetting("place", "In the world", FOLLOW, "Follows your view", "Stays where the song started"));
    public final SliderSetting distance = add(new SliderSetting("distance", "Distance", 2f, 16f, 0.5f, 6f, "%.1f blocks"));
    public final SliderSetting height = add(new SliderSetting("height", "Height", -2f, 4f, 0.25f, 0.75f, "%+.2f blocks"));
    public final SliderSetting worldSize = add(new SliderSetting("world_size", "Size in the world", 0.5f, 4f, 0.1f, 1.6f, "%.1fx"));
    public final SliderSetting depth = add(new SliderSetting("depth", "3D depth", 0f, 8f, 1f, 4f, "%.0f"));

    /** Placeholder lines for placing it (Edit HUD) when nothing's playing: not from any song. */
    private static final String[] SAMPLE = {"Lyrics show up here", "line by line as the song plays", "and fill in as they're sung"};

    private double ax, ay, az;      // where the words are in the world
    private boolean anchored;
    private String anchorFor = "";
    private long lineChangedAt;
    private int lastLine = Integer.MIN_VALUE;

    public LyricsHud() {
        super("lyrics", "Lyrics", "Words to the song you're playing, in time with it: on screen or in 3D in the world",
                false, 0.5f, 0.82f);
    }

    @Override
    public boolean supportsWorldRender() {
        return true;
    }

    // ---- what to show

    /** The lines to show around the current one, and how far through the current line it is. */
    private static final class View {
        String[] text;    // lines, the current one at index cur
        int cur;
        float progress;   // 0..1 through the current line
        String title = "";
        String status;    // when there's nothing to show: why
    }

    private View view(Platform p) {
        View v = new View();
        int n = Math.round(lines.value);
        NowPlaying.Track t = NowPlaying.get();
        if (t == null || t.title.isEmpty()) {
            if (!p.inWorld()) {   // placing it: placeholder words
                v.text = new String[Math.min(n, SAMPLE.length)];
                int c = Math.min(v.text.length / 2, SAMPLE.length - 1);
                for (int i = 0; i < v.text.length; i++) {
                    v.text[i] = SAMPLE[(i + 1 - c + SAMPLE.length) % SAMPLE.length];
                }
                v.cur = c;
                v.progress = (System.currentTimeMillis() % 3000) / 3000f;
                v.title = "Song name - Artist";
                return v;
            }
            v.status = "Play a song in Spotify, YouTube or any music app";
            return v;
        }
        v.title = t.artist.isEmpty() ? t.title : t.title + " - " + t.artist;
        LyricsSource.Song s = LyricsSource.get(t);
        if (s == null) {
            v.status = "Looking up the lyrics...";
            return v;
        }
        if (s.lines.isEmpty()) {
            v.status = s.status;
            return v;
        }
        long now = t.positionNow() + Math.round(timing.value);
        List<LyricsSource.Line> all = s.lines;
        int i = LyricsSource.lineAt(all, now);
        if (i != lastLine) {
            lastLine = i;
            lineChangedAt = System.currentTimeMillis();
        }
        int before = (n - 1) / 2;
        v.text = new String[n];
        for (int k = 0; k < n; k++) {
            int idx = i - before + k;
            v.text[k] = idx >= 0 && idx < all.size() ? all.get(idx).text : "";
        }
        v.cur = before;
        if (i >= 0) {
            long start = all.get(i).at;
            long end = i + 1 < all.size() ? all.get(i + 1).at : start + 4000;
            v.progress = (float) Math.max(0, Math.min(1, (now - start) / (double) Math.max(1, end - start)));
            if (v.text[before].isEmpty()) {
                v.text[before] = "♪";   // an instrumental break
            }
        } else {
            v.text[before] = "♪";
            v.progress = 0;
        }
        return v;
    }

    private int sungColor() {
        return sung.value() == -1 || sung.value() == 0 ? Peregrine.get().accent() : sung.value();
    }

    // ---- on screen

    private boolean onScreen() {
        return where.index != WORLD;
    }

    @Override
    public boolean hasContent(Platform p) {
        return onScreen() || !p.inWorld();
    }

    @Override
    public int width(Draw d, Platform p) {
        return Math.round(width.value);
    }

    @Override
    public int height(Draw d, Platform p) {
        int lh = d.lineHeight() + 3;
        return 6 + (songName.value ? lh : 0) + Math.round(lines.value) * lh + 4;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        if (!onScreen() && p.inWorld()) {
            return;
        }
        int w = width(d, p), h = height(d, p);
        d.roundRect(x, y, w, h, Theme.HUD_BG);
        View v = view(p);
        int lh = d.lineHeight() + 3;
        int ty = y + 5;
        if (songName.value) {
            String tl = d.trim(v.title, w - 10);
            d.text(tl, x + (w - d.width(tl)) / 2, ty, Theme.FAINT, true);
            ty += lh;
        }
        if (v.text == null) {
            String msg = d.trim(v.status == null ? "" : v.status, w - 10);
            d.text(msg, x + (w - d.width(msg)) / 2, ty + (Math.round(lines.value) - 1) * lh / 2, Theme.FAINT, true);
            return;
        }
        // a little slide up when the line changes
        float slide = 1f - Math.min(1f, (System.currentTimeMillis() - lineChangedAt) / 220f);
        int shift = Math.round(slide * slide * lh);
        d.clip(x + 2, ty - 1, w - 4, v.text.length * lh);
        for (int k = 0; k < v.text.length; k++) {
            String line = d.trim(v.text[k], w - 10);
            if (line.isEmpty()) {
                continue;
            }
            int lx = x + (w - d.width(line)) / 2, ly = ty + k * lh + shift;
            if (k == v.cur) {
                lineFill(d, line, lx, ly, v.progress, lh);
            } else {
                int a = k < v.cur ? 0x60 : 0x90;
                d.text(line, lx, ly, Theme.withAlpha(unsung.value(), a), true);
            }
        }
        d.unclip();
    }

    /** The current line: sung part in the sung colour, the rest in the coming-up colour. */
    private void lineFill(Draw d, String line, int lx, int ly, float progress, int lh) {
        int lw = d.width(line);
        if (!fill.value) {
            d.text(line, lx, ly, sungColor(), true);
            return;
        }
        d.text(line, lx, ly, unsung.value(), true);
        int cut = Math.round(lw * progress);
        if (cut > 0) {
            d.clip(lx, ly - 1, cut, lh);
            d.text(line, lx, ly, sungColor(), true);
            d.unclip();
        }
    }

    // ---- in the world

    @Override
    public void renderWorld(Draw d, Platform p) {
        if (where.index == SCREEN || !p.inWorld()) {
            return;
        }
        View v = view(p);
        if (v.text == null) {
            return;
        }
        double eye = p.y() + 1.62;
        double yaw = Math.toRadians(p.yaw());
        double tx = p.x() - Math.sin(yaw) * distance.value;
        double tz = p.z() + Math.cos(yaw) * distance.value;
        double ty = eye + height.value;
        String song = v.title;
        if (place.index == STAY) {
            if (!anchored || !song.equals(anchorFor)) {   // set where the song started
                ax = tx;
                ay = ty;
                az = tz;
                anchored = true;
                anchorFor = song;
            }
        } else {
            if (!anchored) {
                ax = tx;
                ay = ty;
                az = tz;
                anchored = true;
            }
            double k = 0.12;   // eases after your view, so turning doesn't throw the words around
            ax += (tx - ax) * k;
            ay += (ty - ay) * k;
            az += (tz - az) * k;
        }
        float[] s = p.project(ax, ay, az);
        if (s == null || s[2] <= 0.1f) {
            return;
        }
        float scale = (float) Math.max(0.3, Math.min(6.0, worldSize.value * 6.0 / s[2]));
        int lh = d.lineHeight() + 2;
        int layers = Math.round(depth.value);
        int n = v.text.length;
        float top = s[1] - n * lh * scale / 2f;
        for (int k = 0; k < n; k++) {
            String line = v.text[k];
            if (line == null || line.isEmpty()) {
                continue;
            }
            boolean cur = k == v.cur;
            float ls = cur ? scale : scale * 0.8f;
            int lw = d.width(line);
            int bx = Math.round(s[0] - lw * ls / 2f), by = Math.round(top + k * lh * scale);
            d.pushScale(bx, by, ls);
            int face = cur ? sungColor() : Theme.withAlpha(unsung.value(), k < v.cur ? 0x70 : 0xA0);
            extruded(d, line, layers, face, cur && fill.value ? v.progress : -1, lw, lh);
            d.popScale();
        }
    }

    /**
     * Text with depth: darker copies stepped down and to the right behind it (cheap: a few draws
     * of one line, no 3D models), then the face. With a fill, the face is sung up to progress.
     */
    private void extruded(Draw d, String line, int layers, int face, float progress, int lw, int lh) {
        for (int i = layers; i >= 1; i--) {
            float dark = 0.28f + 0.32f * (1f - i / (float) Math.max(1, layers));
            d.text(line, i, i, shade(face, dark), false);
        }
        if (progress < 0) {
            d.text(line, 0, 0, face, false);
            return;
        }
        d.text(line, 0, 0, unsung.value(), false);
        int cut = Math.round(lw * progress);
        if (cut > 0) {
            d.clip(0, -1, cut, lh + 1);
            d.text(line, 0, 0, face, false);
            d.unclip();
        }
    }

    private static int shade(int argb, float f) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (a << 24) | (Math.round(r * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }
}
