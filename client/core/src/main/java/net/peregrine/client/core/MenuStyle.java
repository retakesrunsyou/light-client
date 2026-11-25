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
        int face = primary ? accent : 0xFF41454E;
        block(d, x, y, w, h, face, hover, pressed);
    }

    static void block(Draw d, int x, int y, int w, int h, int face, float hover, boolean pressed) {
        if (w < 6 || h < 6) {
            d.rect(x, y, w, h, face);
            return;
        }
        int top = y + (pressed ? 1 : 0);
        int height = h - (pressed ? 1 : 0);
        int depth = pressed ? 1 : 2;
        int faceHeight = height - depth - 2;
        int fill = Theme.mix(face, pressed ? 0xFF000000 : 0xFFFFFFFF, pressed ? 0.14f : 0.12f * hover);
        d.rect(x, top, w, height, hover > 0.7f && !pressed ? 0xFFCFD2D8 : 0xFF08090B);
        d.rect(x + 1, top + 1, w - 2, height - 2, Theme.mix(fill, 0xFF000000, 0.45f));
        d.rect(x + 1, top + 1, w - 2, faceHeight, fill);
        if (d.hasTextures()) {
            d.texture(Draw.GUI + "button-grain.png", x + 1, top + 1, w - 2, faceHeight,
                    0, 0, 1, 1, 0xFFFFFFFF);
        }
        int light = Theme.mix(fill, 0xFFFFFFFF, 0.42f);
        d.rect(x + 1, top + 1, w - 2, 1, light);
        d.rect(x + 1, top + 1, 1, faceHeight, light);
        d.rect(x + w - 2, top + 2, 1, faceHeight - 1, Theme.mix(fill, 0xFF000000, 0.17f));
    }

    static void toggle(Draw d, int x, int y, int w, int h, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0xFF4C5564 : 0xFF3A414D, accent, on);
        d.roundRect(x, y, w, h, 3, track);
        int knob = h - 4;
        int kx = x + 2 + Math.round(on * (w - h));
        d.roundRect(kx, y + 2, knob, knob, 2, 0xFFF1F2F5);
    }
}
