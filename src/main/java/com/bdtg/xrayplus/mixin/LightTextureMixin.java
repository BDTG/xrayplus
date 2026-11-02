package com.bdtg.xrayplus.mixin;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright: while Xray is on, the 16x16 lightmap is overwritten with pure white and uploaded.
 *
 * <p>This is renderer agnostic - vanilla, Embeddium and Oculus all sample the same vanilla
 * lightmap texture, so caves and night are lit without touching a single block state.</p>
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow
    private NativeImage lightTextureImage;

    @Shadow
    private DynamicTexture lightTexture;

    @Shadow
    private boolean needsUpdate;

    @Inject(method = "updateLightTexture(F)V", at = @At("HEAD"), cancellable = true)
    private void xrayplus$forceFullbright(float partialTicks, CallbackInfo ci) {
        if (!XrayState.isActive() || !XrayConfig.get().fullbright) {
            return;
        }
        NativeImage image = this.lightTextureImage;
        DynamicTexture texture = this.lightTexture;
        if (image == null || texture == null) {
            return;
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixelRGBA(x, y, 0xFFFFFFFF);
            }
        }
        texture.upload();
        RenderSystem.setShaderTexture(2, texture.getId());
        this.needsUpdate = false;
        ci.cancel();
    }
}
