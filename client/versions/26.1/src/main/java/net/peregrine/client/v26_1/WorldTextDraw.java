package net.peregrine.client.v26_1;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.WorldText;
import org.joml.Matrix4f;


/**
 * Lyrics: draws Hooks.worldTexts in the world. Each piece faces the camera, with layers
 * behind it for thickness, and optional outline, glow and colour fringes, letter by letter when
 * it has per-letter nudges. It all goes into the game's own text drawing (a few quads per
 * letter), so it costs next to nothing.
 */
public final class WorldTextDraw {

    private static final int LIGHT = 0xF000F0;
    private static final Style[] STYLES = new Style[4];
    private static final String[] NAMES = {null, "lyrics_rounded", "lyrics_heavy", "lyrics_handwritten"};

    private WorldTextDraw() {
    }

    static Style style(int font) {
        int f = Math.max(0, Math.min(3, font));
        if (STYLES[f] == null) {
            STYLES[f] = NAMES[f] == null ? Style.EMPTY : Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("peregrine-client", NAMES[f])));
        }
        return STYLES[f];
    }

    static FormattedCharSequence seq(String s, int font) {
        return Component.literal(s).withStyle(style(font)).getVisualOrderText();
    }

    public static float width(String s, int font) {
        Font f = Minecraft.getInstance().font;
        return f == null ? s.length() * 6f : f.width(seq(s, font));
    }

    /** Where the text goes: the game's text buffers or its list of things to draw. */
    interface Sink {
        void text(PoseStack pose, FormattedCharSequence text, float x, float y, int color, int outline, Font.DisplayMode mode);
    }

    public static void draw(PoseStack pose, Sink sink, double camX, double camY, double camZ) {
        WorldText[] all = Hooks.worldTexts;
        for (WorldText t : all) {
            if (t == null || t.text.isEmpty()) {
                continue;
            }
            double dx = t.x - camX, dy = t.y - camY, dz = t.z - camZ;
            if (dx * dx + dy * dy + dz * dz > 160 * 160) {
                continue;
            }
            pose.pushPose();
            pose.translate(dx, dy, dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pose.last().pose().rotate(Axis.YP.rotationDegrees(180f - yaw));   // face the camera
            if (t.faceUpDown) {
                float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                pose.last().pose().rotate(Axis.XP.rotationDegrees(-pitch));
            }
            if (t.roll != 0) {
                pose.last().pose().rotate(Axis.ZP.rotationDegrees(t.roll));
            }
            pose.scale(t.size, -t.size, t.size);
            if (t.slant != 0) {
                pose.last().pose().mul(new Matrix4f().m10(-t.slant));
            }
            Font.DisplayMode mode = t.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET;
            float w = width(t.text, t.font);
            float h = t.font == WorldText.FONT_MINECRAFT ? 8f : 12f;
            if (t.dx == null && t.colors == null) {
                piece(sink, pose, t, t.text, -w / 2f, -h / 2f, t.color, mode);
            } else {
                float cx = -w / 2f;
                int n = t.text.length();
                for (int i = 0; i < n; i++) {
                    String c = t.text.substring(i, i + 1);
                    float cw = width(c, t.font);
                    if (!c.equals(" ")) {
                        int color = t.colors != null && i < t.colors.length ? t.colors[i] : t.color;
                        if (t.dx != null && i < t.dx.length) {
                            pose.pushPose();
                            pose.translate(cx + cw / 2f + t.dx[i], t.dy[i], 0);
                            if (t.rot != null && t.rot[i] != 0) {
                                pose.last().pose().rotate(Axis.ZP.rotationDegrees(t.rot[i]));
                            }
                            if (t.scale != null && t.scale[i] != 1f) {
                                pose.scale(t.scale[i], t.scale[i], t.scale[i]);
                            }
                            piece(sink, pose, t, c, -cw / 2f, -h / 2f, color, mode);
                            pose.popPose();
                        } else {
                            piece(sink, pose, t, c, cx, -h / 2f, color, mode);
                        }
                    }
                    cx += cw;
                }
            }
            pose.popPose();
        }
    }

    /** One run of text: glow, the 3D sides back to front, colour fringes, then the face. */
    private static void piece(Sink sink, PoseStack pose, WorldText t, String s, float x, float y, int color, Font.DisplayMode mode) {
        int alpha = color >>> 24;
        if (alpha < 4) {
            return;
        }
        FormattedCharSequence text = seq(s, t.font);
        if (t.glowColor != 0) {
            int g = withAlpha(t.glowColor, ((t.glowColor >>> 24) * alpha) / 255);
            pose.pushPose();
            pose.translate(0, 0, -0.02f);
            sink.text(pose, text, x - 0.7f, y, g, 0, mode);
            sink.text(pose, text, x + 0.7f, y, g, 0, mode);
            sink.text(pose, text, x, y - 0.7f, g, 0, mode);
            sink.text(pose, text, x, y + 0.7f, g, 0, mode);
            pose.popPose();
        }
        for (int i = t.layers; i >= 1; i--) {
            pose.pushPose();
            pose.translate(0, 0, -i * t.layerStep);   // behind the face
            float shade = 1f - 0.35f * i / Math.max(1, t.layers);
            sink.text(pose, text, x, y, shade(withAlpha(t.sideColor, alpha), shade), 0, mode);
            pose.popPose();
        }
        if (t.fringe > 0) {
            sink.text(pose, text, x - t.fringe, y, withAlpha(t.fringeA, alpha * 3 / 4), 0, mode);
            sink.text(pose, text, x + t.fringe, y, withAlpha(t.fringeB, alpha * 3 / 4), 0, mode);
        }
        sink.text(pose, text, x, y, color, t.outlineColor != 0 && !t.seeThrough ? withAlpha(t.outlineColor, alpha) : 0, mode);
    }

    static int withAlpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    private static int shade(int argb, float f) {
        int r = Math.round(((argb >> 16) & 255) * f), g = Math.round(((argb >> 8) & 255) * f), b = Math.round((argb & 255) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Into the game's text buffers (they're drawn when the buffers are). */
    public static void drawInto(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, double cx, double cy, double cz) {
        if (Hooks.worldTexts.length == 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        draw(pose, (p, text, x, y, color, outline, mode) -> {
            if (outline != 0) {
                font.drawInBatch8xOutline(text, x, y, color, outline, p.last().pose(), buffers, LIGHT);
            } else {
                font.drawInBatch(text, x, y, color, false, p.last().pose(), buffers, mode, 0, LIGHT);
            }
        }, cx, cy, cz);
        if (buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource) {
            ((net.minecraft.client.renderer.MultiBufferSource.BufferSource) buffers).endBatch();
        }
    }

}
