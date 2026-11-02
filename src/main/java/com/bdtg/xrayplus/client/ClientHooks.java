package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client-only helpers that touch Minecraft classes (never loaded on a dedicated server). */
public final class ClientHooks {
    private ClientHooks() {
    }

    /**
     * Rebuilds the terrain and the lightmap so a toggle is visible immediately.
     * Embeddium hooks LevelRenderer#allChanged, so this also invalidates its chunk meshes.
     */
    public static void refreshWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        if (mc.gameRenderer != null && mc.gameRenderer.lightTexture() != null) {
            // Forces LightTexture to recompute; drops the forced fullbright when Xray is off.
            mc.gameRenderer.lightTexture().tick();
        }
    }

    /** Forces the lightmap to recompute; drops the forced fullbright when the mod is off. */
    public static void refreshLightmap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.lightTexture() != null) {
            mc.gameRenderer.lightTexture().tick();
        }
    }

    /**
     * Called when the mode or whitelist changes. Only XRAY mode alters chunk meshes; ESP keeps the
     * normal terrain and just needs the lightmap refreshed.
     */
    public static void onModeChanged() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (XrayState.isXrayMode() && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        refreshLightmap();
    }

    public static void openConfigScreen(Screen parent) {
        Minecraft.getInstance().setScreen(new XrayConfigScreen(parent));
    }

    /** Called after a world load: tags are only bound once the client knows the datapack set. */
    public static void onWorldLoaded() {
        XrayState.rebuild();
        if (XrayState.isActive()) {
            refreshWorld();
        } else if (XrayConfig.get().enabled) {
            XrayState.setActive(true);
        }
    }
}
