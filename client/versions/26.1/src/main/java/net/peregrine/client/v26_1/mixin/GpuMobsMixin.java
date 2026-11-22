package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.peregrine.client.v26_1.GpuEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GPU mobs: plain solid and cut-out mobs are taken instead of drawn corner by corner, then all
 * drawn by the graphics card once the game has finished its solid models.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class GpuMobsMixin {

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$gpuMob(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer buffer,
                                  OutlineBufferSource outlines, MultiBufferSource.BufferSource crumbling, CallbackInfo ci) {
        if (GpuEntities.take(submit, type)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSolid", at = @At("RETURN"), require = 0)
    private void peregrine$gpuMobsDraw(CallbackInfo ci) {
        GpuEntities.flush();
    }
}
