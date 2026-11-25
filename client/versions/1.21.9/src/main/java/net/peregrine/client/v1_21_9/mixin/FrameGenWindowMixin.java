package net.peregrine.client.v1_21_9.mixin;

import com.mojang.blaze3d.platform.Window;
import net.peregrine.client.v1_21_9.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Frame generation, 1.21.9-1.21.11: vsync is set on the window, so the presenter thread gets it. */
@Mixin(Window.class)
public abstract class FrameGenWindowMixin {

    @Redirect(method = "updateVsync", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V", remap = false))
    private void peregrine$vsync(int interval) {
        FrameGen.swapInterval(interval);
    }
}
