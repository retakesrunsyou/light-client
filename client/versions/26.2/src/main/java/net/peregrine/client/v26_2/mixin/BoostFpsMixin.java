package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance boost: fewer frames while the game isn't the window you're using. */
@Mixin(targets = {"net.minecraft.client.FramerateLimitTracker", "com.mojang.blaze3d.platform.FramerateLimitTracker"})
public abstract class BoostFpsMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void peregrine$background(CallbackInfoReturnable<Integer> cir) {
        Integer cap = net.peregrine.client.v26_2.BoostCap.cap(cir.getReturnValue());
        if (cap != null) {
            cir.setReturnValue(cap);
        }
    }

}
