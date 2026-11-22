package net.peregrine.client.core.modules;

import java.util.ArrayList;
import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.LyricsSource;
import net.peregrine.client.core.Module;
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
 *
 * On screen the line being sung is a little larger and fills in as it's sung (smoothly, word by
 * word, or all at once), long lines wrap instead of being cut off, and breaks between verses
 * show three dots counting down to the next line.
 */
public final class LyricsHud extends HudModule {

    static final int SWEEP = 0, WORDS = 1, WHOLE = 2;
    static final int CENTER = 0, LEFT = 1, RIGHT = 2;
    static final int SLIDE = 0, FADE = 1, NONE = 2;

    public final SliderSetting lines = add(new SliderSetting("lines", "Lines", 1f, 7f, 1f, 3f, "%.0f"));
    public final ChoiceSetting fillStyle = add(new ChoiceSetting("fill_style", "Fill style", SWEEP,
            "Smooth", "Word by word", "Whole line"));
    public final ChoiceSetting align = add(new ChoiceSetting("align", "Alignment", CENTER, "Center", "Left", "Right"));
    public final SliderSetting emphasis = add(new SliderSetting("emphasis", "Current line size", 1f, 1.6f, 0.05f, 1.15f, "%.2fx"));
    public final ChoiceSetting transition = add(new ChoiceSetting("transition", "Line transition", SLIDE, "Slide", "Fade", "None"));
    public final BoolSetting glow = add(new BoolSetting("glow", "Glow", true));
    public final BoolSetting shadow = add(new BoolSetting("shadow", "Text shadow", true));
    public final BoolSetting breaks = add(new BoolSetting("breaks", "Break countdown", true));
    public final BoolSetting songName = add(new BoolSetting("song", "Show song name", false));
    public final BoolSetting bar = add(new BoolSetting("bar", "Progress bar", false));
    public final ColorSetting sung = add(new ColorSetting("sung", "Sung color", -1, false));
    public final ColorSetting unsung = add(new ColorSetting("unsung", "Upcoming color", 0xFFFFFFFF, false));
    public final SliderSetting width = add(new SliderSetting("width", "Width", 120f, 420f, 10f, 240f, "%.0f px"));
    public final SliderSetting timing = add(new SliderSetting("timing", "Timing offset", -3000f, 3000f, 50f, 0f, "%+.0f ms"));
    public final BoolSetting untimed = add(new BoolSetting("untimed", "Use untimed lyrics", true));
    public final BoolSetting hideMissing = add(new BoolSetting("hide_missing", "Hide without lyrics", false));

    /** Placeholder lines for placing it (Edit HUD) when nothing's playing: not from any song. */
    private static final String[] SAMPLE = {"Lyrics show up here", "line by line as the song plays",
            "and fill in as they're sung, wrapping when a line is too long to fit"};

    private double ax, ay, az;      // where the words are in the world
    private boolean anchored;
    private String anchorFor = "";
    private long lineChangedAt;
    private int lastLine = Integer.MIN_VALUE;

    public LyricsHud() {
        super("lyrics", "Lyrics", "Synced lyrics for the song you're playing",
                false, 0.5f, 0.82f);
    }


    // ---- what to show

    /** The lines to show around the current one, and how far through the current line it is. */
    private static final class View {
        String[] text;    // lines, the current one at index cur
        int cur;
        float sung;       // characters of the current line sung so far
        float breakProgress = -1;   // 0..1 through a break between lines (dots), -1 when singing
        float song = -1;  // 0..1 through the song, -1 unknown
        String title = "";
        String status;    // when there's nothing to show: why
    }

