package net.peregrine.client.v26_2.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.peregrine.client.core.modules.GpuMobs;
import net.peregrine.client.v26_2.GpuEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU mobs (26.3): the taken mobs are drawn at the start of the game's own solid pass. */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class GpuMobsSolidMixin {

    @Inject(method = "executeSolid", at = @At("HEAD"), require = 0)
    private void peregrine$draw(RenderPass pass, CallbackInfo ci) {
        long t = System.nanoTime();
        GpuEntities.draw(pass);
        GpuMobs.solidNanos += System.nanoTime() - t;
    }
}
