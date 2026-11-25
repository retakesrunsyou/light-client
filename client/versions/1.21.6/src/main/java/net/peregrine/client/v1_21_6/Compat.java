package net.peregrine.client.v1_21_6;

/** Small things that changed between Minecraft versions. */
public final class Compat {

    private Compat() {
    }

    public static float camYaw(net.minecraft.client.Camera c) {
        return c.getYRot();
    }

    public static float camPitch(net.minecraft.client.Camera c) {
        return c.getXRot();
    }

    /** Binds a texture to a shader input, smoothly filtered. */
    public static void bindLinear(com.mojang.blaze3d.systems.RenderPass pass, String name, com.mojang.blaze3d.textures.GpuTextureView view) {
        view.texture().setTextureFilter(com.mojang.blaze3d.textures.FilterMode.LINEAR, false);
        pass.bindSampler(name, view);
    }
}
