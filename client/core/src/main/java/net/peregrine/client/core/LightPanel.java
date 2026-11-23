package net.peregrine.client.core;

import net.peregrine.client.core.modules.LightEngine;
import net.peregrine.client.core.modules.Saturation;

/** The Performance page (Light Engine presets, saturation) and the What's new page in the Right Shift menu. */
final class LightPanel extends MenuPanel {

    /** What changed, newest first: {version, title, what it does}. */
    static final String[][] NEWS = {
        {"2.2.1", "Friends", "Chat and call Light Client friends from the launcher"},
        {"2.2.1", "Tidier mod menu", "Tabs, an Only on button and an on count"},
        {"2.2.1", "New Lyrics", "3D words around you, overhead or on the floor"},
        {"2.2.1", "Chat and scoreboard", "Move and resize them in Edit HUD"},
        {"2.2.0", "GPU mobs", "Mobs drawn on the graphics card (experimental)"},
        {"2.2.0", "Holiday themes", "Seasonal colors and effects"},
        {"2.2.0", "Music in the pause menu", "Song, cover and controls"},
        {"2.2.0", "New skies", "Realistic stars, sun and moon"},
        {"2.2.0", "Light Engine", "One-click FPS presets"},
        {"2.2.0", "Lyrics", "Synced lyrics on screen or in 3D"},
        {"2.2.0", "Dropdown menus", "Pick options from a list"},
        {"2.2.0", "Server icons", "Real icons in the HUD and pause menu"},
        {"2.1.3", "Pie chart", "Minecraft's own profiler pie chart as a HUD item you can move and size"},
        {"2.1.3", "Sliders", "Dragging a slider follows your mouse again, in the flat and 3D menus"},
        {"2.1.2", "Sharper shared capes", "Other players see your cape twice as sharp, every frame"},
        {"2.1.2", "Toggle sneak", "Stays crouched in menus and when you tab out, on every version"},
    };

    private final boolean performance;
    private String feedback = "";
    private long feedbackAt;

    LightPanel(Peregrine pc, Menu menu, boolean performance) {
        super(pc, menu);
        this.performance = performance;
    }

    @Override
    String placeholder() {
        return "";
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int accent) {
        menu.clipArea(d, x, this.y, w, h);
        int y = this.y + 3 - Math.round(smooth);
        if (performance) {
            y = performance(d, mx, my, dt, now, accent, y);
        } else {
            y = news(d, accent, y);
        }
        maxScroll = Math.max(0, y + 13 + Math.round(smooth) - this.y - h);
        menu.unclipArea(d);
    }

    // ---- Performance

    private int performance(Draw d, int mx, int my, float dt, long now, int accent, int y) {
        LightEngine engine = (LightEngine) pc.module("light_engine");
        Platform p = pc.platform();
        d.textScaled("Light Engine", x + 8, y + 4, Theme.TEXT, 1.28f, false);
        String fps = p == null ? "" : p.fps() + " FPS";
        d.text(fps, x + w - 8 - d.width(fps), y + 7, accent, false);
        d.text("One-click FPS presets", x + 8, y + 20, Theme.MUTED, false);
        y += 34;
        // the switch
        boolean on = engine != null && engine.enabled();
        int bx = x + 3, bw = w - 6;
        float hv = glow("engine", in(mx, my, bx, y, bw, 30), dt);
        d.roundRect(bx, y, bw, 30, 6, Theme.mix(on ? Theme.withAlpha(accent, 0x40) : 0x80213043,
                on ? Theme.withAlpha(accent, 0x60) : 0xB0374E64, hv));
        d.text(on ? "On: " + LightEngine.PRESETS[engine.preset.index] : "Off: your own settings", bx + 10, y + 6, Theme.TEXT, false);
        d.text(on ? "Turn off to restore your settings" : "Turn on to apply the preset", bx + 10, y + 17,
                Theme.MUTED, false);
        toggle(d, bx + bw - 34, y + 9, on, accent);
        spot("engine", bx, y, bw, 30);
        y += 36;
        // presets, two to a row
        int gap = 4, cw = (bw - gap) / 2;
        for (int i = 0; i < LightEngine.PRESETS.length; i++) {
            int cx = bx + (i % 2) * (cw + gap), cy = y + (i / 2) * 42;
            boolean picked = on && engine.preset.index == i;
            float g = glow("preset:" + i, in(mx, my, cx, cy, cw, 38), dt);
            d.roundRect(cx, cy, cw, 38, 6, Theme.mix(picked ? Theme.withAlpha(accent, 0x38) : 0x70213043,
                    picked ? Theme.withAlpha(accent, 0x58) : 0xA0374E64, g));
            if (picked) {
                d.roundOutline(cx, cy, cw, 38, 6, Theme.withAlpha(accent, 0xC0));
            }
            d.text(LightEngine.PRESETS[i], cx + 8, cy + 6, picked ? accent : Theme.TEXT, false);
            d.text(d.trim(LightEngine.ABOUT[i], cw - 14), cx + 8, cy + 19, Theme.MUTED, false);
            spot("preset:" + i, cx, cy, cw, 38);
        }
        y += 2 * 42 + 4;
        // saturation
        Saturation sat = (Saturation) pc.module("saturation");
        if (sat != null && (p == null || p.supports("saturation"))) {
            boolean son = sat.enabled();
            d.roundRect(bx, y, bw, 30, 6, 0x80213043);
            d.text("World saturation", bx + 10, y + 6, Theme.TEXT, false);
            d.text(son ? "How vivid everything is" : "Off: the game's own colours", bx + 10, y + 17, Theme.MUTED, false);
            String val = son ? Math.round(sat.amount.value) + "%" : "Off";
            int right = bx + bw - 8;
            chip(d, mx, my, dt, accent, "sat+", "+", right - 16, y + 7);
            d.text(val, right - 22 - d.width(val), y + 11, son ? accent : Theme.MUTED, false);
            chip(d, mx, my, dt, accent, "sat-", "-", right - 22 - d.width(val) - 22, y + 7);
            y += 36;
        }
        y = row(d, mx, my, dt, accent, "Every performance feature", "Fine-tune each one", y, "modules");
        if (!feedback.isEmpty() && System.currentTimeMillis() - feedbackAt < 4000) {
            d.text(d.trim(feedback, w - 16), x + 8, y + 2, accent, false);
            y += 12;
        }
        return y;
    }

