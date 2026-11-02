package com.bdtg.xrayplus.config;

import com.bdtg.xrayplus.XrayPlus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple JSON config (config/xrayplus.json). Deliberately dependency free: no Cloth Config, no
 * Forge config spec, so the mod keeps working in any 1.20.1 pack.
 */
public final class XrayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("xrayplus.json");

    private static XrayConfig instance = defaults();

    /** Master switch, persisted so the mod comes back the way you left it. */
    public boolean enabled = false;
    /** Force the lightmap to full white while Xray is on (works in caves and at night). */
    public boolean fullbright = true;
    /** Small on-screen indicator while Xray is active. */
    public boolean hud = true;
    /** Automatically include any block whose registry path looks like an ore (…_ore, raw_…). */
    public boolean nameHeuristic = true;

    /** "esp" = keep terrain, draw boxes around targets; "xray" = hide terrain (classic). */
    public String mode = "esp";
    /** Block radius scanned for ESP boxes (chunks around the player). */
    public int espRadius = 48;
    /** Draw ESP boxes through walls (no depth test). */
    public boolean espThroughWalls = true;
    /** Hard cap on rendered boxes so big caves stay smooth. */
    public int espMaxBlocks = 400;
    /** Radius used by /xray scan and /xray find statistics. */
    public int scanRadius = 64;
    /** Block id highlighted as search target, e.g. "minecraft:diamond_ore". Null = none. */
    public String focusBlock = null;
    /** When a focus block is set, draw only that block instead of the whole whitelist. */
    public boolean focusOnly = false;
    /** In xray mode, draw faint silhouettes of nearby solid blocks so you can still navigate. */
    public boolean outlineTerrain = true;
    /** Radius of that silhouette pass. */
    public int outlineRadius = 10;

    public List<Group> groups = defaultGroups();
    public List<String> extraBlocks = new ArrayList<>();
    public List<String> blacklist = new ArrayList<>();

    public static final class Group {
        public String id;
        public boolean enabled;
        public List<String> tags = new ArrayList<>();
        public List<String> blocks = new ArrayList<>();
        /** Optional ESP colour, e.g. "#FFC24A"; null = colour for the group id. */
        public String color = null;

        public Group() {
        }

        public Group(String id, boolean enabled, List<String> tags, List<String> blocks) {
            this.id = id;
            this.enabled = enabled;
            this.tags = new ArrayList<>(tags);
            this.blocks = new ArrayList<>(blocks);
        }

        public int color() {
            if (color != null && !color.isBlank()) {
                try {
                    String hex = color.startsWith("#") ? color.substring(1) : color.trim();
                    if (hex.length() == 6) {
                        hex = "FF" + hex;
                    }
                    return (int) Long.parseLong(hex, 16);
                } catch (Exception ignored) {
                    // fall through to the per-group default
                }
            }
            return switch (id == null ? "" : id) {
                case "ores" -> 0xFFFFC24A;
                case "containers" -> 0xFF6BE07A;
                case "spawners" -> 0xFFFF5A5A;
                case "portals" -> 0xFFB98CFF;
                default -> 0xFFFFC24A;
            };
        }
    }

    private static List<Group> defaultGroups() {
        List<Group> list = new ArrayList<>();
        list.add(new Group("ores", true,
                List.of("forge:ores", "c:ores", "forge:ores_in_ground/stone",
                        "forge:ores_in_ground/deepslate", "forge:ores_in_ground/netherrack"),
                List.of("minecraft:ancient_debris",
                        "minecraft:amethyst_cluster",
                        "minecraft:budding_amethyst",
                        "minecraft:nether_quartz_ore",
                        "minecraft:nether_gold_ore")));
        list.add(new Group("containers", true,
                List.of(),
                List.of("minecraft:chest",
                        "minecraft:trapped_chest",
                        "minecraft:barrel",
                        "minecraft:ender_chest")));
        list.add(new Group("spawners", true,
                List.of(),
                List.of("minecraft:spawner")));
        list.add(new Group("portals", false,
                List.of(),
                List.of("minecraft:end_portal_frame",
                        "minecraft:end_portal",
                        "minecraft:nether_portal")));
        return list;
    }

    public static XrayConfig get() {
        return instance;
    }

    public static Group group(String id) {
        for (Group g : instance.groups) {
            if (g.id.equalsIgnoreCase(id)) {
                return g;
            }
        }
        return null;
    }

    /** Restores factory defaults (used by /xray reset). */
    public static void reset() {
        instance = defaults();
        save();
    }

    public static void load() {
        XrayConfig loaded = null;
        if (Files.exists(FILE)) {
            try {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                loaded = GSON.fromJson(json, XrayConfig.class);
            } catch (Exception e) {
                XrayPlus.LOGGER.error("[XrayPlus] Could not read {}, using defaults", FILE, e);
            }
        }
        instance = merged(loaded);
        save();
    }

    /** Re-adds groups/fields that are missing from an older config file. */
    private static XrayConfig merged(XrayConfig loaded) {
        if (loaded == null) {
            return defaults();
        }
        if (loaded.groups == null) {
            loaded.groups = new ArrayList<>();
        }
        if (loaded.extraBlocks == null) {
            loaded.extraBlocks = new ArrayList<>();
        }
        if (loaded.blacklist == null) {
            loaded.blacklist = new ArrayList<>();
        }
        for (Group def : defaultGroups()) {
            if (loaded.group(def.id) == null) {
                loaded.groups.add(def);
            }
        }
        return loaded;
    }

    private static XrayConfig defaults() {
        XrayConfig cfg = new XrayConfig();
        cfg.groups = defaultGroups();
        return cfg;
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            XrayPlus.LOGGER.error("[XrayPlus] Could not write {}", FILE, e);
        }
    }
}
