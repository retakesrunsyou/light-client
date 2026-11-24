package net.peregrine.client.v26_2;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.peregrine.client.core.modules.GpuMobs;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/**
 * GPU mobs for 26.3 (the same idea as 26.1's and 26.2's, on 26.3's new graphics layer): mobs drawn by the
 * graphics card from shapes it keeps, instead of being rebuilt corner by corner on the processor every frame.
 *
 * 26.3 prepares everything first and draws later inside one pass it opens itself, so the table and
 * the uniform blocks are written once the game has prepared its frame (upload), and the mobs are drawn
 * at the start of the game's own solid pass (draw), on whatever the game draws to (OpenGL or Vulkan).
 *
 * For each kind of model (cow, zombie, ...) the shape is baked once: every corner in its own body
 * part's space, tagged with which part it belongs to, in a buffer on the graphics card. Each frame,
 * for every mob, the game poses the model as usual (setupAnim) and we work out each body part's
 * matrix exactly the way the game does (ModelPart.translateAndRotate); only those matrices, the
 * light, the hurt flash and the tint go to the graphics card. Then all copies of one model in one
 * look are drawn with a single instanced draw through Minecraft's own graphics layer, using Minecraft's own entity fragment shader and textures, so they look the same.
 *
 * Only plain solid and cut-out mobs come here; anything else (see-through layers, outlines,
 * cracks, sprites from an atlas, players, a shader pack) stays on the normal path. Any error
 * switches it off for the session and the game goes on drawing normally.
 */
public final class GpuEntities {

    private GpuEntities() {
    }

    static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("peregrine-client", "core/gpu_entity_rp");
    static final Identifier ENTITY_VERTEX_SHADER = Identifier.withDefaultNamespace("core/entity");
    /** Position, UV, part number (in UV1.x), normal: 28 bytes a corner. */
    static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .build();
    /**
     * The table, in 16-byte rows of four 32-bit numbers: per mob one row (light, hurt flash,
     * tint), then per body part six rows (3 for where it is, 3 for which way its faces point).
     * Read by the graphics card as whole rows (a texel buffer of 32-bit unsigned numbers).
     */
    static final int PART_WORDS = 24, HEAD_WORDS = 4;

    // ------------------------------------------------------------------ baked shapes

    static final class Baked {
        final Model<?> model;
        final List<ModelPart> parts = new ArrayList<ModelPart>();   // every part, in drawing order
        final List<int[]> kids = new ArrayList<int[]>();            // children of each part (indexes)
        final List<Integer> slot = new ArrayList<Integer>();         // matrix slot of each part, -1 if it draws nothing
        int slots;
        int quads;
        GpuBuffer vertices;

        Baked(Model<?> model) {
            this.model = model;
        }

        int stride() {
            return HEAD_WORDS + PART_WORDS * slots;
        }
    }

