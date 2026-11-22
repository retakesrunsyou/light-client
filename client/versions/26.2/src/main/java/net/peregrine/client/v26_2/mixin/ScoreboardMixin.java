package net.peregrine.client.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The scoreboard HUD item: moves and sizes the server's sidebar to where it was placed in Edit
 * HUD (or, when it isn't moved, sizes it around its right-middle edge). Where Minecraft drew it
 * is measured from its two background boxes, so the HUD item is exactly its size.
 */
@Mixin(Hud.class)
public abstract class ScoreboardMixin {

    @Unique
    private boolean peregrine$scaled;
    @Unique
    private int peregrine$x0, peregrine$y0, peregrine$x1, peregrine$y1;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void peregrine$scale(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        peregrine$x0 = peregrine$y0 = Integer.MAX_VALUE;
        peregrine$x1 = peregrine$y1 = Integer.MIN_VALUE;
        int[] box = Hooks.scoreboardBox;
        if (Hooks.scoreboardMoved && box[2] > 0) {
            peregrine$scaled = true;
            graphics.pose().pushMatrix();
            graphics.pose().translate(Hooks.scoreboardX, Hooks.scoreboardY);
            graphics.pose().scale(Hooks.scoreboardK, Hooks.scoreboardK);
            graphics.pose().translate(-box[0], -box[1]);
            return;
        }
        float s = Hooks.scoreboardScale;
        peregrine$scaled = s != 1.0F;
        if (peregrine$scaled) {
            if (Hooks.scoreboardHits < 1000) {
                Hooks.scoreboardHits++;
            }
            float ax = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            float ay = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2.0F;
            graphics.pose().pushMatrix();
            graphics.pose().translate(ax, ay);
            graphics.pose().scale(s, s);
            graphics.pose().translate(-ax, -ay);
        }
    }

    @WrapOperation(method = "displayScoreboardSidebar", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void peregrine$measure(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        peregrine$x0 = Math.min(peregrine$x0, Math.min(x0, x1));
        peregrine$y0 = Math.min(peregrine$y0, Math.min(y0, y1));
        peregrine$x1 = Math.max(peregrine$x1, Math.max(x0, x1));
        peregrine$y1 = Math.max(peregrine$y1, Math.max(y0, y1));
        original.call(graphics, x0, y0, x1, y1, color);
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void peregrine$unscale(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (peregrine$x1 > peregrine$x0) {
            Hooks.scoreboardBox = new int[] {peregrine$x0, peregrine$y0, peregrine$x1 - peregrine$x0, peregrine$y1 - peregrine$y0};
            Hooks.scoreboardSeenAt = System.currentTimeMillis();
        }
        if (peregrine$scaled) {
            peregrine$scaled = false;
            graphics.pose().popMatrix();
        }
    }
}
