package net.peregrine.client.v1_21_2.mixin;

import java.util.Collection;
import java.util.Map;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performance boost: past the particle cap, new particles are skipped (explosions stay smooth). */
@Mixin(ParticleEngine.class)
public abstract class BoostParticleMixin {
    @Shadow @Final private Map<?, ?> particles;

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$cap(Particle particle, CallbackInfo ci) {
        int cap = Boost.maxParticles;
        if (cap <= 0) {
            return;
        }
        int n = 0;
        for (Object g : this.particles.values()) {   // a queue per kind (or a group, in newer versions)
            if (g instanceof Collection) {
                n += ((Collection<?>) g).size();
            } else if (g != null) {
                try {
                    n += (Integer) g.getClass().getMethod("size").invoke(g);
                } catch (ReflectiveOperationException | ClassCastException e) {
                    return;
                }
            }
        }
        if (n >= cap) {
            ci.cancel();
        }
    }
}
