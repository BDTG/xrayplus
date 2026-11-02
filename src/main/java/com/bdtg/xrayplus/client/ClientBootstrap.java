package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.XrayPlus;
import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Wires the mod into Forge's client event buses. */
public final class ClientBootstrap {
    public static final String CATEGORY = "key.categories.xrayplus";
    public static KeyMapping toggleKey;
    public static KeyMapping configKey;

    private ClientBootstrap() {
    }

    public static void init(IEventBus modBus) {
        XrayConfig.load();
        XrayState.rebuild();
        XrayState.setActiveSilently(XrayConfig.get().enabled);

        modBus.addListener(ClientBootstrap::clientSetup);
        modBus.addListener(ClientBootstrap::registerKeys);

        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new XrayConfigScreen(parent)));

        XrayPlus.LOGGER.info("[XrayPlus] Client bootstrap complete ({} blocks visible)",
                XrayState.visibleCount());
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        XrayPlus.LOGGER.info("[XrayPlus] Client setup");
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        toggleKey = new KeyMapping("key.xrayplus.toggle", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);
        configKey = new KeyMapping("key.xrayplus.config", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);
        event.register(toggleKey);
        event.register(configKey);
    }

    /** Forge-bus (game) events. */
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                return;
            }
            if (toggleKey != null) {
                while (toggleKey.consumeClick()) {
                    XrayState.toggle();
                }
            }
            if (configKey != null) {
                while (configKey.consumeClick()) {
                    ClientHooks.openConfigScreen(null);
                }
            }
        }

        @SubscribeEvent
        public static void onLevelLoad(LevelEvent.Load event) {
            if (event.getLevel().isClientSide()) {
                ClientHooks.onWorldLoaded();
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterClientCommandsEvent event) {
            XrayCommands.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
                XrayHud.render(event.getGuiGraphics());
            }
        }
    }
}
