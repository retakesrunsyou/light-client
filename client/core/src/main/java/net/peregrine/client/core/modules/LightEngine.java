package net.peregrine.client.core.modules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Light Engine: one switch that takes over the game's render settings and Light Client's FPS
 * features, set for a preset (Potato, Balanced, Max FPS, Quality). Your own settings are kept
 * aside while it's on and put back when it's switched off.
 */
public final class LightEngine extends Module {

    public static final String[] PRESETS = {"Potato", "Balanced", "Max FPS", "Quality"};
    public static final String[] ABOUT = {
        "For older PCs: short view, no extras",
        "Smooth and good-looking: the everyday pick",
        "As many frames as possible",
        "Long view and every detail, kept steady"
    };

    public final ChoiceSetting preset = add(new ChoiceSetting("preset", "Preset", 1, PRESETS));

    /** {render distance, simulation, entity distance x100, particles, clouds, smooth lighting, biome blend, mipmaps, entity shadows} */
    private static final int[][] VIDEO = {
        {6, 5, 50, 2, 0, 0, 0, 0, 0},
        {10, 8, 75, 1, 1, 1, 2, 2, 0},
        {8, 6, 60, 2, 0, 1, 0, 1, 0},
        {16, 12, 100, 0, 2, 1, 5, 4, 1},
    };
    private static final String[] KEYS = {"simulation", "clouds", "smooth_lighting", "biome_blend", "mipmaps", "entity_shadows"};
    /** Light Client features each preset turns on (the rest are left as they are). */
    private static final String[][] FEATURES = {
        {"boost", "fewer_particles", "hide_plants", "smooth_join"},
        {"boost", "fewer_particles", "smooth_join"},
        {"boost", "fewer_particles", "hide_plants", "smooth_join"},
        {"smooth_join"},
    };

    private int applied = -1;

    public LightEngine() {
        super("light_engine", "Light Engine",
                "Takes over the game's render settings and Light Client's FPS features for a preset. Off puts yours back",
                Category.PERFORMANCE, false);
    }

    @Override
    protected void onEnable() {
        applied = -1;
    }

    @Override
    public void tick(Platform p) {
        if (applied != preset.index) {
            apply(p, preset.index);
        }
    }

    @Override
    protected void onDisable() {
        Platform p = platform();
        if (p != null) {
            restore(p);
        }
        applied = -1;
    }

    /** Sets everything for preset i now (keeping your own settings aside the first time). */
    public void apply(Platform p, int i) {
        i = Math.max(0, Math.min(PRESETS.length - 1, i));
        preset.index = i;
        keep(p);
        int[] v = VIDEO[i];
        p.setRenderDistance(v[0]);
        p.setVideo("simulation", v[1]);
        p.setEntityDistance(v[2] / 100.0);
        p.setParticleLevel(v[3]);
        p.setVideo("clouds", v[4]);
        p.setVideo("smooth_lighting", v[5]);
        p.setVideo("biome_blend", v[6]);
        p.setVideo("mipmaps", v[7]);
        p.setVideo("entity_shadows", v[8]);
        p.setVideo("max_fps", 260);   // no cap: Stable FPS and V-Sync below decide
        p.setVideo("vsync", 0);
        Peregrine pc = Peregrine.get();
        for (String id : FEATURES[i]) {
            Module m = pc.module(id);
            if (m != null && !m.enabled() && p.supports(id)) {
                m.setEnabled(true);
            }
        }
        Module sf = pc.module("stable_fps");
        if (sf instanceof StableFps) {
            StableFps s = (StableFps) sf;
            if (i == 0) {
                s.applyPreset(1, true, 6);
            } else if (i == 1) {
                s.applyPreset(2, true, 10);
            } else if (i == 2) {
                s.applyPreset(4, false, 8);
            } else {
                s.applyPreset(2, false, 12);
            }
        }
        p.saveOptions();
        applied = i;
        pc.markDirty();
    }

    // ---- your own settings, kept aside while Light Engine is on

    private Path savedFile() {
        Path cfg = Peregrine.get().configPath();
        return cfg == null ? null : cfg.resolveSibling("light-engine-yours.txt");
    }

    private void keep(Platform p) {
        Path f = savedFile();
        if (f == null || Files.exists(f)) {
            return;   // already kept from before it first took over
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("render", String.valueOf(p.renderDistance()));
        m.put("entity", String.valueOf(p.entityDistance()));
        m.put("particles", String.valueOf(p.particleLevel()));
        for (String k : KEYS) {
            m.put(k, String.valueOf(p.video(k)));
        }
        m.put("max_fps", String.valueOf(p.video("max_fps")));
        m.put("vsync", String.valueOf(p.video("vsync")));
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        try {
            Files.write(f, b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.out.println("[Light Client] couldn't keep your video settings: " + e);
        }
    }

    private void restore(Platform p) {
        Path f = savedFile();
        if (f == null || !Files.exists(f)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = line.substring(0, eq), v = line.substring(eq + 1).trim();
                if (v.startsWith("-1")) {
                    continue;   // unknown on this version
                }
                if (k.equals("render")) {
                    p.setRenderDistance(Integer.parseInt(v));
                } else if (k.equals("entity")) {
                    p.setEntityDistance(Double.parseDouble(v));
                } else if (k.equals("particles")) {
                    p.setParticleLevel(Integer.parseInt(v));
                } else {
                    p.setVideo(k, Integer.parseInt(v));
                }
            }
            p.saveOptions();
            Files.deleteIfExists(f);
        } catch (Exception e) {
            System.out.println("[Light Client] couldn't put your video settings back: " + e);
        }
    }
}
