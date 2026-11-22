package net.peregrine.client.core;

/**
 * One piece of text drawn in 3D in the world this frame (lyrics, for now). The core fills these
 * in; a version adapter that can draw text in the world (see Hooks.canDrawWorldText) draws them
 * during the world, with real perspective, facing the camera.
 *
 * Sizes are in font pixels; size is how many blocks one font pixel is. Per-letter arrays, when
 * set, nudge each letter (in font pixels, degrees, a scale, an ARGB colour) so letters can wave,
 * scatter or light up as they're sung.
 */
public final class WorldText {

    public static final int FONT_MINECRAFT = 0, FONT_ROUNDED = 1, FONT_HEAVY = 2, FONT_HANDWRITTEN = 3;

    public String text = "";
    public double x, y, z;
    /** Blocks per font pixel. */
    public float size = 0.05f;
    /** Turn around the facing axis (degrees), and whether it tilts to face you up and down too. */
    public float roll;
    public boolean faceUpDown = true;
    public int font;
    public int color = 0xFFFFFFFF;
    /** The 3D sides: how many layers, how far apart (font pixels), and their colour. */
    public int layers;
    public float layerStep = 0.3f;
    public int sideColor = 0xFF404040;
    /** 0 for none. */
    public int outlineColor;
    public int glowColor;
    /** Letters lean right by this much per pixel of height (0.2 looks italic). */
    public float slant;
    /** Two colour fringes either side (a glitchy look): 0 for none. */
    public int fringeA, fringeB;
    public float fringe;
    public boolean seeThrough;
    public float[] dx, dy, rot, scale;
    public int[] colors;
}
