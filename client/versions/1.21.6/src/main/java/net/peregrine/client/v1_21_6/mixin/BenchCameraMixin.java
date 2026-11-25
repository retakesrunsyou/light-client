package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Benchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Benchmark: while the test runs, the camera flies its path (before the view is culled). */
@Mixin(Camera.class)
public abstract class BenchCameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)   // 1.21 places the camera in setup()
    private void peregrine$benchmark(CallbackInfo ci) {
        if (!Benchmark.INSTANCE.running()) {
            return;
        }
        Entity e = Minecraft.getInstance().player;
        if (e == null) {
            return;
        }
        double[] c = Benchmark.INSTANCE.camera(e.getX(), e.getEyeY(), e.getZ());
        if (c != null) {
            setPosition(c[0], c[1], c[2]);
            setRotation((float) c[3], (float) c[4]);
        }
    }
}
