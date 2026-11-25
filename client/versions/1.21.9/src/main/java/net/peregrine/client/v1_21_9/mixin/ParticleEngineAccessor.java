package net.peregrine.client.v1_21_9.mixin;

import java.util.Map;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Performance boost: how many particles there are. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    @Accessor("particles")
    Map<?, ?> peregrine$groups();
}
