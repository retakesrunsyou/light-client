package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** World saturation: everything in the world more vivid (or less), as one light screen effect. */
public final class Saturation extends Module {

    public final SliderSetting amount = add(new SliderSetting("amount", "Saturation", 0f, 200f, 25f, 150f, "%.0f%%"));

    public Saturation() {
        super("saturation", "Saturation", "Makes the whole world more vivid (or greyer): 100% is the game as it is",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.saturation = enabled() ? amount.value / 100f : 1f;
    }

    @Override
    protected void onEnable() {
        Hooks.saturation = amount.value / 100f;
    }

    @Override
    protected void onDisable() {
        Hooks.saturation = 1f;
    }
}
