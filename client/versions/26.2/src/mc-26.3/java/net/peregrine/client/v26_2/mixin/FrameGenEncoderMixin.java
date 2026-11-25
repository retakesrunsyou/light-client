package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.v26_2.ClipCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3 copies the finished frame to the window surface itself (no GLFW swap to hook), so clips
 * grab the frame right before that copy.
 */
@Mixin(Minecraft.class)
public abstract class FrameGenEncoderMixin {

    @Inject(method = "renderFrame", require = 0, at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void peregrine$beforeBlit(boolean advance, CallbackInfo ci) {
        ClipCapture.frame();
    }
}
