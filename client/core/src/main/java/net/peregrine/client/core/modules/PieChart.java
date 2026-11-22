package net.peregrine.client.core.modules;

import java.util.ArrayList;
import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;
import net.peregrine.client.core.settings.TextSetting;

/**
 * The game's own profiler pie chart (the one F3 can show) as a HUD item you can move and size.
 * The numbers are Minecraft's: while this is on screen the game measures each frame the same way
 * it does for F3's chart, and this draws exactly what it measured. Number keys go into a part
 * (1-9) and back out (0), like F3's chart.
 */
public final class PieChart extends HudModule {

    /** Between the names in a profiler path, as the game writes it. */
    private static final char SEP = '\u001e';
    private static volatile long shownAt;

    public final TextSetting section = add(new TextSetting("section", "Part shown", "root", 300, "root"));
    public final BoolSetting keys = add(new BoolSetting("keys", "Number key navigation", true));
    public final SliderSetting size = add(new SliderSetting("radius", "Chart size", 16f, 80f, 2f, 36f, "%.0f px"));
    public final SliderSetting parts = add(new SliderSetting("parts", "Parts listed", 3f, 16f, 1f, 10f, "%.0f"));
    public final BoolSetting legend = add(new BoolSetting("legend", "List of parts", true));
    public final BoolSetting whole = add(new BoolSetting("whole", "Show share of frame", false));

    private long firstAsked;

    public PieChart() {
        super("pie_chart", "Pie chart",
                "Profiler pie chart",
                false, 1f, 0.55f);
    }

    /** True while the chart is on screen: the game measures frames for it only then. */
    public static boolean collecting() {
        return System.currentTimeMillis() - shownAt < 1500L;
    }

    /** The path as the game writes it ('/' in the setting, the game's own separator inside). */
    private String path() {
        String s = section.value == null ? "" : section.value.trim();
        if (s.isEmpty()) {
            s = "root";
        }
        return s.replace('/', SEP);
    }

    // ---- the data

    /** One part of the chart. */
    private static final class Part {
        final String name;
        final double share;    // of the part shown, %
        final double global;   // of the whole frame, %
        final int color;
        final int index;       // 1-based place in the game's list (for the number keys), 0 for "other"

        Part(String name, double share, double global, int color, int index) {
            this.name = name;
            this.share = share;
            this.global = global;
            this.color = 0xFF000000 | color;
            this.index = index;
        }
    }

    private Part self;
    private final List<Part> list = new ArrayList<>();
    private boolean demo;

    private void read(Platform p) {
        shownAt = System.currentTimeMillis();
        list.clear();
        self = null;
        demo = false;
        List<Object[]> rows = p.profilerTimes(path());
        if (rows == null || rows.isEmpty()) {
            if (!p.inWorld()) {   // a sample to place it with (Edit HUD from the title screen)
                demo = true;
                self = new Part("root", 100, 100, 0x888888, 0);
                String[] n = {"gameRenderer", "tick", "blit", "sound", "unspecified"};
                double[] v = {52, 27, 11, 6, 4};
                int[] c = {0x4FA3E0, 0xE0904F, 0x7FD06A, 0xC86FD8, 0x9A9A9A};
                for (int i = 0; i < n.length; i++) {
                    list.add(new Part(n[i], v[i], v[i], c[i], i + 1));
                }
            }
            return;
        }
        Object[] s = rows.get(0);
        self = new Part((String) s[0], 100, ((Number) s[2]).doubleValue(), ((Number) s[3]).intValue(), 0);
        int max = Math.round(parts.value);
        double rest = 0, restGlobal = 0;
        for (int i = 1; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            double share = ((Number) r[1]).doubleValue(), global = ((Number) r[2]).doubleValue();
            if (list.size() < max) {
                list.add(new Part((String) r[0], share, global, ((Number) r[3]).intValue(), i));
            } else {
                rest += share;
                restGlobal += global;
            }
        }
        if (rest > 0) {
            list.add(new Part("other", rest, restGlobal, 0x606060, 0));
        }
    }

    // ---- number keys

    @Override
    public void keyPressed(int key, Platform p) {
        if (!keys.value || key < '0' || key > '9') {
            return;
        }
        String path = path();
        int n = key - '0';
        if (n == 0) {
            int cut = path.lastIndexOf(SEP);
            if (cut > 0) {
                section.value = path.substring(0, cut).replace(SEP, '/');
                Peregrine.get().markDirty();
            }
            return;
        }
        List<Object[]> rows = p.profilerTimes(path);
        if (rows == null || n >= rows.size()) {
            return;
        }
        String name = (String) rows.get(n)[0];
        if ("unspecified".equals(name) || name.isEmpty()) {
            return;
        }
        section.value = (path + SEP + name).replace(SEP, '/');
        Peregrine.get().markDirty();
    }

    // ---- size

    private int radius() {
        return Math.round(size.value);
    }

    private int legendWidth(Draw d) {
        if (!legend.value) {
            return 0;
        }
        int w = d.width("[9] ") + 92 + d.width(" 100.0%");
        return whole.value ? w + d.width(" 100.0%") + 2 : w;
    }

    private int rows() {
        return Math.max(list.size(), demo ? 5 : 1);
    }

    @Override
    public int width(Draw d, Platform p) {
        int lw = legendWidth(d);
        return 6 + radius() * 2 + (lw > 0 ? 8 + lw : 0) + 6;
    }

    @Override
    public int height(Draw d, Platform p) {
        int lh = d.lineHeight() + 1;
        int title = lh + 3;
        int body = Math.max(radius() * 2, legend.value ? rows() * lh : 0);
        return 5 + title + body + 5;
    }

