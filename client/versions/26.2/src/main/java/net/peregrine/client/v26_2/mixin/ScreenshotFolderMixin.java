package net.peregrine.client.v26_2.mixin;

import java.io.File;

import net.minecraft.client.Screenshot;
import net.peregrine.client.core.CapturePaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 26.2+: F2 goes through Screenshot.grab(Minecraft, boolean); the picture lands in Pictures > Light Client. */
@Mixin(Screenshot.class)
public abstract class ScreenshotFolderMixin {
    @ModifyArg(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", index = 0, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private static File lightclient$screenshotRoot(File gameDir) {
        return CapturePaths.screenshotRoot(gameDir);
    }
}
