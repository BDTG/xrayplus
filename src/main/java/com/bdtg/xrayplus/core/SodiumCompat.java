package com.bdtg.xrayplus.core;

import com.bdtg.xrayplus.XrayPlus;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Reads the block state out of Embeddium's chunk-build context without a compile-time dependency
 * on Embeddium (so the mod still loads in packs that do not ship it).
 */
public final class SodiumCompat {
    private static MethodHandle stateGetter;
    private static boolean unavailable;

    private SodiumCompat() {
    }

    public static BlockState stateOf(Object renderContext) {
        if (renderContext == null || unavailable) {
            return null;
        }
        try {
            MethodHandle handle = stateGetter;
            if (handle == null) {
                Class<?> contextClass = Class.forName(
                        "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext");
                Method method = contextClass.getMethod("state");
                method.setAccessible(true);
                handle = MethodHandles.lookup().unreflect(method);
                stateGetter = handle;
            }
            return (BlockState) handle.invoke(renderContext);
        } catch (Throwable t) {
            unavailable = true;
            XrayPlus.LOGGER.warn("[XrayPlus] Embeddium chunk hook disabled ({}); vanilla renderer only", t.toString());
            return null;
        }
    }
}
