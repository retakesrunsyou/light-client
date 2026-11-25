package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v26_2.ViewmodelHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knives and viewmodel effects on 26.3, where the first-person hand renderer works from render
 * states. First-person hands are always the local player's, so the player comes from Minecraft.
 */
@Mixin(targets = {"net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer"})
public abstract class KnifeHandMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$knife(PlayerRenderState state, FirstPersonHandsAndItemsRenderState hands, float partial, float pitch,
                                 InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
                                 SubmitNodeCollector collector, int light, CallbackInfo ci) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player != null && ViewmodelHook.before(this, state, hands, player, partial, pitch, hand, swing, stack, equip, pose,
                collector, light)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private SubmitNodeCollector peregrine$effects(SubmitNodeCollector collector, PlayerRenderState state,
                                                  FirstPersonHandsAndItemsRenderState hands, float partial, float pitch,
                                                  InteractionHand hand, float swing, ItemStack stack) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        return player == null ? collector : ViewmodelHook.collector(collector, player, hand, stack);
    }
}
