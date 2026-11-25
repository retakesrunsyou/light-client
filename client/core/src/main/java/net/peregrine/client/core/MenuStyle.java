package net.peregrine.client.core;

/** The surfaces and controls shared by the in-game menu and its pages. */
final class MenuStyle {
    static final int WINDOW = 0xFA171A21;
    static final int FIELD = 0xFF101217;
    static final int BORDER = 0xFF373D49;
    static final int ROW = 0xFF20242D;
    static final int ROW_HOVER = 0xFF2A2F3A;

    private MenuStyle() {
    }

    static void panel(Draw d, int x, int y, int w, int h, int fill) {
        d.roundRect(x, y, w, h, 3, fill);
        d.roundOutline(x, y, w, h, 3, BORDER);
    }

    static void button(Draw d, int x, int y, int w, int h, boolean primary, float hover, int accent) {
        if (primary) {
            d.roundRect(x, y, w, h, 3, Theme.mix(accent, 0xFFFFFFFF, hover * 0.14f));
        } else {
            panel(d, x, y, w, h, Theme.mix(ROW, ROW_HOVER, hover));
        }
    }

    static void toggle(Draw d, int x, int y, int w, int h, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0xFF4C5564 : 0xFF3A414D, accent, on);
        d.roundRect(x, y, w, h, 3, track);
        int knob = h - 4;
        int kx = x + 2 + Math.round(on * (w - h));
        d.roundRect(kx, y + 2, knob, knob, 2, 0xFFF1F2F5);
    }
}
