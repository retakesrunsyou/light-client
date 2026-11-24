package net.peregrine.client.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v26_2.WorldScale;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World resolution: while the world is drawn, "the main picture" is the smaller one. */
@Mixin(GameRenderer.class)
public abstract class WorldScaleTargetMixin {

    // everyone else asks the game renderer for it (26.2 moved it there from Minecraft)
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$scaled(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget t = WorldScale.current();
        if (t != null) {
            cir.setReturnValue(t);
        }
    }

    // ...and the game renderer reads its own field directly while it draws
    @ModifyExpressionValue(method = {"render", "renderLevel", "processBlurEffect"}, require = 0,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget peregrine$scaledField(RenderTarget real) {
        RenderTarget t = WorldScale.current();
        return t != null ? t : real;
    }
}
