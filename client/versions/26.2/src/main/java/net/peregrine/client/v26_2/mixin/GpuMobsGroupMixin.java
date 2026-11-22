package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.peregrine.client.core.modules.GpuMobs;
import net.peregrine.client.v26_2.GpuEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GPU mobs: times how long the game spends preparing its models (for the before / after test),
 * and draws the taken mobs just before the game draws its own models.
 */
@Mixin(RenderTypeFeatureRenderer.class)
public abstract class GpuMobsGroupMixin {

    private static long peregrine$prepareStart;

    @Inject(method = "prepareGroup", at = @At("HEAD"), require = 0)
    private void peregrine$prepareStart(CallbackInfo ci) {
        if ((Object) this instanceof ModelFeatureRenderer) {
            peregrine$prepareStart = System.nanoTime();
        }
    }

    @Inject(method = "prepareGroup", at = @At("RETURN"), require = 0)
    private void peregrine$prepareEnd(CallbackInfo ci) {
        if ((Object) this instanceof ModelFeatureRenderer) {
            GpuMobs.solidNanos += System.nanoTime() - peregrine$prepareStart;
        }
    }

    @Inject(method = "executeGroup", at = @At("HEAD"), require = 0)
    private void peregrine$drawFirst(CallbackInfo ci) {
        if ((Object) this instanceof ModelFeatureRenderer) {
            long t = System.nanoTime();
            GpuEntities.flush();
            GpuMobs.solidNanos += System.nanoTime() - t;
        }
    }
}
