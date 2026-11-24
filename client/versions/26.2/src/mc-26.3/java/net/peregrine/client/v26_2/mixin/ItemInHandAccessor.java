package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Peregrine draw a hand again (for swing afterimages). */
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")   // gone in 26.3 (knives are off there)
public interface ItemInHandAccessor {

    @Invoker("submitArmWithItem")   // renamed in 26.2
    void peregrine$renderArmWithItem(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                     ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light);
}
