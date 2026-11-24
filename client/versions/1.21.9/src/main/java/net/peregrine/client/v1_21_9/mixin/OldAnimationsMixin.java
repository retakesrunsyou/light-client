package net.peregrine.client.v1_21_9.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7 animations, in first person only: swing while using items, items held lower and further out. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class OldAnimationsMixin {

    @Shadow
    protected abstract void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swing);

    @Inject(method = "applyEatTransform", at = @At("TAIL"))
    private void peregrine$swingWhileUsing(PoseStack poseStack, float partial, HumanoidArm arm, ItemStack stack, Player player,
                                           CallbackInfo ci) {
        if (Hooks.oldSwing) {
            float swing = player.getAttackAnim(partial);
            if (swing > 0) {
                this.applyItemArmAttackTransform(poseStack, arm, swing);
            }
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void peregrine$position(PoseStack poseStack, HumanoidArm arm, float height, CallbackInfo ci) {
        if (Hooks.oldPosition) {
            if (Hooks.animationHits < 1000) {
                Hooks.animationHits++;
            }
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(side * 0.04F, -0.06F, -0.05F);
        }
    }

    // 1.7 sword blocking: remember what this arm holds, then turn the sword up into the old block

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void peregrine$holding(CallbackInfo ci,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.world.InteractionHand hand) {
        Hooks.handStack = stack;
        Hooks.handIsMain = hand == net.minecraft.world.InteractionHand.MAIN_HAND;
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void peregrine$done(CallbackInfo ci) {
        Hooks.handStack = null;
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("TAIL"))
    private void peregrine$block(PoseStack poseStack, HumanoidArm arm, float swing, CallbackInfo ci) {
        if (!Hooks.oldBlockHit || !Hooks.handIsMain || !(Hooks.handStack instanceof ItemStack)) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen != null || !mc.options.keyUse.isDown()) {
            return;
        }
        ItemStack stack = (ItemStack) Hooks.handStack;
        if (stack.isEmpty() || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_sword")) {
            return;
        }
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(side * -0.4F, 0.32F, 0.1F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 30F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-80F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 60F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 20F));
    }
}
