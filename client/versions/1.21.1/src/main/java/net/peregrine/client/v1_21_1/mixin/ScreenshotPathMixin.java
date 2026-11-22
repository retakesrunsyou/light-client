package net.peregrine.client.v1_21_1.mixin;

import java.io.File;
import net.minecraft.client.KeyboardHandler;
import net.peregrine.client.core.CapturePaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(KeyboardHandler.class)
public abstract class ScreenshotPathMixin {
    @ModifyArg(method = "keyPress", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private File lightclient$screenshotRoot(File gameDir) {
        return CapturePaths.screenshotRoot(gameDir);
    }
}
