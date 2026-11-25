package net.peregrine.client.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.peregrine.client.core.modules.SkeletonLook;
import net.peregrine.client.v1_21_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public abstract class SkeletonHandMixin {
    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void peregrine$drawBoneHand(ModelPart part, PoseStack pose, VertexConsumer vertices, int light,
                                         int overlay, Operation<Void> original,
                                         @Local(argsOnly = true) MultiBufferSource buffers,
                                         @Share("boneHandDrawn") LocalBooleanRef drawn) {
        if (SkeletonLook.arm()) {
            boolean right = part == ((PlayerRenderer) (Object) this).getModel().rightArm;
            if (SkeletonDraw.hand(part, right, pose, buffers, light, overlay)) {
                drawn.set(true);
                return;
            }
        }
        original.call(part, pose, vertices, light, overlay);
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void peregrine$hideSleeve(ModelPart part, PoseStack pose, VertexConsumer vertices, int light,
                                      int overlay, Operation<Void> original,
                                      @Share("boneHandDrawn") LocalBooleanRef drawn) {
        if (!drawn.get()) {
            original.call(part, pose, vertices, light, overlay);
        }
    }
}
