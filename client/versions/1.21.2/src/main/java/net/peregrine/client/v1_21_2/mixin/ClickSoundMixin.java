package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.modules.ClickSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Click sounds: every key press and release. */
@Mixin(targets = {"net.minecraft.client.KeyboardHandler"})
public abstract class ClickSoundMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void peregrine$click(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (window == mc.getWindow().getWindow()) {
                ClickSounds.key(key, action, mc.screen != null);
            }
        } catch (Throwable ignored) {
            // never in the way of typing
        }
    }
}
