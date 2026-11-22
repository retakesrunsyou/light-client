package net.peregrine.client.core;

import net.peregrine.client.core.modules.StableFps;

/** The hardware-friendly presets and release notes pages in the Right Shift menu. */
final class LightPanel extends MenuPanel {
    private static final String[] PRESETS = {"Low-end", "Balanced", "High-end"};
    private static final String[] POLICIES = {
        "60 FPS target · 6-chunk floor",
        "90 FPS target · 10-chunk floor",
        "144 FPS · keeps view distance"
    };
    private record Note(String title, String description) {}
    private static final Note[] NEWS = {
        new Note("Starting screen", "Play, recent servers, and Locker"),
        new Note("Right Shift menu", "Modules and presets in one place"),
        new Note("Stable FPS presets", "Tune how FPS dips are handled")
    };

    private final boolean performance;
    private String feedback = "";

    LightPanel(Peregrine pc, Menu menu, boolean performance) {
        super(pc, menu);
        this.performance = performance;
    }

    @Override String placeholder() {
        return "";
    }

    @Override void draw(Draw d, int mx, int my, float dt, long now, int accent) {
        menu.clipArea(d, x, this.y, w, h);
        int y = this.y + 3 - Math.round(smooth);
        d.textScaled(performance ? "Performance" : "What's new", x + 8, y + 4, Theme.TEXT, 1.28f, false);
        d.text(performance ? "Stable FPS presets" : TitleMenu.BRAND, x + 8, y + 20, accent, false);
        y += 35;
        if (performance) {
            for (int i = 0; i < PRESETS.length; i++) {
                row(d, mx, my, dt, accent, PRESETS[i], POLICIES[i], y, PRESETS[i]);
                y += 32;
            }
            row(d, mx, my, dt, accent, "Performance modules", "Open individual settings", y, "modules");
            y += 32;
            if (!feedback.isEmpty()) d.text(d.trim(feedback, w - 16), x + 8, y + 1, accent, false);
        } else {
            for (Note note : NEWS) {
                row(d, mx, my, dt, accent, note.title(), note.description(), y, null);
                y += 32;
            }
        }
        maxScroll = Math.max(0, y + 13 + Math.round(smooth) - this.y - h);
        menu.unclipArea(d);
    }

    private void row(Draw d, int mx, int my, float dt, int accent, String name, String detail, int y, String action) {
        int x = this.x + 3, w = this.w - 6;
        if (y + 29 <= this.y || y >= this.y + h) return;
        float hover = action == null ? 0 : glow(action, in(mx, my, x, y, w, 28), dt);
        d.roundRect(x, y, w, 28, 5, Theme.mix(0x80213043, 0xB0374E64, hover));
        d.rect(x + 1, y + 6, 2, 16, Theme.withAlpha(accent, 0xA0));
        d.text(d.trim(name, w - 30), x + 9, y + 5, Theme.TEXT, false);
        d.text(d.trim(detail, w - 30), x + 9, y + 16, Theme.MUTED, false);
        if (action != null) {
            d.text("›", x + w - 12, y + 10, accent, false);
            int from = Math.max(this.y, y), to = Math.min(this.y + h, y + 28);
            spot(action, x, from, w, to - from);
        }
    }

    @Override boolean clicked(String id, int mx, int my, int button) {
        if (button != 0 || !performance) return false;
        if (id.equals("modules")) {
            menu.choose("cat:PERFORMANCE");
            return true;
        }
        if (!(pc.module("stable_fps") instanceof StableFps stable)) return false;
        switch (id) {
            case "Low-end" -> stable.applyPreset(1, true, 6);
            case "Balanced" -> stable.applyPreset(2, true, 10);
            case "High-end" -> stable.applyPreset(4, false, 10);
            default -> { return false; }
        }
        pc.markDirty();
        feedback = id + " preset applied";
        return true;
    }
}
