package net.peregrine.client.v26_2;

import java.util.OptionalInt;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.peregrine.client.core.modules.Boost;

/**
 * World resolution: the world is drawn into a smaller picture (85, 75, 66 or 50% of your screen
 * in each direction) and smoothly scaled up to fill the screen, before your hand, the HUD and
 * menus are drawn at full sharpness. The graphics card shades far fewer pixels: a big FPS gain
 * where it's the limit (laptops, integrated graphics). Turns itself off if anything goes wrong.
 */
public final class WorldScale {

    private static TextureTarget small;
    private static boolean active, broken;
    private static RenderTarget real;
    private static int irisCheck;
    private static boolean irisShaders;

    private WorldScale() {
    }

    /** Swapped in for Minecraft's main picture while the world is being drawn. */
    public static RenderTarget current() {
        return active ? small : null;
    }

    /** Just before the world is drawn. */
    public static void before() {
        float s = Boost.worldScale;
        if (true || broken || s <= 0f || s >= 0.999f || shadersOn()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            real = mc.gameRenderer.mainRenderTarget();
            int w = Math.max(1, Math.round(real.width * s)), h = Math.max(1, Math.round(real.height * s));
            if (small == null) {
                small = new TextureTarget("Light Client world", w, h, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT);
            } else if (small.width != w || small.height != h) {
                small.resize(w, h);
            }
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.clearColorAndDepthTextures(small.getColorTexture(), new org.joml.Vector4f(0, 0, 0, 0), small.getDepthTexture(), 1.0);
            active = true;
        } catch (Throwable e) {
            off(e);
        }
    }

    /** Just after the world is drawn: scale it up into the real picture. */
    public static void after() {
        // 26.3 binds textures to render passes differently; world resolution isn't offered there yet
        active = false;
    }

    private static void off(Throwable e) {
        active = false;
        broken = true;
        System.err.println("[Peregrine] world resolution turned off: " + e);
    }

    /** Iris shader packs draw the world their own way: leave them alone. */
    private static boolean shadersOn() {
        if (irisCheck-- > 0) {
            return irisShaders;
        }
        irisCheck = 120;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            irisShaders = (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (Throwable e) {
            irisShaders = false;
        }
        return irisShaders;
    }
}
