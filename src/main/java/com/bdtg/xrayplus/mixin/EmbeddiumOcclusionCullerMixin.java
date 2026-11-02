package com.bdtg.xrayplus.mixin;

import com.bdtg.xrayplus.core.XrayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Embeddium culls whole 16x16x16 sections that are fully sealed by opaque blocks, which would hide
 * the very ores Xray is meant to reveal. Passing {@code useOcclusionCulling = false} makes its
 * graph walk visit every section inside the render distance, so hidden terrain stays gone while the
 * whitelisted blocks are all drawn.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
public abstract class EmbeddiumOcclusionCullerMixin {
    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true, index = 4, remap = false, require = 0)
    private boolean xrayplus$disableOcclusionCulling(boolean useOcclusionCulling) {
        return XrayState.isActive() ? false : useOcclusionCulling;
    }
}
