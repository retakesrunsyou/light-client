package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** 1.7-style first-person animations (only how your hands look; nothing changes for the server). */
public final class OldAnimations extends Module {

    private final BoolSetting swing = add(new BoolSetting("swing", "Swing while using items", true));
    private final BoolSetting position = add(new BoolSetting("position", "Old item position", true));

    public OldAnimations() {
        super("old_animations", "1.7 animations", "1.7 style swing and item positions",
                Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.oldSwing = on && swing.value;
        Hooks.oldPosition = on && position.value;
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
