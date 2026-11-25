package net.peregrine.client.v26_2.mixin;

import net.peregrine.client.core.Skies;
import net.peregrine.client.v26_2.SkyDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom sky: drawn over Minecraft's sky, after where its sun, moon and stars go. While it's on,
 * Minecraft's own square sun and moon and its stars are skipped (ours take their place).
 */
@Mixin(targets = {"net.minecraft.client.renderer.SkyRenderer"})
public abstract class CustomSkyMixin {

    @Inject(method = "renderSunMoonAndStars", at = @At("RETURN"), require = 0)
    private void peregrine$customSky(CallbackInfo ci) {
        SkyDraw.draw();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$noSun(CallbackInfo ci) {
        if (SkyDraw.active() && Skies.sun) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$noMoon(CallbackInfo ci) {
        if (SkyDraw.active() && Skies.moon && Skies.sky != 3) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$noStars(CallbackInfo ci) {
        if (SkyDraw.active()) {
            ci.cancel();
        }
    }
}
