package net.peregrine.client.v26_2;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * Frame generation on 26.3: not here yet. 26.3 no longer opens its window through GLFW, and frame
 * generation hands the window to its own thread, so it needs a new way in. The module stays hidden
 * on 26.3; this keeps the shared code building.
 */
public final class FrameGen {

    static volatile boolean active;

    private FrameGen() {
    }

    public static int shownFps() {
        return -1;
    }

    public static void makeCurrent(long w) {
    }

    public static void swapInterval(int interval) {
    }

    public static void captureWorld() {
    }

    public static boolean capturePresent(GpuTextureView view) {
        return false;
    }

    public static void present(long handle) {
    }

    public static void shutdown() {
    }

    /**
     * The turn from a direction seen from the view now (yaw1, pitch1) to the same direction seen
     * from when the frame was drawn (yaw0, pitch0), as a column-major 3x3 matrix. Minecraft's
     * camera is turned by Ry(180 - yaw) then Rx(-pitch).
     */
    static float[] turn(float yaw0, float pitch0, float yaw1, float pitch1) {
        float[] q0 = cameraToWorld(yaw0, pitch0), q1 = cameraToWorld(yaw1, pitch1);
        float[] r = new float[9];   // r = transpose(q0) * q1, both row-major here
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float v = 0;
                for (int k = 0; k < 3; k++) {
                    v += q0[k * 3 + i] * q1[k * 3 + j];
                }
                r[j * 3 + i] = v;   // column-major for GL
            }
        }
        return r;
    }

    /** Row-major rotation from the camera's own directions to the world's. */
    static float[] cameraToWorld(float yaw, float pitch) {
        double a = Math.PI - Math.toRadians(yaw), b = -Math.toRadians(pitch);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
        // Ry(a) * Rx(b)
        return new float[] {
            ca, sa * sb, sa * cb,
            0, cb, -sb,
            -sa, ca * sb, ca * cb,
        };
    }

}
