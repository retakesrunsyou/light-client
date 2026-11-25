package net.peregrine.client.v1_21_6.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v1_21_6.KnifeHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Knives: in first person a held sword is drawn as Peregrine's knife (and arm) instead. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class KnifeHandMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$knife(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                 ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light,
                                 CallbackInfo ci) {
        if (KnifeHand.draw(player, partial, hand, swing, stack, pose, buffers, light)) {
            ci.cancel();
        }
    }
}
