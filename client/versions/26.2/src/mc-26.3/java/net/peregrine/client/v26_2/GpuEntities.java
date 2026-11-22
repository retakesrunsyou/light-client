package net.peregrine.client.v26_2;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

/**
 * GPU mobs on 26.3: not here yet (26.3 moved Minecraft's graphics layer to a new API). Mobs are
 * always drawn the normal way, and the setting is hidden on this version.
 */
public final class GpuEntities {

    public static volatile boolean inLevel;

    private GpuEntities() {
    }

    public static boolean take(ModelFeatureRenderer.Submit<?> submit) {
        return false;
    }

    public static void flush() {
    }
}