    private View view(Platform p) {
        LyricsSource.allowUntimed = untimed.value;
        View v = new View();
        int n = Math.round(lines.value);
        NowPlaying.Track t = NowPlaying.get();
        if (t == null || t.title.isEmpty()) {
            if (!p.inWorld()) {   // placing it: placeholder words
                v.text = new String[n];
                int c = (n - 1) / 2;
                for (int i = 0; i < n; i++) {
                    v.text[i] = SAMPLE[((i - c) % SAMPLE.length + SAMPLE.length) % SAMPLE.length];
                }
                v.cur = c;
                v.sung = v.text[c].length() * ((System.currentTimeMillis() % 3000) / 3000f);
                v.title = "Song name - Artist";
                v.song = 0.4f;
                return v;
            }
            v.status = "Play a song in Spotify, YouTube or any music app";
            return v;
        }
        v.title = t.artist.isEmpty() ? t.title : t.title + " - " + t.artist;
        long now = t.positionNow() + Math.round(timing.value);
        v.song = t.length > 0 ? (float) Math.max(0, Math.min(1, t.positionNow() / (double) t.length)) : -1;
        LyricsSource.Song s = LyricsSource.get(t);
        if (s == null) {
            v.status = "Looking up the lyrics...";
            return v;
        }
        if (s.lines.isEmpty()) {
            v.status = s.status;
            return v;
        }
        if (s.estimated) {
            v.title += "  (timing estimated)";
        }
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
        long next = i + 1 < all.size() ? all.get(i + 1).at : (i >= 0 ? all.get(i).at + 5000 : 0);
        if (i >= 0 && !all.get(i).text.isEmpty()) {
            LyricsSource.Line line = all.get(i);
            v.sung = line.sungChars(now, next);
            if (fillStyle.index == WORDS) {
                v.sung = wordEnd(line.text, v.sung);
            } else if (fillStyle.index == WHOLE) {
                v.sung = line.text.length();
            }
        } else {
            // before the first line or an instrumental break: count down to the next line
            long from = i >= 0 ? all.get(i).at : 0;
            long to = i + 1 < all.size() ? all.get(i + 1).at : from + 5000;
            v.text[before] = "";
            if (to - from > 2500) {
                v.breakProgress = (float) Math.max(0, Math.min(1, (now - from) / (double) (to - from)));
            } else {
                v.text[before] = "♪";
            }
        }
        return v;
    }

    /** Word by word: once a word has started, all of it is lit. */
    private static float wordEnd(String text, float sung) {
        int k = (int) Math.ceil(sung);
        if (k <= 0) {
            return 0;
        }
        while (k < text.length() && text.charAt(k) != ' ') {
            k++;
        }
        return k;
    }

    private int sungColor() {
        return sung.value() == -1 || sung.value() == 0 ? Peregrine.get().accent() : sung.value();
    }

    // ---- on screen

    private boolean onScreen() {
        return true;   // in the world: the World Lyrics module
    }

    @Override
    public boolean hasContent(Platform p) {
        if (!p.inWorld()) {
            return true;
        }
        if (!onScreen()) {
            return false;
        }
        if (hideMissing.value) {
            View v = view(p);
            return v.text != null;
        }
        return true;
    }

    @Override
    public int width(Draw d, Platform p) {
        return Math.round(width.value);
    }

    private int lineGap(Draw d) {
        return d.lineHeight() + 3;
    }

    @Override
    public int height(Draw d, Platform p) {
        int lh = lineGap(d);
        return 6 + (songName.value ? lh : 0) + Math.round(lines.value) * lh + (bar.value ? 5 : 0) + 4;
    }

