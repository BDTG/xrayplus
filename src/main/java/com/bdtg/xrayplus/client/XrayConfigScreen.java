package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** In-game settings screen (Mods list -> XrayPlus -> Config, or F9 in game). */
public class XrayConfigScreen extends Screen {
    private final Screen parent;

    public XrayConfigScreen(Screen parent) {
        super(Component.literal("XrayPlus"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XrayConfig cfg = XrayConfig.get();
        int center = this.width / 2;
        int y = 40;
        int step = 24;

        this.addRenderableWidget(Button.builder(toggleLabel("Xray", XrayState.isActive()), button -> {
            XrayState.toggle();
            button.setMessage(toggleLabel("Xray", XrayState.isActive()));
        }).bounds(center - 120, y, 240, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("Fullbright", cfg.fullbright), button -> {
            cfg.fullbright = !cfg.fullbright;
            XrayConfig.save();
            ClientHooks.refreshWorld();
            button.setMessage(toggleLabel("Fullbright", cfg.fullbright));
        }).bounds(center - 120, y, 240, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("HUD", cfg.hud), button -> {
            cfg.hud = !cfg.hud;
            XrayConfig.save();
            button.setMessage(toggleLabel("HUD", cfg.hud));
        }).bounds(center - 120, y, 240, 20).build());
        y += step;

        this.addRenderableWidget(Button.builder(toggleLabel("Auto-detect ores", cfg.nameHeuristic), button -> {
            cfg.nameHeuristic = !cfg.nameHeuristic;
            XrayConfig.save();
            XrayState.rebuild();
            ClientHooks.refreshWorld();
            button.setMessage(toggleLabel("Auto-detect ores", cfg.nameHeuristic));
        }).bounds(center - 120, y, 240, 20).build());
        y += step + 6;

        for (XrayConfig.Group group : cfg.groups) {
            this.addRenderableWidget(Button.builder(toggleLabel("Group: " + group.id, group.enabled), button -> {
                group.enabled = !group.enabled;
                XrayConfig.save();
                XrayState.rebuild();
                ClientHooks.refreshWorld();
                button.setMessage(toggleLabel("Group: " + group.id, group.enabled));
            }).bounds(center - 120, y, 240, 20).build());
            y += step;
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(center - 60, this.height - 30, 120, 20).build());
    }

    private static Component toggleLabel(String label, boolean value) {
        return Component.literal(label + ": ").append(value
                ? Component.literal("ON").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(net.minecraft.ChatFormatting.RED));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal(XrayState.visibleCount() + " blocks visible"),
                this.width / 2, this.height - 46, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
