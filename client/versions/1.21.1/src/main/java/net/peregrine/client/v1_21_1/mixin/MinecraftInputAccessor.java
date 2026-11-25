package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zero delay: attack, use and look at what's in front of you right now. */
@Mixin(Minecraft.class)
public interface MinecraftInputAccessor {

    @Invoker("startAttack")
    boolean peregrine$startAttack();

    @Invoker("startUseItem")
    void peregrine$startUseItem();
}
