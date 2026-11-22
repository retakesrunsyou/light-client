package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.peregrine.client.v26_2.GpuEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU mobs only take mobs while the world itself is being drawn (not the hand, menus or inventory). */
@Mixin(LevelRenderer.class)
public abstract class GpuMobsLevelMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void peregrine$levelStart(CallbackInfo ci) {
        GpuEntities.inLevel = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void peregrine$levelEnd(CallbackInfo ci) {
        GpuEntities.inLevel = false;
    }
}
