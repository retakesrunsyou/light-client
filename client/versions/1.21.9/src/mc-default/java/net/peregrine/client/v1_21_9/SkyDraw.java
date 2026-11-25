package net.peregrine.client.v1_21_9;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.peregrine.client.core.Skies;

/**
 * Draws Peregrine's custom sky right after Minecraft's sun, moon and stars. Its own buffer, its own
 * textures (never a resource pack's). If anything about it ever fails, it turns itself off for the
 * rest of the session and the game carries on with the normal sky.
 */
public final class SkyDraw {

    private SkyDraw() {
    }

    /** 1.21.9 and 1.21.10 make their sky draws differently; the custom sky is on 1.21.11 and newer. */
    public static boolean active() {
        return false;
    }

    public static void draw() {
    }
}
