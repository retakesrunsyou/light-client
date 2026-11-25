package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Peregrine draw a hand again (for swing afterimages). 26.3 renamed the hand renderer. */
@Mixin(targets = "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer")
public interface ItemInHandAccessor {

    @Invoker("submitArmWithItem")
    void peregrine$renderArmWithItem(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partial, float pitch,
                                     InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
                                     SubmitNodeCollector collector, int light);
}
