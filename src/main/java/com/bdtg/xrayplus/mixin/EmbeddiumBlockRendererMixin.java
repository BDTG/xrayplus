package com.bdtg.xrayplus.mixin;

import com.bdtg.xrayplus.core.SodiumCompat;
import com.bdtg.xrayplus.core.XrayState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium (Sodium) compat: its chunk mesher builds geometry itself instead of going through
 * ModelBlockRenderer#tesselateBlock, so the block has to be skipped here as well.
 *
 * <p>The target is a mod class with stable names; {@code @Pseudo} keeps the mod loading in packs
 * that do not ship Embeddium at all.</p>
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class EmbeddiumBlockRendererMixin {
    @Inject(
            method = "renderModel(Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext;"
                    + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xrayplus$skipHiddenBlock(@Coerce Object renderContext, @Coerce Object buffers, CallbackInfo ci) {
        if (!XrayState.isActive()) {
            return;
        }
        BlockState state = SodiumCompat.stateOf(renderContext);
        if (state != null && XrayState.shouldHide(state)) {
            ci.cancel();
        }
    }
}
