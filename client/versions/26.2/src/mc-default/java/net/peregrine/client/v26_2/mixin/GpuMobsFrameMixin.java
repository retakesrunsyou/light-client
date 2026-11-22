package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Only used on 26.3 (see the 26.3 copy); on 26.2 GPU mobs draw from GpuMobsGroupMixin, so this does nothing. */
@Mixin(LevelRenderer.class)
public abstract class GpuMobsFrameMixin {
}