    private static final Map<Model<?>, Baked> BAKED = new IdentityHashMap<Model<?>, Baked>();
    private static final Map<Model<?>, Boolean> UNSUPPORTED = new IdentityHashMap<Model<?>, Boolean>();
    private static Field cubesField, childrenField;

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart p) throws Exception {
        if (cubesField == null) {
            cubesField = ModelPart.class.getDeclaredField("cubes");
            cubesField.setAccessible(true);
        }
        return (List<ModelPart.Cube>) cubesField.get(p);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart p) throws Exception {
        if (childrenField == null) {
            childrenField = ModelPart.class.getDeclaredField("children");
            childrenField.setAccessible(true);
        }
        return (Map<String, ModelPart>) childrenField.get(p);
    }

    private static int collect(Baked b, ModelPart part) throws Exception {
        int index = b.parts.size();
        b.parts.add(part);
        b.kids.add(null);
        List<ModelPart.Cube> cs = cubes(part);
        int s = -1;
        if (!cs.isEmpty()) {
            s = b.slots++;
            for (ModelPart.Cube c : cs) {
                b.quads += c.polygons.length;
            }
        }
        b.slot.add(s);
        Map<String, ModelPart> ch = children(part);
        int[] k = new int[ch.size()];
        int i = 0;
        for (ModelPart child : ch.values()) {
            k[i++] = collect(b, child);
        }
        b.kids.set(index, k);
        return index;
    }

    private static Baked bake(Model<?> model) throws Exception {
        Baked b = shape(model);
        if (b == null) {
            return null;
        }
        ByteBuffer buf = shapeBytes(b);
        try {
            b.vertices = RenderSystem.getDevice().createBuffer(() -> "Light Client GPU mob shape", GpuBuffer.USAGE_VERTEX, buf);
        } finally {
            MemoryUtil.memFree(buf);
        }
        return b;
    }

    /** The model's parts and slots (no graphics card needed). */
    static Baked shape(Model<?> model) throws Exception {
        Baked b = new Baked(model);
        collect(b, model.root());
        if (b.quads == 0 || b.slots == 0 || b.slots > 512) {
            return null;
        }
        return b;
    }

    /** Every corner of the shape in its part's space (free it with MemoryUtil.memFree). */
    static ByteBuffer shapeBytes(Baked b) throws Exception {
        int size = FORMAT.getVertexSize();
        int oPos = FORMAT.getElement("Position").offset(), oUv = FORMAT.getElement("UV0").offset();
        int oPart = FORMAT.getElement("UV1").offset(), oNrm = FORMAT.getElement("Normal").offset();
        ByteBuffer buf = MemoryUtil.memAlloc(b.quads * 4 * size).order(ByteOrder.LITTLE_ENDIAN);
        try {
            int at = 0;
            for (int pi = 0; pi < b.parts.size(); pi++) {
                int s = b.slot.get(pi);
                if (s < 0) {
                    continue;
                }
                for (ModelPart.Cube c : cubes(b.parts.get(pi))) {
                    for (ModelPart.Polygon poly : c.polygons) {
                        org.joml.Vector3fc n = poly.normal();
                        for (ModelPart.Vertex v : poly.vertices()) {
                            buf.putFloat(at + oPos, v.worldX());
                            buf.putFloat(at + oPos + 4, v.worldY());
                            buf.putFloat(at + oPos + 8, v.worldZ());
                            buf.putFloat(at + oUv, v.u());
                            buf.putFloat(at + oUv + 4, v.v());
                            buf.putShort(at + oPart, (short) s);
                            buf.putShort(at + oPart + 2, (short) 0);
                            buf.put(at + oNrm, normalByte(n.x()));
                            buf.put(at + oNrm + 1, normalByte(n.y()));
                            buf.put(at + oNrm + 2, normalByte(n.z()));
                            buf.put(at + oNrm + 3, (byte) 0);
                            at += size;
                        }
                    }
                }
            }
            buf.limit(at);
            buf.position(0);
            return buf;
        } catch (Exception e) {
            MemoryUtil.memFree(buf);
            throw e;
        }
    }

    private static byte normalByte(float f) {
        return (byte) Math.round(Math.max(-1f, Math.min(1f, f)) * 127f);
    }

    // ------------------------------------------------------------------ this frame's copies

    /** All copies of one model in one look, in the order they came. */
    static final class Group {
        final Baked baked;
        int[] words = new int[1024];
        int len, count, start;

        Group(Baked baked) {
            this.baked = baked;
        }

        /** Just what draw needs (the shape, how many copies, where they start). */
        Group snapshot() {
            Group c = new Group(baked);
            c.words = null;
            c.count = count;
            c.start = start;
            return c;
        }

        void ensure(int more) {
            if (len + more > words.length) {
                words = java.util.Arrays.copyOf(words, Math.max(words.length * 2, len + more));
            }
        }
    }

    /** One look this frame: how the game prepared it (textures, target), the camera then, and its copies. */
    static final class Look {
        PreparedRenderType prepared;
        RenderPipeline pipeline;
        Matrix4f modelView;
        final Map<Baked, Group> groups = new LinkedHashMap<Baked, Group>();

        /** What draw needs, kept apart from the next frame's takes. */
        Look copyForDraw() {
            Look c = new Look();
            c.prepared = prepared;
            c.pipeline = pipeline;
            c.modelView = modelView;
            return c;
        }
    }

    private static final Map<RenderType, Look> FRAME = new LinkedHashMap<RenderType, Look>();
    private static boolean pending;
    private static final PoseStack POSES = new PoseStack();
    private static int frameCopies, passedBack;

    /** Whether this pipeline is one we can stand in for: Minecraft's entity shader, solid or cut-out. */
    private static final Map<RenderPipeline, RenderPipeline> OURS = new IdentityHashMap<RenderPipeline, RenderPipeline>();
    private static final Map<RenderPipeline, Boolean> REFUSED = new IdentityHashMap<RenderPipeline, Boolean>();

    private static RenderPipeline ours(RenderPipeline v) {
        RenderPipeline p = OURS.get(v);
        if (p != null || REFUSED.containsKey(v)) {
            return p;
        }
        ShaderDefines defines = v.getShaderDefines();
        boolean ok = ENTITY_VERTEX_SHADER.equals(v.getShaders().get(ShaderType.VERTEX))
                && v.getPrimitiveTopology() == PrimitiveTopology.QUADS
                && !defines.flags().contains("APPLY_TEXTURE_MATRIX");
        if (ok) {
            try {
                RenderPipeline.Builder b = RenderPipeline.builder()
                        .withLocation(Identifier.fromNamespaceAndPath("peregrine-client",
                                "pipeline/gpu_" + v.getLocation().getPath().replaceAll("[^a-z0-9_./-]", "_")))
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(v.getShaders().get(ShaderType.FRAGMENT));
                for (String flag : defines.flags()) {
                    b.withShaderDefine(flag);
                }
                for (Map.Entry<String, String> e : defines.values().entrySet()) {
                    try {
                        b.withShaderDefine(e.getKey(), Integer.parseInt(e.getValue().trim()));
                    } catch (NumberFormatException nfe) {
                        b.withShaderDefine(e.getKey(), Float.parseFloat(e.getValue().trim()));
                    }
                }
                for (BindGroupLayout layout : v.getBindGroupLayouts()) {
                    b.withBindGroupLayout(layout);
                }
                b.withBindGroupLayout(BindGroupLayout.builder().withUniform("PartData", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT).build());
                b.withPolygonMode(v.getPolygonMode());
                b.withCull(v.isCull());
                List<ColorTargetState> targets = v.getColorTargetStates();
                for (int i = 0; i < targets.size(); i++) {
                    if (targets.get(i) != null) {
                        b.withColorTargetState(i, targets.get(i));
                    } else {
                        b.withUnusedColorTargetState(i);
                    }
                }
                b.withDepthStencilState(Optional.ofNullable(v.getDepthStencilState()));
                b.withVertexBinding(0, FORMAT);
                b.withPrimitiveTopology(PrimitiveTopology.QUADS);
                if (v.pushConstantSize() > 0) {
                    b.withPushConstantSize(v.pushConstantSize());
                }
                p = b.build();
                CompiledRenderPipeline compiled = RenderSystem.getCompiledPipeline(p);   // compiles it now (throws if it can't)
                if (compiled == null || compiled.isClosed()) {
                    fail("its shader didn't compile on this graphics card", null);
                    return null;
                }
                OURS.put(v, p);
                return p;
            } catch (Throwable t) {
                fail("couldn't set up its shader", t);
                return null;
            }
        }
        REFUSED.put(v, Boolean.TRUE);
        return null;
    }

    private static long irisCheckedAt;
    private static boolean irisShaders;

    private static boolean shaderPackOn() {
        long now = System.currentTimeMillis();
        if (now - irisCheckedAt > 1000) {
            irisCheckedAt = now;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object inst = api.getMethod("getInstance").invoke(null);
                irisShaders = (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
            } catch (ClassNotFoundException e) {
                irisShaders = false;
            } catch (Throwable t) {
                irisShaders = true;   // can't tell: be safe
            }
        }
        return irisShaders;
    }

    /**
     * Called for each mob instead of drawing it: true when it was taken (the game must not draw
     * it too), false to let the game draw it as usual.
     */
    /** True while the game draws the world (never for menus, the inventory or the main menu's player). */
    public static volatile boolean inLevel;
    private static Boolean skinLayers;

    /** 3D Skin Layers draws players' outer skin through the normal path: players stay there when it's installed. */
    private static boolean playersStayNormal() {
        if (skinLayers == null) {
            skinLayers = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skinlayers3d");
        }
        return skinLayers;
    }

    public static boolean take(ModelFeatureRenderer.Submit<?> submit) {
        RenderType type = unwrapType(submit.renderType());
        if (!GpuMobs.on || !GpuMobs.broken.isEmpty() || !inLevel) {
            return false;
        }
        try {
            Model<?> model = submit.model();
            if (submit.uvMapping() != null || submit.sheetedDecalPose() != null
                    || type.hasBlending() || type.isOutline()
                    || (model instanceof net.minecraft.client.model.player.PlayerModel && playersStayNormal())
                    || model instanceof net.minecraft.client.model.player.PlayerCapeModel
                    || UNSUPPORTED.containsKey(model) || shaderPackOn()) {
                passedBack++;
                return false;
            }
            if (ours(type.pipeline()) == null) {
                passedBack++;
                return false;
            }
            Baked b = BAKED.get(model);
            if (b == null) {
                b = bake(model);
                if (b == null) {
                    UNSUPPORTED.put(model, Boolean.TRUE);
                    passedBack++;
                    return false;
                }
                BAKED.put(model, b);
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Model<Object> m = (Model) model;
            m.setupAnim(submit.state());
            Look look = FRAME.get(type);
            if (look == null || look.prepared == null) {
                look = look == null ? new Look() : look;
                look.prepared = type.prepare();          // textures and scissor, as the game would draw it
                look.pipeline = type.pipeline();
                look.modelView = RenderSystem.getModelViewMatrixCopy();
                java.util.function.Consumer<Matrix4f> modifier = layering(setup(type));
                if (modifier != null) {
                    modifier.accept(look.modelView);
                }
                FRAME.put(type, look);
            }
            Group g = look.groups.get(b);
            if (g == null) {
                g = new Group(b);
                look.groups.put(b, g);
            }
            int stride = b.stride();
            g.ensure(stride);
            int base = g.len;
            java.util.Arrays.fill(g.words, base, base + stride, 0);
            g.words[base] = submit.lightCoords();
            g.words[base + 1] = submit.overlayCoords();
            g.words[base + 2] = submit.tintedColor();
            POSES.last().set(submit.pose());
            pose(b, 0, g.words, base + HEAD_WORDS);
            g.len += stride;
            g.count++;
            frameCopies++;
            pending = true;
            return true;
        } catch (Throwable t) {
            fail("a mob couldn't be posed", t);
            return false;
        }
    }

    private static final Map<Class<?>, Field> WRAPPED = new IdentityHashMap<Class<?>, Field>();

    /**
     * The render type underneath another mod's wrapper. Iris wraps chests' and other block
     * entities' types even with no shader pack on; the wrapper carries a stand-in setup with no
     * textures, so drawing from it binds the wrong texture. Unwrapped, it's the game's own type.
     */
    static RenderType unwrapType(RenderType t) {
        for (int depth = 0; depth < 4 && t != null && t.getClass() != RenderType.class; depth++) {
            Class<?> c = t.getClass();
            Field f;
            if (WRAPPED.containsKey(c)) {
                f = WRAPPED.get(c);
            } else {
                f = null;
                for (Class<?> k = c; k != null && k != RenderType.class && f == null; k = k.getSuperclass()) {
                    for (Field d : k.getDeclaredFields()) {
                        if (RenderType.class.isAssignableFrom(d.getType()) && !java.lang.reflect.Modifier.isStatic(d.getModifiers())) {
                            d.setAccessible(true);
                            f = d;
                            break;
                        }
                    }
                }
                WRAPPED.put(c, f);
            }
            if (f == null) {
                return t;
            }
            try {
                RenderType inner = (RenderType) f.get(t);
                if (inner == null || inner == t) {
                    return t;
                }
                t = inner;
            } catch (IllegalAccessException e) {
                return t;
            }
        }
        return t;
    }

    /** One part and its children, exactly as ModelPart.render walks them. */
    static void poseFrom(PoseStack.Pose start, Baked b, int[] out, int at) {
        POSES.last().set(start);
        pose(b, 0, out, at);
    }

    private static void pose(Baked b, int index, int[] out, int at) {
        ModelPart part = b.parts.get(index);
        int[] kids = b.kids.get(index);
        int s = b.slot.get(index);
        if (!part.visible || (s < 0 && kids.length == 0)) {
            return;   // hidden, with everything under it (its slots stay zero: nothing drawn)
        }
        POSES.pushPose();
        part.translateAndRotate(POSES);
        if (s >= 0 && !part.skipDraw) {
            PoseStack.Pose pose = POSES.last();
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            int o = at + s * PART_WORDS;
            put(out, o, m.m00(), m.m10(), m.m20(), m.m30());
            put(out, o + 4, m.m01(), m.m11(), m.m21(), m.m31());
            put(out, o + 8, m.m02(), m.m12(), m.m22(), m.m32());
            put(out, o + 12, n.m00(), n.m10(), n.m20(), 0f);
            put(out, o + 16, n.m01(), n.m11(), n.m21(), 0f);
            put(out, o + 20, n.m02(), n.m12(), n.m22(), 0f);
        }
        for (int k : kids) {
            pose(b, k, out, at);
        }
        POSES.popPose();
    }

    private static void put(int[] out, int o, float a, float b, float c, float d) {
        out[o] = Float.floatToRawIntBits(a);
        out[o + 1] = Float.floatToRawIntBits(b);
        out[o + 2] = Float.floatToRawIntBits(c);
        out[o + 3] = Float.floatToRawIntBits(d);
    }

    // ------------------------------------------------------------------ drawing

    private static final GpuBuffer[] TABLES = new GpuBuffer[3];
    private static ByteBuffer staging;

    /** One reused native buffer for the table (grown when needed). */
    private static ByteBuffer staging(int bytes) {
        if (staging == null || staging.capacity() < bytes) {
            if (staging != null) {
                MemoryUtil.memFree(staging);
            }
            staging = MemoryUtil.memAlloc(Math.max(1 << 18, Integer.highestOneBit(bytes) << 1)).order(ByteOrder.LITTLE_ENDIAN);
        }
        staging.clear();
        return staging;
    }
    private static int tableAt;
    private static long lastLog;

    /** Called by the game hooks of other versions; on 26.3 drawing happens in draw(pass). */
    public static void flush() {
    }

    /** Uniform blocks of this frame's draws (written in upload, used in draw). */
    private static final List<Look> READY_LOOKS = new ArrayList<Look>();
    private static final List<GpuBufferSlice[]> READY_SLICES = new ArrayList<GpuBufferSlice[]>();
    private static final List<List<Group>> READY_GROUPS = new ArrayList<List<Group>>();
    private static GpuBuffer readyTable;
    private static int readyCopies;

    /**
     * Once the game has prepared its frame (before any pass is open): the table of where every
     * body part is goes to the graphics card, and one uniform block per draw is written.
     */
    public static void upload() {
        if (!pending) {
            return;
        }
        pending = false;
        clearReady();
        try {
            int words = 0;
            for (Look look : FRAME.values()) {
                for (Group g : look.groups.values()) {
                    if (g.count > 0) {
                        g.start = words;
                        words += g.len;
                    }
                }
            }
            if (words == 0) {
                resetFrame();
                return;
            }
            long bytes = (long) words * 4;
            int slot = tableAt = (tableAt + 1) % TABLES.length;
            GpuBuffer table = TABLES[slot];
            if (table == null || table.isClosed() || table.size() < bytes) {
                if (table != null && !table.isClosed()) {
                    table.close();
                }
                long size = Math.max(1 << 16, Long.highestOneBit(bytes) << 1);
                table = RenderSystem.getDevice().createBuffer(() -> "Light Client GPU mob table",
                        GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, size);
                TABLES[slot] = table;
            }
            ByteBuffer data = staging((int) bytes);
            for (Look look : FRAME.values()) {
                for (Group g : look.groups.values()) {
                    if (g.count > 0) {
                        data.asIntBuffer().position(g.start).put(g.words, 0, g.len);
                    }
                }
            }
            data.position(0).limit((int) bytes);
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.writeToBuffer(table.slice(0, bytes), data);
            for (Look look : FRAME.values()) {
                if (look.prepared == null || ours(look.pipeline) == null) {
                    continue;
                }
                List<Group> live = new ArrayList<Group>();
                for (Group g : look.groups.values()) {
                    if (g.count > 0) {
                        live.add(g.snapshot());
                    }
                }
                if (live.isEmpty()) {
                    continue;
                }
                DynamicGpuData.Transform[] ts = new DynamicGpuData.Transform[live.size()];
                for (int i = 0; i < ts.length; i++) {
                    Group g = live.get(i);
                    ts[i] = new DynamicGpuData.Transform(look.modelView, new Vector4f(1f, 1f, 1f, 1f),
                            new Vector3f(g.start / 4f, g.baked.stride() / 4f, 0f), new Matrix4f());
                }
                READY_LOOKS.add(look.copyForDraw());
                READY_SLICES.add(RenderSystem.getDynamicUniforms().writeTransforms(ts));
                READY_GROUPS.add(live);
            }
            readyTable = table;
            readyCopies = frameCopies;
        } catch (Throwable t) {
            clearReady();
            fail("getting the mobs ready went wrong", t);
        } finally {
            resetFrame();
        }
    }

    private static void resetFrame() {
        for (Look look : FRAME.values()) {
            look.prepared = null;
            for (Group g : look.groups.values()) {
                g.len = 0;
                g.count = 0;
            }
        }
        frameCopies = 0;
    }

    private static void clearReady() {
        READY_LOOKS.clear();
        READY_SLICES.clear();
        READY_GROUPS.clear();
        readyTable = null;
        readyCopies = 0;
    }

    /** At the start of the game's solid pass: draw everything uploaded for this frame (once). */
    public static void draw(RenderPass pass) {
        if (readyTable == null) {
            return;
        }
        int draws = 0;
        try {
            for (int i = 0; i < READY_LOOKS.size(); i++) {
                draws += drawLook(pass, READY_LOOKS.get(i), READY_SLICES.get(i), READY_GROUPS.get(i), readyTable);
            }
            report(readyCopies, draws);
        } catch (Throwable t) {
            fail("drawing went wrong", t);
        } finally {
            clearReady();
        }
    }

    private static Field stateField, layeringField;

    private static RenderSetup setup(RenderType type) throws Exception {
        if (stateField == null) {
            stateField = RenderType.class.getDeclaredField("state");
            stateField.setAccessible(true);
            layeringField = RenderSetup.class.getDeclaredField("layeringTransform");
            layeringField.setAccessible(true);
        }
        return (RenderSetup) stateField.get(type);
    }

    private static Consumer<Matrix4f> layering(RenderSetup setup) {
        try {
            LayeringTransform lt = (LayeringTransform) layeringField.get(setup);
            return lt == null ? null : lt.getModifier();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int drawLook(RenderPass pass, Look look, GpuBufferSlice[] slices, List<Group> live, GpuBuffer table) throws Exception {
        RenderPipeline pipeline = ours(look.pipeline);
        PreparedRenderType prep = look.prepared;
        if (pipeline == null || prep == null || live.isEmpty()) {
            return 0;
        }
        int most = 0;
        for (Group g : live) {
            most = Math.max(most, g.baked.quads * 6);
        }
        RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = quads.getBuffer(most);
        pass.pushDebugGroup(() -> "Light Client GPU mobs");
        try {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            ScissorState sc = prep.scissorState();
            if (sc.enabled()) {
                pass.enableScissor(sc.x(), sc.y(), sc.width(), sc.height());
            } else {
                pass.disableScissor();
            }
            RenderSystem.bindDefaultUniforms(pass);
            for (PreparedRenderType.Texture t : prep.textures()) {
                pass.setUniform(t.name(), t.textureView(), t.sampler());
            }
            pass.setUniform("PartData", table);
            pass.setIndexBuffer(indices, quads.type());
            for (int i = 0; i < live.size(); i++) {
                Group g = live.get(i);
                pass.setUniform("DynamicTransforms", slices[i]);
                pass.setVertexBuffer(0, g.baked.vertices.slice());
                pass.drawIndexed(g.baked.quads * 6, g.count, 0, 0, 0);   // index count, copies, first index, base corner, first copy
            }
            if (sc.enabled()) {
                pass.disableScissor();
            }
        } finally {
            pass.popDebugGroup();
        }
        return live.size();
    }

    private static int peakCopies, peakDraws;

    private static void report(int copies, int draws) {
        // the solid pass can run more than once a frame: keep the biggest of the last few seconds
        peakCopies = Math.max(peakCopies, copies);
        peakDraws = Math.max(peakDraws, draws);
        if (copies > 0) {
            GpuMobs.instances = copies;
            GpuMobs.draws = draws;
        }
        GpuMobs.models = BAKED.size();
        GpuMobs.passedBack = passedBack;
        passedBack = 0;
        long now = System.currentTimeMillis();
        if (now - lastLog > 10_000 && GpuMobs.on) {
            lastLog = now;
            System.out.println("[Light Client] GPU mobs: up to " + peakCopies + " mobs in " + peakDraws + " draws; "
                    + BAKED.size() + " shapes on the graphics card; " + net.minecraft.client.Minecraft.getInstance().getFps() + " FPS");
            peakCopies = peakDraws = 0;
        }
    }

    private static void fail(String why, Throwable t) {
        if (!GpuMobs.broken.isEmpty()) {
            return;
        }
        GpuMobs.broken = why;
        GpuMobs.on = false;
        System.out.println("[Light Client] GPU mobs switched off: " + why + (t == null ? "" : " (" + t + ")"));
        if (t != null) {
            t.printStackTrace();
        }
    }
}
