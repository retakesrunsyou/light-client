package net.peregrine.client.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Holiday themes for the main menu and the pause menu: Christmas, New Year, Valentine's, Easter,
 * Halloween and Thanksgiving. Each recolours the menus (the accent) and has its own greeting and
 * scene: snow for Christmas, fireworks for New Year, real-looking hearts falling for Valentine's,
 * eggs in the grass for Easter, bats and pumpkins for Halloween, real maple and oak leaves
 * fluttering down for Thanksgiving. Each runs from the 1st of its month (see byDate).
 *
 * Kept light: a few dozen small pictures (one textured square each), only while a menu is open.
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
        {0xFFD8343C, 0xFF2E9E57, 0xFFF4F7FF},   // Christmas red, pine green, snow white
        {0xFFE8B84A, 0xFFB98CFF, 0xFF7FD7FF},   // gold, violet, ice blue
        {0xFFE0304E, 0xFFFF6F96, 0xFFC4122F},   // red, pink, deep red
        {0xFFEE8AB6, 0xFF8ED081, 0xFFA9D8FF},   // pink, spring green, sky
        {0xFFFF7A1A, 0xFF8E5BD8, 0xFF2A2233},   // pumpkin, purple, night
        {0xFFD9752B, 0xFFB5432A, 0xFFE8B04A},   // harvest orange, maple red, gold
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

    /**
     * Each theme starts on the 1st of its holiday's month and ends on the day: Christmas from
     * December 1st, New Year from New Year's Eve to January 7th, Valentine's February 1st to 14th,
     * Easter from the 1st of its month to Easter Monday, Halloween all October, Thanksgiving from
     * November 1st to Thanksgiving Day.
     */
    public static int byDate(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if ((m == 12 && day == 31) || (m == 1 && day <= 7)) {
            return NEW_YEAR;
        }
        if (m == 12) {
            return CHRISTMAS;
        }
        if (m == 2 && day <= 14) {
            return VALENTINES;
        }
        LocalDate easter = easter(d.getYear());
        if (m == easter.getMonthValue() && !d.isAfter(easter.plusDays(1))) {
            return EASTER;
        }
        if (m == 10) {
            return HALLOWEEN;
        }
        if (m == 11 && !d.isAfter(thanksgiving(d.getYear()))) {
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

    static final String TEX = "peregrine-client:textures/gui/holiday/";
    static final String[] HEARTS = {"heart_red.png", "heart_red.png", "heart_deep.png", "heart_pink.png"};
    static final String[] LEAVES = {"maple_red.png", "maple_orange.png", "oak_gold.png", "oak_brown.png", "maple_orange.png"};
    static final int[] LEAF_COLORS = {0xFFD2361F, 0xFFE4731F, 0xFFE8B84A, 0xFF8A4F24, 0xFFE4731F};
    static final int[] FIREWORK = {0xFFF2C14E, 0xFFB98CFF, 0xFF7FD7FF, 0xFFFF7AA2, 0xFF8EF0A8, 0xFFFFFFFF};

    /** A picture centred at cx, cy, `size` across, turned by rot and squashed sideways by flip (0..1). */
    static void pic(Draw d, String name, float cx, float cy, float size, float rot, float flip, int argb) {
        if (d.supportsQuads()) {
            float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
            float ax = c * size * flip, ay = s * size * flip, bx = -s * size, by = c * size;
            d.textureQuad(TEX + name, cx - (ax + bx) / 2, cy - (ay + by) / 2, ax, ay, bx, by, 0f, 0f, 1f, 1f, argb);
        } else {
            int w = Math.max(1, Math.round(size * flip)), h = Math.max(1, Math.round(size));
            d.texture(TEX + name, Math.round(cx - w / 2f), Math.round(cy - h / 2f), w, h, 0f, 0f, 1f, 1f, argb);
        }
    }

    /** One drifting thing in a scene. */
    static final class Bit {
        float x, y, vx, vy, phase, life, age, rot, spin, px, py;
        int kind, color, size, pic;
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
        private float nextRocket;

        /** count: about how many things drift at once (fewer over the game, more on the main menu). */
        public Scene(int count) {
            this.count = count;
        }

        public void render(Draw d, int theme, int x, int y, int w, int h, float dt) {
            if (theme <= 0 || w <= 0 || h <= 0) {
                return;
            }
            boolean pics = d.hasTextures();
            if (theme != this.theme || Math.abs(w - this.w) > 40 || Math.abs(h - this.h) > 40) {
                this.theme = theme;
                this.w = w;
                this.h = h;
                bits.clear();
                int n = theme == NEW_YEAR ? count / 3 : count;
                for (int i = 0; i < n; i++) {
                    bits.add(spawn(true));
                }
            }
            int[] c = COLORS[theme];
            long ms = System.currentTimeMillis();
            // a soft glow of the theme's colour rising from the bottom
            d.rect(x, y + h - h / 4, w, h / 4, Theme.withAlpha(c[0], 0x0A));
            d.rect(x, y + h - h / 9, w, h / 9, Theme.withAlpha(c[0], 0x0C));
            if (theme == CHRISTMAS) {   // pine green across the top, red rising from the bottom
                d.rect(x, y, w, h / 5, Theme.withAlpha(c[1], 0x10));
                d.rect(x, y, w, h / 12, Theme.withAlpha(c[1], 0x0C));
            }
            if (theme == NEW_YEAR) {
                nextRocket -= dt;
                if (nextRocket <= 0) {
                    bits.add(rocket());
                    nextRocket = 0.5f + rnd.nextFloat() * 1.1f;
                }
            }
            for (int i = bits.size() - 1; i >= 0; i--) {
                Bit b = bits.get(i);
                b.age += dt;
                b.px = b.x;
                b.py = b.y;
                b.x += (b.vx + (float) Math.sin(b.age * 1.3f + b.phase) * sway(b)) * dt;
                b.y += b.vy * dt;
                b.rot += b.spin * dt;
                if (b.kind == 9) {
                    b.vy += 22 * dt;      // sparks fall as they fade
                    b.vx *= 1 - 0.9f * dt;
                }
                if (b.kind == 8 && b.age >= b.life) {   // the rocket bursts
                    burst(b.x, b.y);
                    bits.remove(i);
                    continue;
                }
                boolean gone = b.life > 0 ? b.age >= b.life : (b.vy > 0 ? b.y > h + 24 : b.y < -24) || b.x < -30 || b.x > w + 30;
                if (gone) {
                    if (b.kind == 9 || b.kind == 8) {
                        bits.remove(i);
                    } else {
                        bits.set(i, spawn(false));
                    }
                    continue;
                }
                draw(d, b, x + b.x, y + b.y, ms, pics);
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
                case 4: return 26f;           // leaves flutter side to side
                case 2: return 14f;           // hearts
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
                    b.vy = 9 + rnd.nextFloat() * 14;
                    b.vx = -3 + rnd.nextFloat() * 6;
                    b.color = Theme.withAlpha(0xFFFFFF, 0x90 + rnd.nextInt(0x60));
                    b.y = anywhere ? rnd.nextFloat() * h : -10;
                    break;
                case VALENTINES:
                    b.kind = 2;     // hearts falling, tilting as they sway
                    b.size = 7 + rnd.nextInt(9);
                    b.vy = 10 + rnd.nextFloat() * 14;
                    b.vx = -2 + rnd.nextFloat() * 4;
                    b.pic = rnd.nextInt(HEARTS.length);
                    b.color = COLORS[VALENTINES][b.pic == 3 ? 1 : b.pic == 2 ? 2 : 0];
                    b.y = anywhere ? rnd.nextFloat() * h : -14;
                    break;
                case EASTER:
                    b.kind = 6;
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
                        b.vy = -(6 + rnd.nextFloat() * 10);
                        b.vx = -2 + rnd.nextFloat() * 4;
                        b.color = rnd.nextBoolean() ? 0xFFFF8A1F : 0xFFFFC14D;
                        b.y = anywhere ? rnd.nextFloat() * h : h + 4;
                    }
                    break;
                case THANKSGIVING:
                    b.kind = 4;     // a maple or oak leaf, turning and flipping as it falls
                    b.size = 9 + rnd.nextInt(10);
                    b.vy = 11 + rnd.nextFloat() * 13;
                    b.vx = -7 + rnd.nextFloat() * 6;
                    b.spin = (rnd.nextBoolean() ? 1 : -1) * (0.4f + rnd.nextFloat() * 1.4f);
                    b.rot = rnd.nextFloat() * 6.3f;
                    b.pic = rnd.nextInt(LEAVES.length);
                    b.color = LEAF_COLORS[b.pic];
                    b.y = anywhere ? rnd.nextFloat() * h : -16;
                    break;
                case NEW_YEAR:
                default:
                    b.kind = 7;   // a little confetti between the fireworks
                    b.vy = 14 + rnd.nextFloat() * 16;
                    b.vx = -4 + rnd.nextFloat() * 8;
                    b.color = FIREWORK[rnd.nextInt(FIREWORK.length)];
                    b.y = anywhere ? rnd.nextFloat() * h : -6;
                    break;
            }
            return b;
        }

        /** A firework rocket: climbs from the bottom with a trail, then bursts. */
        private Bit rocket() {
            Bit b = new Bit();
            b.kind = 8;
            b.x = w * (0.12f + rnd.nextFloat() * 0.76f);
            b.y = h + 4;
            b.vy = -(h * (0.75f + rnd.nextFloat() * 0.35f));
            b.vx = -6 + rnd.nextFloat() * 12;
            b.life = 0.55f + rnd.nextFloat() * 0.35f;
            b.color = FIREWORK[rnd.nextInt(FIREWORK.length)];
            return b;
        }

        /** A burst: a ring of glowing sparks (sometimes two colours) flying out and falling. */
        private void burst(float cx, float cy) {
            int c1 = FIREWORK[rnd.nextInt(FIREWORK.length)], c2 = rnd.nextFloat() < 0.4f ? FIREWORK[rnd.nextInt(FIREWORK.length)] : c1;
            int n = 26 + rnd.nextInt(10);
            float speed = 34 + rnd.nextFloat() * 22;
            for (int i = 0; i < n; i++) {
                Bit b = new Bit();
                double ang = Math.PI * 2 * i / n + rnd.nextFloat() * 0.15;
                float sp = speed * (0.85f + rnd.nextFloat() * 0.3f);
                b.kind = 9;
                b.x = cx;
                b.y = cy;
                b.vx = (float) Math.cos(ang) * sp;
                b.vy = (float) Math.sin(ang) * sp;
                b.life = 1.2f + rnd.nextFloat() * 0.6f;
                b.color = i % 2 == 0 ? c1 : c2;
                b.phase = rnd.nextFloat() * 6.3f;
                bits.add(b);
            }
        }

        private void draw(Draw d, Bit b, float fx, float fy, long ms, boolean pics) {
            int x = Math.round(fx), y = Math.round(fy);
            switch (b.kind) {
                case 0:
                    sprite(d, SNOWFLAKE, x, y, 1, b.color);
                    break;
                case 1:
                    sprite(d, FLAKE_SMALL, x, y, 1, b.color);
                    break;
                case 2: {
                    float tilt = (float) Math.sin(b.age * 1.3f + b.phase) * 0.35f;
                    if (pics) {
                        pic(d, HEARTS[b.pic], fx, fy, b.size, tilt, 1f, 0xF0FFFFFF);
                    } else {
                        sprite(d, HEART, x, y, b.size > 11 ? 2 : 1, b.color, 0xC0FFFFFF);
                    }
                    break;
                }
                case 3: {
                    boolean up = ((ms / 140) + (long) (b.phase * 10)) % 2 == 0;
                    sprite(d, up ? BAT_UP : BAT_DOWN, x, y, b.size, b.color);
                    break;
                }
                case 4: {
                    float flip = 0.3f + 0.7f * Math.abs((float) Math.cos(b.age * 2.1f + b.phase));
                    if (pics) {
                        pic(d, LEAVES[b.pic], fx, fy, b.size, b.rot, flip, 0xF2FFFFFF);
                    } else {
                        sprite(d, LEAF, x, y, b.size > 13 ? 2 : 1, b.color, 0xFF6B3A1E);
                    }
                    break;
                }
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
                    d.rect(x, y, flat ? 2 : 1, flat ? 1 : 2, Theme.withAlpha(b.color, 0xB0));
                    break;
                }
                case 8: {   // rocket: a bright head and a fading trail below it
                    for (int k = 1; k <= 4; k++) {
                        d.rect(x, y + k * 2, 1, 2, Theme.withAlpha(0xFFE7B0, 0xC0 - k * 0x28));
                    }
                    if (pics) {
                        pic(d, "spark.png", fx + 0.5f, fy + 0.5f, 6, 0, 1, Theme.withAlpha(0xFFF0C8, 0xE0));
                    } else {
                        d.rect(x, y, 1, 1, 0xFFFFF0C8);
                    }
                    break;
                }
                case 9: {
                    float k = b.age / b.life;
                    // the sparks flicker as they die out
                    float flick = k > 0.6f ? (0.55f + 0.45f * (float) Math.sin(b.age * 40 + b.phase)) : 1f;
                    int a = Math.round(0xFF * (1 - k) * flick);
                    if (pics) {
                        pic(d, "spark.png", fx, fy, 5 - 2 * k, 0, 1, Theme.withAlpha(b.color, a));
                        if (k < 0.5f) {
                            pic(d, "spark.png", (fx + b.px) / 2 - b.vx * 0.03f, (fy + b.py) / 2 - b.vy * 0.03f, 3, 0, 1, Theme.withAlpha(b.color, a / 3));
                        }
                    } else {
                        d.rect(x, y, 1, 1, Theme.withAlpha(b.color, a));
                    }
                    break;
                }
                default:
                    break;
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
            case VALENTINES:
                if (d.hasTextures()) {
                    pic(d, "heart_red.png", sx + 4, sy + 4, 9, 0, 1, 0xFFFFFFFF);
                } else {
                    sprite(d, HEART, sx, sy + 2, 1, c[0], 0xFFFFFFFF);
                }
                break;
            case EASTER: sprite(d, EGG, sx + 1, sy + 1, 1, c[1], 0xFFFFFFFF, c[2]); break;
            case HALLOWEEN: sprite(d, PUMPKIN, sx - 1, sy + 1, 1, c[0], 0xFF4F8A3A, 0xFF2A2233); break;
            case THANKSGIVING:
                if (d.hasTextures()) {
                    pic(d, "maple_red.png", sx + 4, sy + 4, 10, -0.3f, 1, 0xFFFFFFFF);
                } else {
                    sprite(d, LEAF, sx, sy + 1, 1, c[0], 0xFF6B3A1E);
                }
                break;
            case NEW_YEAR:
            default: sprite(d, FLAKE_SMALL, sx + 2, sy + 3, 1, c[0]); break;
        }
        d.text(g, x + 17, y + 3, Theme.TEXT, true);
    }
}
