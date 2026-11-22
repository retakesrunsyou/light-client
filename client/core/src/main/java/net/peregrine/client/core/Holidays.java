package net.peregrine.client.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Holiday themes for the main menu and the pause menu: Christmas, New Year, Valentine's, Easter,
 * Halloween and Thanksgiving, each with its own colours, a greeting and its own scene drawn in
 * Minecraft-style pixel art (snow and fairy lights, fireworks and confetti, rising hearts, eggs in
 * the grass, bats and pumpkins, falling leaves). Picked by the date unless you choose one.
 *
 * Kept light: a few dozen sprites drawn as small rectangles, only while a menu is open.
 */
public final class Holidays {

    public static final int NONE = 0, CHRISTMAS = 1, NEW_YEAR = 2, VALENTINES = 3, EASTER = 4, HALLOWEEN = 5, THANKSGIVING = 6;
    /** The setting's choices: index 0 = by the date, 1 = off, then each theme (index - 1 = theme). */
    public static final String[] CHOICES = {"Automatic (by date)", "Off", "Christmas", "New Year", "Valentine's", "Easter",
        "Halloween", "Thanksgiving"};
    static final String[] GREETINGS = {"", "Merry Christmas", "Happy New Year", "Happy Valentine's Day", "Happy Easter",
        "Happy Halloween", "Happy Thanksgiving"};
    /** Each theme's colours: accent, second colour, third colour. */
    static final int[][] COLORS = {
        {0, 0, 0},
        {0xFFE0474C, 0xFF3DAA5C, 0xFFF4F7FF},   // red, pine green, snow
        {0xFFF2C14E, 0xFFB98CFF, 0xFF7FD7FF},   // gold, violet, ice blue
        {0xFFFF5C8A, 0xFFFFB3C9, 0xFFE0304E},   // pink, blush, red
        {0xFF8ED081, 0xFFF7B5D2, 0xFFA9D8FF},   // spring green, pink, sky
        {0xFFFF8A1F, 0xFF8E5BD8, 0xFF2A2233},   // pumpkin, purple, night
        {0xFFE07B2E, 0xFFB5432A, 0xFFE8B04A},   // orange, maple red, gold
    };

    private Holidays() {
    }

    /** The theme for this setting value (see CHOICES) on this date. */
    public static int pick(int setting, LocalDate d) {
        if (setting == 1) {
            return NONE;
        }
        if (setting >= 2 && setting < CHOICES.length) {
            return setting - 1;
        }
        return byDate(d);
    }

    public static int byDate(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if ((m == 12 && day >= 27) || (m == 1 && day <= 2)) {
            return NEW_YEAR;
        }
        if (m == 12) {
            return CHRISTMAS;
        }
        if (m == 2 && day >= 7 && day <= 15) {
            return VALENTINES;
        }
        LocalDate easter = easter(d.getYear());
        if (!d.isBefore(easter.minusDays(6)) && !d.isAfter(easter.plusDays(1))) {
            return EASTER;
        }
        if ((m == 10 && day >= 15) || (m == 11 && day == 1)) {
            return HALLOWEEN;
        }
        LocalDate thanks = thanksgiving(d.getYear());
        if (m == 11 && day >= 10 && !d.isAfter(thanks.plusDays(1))) {
            return THANKSGIVING;
        }
        return NONE;
    }

