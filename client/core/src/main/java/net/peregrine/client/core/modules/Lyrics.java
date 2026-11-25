package net.peregrine.client.core.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.LyricsSource;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.NowPlaying;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.WorldText;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.ColorSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Lyrics: the song you're playing, word by word, as real 3D text in the world. Words can
 * pop up all around where you look, sit on an arc around you, follow your view, stay in one
 * place, or hang huge in the sky; each comes in and goes out with an animation you pick.
 *
 * The words are handed to the version adapter as WorldText (drawn into the game's own text
 * buffers), so each letter is a few quads: cheap even with thickness, glow and outline on.
 */
public final class Lyrics extends Module {

    static final int AROUND = 0, ARC = 1, FOLLOW = 2, FIXED = 3, SKY = 4;
    static final int HIGHLIGHT = 0, SOLID = 1, GRADIENT = 2, RAINBOW = 3, GLITCH = 4;
    static final int IN_FADE = 0, IN_POP = 1, IN_SLIDE = 2, IN_DROP = 3, IN_SPIN = 4, IN_ZOOM = 5, IN_WAVE = 6, IN_GLITCH = 7;
    static final int STILL = 0, FLOAT = 1, WAVE = 2, BREATHE = 3;
    static final int OUT_FADE = 0, OUT_UP = 1, OUT_SHRINK = 2, OUT_FALL = 3, OUT_SCATTER = 4, OUT_TOWARD = 5, OUT_SPIN = 6, OUT_GLITCH = 7;

