package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.MouseHandler;
import net.peregrine.client.v1_21_6.ZeroDelayHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zero delay: a click acts the moment it happens (when instant clicks are on). */
@Mixin(MouseHandler.class)
public abstract class ZeroDelayClickMixin {

    @Inject(method = "onPress", at = @At("TAIL"), require = 0)
    private void peregrine$click(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == 1) {
            ZeroDelayHooks.input();
        }
    }
}
