package com.bdtg.xrayplus.mixin;

import com.bdtg.xrayplus.core.XrayState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides non-whitelisted blocks in the vanilla block mesher.
 *
 * <p>The two targets are Forge-added overloads that take ModelData + RenderType. Forge keeps their
 * Mojang names at runtime (they are not in the SRG mapping table), so they are targeted verbatim
 * with {@code remap = false}. Vanilla's own tesselateBlock delegates here, and Embeddium's
 * ForgeBlockRenderer calls them directly, so both render paths are covered by this class.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    private static final String DESC = "(Lnet/minecraft/world/level/BlockAndTintGetter;"
            + "Lnet/minecraft/client/resources/model/BakedModel;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "ZLnet/minecraft/util/RandomSource;J"
            + "Lnet/minecraftforge/client/model/data/ModelData;"
            + "Lnet/minecraft/client/renderer/RenderType;)V";

    @Inject(method = "tesselateWithAO" + DESC, at = @At("HEAD"), cancellable = true, remap = false)
    private void xrayplus$hideWithAo(BlockAndTintGetter level, BakedModel model, BlockState state,
                                     BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                     boolean checkSides, RandomSource random, long seed,
                                     ModelData modelData, RenderType renderType, CallbackInfo ci) {
        if (XrayState.shouldHide(state)) {
            ci.cancel();
        }
    }

    @Inject(method = "tesselateWithoutAO" + DESC, at = @At("HEAD"), cancellable = true, remap = false)
    private void xrayplus$hideWithoutAo(BlockAndTintGetter level, BakedModel model, BlockState state,
                                        BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                        boolean checkSides, RandomSource random, long seed,
                                        ModelData modelData, RenderType renderType, CallbackInfo ci) {
        if (XrayState.shouldHide(state)) {
            ci.cancel();
        }
    }
}
