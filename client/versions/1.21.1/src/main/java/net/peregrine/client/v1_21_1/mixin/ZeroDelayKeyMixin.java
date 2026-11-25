package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.KeyboardHandler;
import net.peregrine.client.v1_21_1.ZeroDelayHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zero delay: hotbar keys (and keys bound to attack or use) act the moment they're pressed. */
@Mixin(KeyboardHandler.class)
public abstract class ZeroDelayKeyMixin {

    @Inject(method = "keyPress", at = @At("TAIL"), require = 0)
    private void peregrine$key(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (action == 1) {
            ZeroDelayHooks.input();
        }
    }
}