    private void toggle(Draw d, int x, int y, boolean on, int accent) {
        d.roundRect(x, y, 24, 12, 6, on ? accent : 0xFF3A4252);
        d.roundRect(on ? x + 13 : x + 1, y + 1, 10, 10, 5, 0xFFFFFFFF);
    }

    private void chip(Draw d, int mx, int my, float dt, int accent, String id, String label, int x, int y) {
        float g = glow(id, in(mx, my, x, y, 16, 16), dt);
        d.roundRect(x, y, 16, 16, 4, Theme.mix(0xA0374E64, Theme.withAlpha(accent, 0x90), g));
        d.text(label, x + (16 - d.width(label)) / 2, y + 4, Theme.TEXT, false);
        spot(id, x, y, 16, 16);
    }

    private int row(Draw d, int mx, int my, float dt, int accent, String name, String detail, int y, String action) {
        int x = this.x + 3, w = this.w - 6;
        if (y + 29 > this.y && y < this.y + h) {
            float hover = action == null ? 0 : glow(action, in(mx, my, x, y, w, 28), dt);
            d.roundRect(x, y, w, 28, 6, Theme.mix(0x80213043, 0xB0374E64, hover));
            d.rect(x + 1, y + 6, 2, 16, Theme.withAlpha(accent, 0xA0));
            d.text(d.trim(name, w - 30), x + 9, y + 5, Theme.TEXT, false);
            d.text(d.trim(detail, w - 30), x + 9, y + 16, Theme.MUTED, false);
            if (action != null) {
                d.text("›", x + w - 12, y + 10, accent, false);
                spot(action, x, y, w, 28);
            }
        }
        return y + 32;
    }

    // ---- What's new

    private int news(Draw d, int accent, int y) {
        d.textScaled("What's new", x + 8, y + 4, Theme.TEXT, 1.28f, false);
        d.text(TitleMenu.BRAND + (System.getProperty("peregrine.version", "").isEmpty() ? "" : " " + System.getProperty("peregrine.version")), x + 8, y + 20, accent, false);
        y += 34;
        String last = "";
        for (String[] n : NEWS) {
            if (!n[0].equals(last)) {
                last = n[0];
                d.text("Version " + n[0], x + 8, y + 2, Theme.FAINT, false);
                y += 13;
            }
            int bx = x + 3, bw = w - 6;
            if (y + 29 > this.y && y < this.y + h) {
                d.roundRect(bx, y, bw, 28, 6, 0x80213043);
                d.roundRect(bx + 7, y + 11, 6, 6, 3, accent);
                d.text(d.trim(n[1], bw - 30), bx + 19, y + 5, Theme.TEXT, false);
                d.text(d.trim(n[2], bw - 26), bx + 19, y + 16, Theme.MUTED, false);
            }
            y += 32;
        }
        return y;
    }

    // ---- clicks

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        if (button != 0 || !performance) {
            return false;
        }
        LightEngine engine = (LightEngine) pc.module("light_engine");
        Platform p = pc.platform();
        if (id.equals("modules")) {
            menu.choose("cat:PERFORMANCE");
            return true;
        }
        if (id.equals("engine") && engine != null) {
            engine.setEnabled(!engine.enabled());
            say(engine.enabled() ? "Light Engine is on: " + LightEngine.PRESETS[engine.preset.index]
                    : "Light Engine off, your settings restored");
            pc.markDirty();
            return true;
        }
        if (id.startsWith("preset:") && engine != null) {
            int i = Integer.parseInt(id.substring(7));
            engine.preset.index = i;
            if (!engine.enabled()) {
                engine.setEnabled(true);
            }
            if (p != null && p.inWorld()) {
                engine.apply(p, i);
            }
            say(LightEngine.PRESETS[i] + " is on");
            pc.markDirty();
            return true;
        }
        Saturation sat = (Saturation) pc.module("saturation");
        if ((id.equals("sat+") || id.equals("sat-")) && sat != null) {
            float v = sat.enabled() ? sat.amount.value : 100f;
            v += id.equals("sat+") ? 25f : -25f;
            v = Math.max(0f, Math.min(200f, v));
            sat.amount.value = v;
            sat.setEnabled(v != 100f);
            Hooks.saturation = sat.enabled() ? v / 100f : 1f;
            pc.markDirty();
            return true;
        }
        return false;
    }

    private void say(String s) {
        feedback = s;
        feedbackAt = System.currentTimeMillis();
    }
}
