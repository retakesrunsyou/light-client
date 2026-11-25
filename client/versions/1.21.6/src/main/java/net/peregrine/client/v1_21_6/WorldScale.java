package net.peregrine.client.v1_21_6;

import java.util.OptionalInt;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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
    private static int readFbo, drawFbo;

    private WorldScale() {
    }

    /** Swapped in for Minecraft's main picture while the world is being drawn. */
    public static RenderTarget current() {
        return active ? small : null;
    }

    /** Just before the world is drawn. */
    public static void before() {
        float s = Boost.worldScale;
        if (broken || s <= 0f || s >= 0.999f || shadersOn()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            real = mc.getMainRenderTarget();
            int w = Math.max(1, Math.round(real.width * s)), h = Math.max(1, Math.round(real.height * s));
            if (small == null) {
                small = new TextureTarget("Light Client world", w, h, true);
            } else if (small.width != w || small.height != h) {
                small.resize(w, h);
            }
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.clearColorAndDepthTextures(small.getColorTexture(), 0, small.getDepthTexture(), 1.0);
            active = true;
        } catch (Throwable e) {
            off(e);
        }
    }

    /** Just after the world is drawn: scale it up into the real picture. */
    public static void after() {
        if (!active) {
            return;
        }
        active = false;
        try {
            // a plain smoothed copy between the two pictures (1.21.6-1.21.8 have no shared blit pass to borrow)
            int from = ((com.mojang.blaze3d.opengl.GlTexture) small.getColorTexture()).glId();
            int to = ((com.mojang.blaze3d.opengl.GlTexture) real.getColorTexture()).glId();
            if (readFbo == 0) {
                readFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
                drawFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
            }
            int oldRead = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
            int oldDraw = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readFbo);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, from, 0);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, to, 0);
            org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, small.width, small.height, 0, 0, real.width, real.height,
                    org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, oldRead);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
        } catch (Throwable e) {
            off(e);
        }
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
