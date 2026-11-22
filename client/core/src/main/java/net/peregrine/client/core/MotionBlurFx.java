package net.peregrine.client.core;

/**
 * The screen effect for motion blur and world saturation together (the game has room for one
 * post effect): which bundled effect matches what's picked. Motion blur comes in 10% steps, the
 * saturation in 25% steps (100% = as the game draws it).
 */
public final class MotionBlurFx {

    private MotionBlurFx() {
    }

    /** The saturation step, 0..200 in 25s; 100 when it's left alone. */
    public static int saturationStep() {
        float s = Hooks.saturation;
        int step = Math.round(Math.max(0f, Math.min(2f, s)) * 4f) * 25;
        return step;
    }

    /**
     * "motion_blur_50" for 50% blur, "saturation_150" for 150% saturation alone,
     * "motion_blur_50_s150" for both, or null when neither is on. prefix is "motion_blur_"
     * (or the version's own variant, such as "motion_blur_sdl_").
     */
    public static String effect(String prefix) {
        float s = Hooks.motionBlur;
        int sat = saturationStep();
        boolean blur = s > 0f;
        if (!blur && sat == 100) {
            return null;
        }
        if (Hooks.motionBlurHits < 100000) {
            Hooks.motionBlurHits++;
        }
        if (!blur) {
            return prefix.replace("motion_blur", "saturation") + sat;
        }
        int step = Math.max(1, Math.min(9, Math.round(s * 10f)));
        return prefix + (step * 10) + (sat == 100 ? "" : "_s" + sat);
    }

    public static boolean ours(String path) {
        return path != null && (path.contains("motion_blur_") || path.contains("saturation_"));
    }
}
