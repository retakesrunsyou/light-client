package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** 1.7 animations. Only how things look on your screen; the server sees nothing different. */
public final class OldAnimations extends Module {

    private final BoolSetting swing = add(new BoolSetting("swing", "Swing while using items", true));
    private final BoolSetting position = add(new BoolSetting("position", "Old item position", true));
    private final BoolSetting sneak = add(new BoolSetting("sneak", "Old sneak (camera only drops a little)", true));
    private final BoolSetting block = add(new BoolSetting("block", "Block with swords (hold use)", true));

    public OldAnimations() {
        super("old_animations", "1.7 animations", "1.7 swing, sneak, sword blocking and item positions",
                Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.oldSwing = on && swing.value;
        Hooks.oldPosition = on && position.value;
        Hooks.oldSneak = on && sneak.value;
        Hooks.oldBlockHit = on && block.value;
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
