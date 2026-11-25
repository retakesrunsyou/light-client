package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zero delay: before 26.1 the "what am I looking at" update lives on the game renderer. */
@Mixin(GameRenderer.class)
public interface GameRendererPickAccessor {

    @Invoker("pick")
    void peregrine$pick(float partial);
}
