package net.peregrine.client.v1_21_6.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v1_21_6.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pickaxe look in first person: your pickaxe drawn as the look you picked. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class PickaxeLookMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$look(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (SkeletonDraw.pickaxe(stack, context, pose, buffers, light, OverlayTexture.NO_OVERLAY)) {
            ci.cancel();
        }
    }
}
