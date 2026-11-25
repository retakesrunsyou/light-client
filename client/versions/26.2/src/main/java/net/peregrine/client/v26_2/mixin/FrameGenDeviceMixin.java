package net.peregrine.client.v26_2.mixin;

import net.peregrine.client.v26_2.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Frame generation: the game draws on a shared context, and the presenter thread owns the window. */
@Mixin(targets = {"com.mojang.blaze3d.opengl.GlDevice", "com.mojang.blaze3d.opengl.GlSurface",
        "com.mojang.renderpearl.backend.opengl.GlDevice", "com.mojang.renderpearl.backend.opengl.GlSurface"})
public abstract class FrameGenDeviceMixin {

    @Redirect(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V", remap = false))
    private void peregrine$context(long window) {
        FrameGen.makeCurrent(window);
    }

    @Redirect(method = "configure", require = 0,   // 26.2 moved vsync and presenting to the window surface
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V", remap = false))
    private void peregrine$vsync(int interval) {
        FrameGen.swapInterval(interval);
    }

    @Redirect(method = "present", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private void peregrine$swap(long window) {
        FrameGen.present(window);
    }
}
