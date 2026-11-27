package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/** How far away you were when you last hit something. */
public final class ReachHud extends LineHud {

    private final ChoiceSetting decimals = add(new ChoiceSetting("decimals", "Decimals", 0, "One", "Two"));
    private final net.peregrine.client.core.settings.SliderSetting hold = add(
            new net.peregrine.client.core.settings.SliderSetting("hold", "Keep showing for", 1f, 10f, 0.5f, 3f, "%.1f s"));
    private final net.peregrine.client.core.settings.BoolSetting unit = add(
            new net.peregrine.client.core.settings.BoolSetting("unit", "Show the unit", true));
    private double last = -1;
    private long lastAt;

    public ReachHud() {
        super("reach", "Reach display", "Distance of your last hit", "Reach", 0f, 0.80f);
    }

    /** Called by the core on each attack. */
    public void hit(double distance) {
        last = distance;
        lastAt = System.currentTimeMillis();
    }

    @Override
    String value(Platform p) {
        if (last < 0 || System.currentTimeMillis() - lastAt > hold.value * 1000) {
            return "-";
        }
        return String.format(decimals.index == 0 ? "%.1f" : "%.2f", last) + (unit.value ? " blocks" : "");
    }
}
