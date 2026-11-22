package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.peregrine.client.core.modules.GpuMobs;
import net.peregrine.client.v26_2.GpuEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GPU mobs (26.3): once the game has prepared its frame, the taken mobs' table goes to the graphics card. */
@Mixin(FeatureRenderDispatcher.class)
public abstract class GpuMobsFrameMixin {

    @Inject(method = "prepareFrameWithContext", at = @At("RETURN"), require = 0)
    private void peregrine$upload(CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
        long t = System.nanoTime();
        GpuEntities.upload();
        GpuMobs.solidNanos += System.nanoTime() - t;
    }
}
