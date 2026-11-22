package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance boost, before the game had a frame-limit tracker: fewer frames in the background. */
@Mixin(Minecraft.class)
public abstract class BoostFpsOldMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void peregrine$background(CallbackInfoReturnable<Integer> cir) {
        Integer cap = BoostFpsMixin.peregrine$cap(cir.getReturnValue());
        if (cap != null) {
            cir.setReturnValue(cap);
        }
    }
}
