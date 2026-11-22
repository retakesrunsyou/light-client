package net.peregrine.client.v1_21_9.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.peregrine.client.v1_21_9.WorldTextDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World Lyrics: text in the world, drawn with the see-through pass after the world (so it blends over water and glass). */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class WorldTextMixin {

    @Inject(method = "renderBlockOutline", at = @At("RETURN"), require = 0)
    private void peregrine$worldText(net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucent,
                                     net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
        if (translucent) {
            net.minecraft.world.phys.Vec3 c = state.cameraRenderState.pos;
            WorldTextDraw.drawInto(pose, buffers, c.x, c.y, c.z);
        }
    }
}
