package net.peregrine.client.v1_21_2;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.modules.Boost;

/**
 * Performance boost: the lower frame limit while the game isn't the window you're using. A plain
 * class (not a mixin) so every frame-limit hook can share it.
 */
public final class BoostCap {

    private BoostCap() {
    }

    /** The lower limit while the window isn't in front, or null to leave it. */
    public static Integer cap(int limit) {
        int cap = Boost.backgroundFps;
        if (cap <= 0) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isWindowActive() || net.peregrine.client.core.Benchmark.INSTANCE.running()) {
            return null;
        }
        return limit <= 0 || limit > cap ? cap : null;
    }
}
