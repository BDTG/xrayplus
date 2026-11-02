package com.bdtg.xrayplus;

import com.bdtg.xrayplus.client.ClientBootstrap;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * XrayPlus - client-side Xray tuned for the Soulrend modpack (Forge 1.20.1 + Embeddium/Oculus).
 *
 * Everything in this mod is client-only: no packets, no server hooks, no world data is modified.
 */
@Mod(XrayPlus.MOD_ID)
public final class XrayPlus {
    public static final String MOD_ID = "xrayplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XrayPlus() {
        // Only wire client classes on the physical client, so a dedicated server never loads them.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            ClientBootstrap.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
        ModLoadingContext.get();
    }
}
