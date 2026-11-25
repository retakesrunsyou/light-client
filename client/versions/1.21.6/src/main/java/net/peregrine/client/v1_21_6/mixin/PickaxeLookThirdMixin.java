package net.peregrine.client.v1_21_6.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v1_21_6.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pickaxe look in third person: your own pickaxe in your hand (others' stay as they are); with the Skeleton look, held items sit in the bony hand. */
@Mixin(ItemInHandLayer.class)
public abstract class PickaxeLookThirdMixin {

    @WrapOperation(method = "renderArmWithItem", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void peregrine$look(ItemStackRenderState item, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                Operation<Void> original, @Local(argsOnly = true) ArmedEntityRenderState state,
                                @Local(argsOnly = true) HumanoidArm arm) {
        boolean bones = net.peregrine.client.core.modules.SkeletonLook.on()
                && state instanceof net.minecraft.client.renderer.entity.state.PlayerRenderState;
        if (bones) {
            pose.pushPose();
            pose.translate(0f, 0f, -1.5f / 16f);
        }
        try {
            if (SkeletonDraw.isYou(state)) {
                Minecraft mc = Minecraft.getInstance();
                // this version doesn't pass the item along, so take it from your own hand on that side
                ItemStack stack = arm == mc.player.getMainArm() ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
                if (SkeletonDraw.pickaxe(stack, arm == HumanoidArm.LEFT
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, pose, buffers, light, overlay)) {
                    return;
                }
            }
            original.call(item, pose, buffers, light, overlay);
        } finally {
            if (bones) {
                pose.popPose();
            }
        }
    }
}
