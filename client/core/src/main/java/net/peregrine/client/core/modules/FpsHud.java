package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class FpsHud extends LineHud {
    private final net.peregrine.client.core.settings.BoolSetting breakdown = add(
            new net.peregrine.client.core.settings.BoolSetting("drawn_fps", "Show drawn FPS alongside generated FPS", true));

    private final net.peregrine.client.core.settings.BoolSetting minimal = add(new net.peregrine.client.core.settings.BoolSetting("plain", "Number only (no drawn count)", false));

    public FpsHud() {
        super("fps", "FPS", "Frames per second", "FPS", true, 0f, 0f);
    }

    @Override
    String value(Platform p) {
        int shown = p.shownFps();
        if (shown <= 0) return String.valueOf(p.fps());
        return breakdown.value && !minimal.value ? shown + "  (" + p.fps() + " drawn)" : String.valueOf(shown);
    }

    @Override
    boolean rated() {
        return true;
    }

    @Override
    int rate(Platform p) {
        int f = p.fps();
        return f >= 60 ? 0 : f >= 30 ? 1 : 2;
    }
}
