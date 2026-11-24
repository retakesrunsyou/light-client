package net.peregrine.client.v26_2;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 26.3: viewmodel effects aren't offered yet (first-person hands draw differently there), so this
 * only passes everything straight through to the real collector.
 */
public final class FxCollector implements SubmitNodeCollector {

    private final OrderedSubmitNodeCollector d;
    private final SubmitNodeCollector root;

    public FxCollector(SubmitNodeCollector root, float ghost, String skin) {
        this(root, root);
    }

    private FxCollector(OrderedSubmitNodeCollector d, SubmitNodeCollector root) {
        this.d = d;
        this.root = root;
    }

    public SubmitNodeCollector root() {
        return root;
    }

    @Override
    public OrderedSubmitNodeCollector order(int i) {
        return new FxCollector(root.order(i), root);
    }

    @Override
    public void submitShadow(PoseStack pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        d.submitShadow(pose, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack pose, Vec3 at, int y, Component name, boolean seeThrough, int light, CameraRenderState camera) {
        d.submitNameTag(pose, at, y, name, seeThrough, light, camera);
    }

    @Override
    public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode,
                           int light, int color, int background, int outline) {
        d.submitText(pose, x, y, text, shadow, mode, light, color, background, outline);
    }

    @Override
    public void submitTextBackground(PoseStack pose, float x0, float y0, float x1, float y1, int color, Font.DisplayMode mode, int light) {
        d.submitTextBackground(pose, x0, y0, x1, y1, color, mode, light);
    }

    @Override
    public void submitFlame(PoseStack pose, EntityRenderState state, Quaternionf rot) {
        d.submitFlame(pose, state, rot);
    }

    @Override
    public void submitLeash(PoseStack pose, EntityRenderState.LeashState leash) {
        d.submitLeash(pose, leash);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int tint,
                                UvMapping uv, int outline) {
        d.submitModel(model, state, pose, type, light, overlay, tint, uv, outline);
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay,
                                           int tint, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        d.submitCrumblingOverlay(model, state, pose, type, light, overlay, tint, crumbling);
    }

    @Override
    public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state, int light) {
        d.submitMovingBlock(pose, state, light);
    }

    @Override
    public void submitBlockModel(PoseStack pose, RenderType type, List<BlockStateModelPart> parts, int[] tints, int light, int overlay,
                                 int outline) {
        d.submitBlockModel(pose, type, parts, tints, light, overlay, outline);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack pose, List<BlockStateModelPart> parts, int progress, boolean flag) {
        d.submitBreakingBlockModel(pose, parts, progress, flag);
    }

    @Override
    public void submitShapeOutline(PoseStack pose, net.minecraft.world.phys.shapes.VoxelShape shape, RenderType type, int color,
                                   float width, boolean seeThrough) {
        d.submitShapeOutline(pose, shape, type, color, width, seeThrough);
    }

    @Override
    public void submitItem(PoseStack pose, ItemDisplayContext ctx, int light, int overlay, int outline, int[] tints, ItemQuads quads,
                           ItemStackRenderState.FoilType foil) {
        d.submitItem(pose, ctx, light, overlay, outline, tints, quads, foil);
    }

    @Override
    public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        d.submitCustomGeometry(pose, type, renderer);
    }

    @Override
    public void submitQuadParticleGroup(net.minecraft.client.renderer.state.level.QuadParticleRenderState state) {
        d.submitQuadParticleGroup(state);
    }

    @Override
    public void submitGizmoPrimitives(net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group group, CameraRenderState camera,
                                      boolean seeThrough) {
        d.submitGizmoPrimitives(group, camera, seeThrough);
    }
}
