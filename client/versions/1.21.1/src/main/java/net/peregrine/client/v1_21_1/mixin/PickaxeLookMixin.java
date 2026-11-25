package net.peregrine.client.v1_21_1.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v1_21_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pickaxe look in first person: your pickaxe drawn as the look you picked. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class PickaxeLookMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$look(CallbackInfo ci, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context,
                                @Local(argsOnly = true) PoseStack pose, @Local(argsOnly = true) MultiBufferSource buffers,
                                @Local(argsOnly = true) int light) {
        if (SkeletonDraw.pickaxe(stack, context, pose, buffers, light, OverlayTexture.NO_OVERLAY)) {
            ci.cancel();
        }
    }
}
