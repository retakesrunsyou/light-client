package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Performance boost: dropped items, XP orbs, item frames (with their maps) and display entities
 * are left out past the chosen distance. Mobs and players are never touched (Minecraft's own
 * entity distance setting handles those). Each version has one of these two forms.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class BoostEntityMixin {

    private static boolean peregrine$far(Entity e, double camX, double camY, double camZ) {
        int d = Boost.smallEntityDistance;
        if (d <= 0 || !(e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof ItemFrame || e instanceof Display)) {
            return false;
        }
        double dx = e.getX() - camX, dy = e.getY() - camY, dz = e.getZ() - camZ;
        return dx * dx + dy * dy + dz * dz > (double) d * d;
    }

    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$farEntity(Entity e, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (peregrine$far(e, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }

    // 26.3+: one more value (the frame's partial tick)
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$farEntity2(Entity e, Frustum frustum, double camX, double camY, double camZ, float partial, CallbackInfoReturnable<Boolean> cir) {
        if (peregrine$far(e, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
