package net.peregrine.client.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The tabs along the top of the modules list. Module.Category is too coarse to find things
 * by (Visuals alone had 50 entries), so each module is filed under one of these by hand.
 * Anything not listed falls back to its category: HUD items to HUD, performance to
 * Performance, the rest to World.
 */
final class ModuleGroups {

    static final String PVP = "PvP", HUD = "HUD", HAND = "Hand & items", WORLD = "World",
            CAMERA = "Camera", SOCIAL = "Social", MENUS = "Menus", PERF = "Performance";

    /** In the order the tabs are shown (after "All"). */
    static final String[] ALL = {PVP, HUD, HAND, WORLD, CAMERA, SOCIAL, MENUS, PERF};

    private static final Map<String, String> BY_ID = new HashMap<String, String>();

    private static void put(String group, String... ids) {
        for (String id : ids) {
            BY_ID.put(id, group);
        }
    }

    static {
        put(PVP, "combo", "cps", "reach", "target", "cooldowns", "totems", "potion_count", "arrows",
                "hit_color", "hitboxes", "mc_tiers", "zero_delay", "small_totem", "old_animations",
                "crosshair", "durability_alert", "armor");
        put(HUD, "biome", "block_count", "block_info", "chunk", "clock", "compass", "coords", "day",
                "effects", "food", "fps", "health", "inventory_hud", "item_tracker", "keystrokes", "light",
                "memory", "music", "nether_coords", "packs", "pie_chart", "ping", "players", "profile",
                "rotation", "schematic", "server", "session", "speed", "stopwatch", "xp", "scoreboard",
                "chat_size", "action_bar", "boss_bars", "screen_text");
        put(HAND, "hand_position", "item_physics", "item_switch", "knives", "pickaxe_look", "swing_speed",
                "viewmodel_fx", "glint_color", "shiny_potions", "smooth_hotbar", "inventory_tweaks",
                "shulker_preview", "place_animation", "low_fire");
        put(WORLD, "block_outline", "chunk_borders", "chunk_fade", "clear_lava", "clear_water", "clear_weather",
                "custom_sky", "fog", "fullbright", "ore_outline", "saturation", "static_sky", "time_changer",
                "tnt_timer", "clean_overlays", "clean_edges", "anti_leak", "waypoints", "area_miner", "hd_look",
                "lyrics", "stretched", "motion_blur", "skeletons");
        put(CAMERA, "freelook", "snaplook", "zoom", "fly_stop", "no_hurt_cam", "static_fov", "steady_camera",
                "toggle_sprint", "toggle_sneak", "quick_commands", "anti_afk", "flight_boost");
        put(SOCIAL, "chat", "nick_hider", "cosmetics", "own_nametag", "tab_list", "auto_reconnect", "clips",
                "click_sounds", "sound_filters", "voice");
        put(MENUS, "main_menu", "pause_menu", "menu_3d", "clean_menus", "no_menu_blur", "smooth_inventory",
                "better_f3", "borderless");
    }

    private ModuleGroups() {
    }

    static String of(Module m) {
        String g = BY_ID.get(m.id);
        if (g != null) {
            return g;
        }
        if (m.category == Module.Category.HUD) {
            return HUD;
        }
        if (m.category == Module.Category.PERFORMANCE) {
            return PERF;
        }
        return WORLD;
    }
}
