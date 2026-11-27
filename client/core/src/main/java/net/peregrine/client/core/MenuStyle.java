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
        if (primary) {
            flat(d, x, y, w, h, accent, Theme.mix(accent, 0xFFFFFFFF, 0.14f), accent, hover, pressed);
        } else {
            flat(d, x, y, w, h, 0xF01D1E25, 0xF02B2C36, 0x26FFFFFF, hover, pressed);
        }
    }

    /** A flat rounded button: a face that lightens on hover, a hairline edge, nothing else. */
    static void flat(Draw d, int x, int y, int w, int h, int face, int faceHover, int edge, float hover, boolean pressed) {
        if (w < 6 || h < 6) {
            d.rect(x, y, w, h, face);
            return;
        }
        int fill = Theme.mix(face, faceHover, hover);
        if (pressed) {
            fill = Theme.mix(fill, 0xFF000000, 0.16f);
        }
        d.roundRect(x, y, w, h, 3, fill);
        d.roundOutline(x, y, w, h, 3, Theme.mix(edge, Theme.withAlpha(0xFFFFFF, 0x4A), hover * ((edge >>> 24) < 0xF0 ? 1f : 0f)));
    }

    /** The old name, kept for callers that pass their own face colour (the pause menu's Leave). */
    static void block(Draw d, int x, int y, int w, int h, int face, float hover, boolean pressed) {
        flat(d, x, y, w, h, face, Theme.mix(face, 0xFFFFFFFF, 0.12f), Theme.withAlpha(0xFFFFFF, 0x26), hover, pressed);
    }

    static void toggle(Draw d, int x, int y, int w, int h, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0xFF4C5564 : 0xFF3A414D, accent, on);
        d.roundRect(x, y, w, h, 3, track);
        int knob = h - 4;
        int kx = x + 2 + Math.round(on * (w - h));
        d.roundRect(kx, y + 2, knob, knob, 2, Theme.mix(0xFFF1F2F5, Theme.onColor(accent), on));
    }
}
