package net.peregrine.client.v1_21_9.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fog off: Minecraft sets the fog at the edge of your render distance after the open-air fog,
 * so it has to be pushed away too or it still hangs in front of you. On these versions the fog
 * is worked out into a local and written out straight after, so it's changed just before that
 * (and before Sodium copies it for the terrain).
 */
@Mixin(FogRenderer.class)
public abstract class FogDistanceMixin {

    @Inject(method = "setupFog", at = @At("HEAD"), require = 0)
    private void peregrine$start(CallbackInfoReturnable<?> cir) {
        Hooks.fogInAir = false;
    }

    @Inject(method = "setupFog", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private void peregrine$far(CallbackInfoReturnable<?> cir, @Local FogData fog) {
        if (Hooks.fogOff && Hooks.fogInAir && fog != null) {
            fog.renderDistanceStart = 1.0E6F;
            fog.renderDistanceEnd = 2.0E6F;
        }
    }
}
