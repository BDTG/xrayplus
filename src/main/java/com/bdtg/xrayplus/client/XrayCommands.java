package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.StringJoiner;

/** Client-side /xray command tree. */
public final class XrayCommands {
    private static final int MAX_LISTED = 60;

    private XrayCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xray")
                .executes(ctx -> toggle(ctx))
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                .then(Commands.literal("toggle").executes(ctx -> toggle(ctx)))
                .then(Commands.literal("status").executes(ctx -> status(ctx)))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx)))
                .then(Commands.literal("list").executes(ctx -> list(ctx)))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx)))
                .then(Commands.literal("fullbright")
                        .executes(ctx -> flag(ctx, "fullbright", !XrayConfig.get().fullbright))
                        .then(Commands.literal("on").executes(ctx -> flag(ctx, "fullbright", true)))
                        .then(Commands.literal("off").executes(ctx -> flag(ctx, "fullbright", false))))
                .then(Commands.literal("hud")
                        .executes(ctx -> flag(ctx, "hud", !XrayConfig.get().hud))
                        .then(Commands.literal("on").executes(ctx -> flag(ctx, "hud", true)))
                        .then(Commands.literal("off").executes(ctx -> flag(ctx, "hud", false))))
                .then(Commands.literal("heuristic")
                        .executes(ctx -> flag(ctx, "heuristic", !XrayConfig.get().nameHeuristic))
                        .then(Commands.literal("on").executes(ctx -> flag(ctx, "heuristic", true)))
                        .then(Commands.literal("off").executes(ctx -> flag(ctx, "heuristic", false))))
                .then(Commands.literal("mode")
                        .executes(ctx -> mode(ctx, null))
                        .then(Commands.literal("esp").executes(ctx -> mode(ctx, "esp")))
                        .then(Commands.literal("xray").executes(ctx -> mode(ctx, "xray"))))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(8, 96))
                                .executes(ctx -> radius(ctx))))
                .then(Commands.literal("through")
                        .executes(ctx -> flag(ctx, "through", !XrayConfig.get().espThroughWalls))
                        .then(Commands.literal("on").executes(ctx -> flag(ctx, "through", true)))
                        .then(Commands.literal("off").executes(ctx -> flag(ctx, "through", false))))
                .then(Commands.literal("outline")
                        .executes(ctx -> flag(ctx, "outline", !XrayConfig.get().outlineTerrain))
                        .then(Commands.literal("on").executes(ctx -> flag(ctx, "outline", true)))
                        .then(Commands.literal("off").executes(ctx -> flag(ctx, "outline", false))))
                .then(Commands.literal("groups").executes(ctx -> groups(ctx)))
                .then(Commands.literal("group")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.literal("on").executes(ctx -> group(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> group(ctx, false)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .executes(ctx -> edit(ctx, true))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .executes(ctx -> edit(ctx, false)))));
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal("[XrayPlus] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY)), false);
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        XrayState.toggle();
        feedback(ctx, "Xray " + (XrayState.isActive() ? "ON" : "OFF"));
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean value) {
        XrayState.setActive(value);
        feedback(ctx, "Xray " + (value ? "ON" : "OFF"));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        XrayConfig cfg = XrayConfig.get();
        feedback(ctx, "enabled=" + XrayState.isActive()
                + " mode=" + XrayState.mode()
                + " radius=" + cfg.espRadius
                + " through=" + cfg.espThroughWalls
                + " blocks=" + XrayState.visibleCount()
                + " groups=" + String.join(",", XrayState.activeGroupIds())
                + " fullbright=" + cfg.fullbright
                + " hud=" + cfg.hud
                + " heuristic=" + cfg.nameHeuristic);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        XrayConfig.load();
        XrayState.rebuild();
        ClientHooks.refreshWorld();
        feedback(ctx, "Reloaded config/xrayplus.json (" + XrayState.visibleCount() + " blocks)");
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        XrayConfig.reset();
        XrayState.rebuild();
        ClientHooks.refreshWorld();
        feedback(ctx, "Config reset to defaults");
        return 1;
    }

    private static int flag(CommandContext<CommandSourceStack> ctx, String name, boolean value) {
        XrayConfig cfg = XrayConfig.get();
        switch (name) {
            case "fullbright" -> cfg.fullbright = value;
            case "hud" -> cfg.hud = value;
            case "heuristic" -> cfg.nameHeuristic = value;
            case "through" -> cfg.espThroughWalls = value;
            case "outline" -> cfg.outlineTerrain = value;
            default -> {
            }
        }
        XrayConfig.save();
        if (name.equals("fullbright") || name.equals("heuristic")) {
            XrayState.rebuild();
            ClientHooks.onModeChanged();
        }
        feedback(ctx, name + " = " + value);
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, String requested) {
        XrayConfig cfg = XrayConfig.get();
        if (requested == null) {
            cfg.mode = XrayState.isXrayMode() ? "esp" : "xray";
        } else {
            cfg.mode = requested;
        }
        XrayConfig.save();
        XrayState.rebuild();
        ClientHooks.onModeChanged();
        feedback(ctx, "mode = " + XrayState.mode()
                + " (" + (XrayState.isXrayMode() ? "ẩn địa hình" : "địa hình bình thường + khung ESP") + ")");
        return 1;
    }

    private static int radius(CommandContext<CommandSourceStack> ctx) {
        int value = IntegerArgumentType.getInteger(ctx, "blocks");
        XrayConfig.get().espRadius = value;
        XrayConfig.save();
        feedback(ctx, "espRadius = " + value);
        return 1;
    }

    private static int groups(CommandContext<CommandSourceStack> ctx) {
        StringJoiner joiner = new StringJoiner(", ");
        for (XrayConfig.Group group : XrayConfig.get().groups) {
            joiner.add(group.id + "=" + (group.enabled ? "on" : "off"));
        }
        feedback(ctx, "Groups: " + joiner);
        return 1;
    }

    private static int group(CommandContext<CommandSourceStack> ctx, boolean value) {
        String id = StringArgumentType.getString(ctx, "id");
        XrayConfig.Group group = XrayConfig.group(id);
        if (group == null) {
            feedback(ctx, "Unknown group '" + id + "'");
            return 0;
        }
        group.enabled = value;
        XrayConfig.save();
        XrayState.rebuild();
        ClientHooks.refreshWorld();
        feedback(ctx, "Group " + id + " = " + value);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> ctx, boolean add) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "block");
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            feedback(ctx, "Unknown block '" + id + "'");
            return 0;
        }
        List<String> target = XrayConfig.get().extraBlocks;
        String name = id.toString();
        if (add) {
            if (!target.contains(name)) {
                target.add(name);
            }
            XrayConfig.get().blacklist.remove(name);
        } else {
            target.remove(name);
            if (!XrayConfig.get().blacklist.contains(name)) {
                XrayConfig.get().blacklist.add(name);
            }
        }
        XrayConfig.save();
        XrayState.rebuild();
        ClientHooks.refreshWorld();
        feedback(ctx, (add ? "Showing " : "Hiding ") + name);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> ids = XrayState.visibleBlockIds();
        int shown = Math.min(ids.size(), MAX_LISTED);
        StringBuilder builder = new StringBuilder(ids.size() + " visible blocks: ");
        builder.append(String.join(", ", ids.subList(0, shown)));
        if (ids.size() > shown) {
            builder.append(" … (+").append(ids.size() - shown).append(" more)");
        }
        feedback(ctx, builder.toString());
        return 1;
    }
}