    /** A line broken into rows that fit maxW: each row with where it starts in the line. */
    private static List<int[]> wrap(Draw d, String s, int maxW, int maxRows) {
        List<int[]> rows = new ArrayList<int[]>();
        int start = 0, len = s.length();
        while (start < len && rows.size() < maxRows) {
            if (rows.size() == maxRows - 1 || d.width(s.substring(start)) <= maxW) {
                rows.add(new int[] {start, len});
                break;
            }
            int end = start, lastSpace = -1;
            while (end < len && d.width(s.substring(start, end + 1)) <= maxW) {
                if (s.charAt(end) == ' ') {
                    lastSpace = end;
                }
                end++;
            }
            int cut = lastSpace > start ? lastSpace : Math.max(start + 1, end);
            rows.add(new int[] {start, cut});
            start = cut;
            while (start < len && s.charAt(start) == ' ') {
                start++;
            }
        }
        return rows;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        if (!onScreen() && p.inWorld()) {
            return;
        }
        View v = view(p);
        if (v.text == null && hideMissing.value && p.inWorld()) {
            return;
        }
        int w = width(d, p), h = height(d, p);
        panel(d, x, y, w, h);
        int lh = lineGap(d);
        int ty = y + 5;
        if (songName.value) {
            String tl = d.trim(v.title, w - 10);
            d.text(tl, alignX(x, w, d.width(tl)), ty, Theme.FAINT, shadow.value);
            ty += lh;
        }
        int n = Math.round(lines.value);
        int areaH = n * lh;
        if (v.text == null) {
            String msg = d.trim(v.status == null ? "" : v.status, w - 10);
            d.text(msg, alignX(x, w, d.width(msg)), ty + (n - 1) * lh / 2, Theme.FAINT, shadow.value);
        } else {
            long since = System.currentTimeMillis() - lineChangedAt;
            float slide = transition.index == SLIDE ? 1f - Math.min(1f, since / 260f) : 0f;
            float fade = transition.index == FADE ? Math.min(1f, since / 260f) : 1f;
            int shift = Math.round(slide * slide * lh);
            d.clip(x + 2, ty - 1, w - 4, areaH + 1);
            renderLines(d, v, x, ty, w, areaH, lh, shift, fade);
            d.unclip();
        }
        if (bar.value && v.song >= 0) {
            int by = y + h - 6, bw = w - 12;
            d.roundRect(x + 6, by, bw, 2, 1, 0x40FFFFFF);
            d.roundRect(x + 6, by, Math.max(2, Math.round(bw * v.song)), 2, 1, sungColor());
        }
    }

    private int alignX(int x, int w, int tw) {
        switch (align.index) {
            case LEFT: return x + 6;
            case RIGHT: return x + w - 6 - tw;
            default: return x + (w - tw) / 2;
        }
    }

    /** The lines, the current one centred in the area and larger, the others above and below it. */
    private void renderLines(Draw d, View v, int x, int top, int w, int areaH, int lh, int shift, float fade) {
        float big = emphasis.value;
        int n = v.text.length;
        List<List<int[]>> rows = new ArrayList<List<int[]>>();
        int[] blockH = new int[n];
        for (int k = 0; k < n; k++) {
            float sc = k == v.cur ? big : 1f;
            int maxW = (int) ((w - 12) / sc);
            List<int[]> r = new ArrayList<int[]>();
            if (!v.text[k].isEmpty()) {
                if (k == v.cur) {
                    r = wrap(d, v.text[k], maxW, 3);   // the line being sung wraps: every word stays readable
                } else {
                    v.text[k] = d.trim(v.text[k], maxW);   // the others stay one row each
                    r.add(new int[] {0, v.text[k].length()});
                }
            }
            rows.add(r);
            int count = Math.max(1, r.size());
            blockH[k] = Math.round(count * lh * sc);
        }
        // the current line's block centred, the rest stacked from it
        int[] blockY = new int[n];
        blockY[v.cur] = top + (areaH - blockH[v.cur]) / 2 + shift;
        for (int k = v.cur - 1; k >= 0; k--) {
            blockY[k] = blockY[k + 1] - blockH[k];
        }
        for (int k = v.cur + 1; k < n; k++) {
            blockY[k] = blockY[k - 1] + blockH[k - 1];
        }
        for (int k = 0; k < n; k++) {
            boolean cur = k == v.cur;
            if (cur && v.breakProgress >= 0) {
                if (breaks.value) {
                    dots(d, x, w, blockY[k] + blockH[k] / 2, v.breakProgress);
                }
                continue;
            }
            List<int[]> r = rows.get(k);
            if (r.isEmpty()) {
                continue;
            }
            if (!cur) {   // a neighbour pushed mostly out by a long current line isn't drawn half cut off
                int y0 = blockY[k] - shift, y1 = y0 + blockH[k];
                int seen = Math.min(y1, top + areaH) - Math.max(y0, top);
                if (seen < blockH[k] * 0.7f) {
                    continue;
                }
            }
            String line = v.text[k];
            float sc = cur ? big : 1f;
            int dist = Math.abs(k - v.cur);
            int alpha = cur ? Math.round(255 * fade) : Math.max(0x30, (k < v.cur ? 0x70 : 0xA0) - (dist - 1) * 0x28);
            for (int ri = 0; ri < r.size(); ri++) {
                String row = line.substring(r.get(ri)[0], r.get(ri)[1]);
                int rw = Math.round(d.width(row) * sc);
                int rx = alignX(x, w, rw);
                int ry = blockY[k] + Math.round(ri * lh * sc);
                if (sc != 1f) {
                    d.pushScale(rx, ry, sc);
                    drawRow(d, row, 0, 0, cur, v.sung - r.get(ri)[0], alpha, lh);
                    d.popScale();
                } else {
                    drawRow(d, row, rx, ry, cur, v.sung - r.get(ri)[0], alpha, lh);
                }
            }
        }
    }

