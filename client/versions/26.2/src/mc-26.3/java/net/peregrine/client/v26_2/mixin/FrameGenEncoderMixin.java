package net.peregrine.client.v26_2.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.peregrine.client.v26_2.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame generation: the finished frame goes to the presenter instead of straight to the window. */
@Mixin(targets = {"com.mojang.renderpearl.backend.opengl.GlCommandEncoder"})
public abstract class FrameGenEncoderMixin {

    @Inject(method = "presentTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$present(GpuTextureView view, int width, int height, CallbackInfo ci) {   // 26.2 passes the window size
        if (FrameGen.capturePresent(view)) {
            ci.cancel();
        }
    }
}
