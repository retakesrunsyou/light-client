package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** The clouds, and the sun, moon and stars, stop moving on your screen. */
public final class StaticSky extends Module {

    private final BoolSetting clouds = add(new BoolSetting("clouds", "Clouds stop drifting", true));
    private final BoolSetting sky = add(new BoolSetting("sky", "Freeze sun, moon and stars", true));

    public StaticSky() {
        super("static_sky", "Static sky", "Frozen clouds, sun and moon",
                Category.VISUALS, false);
    }

    private void apply() {
        Hooks.staticClouds = enabled() && clouds.value;
        Hooks.staticSky = enabled() && sky.value;
    }

    @Override
    public void tick(Platform p) {
        apply();
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        apply();
    }
}
