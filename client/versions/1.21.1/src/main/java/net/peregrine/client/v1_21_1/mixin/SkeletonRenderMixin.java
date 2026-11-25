package net.peregrine.client.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.modules.SkeletonLook;
import net.peregrine.client.v1_21_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class SkeletonRenderMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void peregrine$drawSkeleton(EntityModel<?> model, PoseStack pose, VertexConsumer vertices,
                                         int light, int overlay, int tint, Operation<Void> original,
                                         @Local(argsOnly = true) LivingEntity entity,
                                         @Local(argsOnly = true) MultiBufferSource buffers,
                                         @Share("skeletonDrawn") LocalBooleanRef drawn) {
        if (SkeletonLook.on() && model instanceof PlayerModel<?> playerModel
                && entity instanceof AbstractClientPlayer player
                && SkeletonDraw.player(playerModel, player, pose, buffers, light, overlay, tint)) {
            drawn.set(true);
            return;
        }
        original.call(model, pose, vertices, light, overlay, tint);
    }

    @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private boolean peregrine$keepLayer(RenderLayer<?, ?> layer, PoseStack pose, MultiBufferSource buffers, int light,
                                        Entity entity, float walk, float amount, float tick, float age, float yaw, float pitch,
                                        @Share("skeletonDrawn") LocalBooleanRef drawn) {
        return !drawn.get() || layer instanceof ItemInHandLayer
                || (layer instanceof HumanoidArmorLayer && SkeletonLook.showArmor());
    }
}
