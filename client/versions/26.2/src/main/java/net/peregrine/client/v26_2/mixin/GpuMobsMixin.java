package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.peregrine.client.v26_2.GpuEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU mobs: plain solid and cut-out mobs are taken instead of being built corner by corner. */
@Mixin(ModelFeatureRenderer.class)
public abstract class GpuMobsMixin {

    @Inject(method = "prepareModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$gpuMob(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        if (GpuEntities.take(submit)) {
            ci.cancel();
        }
    }
}
