package net.peregrine.client.v1_21_6.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.peregrine.client.v1_21_6.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Frame generation, 1.21.9-1.21.11: the finished frame is shown from here, so it goes to the presenter. */
@Mixin(RenderSystem.class)
public abstract class FrameGenFlipMixin {

    @Redirect(method = "flipFrame", require = 0, remap = false,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void peregrine$swap(long window) {
        FrameGen.present(window);
    }
}
