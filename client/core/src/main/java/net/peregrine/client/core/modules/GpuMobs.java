package net.peregrine.client.core.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ActionSetting;

/**
 * GPU mobs (experimental): mobs are posed by the graphics card instead of rebuilt every frame.
 *
 * Normally the game turns every mob into thousands of corner points on the processor each frame
 * and sends them all to the graphics card. With this on, each kind of mob's shape is sent to the
 * graphics card once; every frame only a small table of where each body part is goes over, and
 * all the cows (say) are drawn in one go. The version adapter does the drawing (GpuEntities in
 * the 26.1 adapter); this module holds the switch, what it did last, and a before / after test.
 *
 * Left to the normal path: see-through layers, glowing outlines, players (so 3D skin layers
 * work), and everything while an Iris shader pack is on. If anything goes wrong it switches
 * itself off and the game carries on as normal.
 */
public final class GpuMobs extends Module {

    /** Read by the game hooks. */
    public static volatile boolean on;
    /** Set by the adapter: what it drew last frame, and why it stopped if it did. */
    public static volatile int instances, draws, models, passedBack;
    public static volatile String broken = "";
    /** Time the game spends on its solid models (mobs) so far, added up by the adapter. */
    public static volatile long solidNanos;

    public final ActionSetting test = add(new ActionSetting("ab_test",
            "Before / after test (a creative world: spawns 300 still mobs in front of you, then removes them)",
            this::buttonText, this::toggleTest));

    public GpuMobs() {
        super("gpu_mobs", "GPU mobs (experimental)",
                "Mobs are posed by your graphics card instead of rebuilt every frame: more FPS with lots of mobs. Same look",
                Category.PERFORMANCE, false);
    }

    @Override
    public void tick(Platform p) {
        on = (enabled() || testState != 0) && broken.isEmpty() && (testState == 0 || testOn);
        if (testState != 0) {
            testTick(p);
        }
    }

    @Override
    protected void onDisable() {
        if (testState == 0) {
            on = false;
        }
    }

    /** One line for the menu and the log. */
    public static String summary() {
        if (!broken.isEmpty()) {
            return "Switched off: " + broken;
        }
        return instances + " mobs in " + draws + " draws, " + models + " shapes on the graphics card, "
                + passedBack + " left to the normal path";
    }

    // ------------------------------------------------------------------ the before / after test

    static final String[] MOBS = {"cow", "pig", "sheep", "chicken", "villager", "zombie"};
    static final int COLS = 20, ROWS = 15;
    static final long PHASE_MS = 8_000, SETTLE_MS = 2_000;
    static final int PHASES = 6;     // off, on, off, on, off, on: each setting gets the same luck

    private int testState;        // 0 off, 1 spawning, 2 measuring, 3 cleaning up
    private boolean testOn;
    private int spawned, phase;
    private long phaseStart;
    private final List<Long> frames = new ArrayList<Long>();
    private long lastFrame;
    private final double[][] results = new double[PHASES][];
    private int[] perPhaseMobs = new int[PHASES];
    private long solidSnap;
    private boolean measuring;
    private boolean enabledBefore;

    private String buttonText() {
        return testState == 0 ? "Start" : "Stop (" + Math.min(PHASES, phase + (testState == 2 ? 1 : 0)) + "/" + PHASES + ")";
    }

    private void toggleTest() {
        Platform p = platform();
        if (p == null) {
            return;
        }
        if (testState != 0) {
            finishTest(p, "stopped");
            return;
        }
        if (!p.inWorld()) {
            Peregrine.get().log("GPU mobs test: join a world first");
            return;
        }
        enabledBefore = enabled();
        if (!enabledBefore) {
            setEnabled(true);   // so the frames can be timed; put back afterwards
        }
        testState = 1;
        spawned = 0;
        phase = 0;
        Arrays.fill(results, null);
        p.sendChat("/kill @e[tag=lc_gpu_test]");
        Peregrine.get().log("GPU mobs test: spawning " + COLS * ROWS + " still mobs in front of you; don't move or turn for about a minute");
        Peregrine.get().menu().close();
    }

