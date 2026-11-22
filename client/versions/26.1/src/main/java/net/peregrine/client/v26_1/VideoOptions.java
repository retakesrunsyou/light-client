package net.peregrine.client.v26_1;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** The game's video settings the Light Engine presets change, by name (the same on every version). */
final class VideoOptions {

    private VideoOptions() {
    }

    static int get(String key) {
        Options o = Minecraft.getInstance().options;
        if (o == null) {
            return -1;
        }
        switch (key) {
            case "clouds": return o.cloudStatus().get().ordinal();          // 0 off, 1 fast, 2 fancy
            case "smooth_lighting": return o.ambientOcclusion().get() ? 1 : 0;
            case "biome_blend": return o.biomeBlendRadius().get();
            case "mipmaps": return o.mipmapLevels().get();
            case "entity_shadows": return o.entityShadows().get() ? 1 : 0;
            case "max_fps": return o.framerateLimit().get();                 // 260 = unlimited
            case "vsync": return o.enableVsync().get() ? 1 : 0;
            case "simulation": return o.simulationDistance().get();
            default: return -1;
        }
    }

    static void set(String key, int v) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        if (o == null) {
            return;
        }
        try {
            switch (key) {
                case "clouds": o.cloudStatus().set(CloudStatus.values()[Math.max(0, Math.min(CloudStatus.values().length - 1, v))]); break;
                case "smooth_lighting": o.ambientOcclusion().set(v != 0); break;
                case "biome_blend": o.biomeBlendRadius().set(Math.max(0, Math.min(7, v))); break;
                case "mipmaps": o.mipmapLevels().set(Math.max(0, Math.min(4, v))); break;
                case "entity_shadows": o.entityShadows().set(v != 0); break;
                case "max_fps": o.framerateLimit().set(Math.max(10, Math.min(260, v))); break;
                case "vsync": o.enableVsync().set(v != 0); break;
                case "simulation": o.simulationDistance().set(Math.max(5, Math.min(32, v))); break;
                default: return;
            }
        } catch (RuntimeException e) {
            System.out.println("[Light Client] couldn't set " + key + ": " + e);
        }
    }
}
