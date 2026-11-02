package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * In-game settings screen (F9, or Mods -> XrayPlus -> Config).
 *
 * <p>Left column: master switch, mode, toggles, groups. Right column: the blocks around you
 * (from the ESP scanner), click a row to make it the focus target - so nothing here needs a
 * command.</p>
 */
public class XrayConfigScreen extends Screen {
    private final Screen parent;
    private OreList oreList;
    private int tickCounter;

    public XrayConfigScreen(Screen parent) {
        super(Component.literal("XrayPlus"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XrayConfig cfg = XrayConfig.get();
        int colW = Math.min(210, (this.width - 30) / 2);
        int gap = 10;
        int leftX = this.width / 2 - colW - gap / 2;
        int rightX = this.width / 2 + gap / 2;
        int y = 34;
        int step = 22;

        // ---- left column: switches -------------------------------------------------
        this.addRenderableWidget(Button.builder(toggleLabel("Xray", XrayState.isActive()), button -> {
            XrayState.toggle();
            button.setMessage(toggleLabel("Xray", XrayState.isActive()));
        }).bounds(leftX, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(modeLabel(), button -> {
            cfg.mode = XrayState.isXrayMode() ? XrayState.MODE_ESP : XrayState.MODE_XRAY;
            XrayConfig.save();
            XrayState.rebuild();
            ClientHooks.onModeChanged();
            button.setMessage(modeLabel());
        }).bounds(leftX, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("Xuyên tường", cfg.espThroughWalls), button -> {
            cfg.espThroughWalls = !cfg.espThroughWalls;
            XrayConfig.save();
            XrayEspRenderer.requestScan();
            button.setMessage(toggleLabel("Xuyên tường", cfg.espThroughWalls));
        }).bounds(leftX, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("Fullbright", cfg.fullbright), button -> {
            cfg.fullbright = !cfg.fullbright;
            XrayConfig.save();
            ClientHooks.refreshWorld();
            button.setMessage(toggleLabel("Fullbright", cfg.fullbright));
        }).bounds(leftX, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("HUD", cfg.hud), button -> {
            cfg.hud = !cfg.hud;
            XrayConfig.save();
            button.setMessage(toggleLabel("HUD", cfg.hud));
        }).bounds(leftX, y, colW, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("Tự dò quặng", cfg.nameHeuristic), button -> {
            cfg.nameHeuristic = !cfg.nameHeuristic;
            XrayConfig.save();
            XrayState.rebuild();
            ClientHooks.refreshWorld();
            XrayEspRenderer.requestScan();
            button.setMessage(toggleLabel("Tự dò quặng", cfg.nameHeuristic));
        }).bounds(leftX, y, colW, 20).build());
        y += step + 4;

        for (XrayConfig.Group group : cfg.groups) {
            this.addRenderableWidget(Button.builder(toggleLabel(group.id, group.enabled), button -> {
                group.enabled = !group.enabled;
                XrayConfig.save();
                XrayState.rebuild();
                ClientHooks.refreshWorld();
                XrayEspRenderer.requestScan();
                button.setMessage(toggleLabel(group.id, group.enabled));
            }).bounds(leftX, y, colW, 20).build());
            y += step;
        }

        // ---- right column: nearby ores --------------------------------------------
        if (this.width >= 420) {
            int listTop = 34 + 12;
            int listBottom = Math.max(listTop + 40, this.height - 116);
            this.oreList = new OreList(this.minecraft, colW, listBottom - listTop, listTop, listBottom, 14);
            this.oreList.setLeftPos(rightX);
            this.addRenderableWidget(this.oreList);
            refreshList();

            int by = listBottom + 4;
            int half = (colW - 4) / 2;
            this.addRenderableWidget(Button.builder(Component.literal("Quét lại"), button -> {
                XrayEspRenderer.requestScan();
                refreshList();
            }).bounds(rightX, by, half, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Bỏ focus"), button -> {
                XrayConfig.get().focusBlock = null;
                XrayConfig.save();
                XrayEspRenderer.requestScan();
                refreshList();
            }).bounds(rightX + half + 4, by, half, 20).build());
            by += step;

            this.addRenderableWidget(Button.builder(toggleLabel("Chỉ focus", cfg.focusOnly), button -> {
                cfg.focusOnly = !cfg.focusOnly;
                XrayConfig.save();
                XrayEspRenderer.requestScan();
                button.setMessage(toggleLabel("Chỉ focus", cfg.focusOnly));
            }).bounds(rightX, by, colW, 20).build());
            by += step;

            this.addRenderableWidget(Button.builder(Component.literal("Bán kính: " + cfg.espRadius), button -> {
                int next = cfg.espRadius >= 96 ? 8 : cfg.espRadius + 16;
                cfg.espRadius = next;
                cfg.scanRadius = Math.max(cfg.scanRadius, next);
                XrayConfig.save();
                XrayEspRenderer.requestScan();
                button.setMessage(Component.literal("Bán kính: " + cfg.espRadius));
            }).bounds(rightX, by, colW, 20).build());

            XrayEspRenderer.requestScan();
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
    }

    private Component modeLabel() {
        return Component.literal("Mode: ").append(Component.literal(XrayState.isXrayMode() ? "XRAY" : "ESP")
                .withStyle(XrayState.isXrayMode() ? ChatFormatting.RED : ChatFormatting.GREEN));
    }

    private static Component toggleLabel(String label, boolean value) {
        return Component.literal(label + ": ").append(value
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private void refreshList() {
        if (this.oreList != null) {
            this.oreList.reload();
        }
    }

    @Override
    public void tick() {
        if (++this.tickCounter % 10 == 0) {
            refreshList();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        XrayConfig cfg = XrayConfig.get();
        String focus = cfg.focusBlock == null ? "không" : cfg.focusBlock;
        graphics.drawString(this.font, "Quanh đây — bấm 1 dòng để focus", this.width / 2 + 5, 34, 0xA0A0A0, false);
        graphics.drawCenteredString(this.font,
                Component.literal(XrayState.visibleCount() + " block trong danh sách · focus: " + focus
                        + " · bán kính " + cfg.espRadius),
                this.width / 2, this.height - 44, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.oreList != null && this.oreList.children().isEmpty()) {
            graphics.drawString(this.font, "Chưa có dữ liệu — bật Xray rồi chờ ~1s",
                    this.width / 2 + 5, 50, 0xFF8080, false);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void setFocus(Block block) {
        XrayConfig.get().focusBlock = XrayEspRenderer.nameOf(block);
        XrayConfig.save();
        XrayEspRenderer.requestScan();
        refreshList();
    }

    /** Scrollable list of the whitelisted blocks found around the player. */
    private final class OreList extends ObjectSelectionList<OreList.OreEntry> {
        private OreList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        @Override
        public int getRowWidth() {
            return this.width - 8;
        }

        /** Rebuilds the rows from the latest scan (protected addEntry/clearEntries live here). */
        private void reload() {
            this.clearEntries();
            List<Map.Entry<Block, XrayEspRenderer.Stat>> stats = XrayEspRenderer.sortedStats();
            for (Map.Entry<Block, XrayEspRenderer.Stat> entry : stats) {
                this.addEntry(new OreEntry(entry.getKey(), entry.getValue()));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            OreEntry entry = this.getEntryAtPosition(mouseX, mouseY);
            if (entry != null) {
                setFocus(entry.block);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private final class OreEntry extends ObjectSelectionList.Entry<OreEntry> {
            private final Block block;
            private final XrayEspRenderer.Stat stat;
            private final String label;

            private OreEntry(Block block, XrayEspRenderer.Stat stat) {
                this.block = block;
                this.stat = stat;
                this.label = XrayEspRenderer.nameOf(block) + "  ×" + stat.count();
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                String id = XrayEspRenderer.nameOf(this.block);
                boolean focused = id.equals(XrayConfig.get().focusBlock);
                int color = focused ? 0xFF00E5FF : (hovering ? 0xFFFFFF : 0xC8C8C8);
                net.minecraft.client.gui.Font font = XrayConfigScreen.this.font;

                String dist = Math.round(this.stat.distance()) + "m";
                int distWidth = font.width(dist);
                int nameWidth = Math.max(20, width - distWidth - 14);
                String shown = font.plainSubstrByWidth(this.label, nameWidth);
                graphics.drawString(font, shown, left + 4, top + 3, color, false);
                graphics.drawString(font, dist, left + width - distWidth - 4, top + 3, color, false);
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.label);
            }
        }
    }
}