    private void testTick(Platform p) {
        long now = System.currentTimeMillis();
        if (testState == 1) {
            // flat rows in front of you, level with your feet (whichever way you're looking up or down)
            double yaw = Math.toRadians(p.yaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = -fz, sz = fx;
            for (int i = 0; i < 15 && spawned < COLS * ROWS; i++, spawned++) {
                int c = spawned % COLS, r = spawned / COLS;
                double side = (c - COLS / 2.0) * 1.6, ahead = 5 + r * 1.6;
                String mob = MOBS[spawned % MOBS.length];
                p.sendChat(String.format(java.util.Locale.ROOT,
                        "/summon minecraft:%s ~%.1f ~ ~%.1f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"lc_gpu_test\"]}",
                        mob, fx * ahead + sx * side, fz * ahead + sz * side));
            }
            if (spawned >= COLS * ROWS) {
                testState = 2;
                phase = 0;
                startPhase(now);
            }
        } else if (testState == 2) {
            if (now - phaseStart >= SETTLE_MS + PHASE_MS) {
                double[] st = stats();
                int n = (int) st[2];
                double drawMs = n > 0 ? (solidNanos - solidSnap) / 1e6 / n : 0;
                results[phase] = new double[] {st[0], st[1], st[2], drawMs};
                perPhaseMobs[phase] = instances;
                phase++;
                if (phase >= PHASES) {
                    finishTest(p, "done");
                } else {
                    startPhase(now);
                }
            }
        }
    }

    private void startPhase(long now) {
        testOn = phase % 2 == 1;     // off, on, off, on, off, on
        phaseStart = now;
        measuring = false;
        synchronized (frames) {
            frames.clear();
        }
        lastFrame = 0;
    }

    /** Every frame while the test runs: how long it took. */
    @Override
    public void renderWorld(Draw d, Platform p) {
        if (testState == 0) {
            return;
        }
        String line = testState == 1 ? "GPU mobs test: spawning mobs... don't move or turn"
                : "GPU mobs test " + (phase + 1) + "/" + PHASES + ": " + (testOn ? "ON" : "off") + " - don't move or turn";
        d.text(line, (p.screenWidth() - d.width(line)) / 2, 6, 0xFFFFFFFF, true);
        if (testState != 2) {
            return;
        }
        long t = System.nanoTime();
        if (System.currentTimeMillis() - phaseStart >= SETTLE_MS && !measuring) {
            measuring = true;          // settled: start counting from here
            solidSnap = solidNanos;
            synchronized (frames) {
                frames.clear();
            }
        }
        if (measuring && lastFrame != 0) {
            synchronized (frames) {
                frames.add(t - lastFrame);
            }
        }
        lastFrame = t;
    }

    /** {average FPS, 1% low FPS, frames}. */
    private double[] stats() {
        long[] f;
        synchronized (frames) {
            f = new long[frames.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = frames.get(i);
            }
        }
        if (f.length < 10) {
            return new double[] {0, 0, f.length};
        }
        long sum = 0;
        for (long v : f) {
            sum += v;
        }
        Arrays.sort(f);
        int n = Math.max(1, f.length / 100);
        long worst = 0;
        for (int i = f.length - n; i < f.length; i++) {
            worst += f[i];
        }
        return new double[] {1e9 * f.length / sum, 1e9 * n / worst, f.length};
    }

    private void finishTest(Platform p, String how) {
        testState = 0;
        testOn = false;
        if (!enabledBefore) {
            setEnabled(false);
        }
        p.sendChat("/kill @e[tag=lc_gpu_test]");
        StringBuilder b = new StringBuilder("GPU mobs test (" + how + "):");
        double[] sum = new double[4];   // FPS off, FPS on, drawing ms off, drawing ms on
        int[] cnt = new int[2];
        for (int i = 0; i < PHASES; i++) {
            if (results[i] != null && results[i][2] > 0) {
                int k = i % 2;
                sum[k] += results[i][0];
                sum[2 + k] += results[i][3];
                cnt[k]++;
                b.append(String.format(java.util.Locale.ROOT, " | %d %s: %.0f FPS, 1%% low %.0f, mob drawing %.2f ms a frame",
                        i + 1, k == 1 ? "ON" : "off", results[i][0], results[i][1], results[i][3]));
            }
        }
        if (cnt[0] > 0 && cnt[1] > 0) {
            b.append(String.format(java.util.Locale.ROOT, " || AVERAGE off: %.0f FPS, mob drawing %.2f ms | ON: %.0f FPS, mob drawing %.2f ms",
                    sum[0] / cnt[0], sum[2] / cnt[0], sum[1] / cnt[1], sum[3] / cnt[1]));
        }
        b.append(" | ").append(summary());
        Peregrine.get().log(b.toString());
    }
}
