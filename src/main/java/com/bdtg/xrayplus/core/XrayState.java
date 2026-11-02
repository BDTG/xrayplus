package com.bdtg.xrayplus.core;

import com.bdtg.xrayplus.XrayPlus;
import com.bdtg.xrayplus.client.ClientHooks;
import com.bdtg.xrayplus.config.XrayConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime state of the mod. The visible-block set is rebuilt from the config and swapped in
 * atomically, so chunk builder threads always read a consistent set.
 */
public final class XrayState {
    public static final String MODE_ESP = "esp";
    public static final String MODE_XRAY = "xray";

    private static volatile boolean active = false;
    private static volatile Set<Block> visible = Collections.emptySet();
    private static volatile Map<Block, Integer> colors = Map.of();
    private static volatile List<String> activeGroups = List.of();

    private XrayState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static int visibleCount() {
        return visible.size();
    }

    public static List<String> activeGroupIds() {
        return activeGroups;
    }

    public static String mode() {
        String m = XrayConfig.get().mode;
        return MODE_XRAY.equalsIgnoreCase(m) ? MODE_XRAY : MODE_ESP;
    }

    public static boolean isXrayMode() {
        return MODE_XRAY.equals(mode());
    }

    /** ESP colour of a block (gold when the block has no group colour). */
    public static int colorOf(Block block) {
        Integer c = colors.get(block);
        return c == null ? 0xFFFFC24A : c;
    }

    /** True when the block belongs to an enabled group (used by the ESP scanner). */
    public static boolean shouldShow(BlockState state) {
        return visible.contains(state.getBlock());
    }

    /** True when the block id is part of the current whitelist. */
    public static boolean contains(Block block) {
        return visible.contains(block);
    }

    /**
     * Called from the chunk mesh builder for every block. Must stay allocation free and cheap:
     * it runs millions of times while terrain is being built. Only XRAY mode hides anything.
     */
    public static boolean shouldHide(BlockState state) {
        if (!active || !isXrayMode()) {
            return false;
        }
        Block block = state.getBlock();
        return block != Blocks.AIR && !visible.contains(block);
    }

    public static void toggle() {
        setActive(!active);
    }

    /** Applies the persisted state without saving or rebuilding the world (used at startup). */
    public static void setActiveSilently(boolean value) {
        active = value;
    }

    /** Sorted registry ids of everything Xray currently keeps visible (for /xray list). */
    public static List<String> visibleBlockIds() {
        List<ResourceLocation> ids = new ArrayList<>(visible.size());
        for (Block block : visible) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id != null) {
                ids.add(id);
            }
        }
        ids.sort(ResourceLocation::compareTo);
        List<String> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            out.add(id.toString());
        }
        return out;
    }

    public static void setActive(boolean value) {
        if (value) {
            rebuild();
        }
        active = value;
        XrayConfig.get().enabled = value;
        XrayConfig.save();
        XrayPlus.LOGGER.info("[XrayPlus] {} {}", mode(), value ? "enabled" : "disabled");
        ClientHooks.onModeChanged();
    }

    /** Re-resolves groups/tags and block ids into a plain Set<Block> plus ESP colours. */
    public static void rebuild() {
        XrayConfig cfg = XrayConfig.get();
        Set<Block> set = new HashSet<>(256);
        Map<Block, Integer> palette = new HashMap<>(256);
        List<String> groups = new ArrayList<>();

        for (XrayConfig.Group group : cfg.groups) {
            if (!group.enabled) {
                continue;
            }
            groups.add(group.id);
            int color = group.color();
            for (String tagId : group.tags) {
                try {
                    TagKey<Block> key = TagKey.create(Registries.BLOCK, new ResourceLocation(tagId));
                    BuiltInRegistries.BLOCK.getTag(key).ifPresent(holders -> holders.forEach(holder -> {
                        set.add(holder.value());
                        palette.put(holder.value(), color);
                    }));
                } catch (Exception e) {
                    XrayPlus.LOGGER.warn("[XrayPlus] Skipping bad block tag '{}'", tagId);
                }
            }
            for (String blockId : group.blocks) {
                Block block = resolve(blockId);
                if (block != null) {
                    set.add(block);
                    palette.put(block, color);
                }
            }
        }

        for (String blockId : cfg.extraBlocks) {
            Block block = resolve(blockId);
            if (block != null) {
                set.add(block);
                palette.putIfAbsent(block, 0xFFFFC24A);
            }
        }

        if (cfg.nameHeuristic) {
            for (Block block : ForgeRegistries.BLOCKS) {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                if (id == null) {
                    continue;
                }
                String path = id.getPath();
                if (looksLikeOre(path)) {
                    set.add(block);
                    palette.putIfAbsent(block, 0xFFFFC24A);
                }
            }
        }

        for (String blockId : cfg.blacklist) {
            Block block = resolve(blockId);
            if (block != null) {
                set.remove(block);
                palette.remove(block);
            }
        }

        set.add(Blocks.AIR);
        visible = set;
        colors = Map.copyOf(palette);
        activeGroups = List.copyOf(groups);
        XrayPlus.LOGGER.debug("[XrayPlus] {} visible blocks from groups {}", set.size(), groups);
    }

    public static Block resolve(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return null;
            }
            return ForgeRegistries.BLOCKS.getValue(rl);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Name heuristic used both when building the whitelist and by /xray audit, so "detected" and
     * "reported missing" can never disagree.
     *
     * <p>Decorative look-alikes (crystal lamps, crystal chests, sea shelves, displays, skeletons)
     * are excluded on purpose: they are furniture, not ore, and used to spam ESP boxes.</p>
     */
    public static boolean looksLikeOre(String path) {
        if (path.endsWith("_lamp") || path.endsWith("_display") || path.endsWith("_seashelf")
                || path.endsWith("_skeleton") || path.endsWith("_chest") || path.endsWith("_barrel")
                || path.contains("mucus")) {
            return false;
        }
        return path.endsWith("_ore") || path.startsWith("ore_") || path.contains("_ore_")
                || path.contains("geode") || path.endsWith("_cluster") || path.contains("debris")
                || path.contains("crystal") || path.endsWith("_ore_block")
                || path.startsWith("raw_") && path.endsWith("_block");
    }
}