    // where
    public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Show words", AROUND,
            "All around you", "Arc in front", "Follow your view", "Fixed in place", "Big in the sky"));
    public final SliderSetting textSize = add(new SliderSetting("size", "Text size", 0.3f, 3f, 0.05f, 1f, "%.2fx"));
    public final SliderSetting distance = add(new SliderSetting("distance", "Distance", 2f, 30f, 0.5f, 7f, "%.1f blocks"));
    public final SliderSetting height = add(new SliderSetting("height", "Height", -3f, 6f, 0.25f, 0.5f, "%+.2f blocks"));
    // look
    public final ChoiceSetting font = add(new ChoiceSetting("font", "Font", 1, "Inter", "Inter Medium", "Inter Bold", "Inter Italic"));
    public final ChoiceSetting colors = add(new ChoiceSetting("colors", "Colors", HIGHLIGHT,
            "Highlight as sung", "Solid", "Gradient", "Rainbow", "Glitch"));
    public final ColorSetting color = add(new ColorSetting("color", "Text color", 0xFFFFFFFF, false));
    public final ColorSetting color2 = add(new ColorSetting("color2", "Second color", -1, false));
    public final BoolSetting caps = add(new BoolSetting("caps", "Capital letters", true));
    public final BoolSetting slant = add(new BoolSetting("slant", "Slanted letters", false));
    public final SliderSetting thickness = add(new SliderSetting("thickness", "3D thickness", 0f, 100f, 5f, 40f, "%.0f%%"));
    public final ChoiceSetting sides = add(new ChoiceSetting("sides", "3D sides", 0, "Darker", "Black", "Second color"));
    public final SliderSetting outline = add(new SliderSetting("outline", "Outline", 0f, 1f, 0.05f, 0f, "%.2f"));
    public final SliderSetting glow = add(new SliderSetting("glow", "Glow", 0f, 1f, 0.05f, 0.25f, "%.2f"));
    public final BoolSetting seeThrough = add(new BoolSetting("see_through", "Show through blocks", false));
    public final BoolSetting dimOld = add(new BoolSetting("dim_old", "Older words dimmer", true));
    // all around you
    public final BoolSetting whereLooking = add(new BoolSetting("where_looking", "Appear where you look", true));
    public final SliderSetting maxWords = add(new SliderSetting("max_words", "Words at once", 1f, 12f, 1f, 5f, "%.0f"));
    public final SliderSetting stay = add(new SliderSetting("stay", "Each word stays", 0.5f, 10f, 0.25f, 3f, "%.2f s"));
    public final SliderSetting near = add(new SliderSetting("near", "Closest", 3f, 20f, 0.5f, 5f, "%.1f blocks"));
    public final SliderSetting far = add(new SliderSetting("far", "Farthest", 5f, 40f, 0.5f, 14f, "%.1f blocks"));
    // motion
    public final ChoiceSetting inAnim = add(new ChoiceSetting("in", "Coming in", IN_POP,
            "Fade", "Pop", "Slide up", "Drop in", "Spin", "Zoom from far", "Letter wave", "Glitch"));
    public final ChoiceSetting idle = add(new ChoiceSetting("idle", "While up", FLOAT, "Still", "Float", "Wave letters", "Breathe"));
    public final ChoiceSetting outAnim = add(new ChoiceSetting("out", "Going out", OUT_UP,
            "Fade", "Float up", "Shrink", "Fall", "Scatter letters", "Fly toward you", "Spin away", "Glitch"));
    public final SliderSetting speed = add(new SliderSetting("speed", "Animation speed", 0.25f, 3f, 0.05f, 1f, "%.2fx"));
    public final SliderSetting timing = add(new SliderSetting("timing", "Timing offset", -3000f, 3000f, 50f, 0f, "%+.0f ms"));

    /** One word (or line) up in the world, with when it came and where it is. */
    private static final class Word {
        String text;
        int line, firstChar;
        boolean wholeLine;
        double x, y, z;
        float size, roll, seed, fit = 1f;
        long born, leaving = -1;
        double fromX, fromY, fromZ;   // where "zoom from far" starts
    }

    private final List<Word> words = new ArrayList<Word>();
    private final Random rnd = new Random();
    private String songKey = "";
    private int lastLine = Integer.MIN_VALUE;
    private int spawnedUpTo = -1;   // words of the current line already put up
    private double ax, ay, az, aYaw = Double.NaN, aPitch;
    private boolean anchored;

    public Lyrics() {
        super("lyrics", "Lyrics", "The song you're playing, as 3D words in the world", Category.VISUALS, false);
    }

    @Override
    protected void onDisable() {
        Hooks.worldTexts = new WorldText[0];
        words.clear();
    }

    private int second() {
        int c = color2.value();
        return c == -1 || c == 0 ? Peregrine.get().accent() : c;
    }

    // ------------------------------------------------------------------ every frame

    @Override
    public void renderWorld(Draw d, Platform p) {
        if (!p.inWorld() || !Hooks.canDrawWorldText) {
            Hooks.worldTexts = new WorldText[0];
            return;
        }
        long now = System.currentTimeMillis();
        NowPlaying.Track t = NowPlaying.get();
        LyricsSource.Song song = t == null || t.title.isEmpty() ? null : LyricsSource.get(t);
        List<WorldText> out = new ArrayList<WorldText>();
        if (song == null || song.lines.isEmpty()) {
            leaveAll(now);
        } else {
            if (!song.key.equals(songKey)) {
                songKey = song.key;
                leaveAll(now);
                anchored = false;
                lastLine = Integer.MIN_VALUE;
            }
            long ms = t.positionNow() + Math.round(timing.value);
            List<LyricsSource.Line> lines = song.lines;
            int i = LyricsSource.lineAt(lines, ms);
            long next = i + 1 < lines.size() ? lines.get(i + 1).at : (i >= 0 ? lines.get(i).at + 5000 : 0);
            String text = i >= 0 ? lines.get(i).text : "";
            float sung = i >= 0 ? lines.get(i).sungChars(ms, next) : 0;
            if (i != lastLine) {
                lineChanged(p, i, text, now);
                lastLine = i;
                spawnedUpTo = -1;
            }
            if (mode.index == AROUND) {
                spawnWords(p, i, text, sung, now);
            }
            for (Word w : words) {
                emit(p, w, i, sung, now, out);
            }
        }
        // words that have been up long enough start going
        for (Word w : words) {
            if (!w.wholeLine && w.leaving < 0 && now - w.born > stay.value * 1000f) {
                w.leaving = now;
            }
        }
        // finished words go
        float outLen = 500f / speed.value;
        words.removeIf(w -> w.leaving >= 0 && now - w.leaving > outLen);
        Hooks.worldTexts = out.toArray(new WorldText[0]);
    }

    private void leaveAll(long now) {
        for (Word w : words) {
            if (w.leaving < 0) {
                w.leaving = now;
            }
        }
    }

    /** A new line: the old one goes out; in the line modes the new one comes in as a whole. */
    private void lineChanged(Platform p, int line, String text, long now) {
        if (mode.index != AROUND) {
            leaveAll(now);
            if (line >= 0 && !text.isEmpty()) {
                Word w = new Word();
                w.text = caps.value ? text.toUpperCase() : text;
                w.line = line;
                w.wholeLine = true;
                w.born = now;
                w.seed = rnd.nextFloat() * 10f;
                place(p, w, true);
                if (mode.index == FIXED) {   // every line where the song's first line went
                    if (anchored) {
                        w.x = ax;
                        w.y = ay;
                        w.z = az;
                    } else {
                        ax = w.x;
                        ay = w.y;
                        az = w.z;
                        anchored = true;
                    }
                }
                words.add(w);
            }
        }
    }

    /** All around you: each word goes up when it starts being sung. */
    private void spawnWords(Platform p, int line, String text, float sung, long now) {
        if (line < 0 || text.isEmpty()) {
            return;
        }
        int at = 0, index = 0;
        String[] parts = text.split(" ");
        for (String part : parts) {
            if (!part.isEmpty() && index > spawnedUpTo && sung > at) {
                Word w = new Word();
                w.text = caps.value ? part.toUpperCase() : part;
                w.line = line;
                w.firstChar = at;
                w.born = now;
                w.seed = rnd.nextFloat() * 10f;
                place(p, w, false);
                words.add(w);
                spawnedUpTo = index;
                // too many: the oldest still up starts going
                int up = 0;
                for (Word o : words) {
                    if (o.leaving < 0) {
                        up++;
                    }
                }
                for (Word o : words) {
                    if (up <= Math.round(maxWords.value)) {
                        break;
                    }
                    if (o.leaving < 0) {
                        o.leaving = now;
                        up--;
                    }
                }
            }
            at += part.length() + 1;
            index++;
        }
    }

    /** Where a new word or line goes, and how big. */
    private void place(Platform p, Word w, boolean line) {
        double eyeY = p.y() + 1.62;
        double yaw = Math.toRadians(p.yaw()), pitch = Math.toRadians(p.pitch());
        double dist;
        if (mode.index == AROUND) {
            if (!whereLooking.value) {
                yaw = rnd.nextDouble() * Math.PI * 2;
                pitch = Math.toRadians(-25 + rnd.nextDouble() * 35);
            }
            yaw += Math.toRadians((rnd.nextDouble() - 0.5) * 70);
            pitch += Math.toRadians((rnd.nextDouble() - 0.5) * 24);
            pitch = Math.max(Math.toRadians(-60), Math.min(Math.toRadians(45), pitch));
            double lo = Math.min(near.value, far.value), hi = Math.max(near.value, far.value);
            dist = lo + rnd.nextDouble() * (hi - lo);
            w.roll = (rnd.nextFloat() - 0.5f) * 16f;
        } else if (mode.index == SKY) {
            dist = 45;
            pitch = Math.toRadians(-18);
        } else {
            dist = distance.value;
            pitch = 0;
        }
        double cp = Math.cos(pitch);
        w.x = p.x() - Math.sin(yaw) * cp * dist;
        w.y = eyeY - Math.sin(pitch) * dist + (mode.index == AROUND ? 0 : height.value);
        w.z = p.z() + Math.cos(yaw) * cp * dist;
        // the same size on screen at any distance, a little varied when they're all around
        float base = 0.05f * textSize.value * (float) (dist / 7.0) * (font.index == WorldText.FONT_INTER ? 1.5f : 1f);
        w.size = mode.index == SKY ? base * 1.6f : mode.index == AROUND ? base * (0.85f + rnd.nextFloat() * 0.3f) : base;
        w.fromX = p.x() - Math.sin(yaw) * cp * dist * 3;
        w.fromY = w.y + 4;
        w.fromZ = p.z() + Math.cos(yaw) * cp * dist * 3;
    }

    // ------------------------------------------------------------------ drawing one word

    private void emit(Platform p, Word w, int curLine, float sung, long now, List<WorldText> out) {
        double wx = w.x, wy = w.y, wz = w.z;
        // the line modes keep up with you
        if (w.wholeLine && w.leaving < 0 && (mode.index == FOLLOW || mode.index == SKY || mode.index == ARC)) {
            double yaw = Math.toRadians(p.yaw());
            if (Double.isNaN(aYaw)) {
                aYaw = yaw;
            }
            aYaw += Math.atan2(Math.sin(yaw - aYaw), Math.cos(yaw - aYaw)) * (mode.index == SKY ? 0.02 : 0.08);
            double dist = mode.index == SKY ? 45 : distance.value;
            double eye = p.y() + 1.62;
            double tx, ty, tz;
            if (mode.index == FOLLOW) {
                // up and down too: look up and the words hang over you looking down, look down
                // and they lie on the ground at your feet
                double pitch = Math.toRadians(Math.max(-82, Math.min(82, p.pitch())));
                aPitch = anchored ? aPitch + (pitch - aPitch) * 0.08 : pitch;
                double sp = Math.sin(aPitch), cp = Math.cos(aPitch);
                double d = dist;
                if (sp > 0) {   // looking down: stop at the floor
                    d = Math.min(d, Math.max(0.6, (1.62 + height.value * cp - 0.08) / sp));
                }
                tx = p.x() - Math.sin(aYaw) * cp * d;
                tz = p.z() + Math.cos(aYaw) * cp * d;
                ty = eye - sp * d + height.value * cp;
                w.fit = (float) (d / dist);   // closer on the floor, so smaller to look the same
            } else {
                tx = p.x() - Math.sin(aYaw) * dist;
                tz = p.z() + Math.cos(aYaw) * dist;
                ty = eye + (mode.index == SKY ? 14 : height.value);
            }
            if (!anchored) {
                ax = tx;
                ay = ty;
                az = tz;
                anchored = true;
            }
            ax += (tx - ax) * 0.15;
            ay += (ty - ay) * 0.15;
            az += (tz - az) * 0.15;
            w.x = wx = ax;
            w.y = wy = ay;
            w.z = wz = az;
        }
        float sp = speed.value;
        float tIn = Math.min(1f, (now - w.born) / (420f / sp));
        float q = w.leaving < 0 ? 0f : Math.min(1f, (now - w.leaving) / (500f / sp));
        float eIn = easeOut(tIn), life = (now - w.born) / 1000f;
        float alpha = 1f, scale = 1f, roll = w.roll;
        double offY = 0;
        int n = w.text.length();
        float[] dx = null, dy = null, rot = null, lsc = null;
        float[] la = null;   // per-letter alpha
        float fringe = 0;
        // coming in
        switch (inAnim.index) {
            case IN_FADE: alpha *= eIn; break;
            case IN_POP: scale *= backOut(tIn); alpha *= Math.min(1f, tIn * 3f); break;
            case IN_SLIDE: offY -= (1 - eIn) * 1.2; alpha *= eIn; break;
            case IN_DROP: offY += (1 - eIn) * (1 - eIn) * 3; alpha *= Math.min(1f, tIn * 2f); break;
            case IN_SPIN: roll += (1 - eIn) * 360f; scale *= 0.3f + 0.7f * eIn; break;
            case IN_ZOOM:
                wx = lerp(w.fromX, wx, eIn);
                wy = lerp(w.fromY, wy, eIn);
                wz = lerp(w.fromZ, wz, eIn);
                alpha *= eIn;
                break;
            case IN_WAVE:
                if (tIn < 1f) {
                    dy = new float[n];
                    la = new float[n];
                    for (int k = 0; k < n; k++) {
                        float lk = clamp(tIn * (n + 3) / 3f - k / 3f);
                        dy[k] = (1 - easeOut(lk)) * 10f;
                        la[k] = lk;
                    }
                }
                break;
            case IN_GLITCH:
                if (tIn < 1f) {
                    fringe = 2.5f * (1 - tIn);
                    dx = new float[n];
                    for (int k = 0; k < n; k++) {
                        dx[k] = (float) Math.sin((now / 33) * 1.7 + k * 3.1 + w.seed) * 3f * (1 - tIn);
                    }
                    alpha *= ((now / 50) % 3 == 0) ? 0.4f : 1f;
                }
                break;
            default: break;
        }
        // while it's up
        if (q == 0f && tIn >= 1f) {
            switch (idle.index) {
                case FLOAT:
                    offY += Math.sin(life * 1.3 + w.seed) * 0.08;
                    roll += (float) Math.sin(life * 0.9 + w.seed) * 3f;
                    break;
                case WAVE:
                    dy = new float[n];
                    for (int k = 0; k < n; k++) {
                        dy[k] = (float) Math.sin(life * 4 * sp + k * 0.7) * 1.4f;
                    }
                    break;
                default: break;
            }
        }
        // going out
        if (q > 0f) {
            float eq = q * q;
            switch (outAnim.index) {
                case OUT_FADE: alpha *= 1 - q; break;
                case OUT_UP: offY += q * 1.5; alpha *= 1 - q; break;
                case OUT_SHRINK: scale *= 1 - q; break;
                case OUT_FALL: offY -= eq * 4; roll += q * 50f; alpha *= 1 - eq; break;
                case OUT_SCATTER:
                    dx = new float[n];
                    dy = new float[n];
                    rot = new float[n];
                    for (int k = 0; k < n; k++) {
                        double a = w.seed * 7 + k * 2.39;
                        dx[k] = (float) Math.cos(a) * q * 26f;
                        dy[k] = (float) Math.sin(a) * q * 26f;
                        rot[k] = (k % 2 == 0 ? 1 : -1) * q * 200f;
                    }
                    alpha *= 1 - q;
                    break;
                case OUT_TOWARD:
                    double ex = p.x(), ey = p.y() + 1.62, ez = p.z();
                    wx = lerp(wx, ex, q * 0.75);
                    wy = lerp(wy, ey, q * 0.75);
                    wz = lerp(wz, ez, q * 0.75);
                    alpha *= 1 - q;
                    break;
                case OUT_SPIN: roll += q * 360f; scale *= 1 - q * 0.6f; alpha *= 1 - q; break;
                case OUT_GLITCH:
                    fringe = 3f * q;
                    dx = new float[n];
                    for (int k = 0; k < n; k++) {
                        dx[k] = (float) Math.sin((now / 33) * 2.3 + k * 1.9 + w.seed) * 5f * q;
                    }
                    alpha *= ((now / 40) % 2 == 0) ? 1 - q : (1 - q) * 0.3f;
                    break;
                default: break;
            }
        }
        if (dimOld.value && w.line != curLine) {
            alpha *= 0.6f;
        }
        if (alpha <= 0.02f || scale <= 0.02f) {
            return;
        }
        WorldText t = new WorldText();
        t.text = w.text;
        t.x = wx;
        t.y = wy + offY;
        t.z = wz;
        t.size = w.size * scale * w.fit;
        t.roll = roll;
        t.font = font.index;
        t.layers = Math.round(thickness.value / 100f * 8f);
        t.layerStep = 0.35f;
        t.slant = slant.value ? 0.22f : 0f;
        t.seeThrough = seeThrough.value;
        t.faceUpDown = true;
        int base = color.value(), sec = second();
        int a = Math.round(alpha * 255);
        t.color = argb(base, a);
        t.sideColor = sides.index == 1 ? 0xFF101014 : sides.index == 2 ? sec : base;
        if (outline.value > 0) {
            t.outlineColor = argb(0x000000, Math.round(outline.value * 255));
        }
        if (glow.value > 0) {
            float g = idle.index == BREATHE && q == 0 ? glow.value * (0.55f + 0.45f * (float) Math.sin(life * 2.4 * sp + w.seed)) : glow.value;
            t.glowColor = argb(colors.index == HIGHLIGHT ? sec : base, Math.round(g * 140));
        }
        // colours, letter by letter where it changes along the word
        int[] cols = null;
        switch (colors.index) {
            case HIGHLIGHT:
                cols = new int[n];
                for (int k = 0; k < n; k++) {
                    boolean lit = w.line < curLine || (w.line == curLine && sung > w.firstChar + k);
                    cols[k] = argb(lit ? sec : base, a);
                }
                break;
            case GRADIENT:
                cols = new int[n];
                for (int k = 0; k < n; k++) {
                    cols[k] = argb(mix(base, sec, n <= 1 ? 0 : k / (float) (n - 1)), a);
                }
                break;
            case RAINBOW:
                cols = new int[n];
                for (int k = 0; k < n; k++) {
                    cols[k] = argb(java.awt.Color.HSBtoRGB((k * 0.08f + life * 0.15f + w.seed) % 1f, 0.55f, 1f), a);
                }
                break;
            case GLITCH:
                t.fringeA = 0xFFFF4FA3;
                t.fringeB = 0xFF3FD8FF;
                fringe = Math.max(fringe, 0.7f + 0.5f * (float) Math.abs(Math.sin(life * 9 + w.seed)));
                break;
            default: break;
        }
        t.fringe = fringe;
        if (fringe > 0 && t.fringeA == 0) {
            t.fringeA = 0xFFFF4FA3;
            t.fringeB = 0xFF3FD8FF;
        }
        if (la != null) {
            if (cols == null) {
                cols = new int[n];
                java.util.Arrays.fill(cols, t.color);
            }
            for (int k = 0; k < n; k++) {
                cols[k] = argb(cols[k], Math.round((cols[k] >>> 24) * la[k]));
            }
        }
        t.colors = cols;
        if (dx != null || dy != null || rot != null || lsc != null) {
            t.dx = dx != null ? dx : new float[n];
            t.dy = dy != null ? dy : new float[n];
            t.rot = rot;
            t.scale = lsc;
        }
        if (w.wholeLine && mode.index == ARC) {
            arc(p, t, out);
        } else {
            out.add(t);
        }
    }

    /** Arc in front: the line split into words, each on the curve around you, facing you. */
    private void arc(Platform p, WorldText line, List<WorldText> out) {
        String[] parts = line.text.split(" ");
        float space = p.worldTextWidth(" ", line.font) * 1.5f;
        float total = 0;
        float[] widths = new float[parts.length];
        for (int k = 0; k < parts.length; k++) {
            widths[k] = p.worldTextWidth(parts[k], line.font);
            total += widths[k] + (k > 0 ? space : 0);
        }
        double r = distance.value;
        double cx = p.x(), cz = p.z();
        double baseYaw = Math.atan2(-(line.x - cx), line.z - cz);
        float pos = -total / 2f;
        int at = 0;
        for (int k = 0; k < parts.length; k++) {
            float mid = pos + widths[k] / 2f;
            pos += widths[k] + space;
            double a = baseYaw - mid * line.size / r;
            WorldText w = new WorldText();
            w.text = parts[k];
            w.x = cx - Math.sin(a) * r;
            w.y = line.y;
            w.z = cz + Math.cos(a) * r;
            w.size = line.size;
            w.roll = line.roll;
            w.font = line.font;
            w.color = line.color;
            w.layers = line.layers;
            w.layerStep = line.layerStep;
            w.sideColor = line.sideColor;
            w.outlineColor = line.outlineColor;
            w.glowColor = line.glowColor;
            w.slant = line.slant;
            w.fringe = line.fringe;
            w.fringeA = line.fringeA;
            w.fringeB = line.fringeB;
            w.seeThrough = line.seeThrough;
            int len = parts[k].length();
            if (line.colors != null) {
                w.colors = java.util.Arrays.copyOfRange(line.colors, Math.min(at, line.colors.length), Math.min(at + len, line.colors.length));
            }
            if (line.dx != null) {
                w.dx = java.util.Arrays.copyOfRange(line.dx, Math.min(at, line.dx.length), Math.min(at + len, line.dx.length));
                w.dy = java.util.Arrays.copyOfRange(line.dy, Math.min(at, line.dy.length), Math.min(at + len, line.dy.length));
                if (line.rot != null) {
                    w.rot = java.util.Arrays.copyOfRange(line.rot, Math.min(at, line.rot.length), Math.min(at + len, line.rot.length));
                }
            }
            at += len + 1;
            out.add(w);
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOut(float t) {
        float u = 1 - clamp(t);
        return 1 - u * u * u;
    }

    private static float backOut(float t) {
        t = clamp(t);
        float c = 1.9f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int argb(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
