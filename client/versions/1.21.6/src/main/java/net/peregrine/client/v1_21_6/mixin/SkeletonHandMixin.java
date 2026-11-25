package net.peregrine.client.v1_21_6.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.peregrine.client.core.modules.SkeletonLook;
import net.peregrine.client.v1_21_6.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Skeleton look in first person: your arm drawn as bone. */
@Mixin(PlayerRenderer.class)
public abstract class SkeletonHandMixin {

    @WrapOperation(method = "renderHand", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void peregrine$boneArm(ModelPart part, PoseStack pose, VertexConsumer vc, int light, int overlay, Operation<Void> original,
                                   @Local(argsOnly = true) MultiBufferSource buffers) {
        if (SkeletonLook.arm()) {
            boolean right = part == ((PlayerRenderer) (Object) this).getModel().rightArm;
            if (SkeletonDraw.hand(part, right, pose, buffers, light, overlay)) {
                return;
            }
        }
        original.call(part, pose, vc, light, overlay);
    }
}