    // ---- drawing

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        read(p);
        if (firstAsked == 0) {
            firstAsked = System.currentTimeMillis();
        }
        int w = width(d, p), h = height(d, p);
        d.roundRect(x, y, w, h, Theme.HUD_BG);
        int lh = d.lineHeight() + 1;
        int accent = Peregrine.get().accent();
        String where = ProfileTitle.of(path());
        String title = self == null ? where : where + "  " + pct(self.global);
        d.text(d.trim(title, w - 10), x + 5, y + 5, accent, true);
        int top = y + 5 + lh + 3;
        int r = radius();
        int cx = x + 6 + r, cy = top + r;
        if (self == null || list.isEmpty()) {
            boolean waiting = System.currentTimeMillis() - firstAsked < 3000L;
            String msg = waiting ? "Measuring..." : p.inWorld() ? "Not on this Minecraft version" : "Join a world";
            d.roundRect(cx - r, cy - r, r * 2, r * 2, r, 0x40FFFFFF);
            d.text(d.trim(msg, w - 12), x + 6, cy + r + 2 > y + h - 9 ? cy - 4 : cy + r + 2, Theme.TEXT, true);
            return;
        }
        double total = 0;
        for (Part part : list) {
            total += Math.max(0, part.share);
        }
        if (total <= 0) {
            total = 1;
        }
        double[] cum = new double[list.size() + 1];
        int[] col = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            cum[i + 1] = cum[i] + Math.max(0, list.get(i).share) / total * Math.PI * 2;
            col[i] = list.get(i).color;
        }
        int q = (int) Math.max(1, Math.min(4, Math.round(p.guiScale() * scale)));
        pie(d, cx, cy, r, cum, col, q);
        if (!legend.value) {
            return;
        }
        int lx = x + 6 + r * 2 + 8;
        int lw = legendWidth(d);
        int ly = top + Math.max(0, (r * 2 - list.size() * lh) / 2);
        int numW = d.width("[9] ");
        int pctW = d.width(" 100.0%");
        for (Part part : list) {
            d.rect(lx, ly + 1, 6, 6, part.color);
            String num = part.index > 0 && part.index <= 9 && keys.value ? "[" + part.index + "] " : "";
            int nameX = lx + 9;
            if (!num.isEmpty()) {
                d.text(num, nameX, ly, 0xFFB0B0B8, true);
            }
            int right = lx + lw;
            String share = pct(part.share);
            int nameEnd = right - pctW - (whole.value ? pctW + 2 : 0);
            d.text(d.trim(ProfileTitle.demangle(part.name), nameEnd - nameX - numW), nameX + numW, ly, Theme.TEXT, true);
            if (whole.value) {
                String g = pct(part.global);
                d.text(g, right - d.width(g), ly, 0xFF9A9AA6, true);
                right -= pctW + 2;
            }
            d.text(share, right - d.width(share), ly, part.color | 0xFF000000, true);
            ly += lh;
        }
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", v);
    }

    /**
     * Fills a pie of radius r around (cx, cy): slice i runs clockwise from straight up between
     * angles cum[i] and cum[i + 1]. Drawn in rows of q per pixel, so it's smooth at any size: each
     * row is cut where it crosses a slice's edge, and each piece filled in one go.
     */
    static void pie(Draw d, int cx, int cy, int r, double[] cum, int[] col, int q) {
        int n = col.length;
        double big = r * q;
        int rows = (int) Math.ceil(big);
        double[] xs = new double[n + 2];
        d.pushScale(cx, cy, 1f / q);
        for (int yy = -rows; yy < rows; yy++) {
            double yc = yy + 0.5;
            double hw2 = big * big - yc * yc;
            if (hw2 <= 0) {
                continue;
            }
            double hw = Math.sqrt(hw2);
            int m = 0;
            xs[m++] = -hw;
            for (int k = 0; k < n; k++) {   // where each slice's starting edge crosses this row
                double th = cum[k];
                double dy = -Math.cos(th);   // screen y goes down
                if (Math.abs(dy) < 1e-9) {
                    continue;
                }
                double t = yc / dy;
                if (t <= 0) {
                    continue;
                }
                double xx = t * Math.sin(th);
                if (xx > -hw && xx < hw) {
                    xs[m++] = xx;
                }
            }
            xs[m++] = hw;
            java.util.Arrays.sort(xs, 0, m);
            for (int s = 0; s + 1 < m; s++) {
                int xa = (int) Math.round(xs[s]), xb = (int) Math.round(xs[s + 1]);
                if (xb <= xa) {
                    continue;
                }
                double mid = (xs[s] + xs[s + 1]) / 2;
                double a = Math.atan2(mid, -yc);
                if (a < 0) {
                    a += Math.PI * 2;
                }
                int k = n - 1;
                for (int i = 0; i < n; i++) {
                    if (a < cum[i + 1]) {
                        k = i;
                        break;
                    }
                }
                d.rect(xa, yy, xb - xa, 1, col[k]);
            }
        }
        d.popScale();
    }

    /** How a profiler path reads on screen. */
    static final class ProfileTitle {
        private ProfileTitle() {
        }

        static String of(String path) {
            String[] parts = path.split(String.valueOf(SEP));
            StringBuilder b = new StringBuilder();
            int from = Math.max(0, parts.length - 3);   // the last few, so it fits
            if (from > 0) {
                b.append("... > ");
            }
            for (int i = from; i < parts.length; i++) {
                if (i > from) {
                    b.append(" > ");
                }
                b.append(demangle(parts[i]));
            }
            return b.toString();
        }

        /** The game marks some names (#name for counters); show them plainly. */
        static String demangle(String name) {
            return name.startsWith("#") ? name.substring(1) : name;
        }
    }
}
