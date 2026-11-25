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
        button(d, x, y, w, h, primary, hover, accent, false);
    }

    static void button(Draw d, int x, int y, int w, int h, boolean primary, float hover, int accent, boolean pressed) {
        int face = primary ? Theme.mix(accent, 0xFFFFFFFF, hover * 0.14f) : Theme.mix(ROW, ROW_HOVER, hover);
        bevel(d, x, y, w, h, face, pressed);
    }

    static void bevel(Draw d, int x, int y, int w, int h, int face, boolean pressed) {
        if (w < 6 || h < 6) {
            d.rect(x, y, w, h, face);
            return;
        }
        int alpha = face >>> 24;
        int light = Theme.withAlpha(0xFFFFFF, alpha);
        int dark = Theme.withAlpha(0, alpha);
        int edge = Theme.mix(face, dark, 0.48f);
        d.roundRect(x, y, w, h, 2, edge);
        d.roundRect(x + 1, y + (pressed ? 2 : 1), w - 2, h - 3, 1,
                pressed ? Theme.mix(face, dark, 0.12f) : face);
        if (!pressed) {
            d.rect(x + 2, y + 1, w - 4, 1, Theme.mix(face, light, 0.24f));
        }
    }

    static void toggle(Draw d, int x, int y, int w, int h, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0xFF4C5564 : 0xFF3A414D, accent, on);
        d.roundRect(x, y, w, h, 3, track);
        int knob = h - 4;
        int kx = x + 2 + Math.round(on * (w - h));
        bevel(d, kx, y + 2, knob, knob, 0xFFF1F2F5, false);
    }
}
