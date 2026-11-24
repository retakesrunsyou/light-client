package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 sneak: the camera drops a little (like 1.7/1.8) and does it straight away, instead of
 * sliding down to today's much lower crouch. Only the camera moves; the server sees nothing.
 */
@Mixin(Camera.class)
public abstract class OldSneakMixin {

    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Inject(method = "tick", at = @At("TAIL"))
    private void peregrine$sneak(CallbackInfo ci) {
        if (!Hooks.oldSneak || !(entity instanceof Player)) {
            return;
        }
        Player p = (Player) entity;
        Pose pose = p.getPose();
        if (pose != Pose.CROUCHING && pose != Pose.STANDING) {
            return;   // swimming, crawling, gliding: leave those alone
        }
        float stand = p.getEyeHeight(Pose.STANDING);
        float h = pose == Pose.CROUCHING ? stand - 0.08F : stand;
        eyeHeight = h;
        eyeHeightOld = h;
    }
}
