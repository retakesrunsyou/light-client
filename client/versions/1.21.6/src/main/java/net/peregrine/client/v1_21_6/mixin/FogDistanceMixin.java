package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fog off: Minecraft sets the fog at the edge of your render distance after the open-air fog,
 * so it has to be pushed away here too or it still hangs in front of you.
 */
@Mixin(FogRenderer.class)
public abstract class FogDistanceMixin {

    @Inject(method = "setupFog", at = @At("HEAD"))
    private void peregrine$start(CallbackInfoReturnable<FogData> cir) {
        Hooks.fogInAir = false;
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void peregrine$far(CallbackInfoReturnable<FogData> cir) {
        FogData fog = cir.getReturnValue();
        if (Hooks.fogOff && Hooks.fogInAir && fog != null) {
            fog.renderDistanceStart = 1.0E6F;
            fog.renderDistanceEnd = 2.0E6F;
        }
    }
}