    /** One row of text: the current line's row filled up to `sung` characters, with a soft edge. */
    private void drawRow(Draw d, String row, int rx, int ry, boolean cur, float sung, int alpha, int lh) {
        if (!cur) {
            d.text(row, rx, ry, Theme.withAlpha(unsung.value(), alpha), shadow.value);
            return;
        }
        int sc = Theme.withAlpha(sungColor(), alpha);
        int rest = Theme.withAlpha(unsung.value(), Math.round(alpha * 0.85f));
        int k = (int) Math.floor(Math.max(0, Math.min(row.length(), sung)));
        float frac = Math.max(0, Math.min(1, sung - k));
        int cut = d.width(row.substring(0, k)) + (k < row.length() ? Math.round(frac * d.width(row.substring(k, k + 1))) : 0);
        int rw = d.width(row);
        if (glow.value && cut > 0) {
            int g = Theme.withAlpha(sungColor(), Math.round(alpha * 0.14f));
            d.clip(rx - 2, ry - 2, cut + 3, lh + 3);
            d.text(row, rx - 1, ry, g, false);
            d.text(row, rx + 1, ry, g, false);
            d.text(row, rx, ry - 1, g, false);
            d.text(row, rx, ry + 1, g, false);
            d.unclip();
        }
        if (cut >= rw) {
            d.text(row, rx, ry, sc, shadow.value);
            return;
        }
        d.text(row, rx, ry, rest, shadow.value);
        if (cut <= 0) {
            return;
        }
        d.clip(rx, ry - 1, cut, lh + 1);
        d.text(row, rx, ry, sc, shadow.value);
        d.unclip();
        // a soft edge where the singing is, so the fill glides instead of stepping
        for (int e = 0; e < 3; e++) {
            d.clip(rx + cut + e, ry - 1, 1, lh + 1);
            d.text(row, rx, ry, Theme.withAlpha(sungColor(), Math.round(alpha * (0.6f - e * 0.2f))), false);
            d.unclip();
        }
    }

    /** Three dots during a break: each lights up in turn as the next line comes closer. */
    private void dots(Draw d, int x, int w, int cy, float progress) {
        int r = 5, gap = 6, total = 3 * r + 2 * gap;
        int sx = alignX(x, w, total);
        for (int i = 0; i < 3; i++) {
            float lit = Math.max(0, Math.min(1, progress * 3 - i));
            float pulse = progress > 0.9f ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 90.0) : 1f;
            int c = lit >= 1 ? Theme.withAlpha(sungColor(), Math.round(255 * pulse)) : Theme.withAlpha(unsung.value(), 0x50 + Math.round(lit * 0x60));
            d.roundRect(sx + i * (r + gap), cy - r / 2, r, r, 2, c);
        }
    }
}
