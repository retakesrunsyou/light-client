package net.peregrine.client.v26_2;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.peregrine.client.core.Skies;

/**
 * Draws Peregrine's custom sky right after Minecraft's sun, moon and stars. Its own buffer, its own
 * textures (never a resource pack's). If anything about it ever fails, it turns itself off for the
 * rest of the session and the game carries on with the normal sky.
 */
public final class SkyDraw {

    private static final Map<String, RenderType> TYPES = new HashMap<String, RenderType>();
    private static Method create;
    private static net.minecraft.client.renderer.StagedVertexBuffer buffers;
    private static boolean broken;
    private static final long START = System.currentTimeMillis();

    private SkyDraw() {
    }

    private static RenderType type(String id) throws Exception {
        RenderType t = TYPES.get(id);
        if (t != null) {
            return t;
        }
        RenderSetup setup = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
                .withTexture("Sampler0", Identifier.parse(id))
                .createRenderSetup();
        if (create == null) {
            create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            create.setAccessible(true);
        }
        t = (RenderType) create.invoke(null, "peregrine_sky", setup);
        TYPES.put(id, t);
        return t;
    }

    /** Whether the custom sky is drawing (so Minecraft's own sun, moon and stars can be skipped). */
    public static boolean active() {
        return !broken && Skies.sky >= 0;
    }

    public static void draw() {
        if (broken || Skies.sky < 0) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Skies.weather = mc.level.getRainLevel(1f);
            List<Skies.Quad> qs = Skies.build(System.currentTimeMillis() - START, mc.level.getOverworldClockTime());
            if (qs.isEmpty()) {
                return;
            }
            if (buffers == null) {
                buffers = new net.minecraft.client.renderer.StagedVertexBuffer(() -> "Light Client sky", 1 << 20);
            }
            PoseStack.Pose pose = new PoseStack().last();
            // one draw per picture, in order; uploaded together, then drawn
            List<RenderType> types = new java.util.ArrayList<RenderType>();
            List<net.minecraft.client.renderer.StagedVertexBuffer.Draw> draws = new java.util.ArrayList<net.minecraft.client.renderer.StagedVertexBuffer.Draw>();
            RenderType current = null;
            VertexConsumer vc = null;
            for (Skies.Quad q : qs) {
                RenderType t = type(q.texture);
                if (t != current) {
                    current = t;
                    net.minecraft.client.renderer.StagedVertexBuffer.Draw d = buffers.appendDraw(t.format(), t.primitiveTopology());
                    types.add(t);
                    draws.add(d);
                    vc = buffers.getVertexBuilder(d);
                }
                // both sides: the pipeline hides back faces, and the sky is seen from inside
                for (int i = 0; i < 4; i++) {
                    vc.addVertex(pose, q.xyz[i * 3], q.xyz[i * 3 + 1], q.xyz[i * 3 + 2])
                            .setUv(q.uv[i * 2], q.uv[i * 2 + 1])
                            .setColor(q.argb);
                }
                for (int i = 3; i >= 0; i--) {
                    vc.addVertex(pose, q.xyz[i * 3], q.xyz[i * 3 + 1], q.xyz[i * 3 + 2])
                            .setUv(q.uv[i * 2], q.uv[i * 2 + 1])
                            .setColor(q.argb);
                }
            }
            buffers.upload();
            for (int i = 0; i < draws.size(); i++) {
                types.get(i).prepare().drawFromBuffer(buffers.getExecuteInfo(draws.get(i)));
            }
            buffers.endFrame();
        } catch (Throwable e) {
            broken = true;
            System.err.println("[Peregrine] custom sky turned off: " + e);
        }
    }
}
