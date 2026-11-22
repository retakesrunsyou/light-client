package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/**
 * Zero delay (PvP): end crystals disappear the moment you hit them and respawn anchors the moment
 * you blow them up (both come back if the server says no), so the next one goes down right away;
 * optionally clicks and hotbar keys act the instant you press them, and less input lag when your
 * graphics card is the limit. Minecraft's miss delay is kept.
 */
public final class ZeroDelay extends Module {

    public final BoolSetting crystals = add(new BoolSetting("crystals", "Instant crystals", true));
    public final BoolSetting anchors = add(new BoolSetting("anchors", "Instant anchors", true));
    public final BoolSetting instant = add(new BoolSetting("instant", "Instant clicks (may flag)", false));
    public final BoolSetting gpuLag = add(new BoolSetting("gpu_lag", "Reduce GPU input lag", false));

    /** Read by the game hooks. */
    public static volatile boolean fastCrystals, fastAnchors, instantInput, lowLatency;

    public ZeroDelay() {
        super("zero_delay", "Zero delay", "Instant crystal and anchor removal",
                Category.UTILITY, false);
    }

    @Override
    public String warning() {
        return instant.value ? "Instant clicks send your hits between game ticks: some anti-cheats (like Grim) can flag that" : null;
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
        fastCrystals = false;
        fastAnchors = false;
        instantInput = false;
        lowLatency = false;
    }

    private void apply() {
        boolean on = enabled();
        fastCrystals = on && crystals.value;
        fastAnchors = on && anchors.value;
        instantInput = on && instant.value;
        lowLatency = on && gpuLag.value;
    }
}
