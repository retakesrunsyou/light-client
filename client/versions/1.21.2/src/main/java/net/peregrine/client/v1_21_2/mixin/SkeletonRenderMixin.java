package net.peregrine.client.v1_21_2.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.peregrine.client.core.modules.SkeletonLook;
import net.peregrine.client.v1_21_2.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Skeleton look: players' models are drawn as bone; only what they hold (and armor, if asked) stays on them. */
@Mixin(LivingEntityRenderer.class)
public abstract class SkeletonRenderMixin {

    private static final String RENDER = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @WrapOperation(method = RENDER, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void peregrine$bones(EntityModel<?> model, PoseStack pose, VertexConsumer vc, int light, int overlay, int tint,
                                 Operation<Void> original, @Local(argsOnly = true) LivingEntityRenderState state,
                                 @Local(argsOnly = true) MultiBufferSource buffers) {
        if ((Object) this instanceof PlayerRenderer && SkeletonLook.on() && model instanceof PlayerModel
                && state instanceof PlayerRenderState
                && SkeletonDraw.player((PlayerModel) model, (PlayerRenderState) state, pose, buffers, light, overlay, tint)) {
            return;
        }
        original.call(model, pose, vc, light, overlay, tint);
    }

    @WrapWithCondition(method = RENDER, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private boolean peregrine$layers(RenderLayer<?, ?> layer, PoseStack pose, MultiBufferSource buffers, int light,
                                     EntityRenderState state, float yRot, float xRot) {
        if (!((Object) this instanceof PlayerRenderer) || !SkeletonLook.on() || !(state instanceof PlayerRenderState)) {
            return true;
        }
        // skin layers (cape, ears, arrows stuck in the skin...) go; held items stay, armor if asked
        return layer instanceof ItemInHandLayer || (layer instanceof HumanoidArmorLayer && SkeletonLook.showArmor());
    }
}
