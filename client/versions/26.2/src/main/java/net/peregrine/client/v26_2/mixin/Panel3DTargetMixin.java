package net.peregrine.client.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v26_2.Panel3D;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 3D menu: while the menus are drawn, "the main picture" is the panel's. */
@Mixin(GameRenderer.class)
public abstract class Panel3DTargetMixin {

    // everyone else asks the game renderer for it (26.2 moved it there from Minecraft)
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$panel(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget t = Panel3D.current();
        if (t != null) {
            cir.setReturnValue(t);
        }
    }

    // ...and the game renderer reads its own field directly while it draws
    @ModifyExpressionValue(method = {"render", "renderLevel", "processBlurEffect"}, require = 0,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget peregrine$panelField(RenderTarget real) {
        RenderTarget t = Panel3D.current();
        return t != null ? t : real;
    }
}
