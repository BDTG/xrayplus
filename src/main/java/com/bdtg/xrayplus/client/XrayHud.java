package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small always-on indicator so you can tell at a glance whether Xray is on. */
public final class XrayHud {
    private XrayHud() {
    }

    public static void render(GuiGraphics graphics) {
        if (!XrayState.isActive() || !XrayConfig.get().hud) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        Font font = mc.font;
        String mode = XrayState.isXrayMode() ? "XRAY" : "ESP";
        Component line = Component.literal(mode + " ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(String.join("+", XrayState.activeGroupIds()))
                        .withStyle(ChatFormatting.WHITE));
        int x = 4;
        int y = 4;
        int width = font.width(line);
        graphics.fill(x - 2, y - 2, x + width + 2, y + 10, 0x90000000);
        graphics.drawString(font, line, x, y, 0xFFFFFF, false);

        net.minecraft.world.level.block.Block focus = XrayEspRenderer.focusBlock();
        if (focus != null) {
            XrayEspRenderer.Stat stat = XrayEspRenderer.statOf(focus);
            String text = stat == null
                    ? "◇ " + XrayEspRenderer.nameOf(focus) + " —"
                    : "◇ " + XrayEspRenderer.nameOf(focus) + " " + Math.round(stat.distance()) + "m";
            Component focusLine = Component.literal(text).withStyle(ChatFormatting.AQUA);
            int w2 = font.width(focusLine);
            graphics.fill(x - 2, y + 10, x + w2 + 2, y + 22, 0x90000000);
            graphics.drawString(font, focusLine, x, y + 12, 0xFFFFFF, false);
        }
    }
}
