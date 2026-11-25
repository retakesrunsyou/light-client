package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.KeyboardHandler;
import net.peregrine.client.core.knife.KnifeView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you hold a knife, its inspect key (F) inspects it instead of swapping your hands. */
@Mixin(KeyboardHandler.class)
public abstract class KnifeKeyMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$inspect(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().screen != null) {
            return;
        }
        KnifeView kv = KnifeView.INSTANCE;
        if (key != kv.inspectKey || !kv.holding()) {
            return;
        }
        if (action == 1) {   // pressed (not held or let go)
            kv.inspect();
        }
        ci.cancel();
    }
}
