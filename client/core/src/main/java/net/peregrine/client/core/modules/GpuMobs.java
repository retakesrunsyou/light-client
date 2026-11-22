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
    static final long PHASE_MS = 10_000, SETTLE_MS = 3_000;

    private int testState;        // 0 off, 1 spawning, 2 measuring, 3 cleaning up
    private boolean testOn;
    private int spawned, phase;
    private long phaseStart;
    private final List<Long> frames = new ArrayList<Long>();
    private long lastFrame;
    private final double[][] results = new double[4][];
    private int[] perPhaseMobs = new int[4];
    private boolean enabledBefore;

    private String buttonText() {
        return testState == 0 ? "Start" : "Stop (" + Math.min(4, phase + (testState == 2 ? 1 : 0)) + "/4)";
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
        if (!p.localWorld() || p.gameMode() != 1) {
            Peregrine.get().log("GPU mobs test: open a creative singleplayer world (with cheats on) first");
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
        Peregrine.get().log("GPU mobs test: spawning " + COLS * ROWS + " still mobs in front of you; don't move or turn for about 45 seconds");
        Peregrine.get().menu().close();
    }

    private void testTick(Platform p) {
        long now = System.currentTimeMillis();
        if (testState == 1) {
            for (int i = 0; i < 15 && spawned < COLS * ROWS; i++, spawned++) {
                int c = spawned % COLS, r = spawned / COLS;
                double x = (c - COLS / 2.0) * 1.6, z = 5 + r * 1.6;
                String mob = MOBS[spawned % MOBS.length];
                p.sendChat(String.format(java.util.Locale.ROOT,
                        "/summon minecraft:%s ^%.1f ^ ^%.1f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"lc_gpu_test\"]}",
                        mob, x, z));
            }
            if (spawned >= COLS * ROWS) {
                testState = 2;
                phase = 0;
                startPhase(now);
            }
        } else if (testState == 2) {
            if (now - phaseStart >= SETTLE_MS + PHASE_MS) {
                results[phase] = stats();
                perPhaseMobs[phase] = instances;
                phase++;
                if (phase >= 4) {
                    finishTest(p, "done");
                } else {
                    startPhase(now);
                }
            }
        }
    }

    private void startPhase(long now) {
        testOn = phase % 2 == 1;     // off, on, off, on
        phaseStart = now;
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
                : "GPU mobs test " + (phase + 1) + "/4: " + (testOn ? "ON" : "off") + " - don't move or turn";
        d.text(line, (p.screenWidth() - d.width(line)) / 2, 6, 0xFFFFFFFF, true);
        if (testState != 2) {
            return;
        }
        long t = System.nanoTime();
        if (System.currentTimeMillis() - phaseStart >= SETTLE_MS && lastFrame != 0) {
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
        String[] names = {"off", "on", "off", "on"};
        for (int i = 0; i < 4; i++) {
            if (results[i] != null) {
                b.append(String.format(java.util.Locale.ROOT, " | %s: %.0f FPS, 1%% low %.0f (%d mobs on GPU)",
                        names[i], results[i][0], results[i][1], perPhaseMobs[i]));
            }
        }
        b.append(" | ").append(summary());
        Peregrine.get().log(b.toString());
    }
}
