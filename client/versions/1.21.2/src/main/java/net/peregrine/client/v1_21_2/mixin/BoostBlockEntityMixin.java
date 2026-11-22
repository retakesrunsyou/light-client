package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Performance boost: chests, signs, banners and the like are left out past the chosen distance.
 * Each version draws them through one of these (the others aren't there and are skipped).
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BoostBlockEntityMixin {

    private static boolean peregrine$far(BlockEntity be) {
        int d = Boost.blockEntityDistance;
        if (d <= 0 || be instanceof BeaconBlockEntity) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        BlockPos p = be.getBlockPos();
        double dx = p.getX() + 0.5 - mc.player.getX(), dy = p.getY() + 0.5 - mc.player.getEyeY(), dz = p.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz > (double) d * d;
    }

    // up to 1.21.8: drawn straight away
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$farDraw(BlockEntity be, float partial, @Coerce Object pose, @Coerce Object buffers, CallbackInfo ci) {
        if (peregrine$far(be)) {
            ci.cancel();
        }
    }
}