    /** Easter Sunday (Gregorian calendar, the "anonymous" method). */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }

    /** US Thanksgiving: the fourth Thursday of November. */
    static LocalDate thanksgiving(int y) {
        return LocalDate.of(y, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
    }

    public static String greeting(int theme) {
        return theme > 0 && theme < GREETINGS.length ? GREETINGS[theme] : "";
    }

    public static int accent(int theme) {
        return theme > 0 && theme < COLORS.length ? COLORS[theme][0] : 0;
    }

    // ------------------------------------------------------------------ sprites
    // Rows of characters: '.' is empty, 'a' 'b' 'c' 'd' are colours picked per sprite.

    static final String[] SNOWFLAKE = {"...a...", ".a.a.a.", "..aaa..", "aaaaaaa", "..aaa..", ".a.a.a.", "...a..."};
    static final String[] FLAKE_SMALL = {".a.", "aaa", ".a."};
    static final String[] HEART = {".aa.aa.", "abaaaaa", "aaaaaaa", ".aaaaa.", "..aaa..", "...a..."};
    static final String[] EGG = {".aaa.", "aaaaa", "bbbbb", "acaca", "bbbbb", "aaaaa", ".aaa."};
    static final String[] BAT_UP = {"a...a...a", "aa.aaa.aa", "aaaaaaaaa", "...a.a..."};
    static final String[] BAT_DOWN = {"...aaa...", "..aaaaa..", ".aaaaaaa.", "aa.a.a.aa", "a.......a"};
    static final String[] PUMPKIN = {"....b....", "..aaaaa..", ".aaaaaaa.", "aacaaacaa", "aaaaaaaaa", "aacacacaa", ".aaaaaaa.", "..aaaaa.."};
    static final String[] LEAF = {"...a...", ".a.a.a.", "aaaaaaa", ".aaaaa.", "aaaaaaa", "...b...", "...b..."};
    static final String[] FLOWER = {".a.", "aba", ".a."};
    static final String[] BULB = {"b", "a", "a"};

    /** Draws a sprite with its top-left at x, y, each cell `px` wide, in these colours. */
    static void sprite(Draw d, String[] rows, int x, int y, int px, int... colors) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int run = -1;
            char runC = '.';
            for (int c = 0; c <= row.length(); c++) {
                char ch = c < row.length() ? row.charAt(c) : '.';
                if (ch != runC) {
                    if (runC != '.' && run >= 0) {
                        int idx = runC - 'a';
                        if (idx >= 0 && idx < colors.length) {
                            d.rect(x + run * px, y + r * px, (c - run) * px, px, colors[idx]);
                        }
                    }
                    run = c;
                    runC = ch;
                }
            }
        }
    }

    // ------------------------------------------------------------------ a scene

    /** One drifting thing in a scene. */
    static final class Bit {
        float x, y, vx, vy, phase, life, age;
        int kind, color, size;
    }

    /**
     * A holiday scene for one area of the screen. Keep one per menu: it remembers where every
     * snowflake, heart or leaf is between frames.
     */
    public static final class Scene {
        private final List<Bit> bits = new ArrayList<Bit>();
        private final Random rnd = new Random();
        private int theme = -1, w, h;
        private final int count;
        private float nextBurst;

        /** count: about how many things drift at once (fewer over the game, more on the main menu). */
        public Scene(int count) {
            this.count = count;
        }

        public void render(Draw d, int theme, int x, int y, int w, int h, float dt) {
            if (theme <= 0 || w <= 0 || h <= 0) {
                return;
            }
            if (theme != this.theme || Math.abs(w - this.w) > 40 || Math.abs(h - this.h) > 40) {
                this.theme = theme;
                this.w = w;
                this.h = h;
                bits.clear();
                for (int i = 0; i < count; i++) {
                    bits.add(spawn(true));
                }
            }
            int[] c = COLORS[theme];
            long ms = System.currentTimeMillis();
            // a soft glow of the theme's colour along the bottom
            for (int i = 0; i < 6; i++) {
                int band = h / 5 * (6 - i) / 6;
                d.rect(x, y + h - band, w, band / 6 + 1, Theme.withAlpha(c[0], 0x05));
            }
            if (theme == CHRISTMAS) {
                lights(d, x, y, w, ms, c);
            }
            if (theme == NEW_YEAR) {
                nextBurst -= dt;
                if (nextBurst <= 0) {
                    burst();
                    nextBurst = 0.7f + rnd.nextFloat() * 1.3f;
                }
            }
            for (int i = bits.size() - 1; i >= 0; i--) {
                Bit b = bits.get(i);
                b.age += dt;
                b.x += (b.vx + (float) Math.sin(b.age * 1.3f + b.phase) * sway(b)) * dt;
                b.y += b.vy * dt;
                if (b.kind == 9) {
                    b.vy += 14 * dt;   // firework sparks droop as they fade
                }
                boolean gone = b.life > 0 ? b.age >= b.life : (b.vy > 0 ? b.y > h + 12 : b.y < -12) || b.x < -20 || b.x > w + 20;
                if (gone) {
                    if (b.kind == 9) {
                        bits.remove(i);   // a firework spark: gone for good
                    } else {
                        bits.set(i, spawn(false));
                    }
                    continue;
                }
                draw(d, b, x + Math.round(b.x), y + Math.round(b.y), ms, c);
            }
            if (theme == EASTER) {
                meadow(d, x, y + h, w, c);
            }
            if (theme == HALLOWEEN) {
                sprite(d, PUMPKIN, x + 10, y + h - 20, 2, c[0], 0xFF4F8A3A, 0xFFFFE08A);
                sprite(d, PUMPKIN, x + w - 30, y + h - 20, 2, c[0], 0xFF4F8A3A, 0xFFFFE08A);
            }
        }

        private float sway(Bit b) {
            switch (b.kind) {
                case 0: case 1: return 8f;    // snow
                case 4: return 22f;           // leaves flutter
                case 2: return 10f;           // hearts
                case 6: return 12f;           // petals
                default: return 0f;
            }
        }

        private Bit spawn(boolean anywhere) {
            Bit b = new Bit();
            b.phase = rnd.nextFloat() * 6.3f;
            b.x = rnd.nextFloat() * w;
            switch (theme) {
                case CHRISTMAS:
                    b.kind = rnd.nextFloat() < 0.35f ? 0 : 1;
                    b.size = b.kind == 0 ? 1 : 1;
                    b.vy = 9 + rnd.nextFloat() * 14;
                    b.vx = -3 + rnd.nextFloat() * 6;
                    b.color = Theme.withAlpha(0xFFFFFF, 0x90 + rnd.nextInt(0x60));
                    b.y = anywhere ? rnd.nextFloat() * h : -10;
                    break;
                case VALENTINES:
                    b.kind = 2;
                    b.size = rnd.nextFloat() < 0.3f ? 2 : 1;
                    b.vy = -(8 + rnd.nextFloat() * 12);
                    b.color = COLORS[VALENTINES][rnd.nextInt(3) == 0 ? 2 : rnd.nextInt(2)];
                    b.y = anywhere ? rnd.nextFloat() * h : h + 10;
                    break;
                case EASTER:
                    b.kind = 6;
                    b.size = 1;
                    b.vy = 6 + rnd.nextFloat() * 8;
                    b.vx = 4 + rnd.nextFloat() * 6;
                    b.color = new int[] {0xFFF7B5D2, 0xFFFFF1A8, 0xFFD9C2FF, 0xFFFFFFFF}[rnd.nextInt(4)];
                    b.y = anywhere ? rnd.nextFloat() * h : -8;
                    break;
                case HALLOWEEN:
                    if (rnd.nextFloat() < 0.3f) {   // a bat crossing
                        b.kind = 3;
                        b.size = rnd.nextFloat() < 0.4f ? 2 : 1;
                        boolean right = rnd.nextBoolean();
                        b.vx = (right ? 1 : -1) * (25 + rnd.nextFloat() * 25);
                        b.x = anywhere ? rnd.nextFloat() * w : right ? -15 : w + 15;
                        b.y = rnd.nextFloat() * h * 0.6f;
                        b.vy = -2 + rnd.nextFloat() * 4;
                        b.color = 0xFF17121D;
                    } else {                         // an ember rising
                        b.kind = 5;
                        b.size = 1;
                        b.vy = -(6 + rnd.nextFloat() * 10);
                        b.vx = -2 + rnd.nextFloat() * 4;
                        b.color = rnd.nextBoolean() ? 0xFFFF8A1F : 0xFFFFC14D;
                        b.y = anywhere ? rnd.nextFloat() * h : h + 4;
                    }
                    break;
                case THANKSGIVING:
                    b.kind = 4;
                    b.size = rnd.nextFloat() < 0.3f ? 2 : 1;
                    b.vy = 10 + rnd.nextFloat() * 12;
                    b.vx = -6 + rnd.nextFloat() * 5;
                    b.color = COLORS[THANKSGIVING][rnd.nextInt(3)];
                    b.y = anywhere ? rnd.nextFloat() * h : -10;
                    break;
                case NEW_YEAR:
                default:
                    b.kind = 7;   // confetti
                    b.size = 1;
                    b.vy = 14 + rnd.nextFloat() * 16;
                    b.vx = -4 + rnd.nextFloat() * 8;
                    b.color = new int[] {0xFFF2C14E, 0xFFB98CFF, 0xFF7FD7FF, 0xFFFF7AA2, 0xFFFFFFFF}[rnd.nextInt(5)];
                    b.y = anywhere ? rnd.nextFloat() * h : -6;
                    break;
            }
            return b;
        }

        /** A firework: a ring of sparks flying out from one point. */
        private void burst() {
            float cx = w * (0.15f + rnd.nextFloat() * 0.7f), cy = h * (0.12f + rnd.nextFloat() * 0.35f);
            int color = new int[] {0xFFF2C14E, 0xFFB98CFF, 0xFF7FD7FF, 0xFFFF7AA2}[rnd.nextInt(4)];
            int n = 18;
            for (int i = 0; i < n; i++) {
                Bit b = new Bit();
                double ang = Math.PI * 2 * i / n + rnd.nextFloat() * 0.2;
                float sp = 30 + rnd.nextFloat() * 12;
                b.kind = 9;
                b.x = cx;
                b.y = cy;
                b.vx = (float) Math.cos(ang) * sp;
                b.vy = (float) Math.sin(ang) * sp;
                b.life = 1.1f + rnd.nextFloat() * 0.4f;
                b.color = color;
                b.size = 1;
                bits.add(b);
            }
        }

        private void draw(Draw d, Bit b, int x, int y, long ms, int[] c) {
            switch (b.kind) {
                case 0:
                    sprite(d, SNOWFLAKE, x, y, 1, b.color);
                    break;
                case 1:
                    sprite(d, FLAKE_SMALL, x, y, 1, b.color);
                    break;
                case 2: {
                    float fade = Math.min(1f, Math.max(0f, b.y / Math.max(1f, h * 0.35f)));
                    sprite(d, HEART, x, y, b.size, Theme.withAlpha(b.color, Math.round(0xE0 * fade)), Theme.withAlpha(0xFFFFFF, Math.round(0xC0 * fade)));
                    break;
                }
                case 3: {
                    boolean up = ((ms / 140) + (long) (b.phase * 10)) % 2 == 0;
                    sprite(d, up ? BAT_UP : BAT_DOWN, x, y, b.size, b.color);
                    break;
                }
                case 4:
                    sprite(d, LEAF, x, y, b.size, b.color, 0xFF6B3A1E);
                    break;
                case 5: {
                    float fade = Math.min(1f, Math.max(0f, b.y / Math.max(1f, h * 0.5f)));
                    d.rect(x, y, 1, 1, Theme.withAlpha(b.color, Math.round(0xFF * fade)));
                    break;
                }
                case 6:
                    sprite(d, FLOWER, x, y, 1, b.color, 0xFFFFE27A);
                    break;
                case 7: {
                    boolean flat = ((ms / 180) + (long) (b.phase * 10)) % 2 == 0;
                    d.rect(x, y, flat ? 2 : 1, flat ? 1 : 2, b.color);
                    break;
                }
                case 9: {
                    float k = b.age / b.life;
                    d.rect(x, y, 1, 1, Theme.withAlpha(b.color, Math.round(0xFF * (1 - k))));
                    if (k < 0.4f) {
                        d.rect(x - Math.round(b.vx * 0.05f), y - Math.round(b.vy * 0.05f), 1, 1, Theme.withAlpha(b.color, 0x60));
                    }
                    break;
                }
                default:
                    break;
            }
        }

        /** Christmas: a string of fairy lights along the top, twinkling one by one. */
        private void lights(Draw d, int x, int y, int w, long ms, int[] c) {
            int[] colors = {0xFFE0474C, 0xFFF2C14E, 0xFF3DAA5C, 0xFF5AA8FF};
            int step = 18;
            for (int i = 0; i * step < w + step; i++) {
                int bx = x + i * step + 6;
                int sag = (int) Math.round(Math.sin((i % 2 == 0 ? 0.3 : 0.7) * Math.PI) * 3);
                d.rect(bx - step / 2, y + 1 + sag / 2, step, 1, 0x80303A30);
                boolean on = ((ms / 450) + i * 7) % 5 != 0;
                int col = colors[i % colors.length];
                if (on) {
                    d.glow(bx - 3, y + 2 + sag, 7, 8, Theme.withAlpha(col, 0x50));
                }
                sprite(d, BULB, bx, y + 1 + sag, 1, on ? col : Theme.mix(col, 0xFF000000, 0.6f), 0xFF5D6466);
            }
        }

        /** Easter: grass along the bottom with painted eggs in it. */
        private void meadow(Draw d, int x, int bottom, int w, int[] c) {
            for (int i = 0; i < w; i += 2) {
                int gh = 3 + (int) ((Math.sin(i * 0.9) + Math.sin(i * 0.37)) * 1.5 + 1.5);
                d.rect(x + i, bottom - gh, 2, gh, i % 6 == 0 ? 0xC07BC46E : 0xC05FA855);
            }
            int[][] eggs = {{0xFFF7B5D2, 0xFFFFFFFF, 0xFF9AD0FF}, {0xFFA9D8FF, 0xFFFFF1A8, 0xFFF7B5D2},
                {0xFFFFF1A8, 0xFFD9C2FF, 0xFF8ED081}, {0xFFD9C2FF, 0xFFFFFFFF, 0xFFFFB3C9}};
            for (int i = 0, ex = x + 14; ex < x + w - 10; i++, ex += 46 + (i * 17) % 23) {
                int[] e = eggs[i % eggs.length];
                sprite(d, EGG, ex, bottom - 13, 2, e[0], e[1], e[2]);
            }
        }
    }

    /** A small chip with the theme's sprite and greeting, centred at cx. */
    public static void greetingChip(Draw d, int theme, int cx, int y) {
        String g = greeting(theme);
        if (g.isEmpty()) {
            return;
        }
        int[] c = COLORS[theme];
        int tw = d.width(g), w = tw + 24, x = cx - w / 2;
        d.roundRect(x, y, w, 14, 7, 0xA00E1118);
        d.roundOutline(x, y, w, 14, 7, Theme.withAlpha(c[0], 0x90));
        int sx = x + 5, sy = y + 3;
        switch (theme) {
            case CHRISTMAS: sprite(d, SNOWFLAKE, sx, sy + 1, 1, c[2]); break;
            case VALENTINES: sprite(d, HEART, sx, sy + 2, 1, c[0], 0xFFFFFFFF); break;
            case EASTER: sprite(d, EGG, sx + 1, sy + 1, 1, c[1], 0xFFFFFFFF, c[2]); break;
            case HALLOWEEN: sprite(d, PUMPKIN, sx - 1, sy + 1, 1, c[0], 0xFF4F8A3A, 0xFF2A2233); break;
            case THANKSGIVING: sprite(d, LEAF, sx, sy + 1, 1, c[0], 0xFF6B3A1E); break;
            case NEW_YEAR:
            default: sprite(d, FLAKE_SMALL, sx + 2, sy + 3, 1, c[0]); break;
        }
        d.text(g, x + 17, y + 3, Theme.TEXT, true);
    }
}
