package com.bdtg.xrayplus.client;

import com.bdtg.xrayplus.config.XrayConfig;
import com.bdtg.xrayplus.core.XrayState;
import com.bdtg.xrayplus.XrayPlus;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ESP mode: terrain renders completely normally (so you can navigate), and every block in the
 * whitelist gets a coloured box drawn through walls.
 *
 * <p>Boxes are collected every 10 ticks by walking loaded chunk sections whose palette can contain
 * a target ({@link LevelChunkSection#maybeHas}), then drawn once per frame with a manual
 * {@code DEBUG_LINES} batch so depth test can be switched off without a custom RenderType.</p>
 */
public final class XrayEspRenderer {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final float LINE_WIDTH = 2.0F;

    private static volatile List<Box> targets = List.of();
    private static volatile List<Box> silhouettes = List.of();
    private static long lastScan = -10_000L;

    private XrayEspRenderer() {
    }

    private record Box(BlockPos pos, int color) {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !XrayState.isActive()) {
            targets = List.of();
            silhouettes = List.of();
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastScan < SCAN_INTERVAL_TICKS) {
            return;
        }
        lastScan = now;
        try {
            scan(mc);
        } catch (Exception e) {
            XrayPlus.LOGGER.error("[XrayPlus] esp scan failed", e);
            targets = List.of();
            silhouettes = List.of();
        }
    }

    private static void scan(Minecraft mc) {
        XrayConfig cfg = XrayConfig.get();
        ClientLevel level = mc.level;
        BlockPos center = mc.player.blockPosition();

        boolean wantTargets = !XrayState.isXrayMode();
        boolean wantOutline = XrayState.isXrayMode() && cfg.outlineTerrain;
        if (!wantTargets && !wantOutline) {
            targets = List.of();
            silhouettes = List.of();
            return;
        }

        int targetRadius = clamp(cfg.espRadius, 8, 96);
        int outlineRadius = clamp(cfg.outlineRadius, 2, 24);
        int radius = wantTargets ? targetRadius : outlineRadius;
        int maxTargets = clamp(cfg.espMaxBlocks, 16, 4096);
        int maxOutline = 900;

        List<Box> found = new ArrayList<>();
        List<Box> outline = new ArrayList<>();
        double radiusSq = (double) radius * radius;

        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int minY = chunk.getMinBuildHeight();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    int sectionIndex = minSection + si;
                    if (sectionIndex < minSection || sectionIndex >= maxSection) {
                        continue;
                    }
                    int baseY = minY + (si << 4);
                    if (Math.abs(baseY + 8 - center.getY()) > radius + 8) {
                        continue;
                    }
                    boolean hasTarget = wantTargets && section.maybeHas(XrayState::shouldShow);
                    boolean hasSolid = wantOutline && section.maybeHas(state -> !state.isAir());
                    if (!hasTarget && !hasSolid) {
                        continue;
                    }
                    int worldChunkX = cx << 4;
                    int worldChunkZ = cz << 4;
                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        if (Math.abs(worldY - center.getY()) > radius) {
                            continue;
                        }
                        for (int x = 0; x < 16; x++) {
                            int worldX = worldChunkX + x;
                            int dx = worldX - center.getX();
                            for (int z = 0; z < 16; z++) {
                                int worldZ = worldChunkZ + z;
                                int dz = worldZ - center.getZ();
                                if ((double) dx * dx + (double) dz * dz > radiusSq) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(x, y, z);
                                if (state.isAir()) {
                                    continue;
                                }
                                if (hasTarget && XrayState.shouldShow(state)) {
                                    found.add(new Box(new BlockPos(worldX, worldY, worldZ),
                                            XrayState.colorOf(state.getBlock())));
                                }
                                if (hasSolid && state.isSolidRender(level, new BlockPos(worldX, worldY, worldZ))) {
                                    outline.add(new Box(new BlockPos(worldX, worldY, worldZ), 0xFF8C9AA8));
                                }
                            }
                        }
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(b -> b.pos().distSqr(center)));
        if (found.size() > maxTargets) {
            found = new ArrayList<>(found.subList(0, maxTargets));
        }
        if (outline.size() > maxOutline) {
            outline.sort(Comparator.comparingDouble(b -> b.pos().distSqr(center)));
            outline = new ArrayList<>(outline.subList(0, maxOutline));
        }

        targets = List.copyOf(found);
        silhouettes = List.copyOf(outline);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // AFTER_TRANSLUCENT_BLOCKS is still inside LevelRenderer's render pass, so the shader's
        // model-view matrix is the camera one. Drawing at AFTER_LEVEL leaves an identity model-view
        // and the boxes end up glued to the camera (they appear to follow the player).
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !XrayState.isActive()) {
            return;
        }
        List<Box> outline = silhouettes;
        List<Box> boxes = targets;
        if (outline.isEmpty() && boxes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        try {
            if (!outline.isEmpty()) {
                drawBoxes(poseStack, outline, true, 0.55F);
            }
            if (!boxes.isEmpty()) {
                drawBoxes(poseStack, boxes, !XrayConfig.get().espThroughWalls, 1.0F);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawBoxes(PoseStack poseStack, List<Box> boxes, boolean depthTest, float alphaScale) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        try {
            buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            for (Box box : boxes) {
                int argb = box.color();
                float a = ((argb >>> 24) & 0xFF) / 255.0F * alphaScale;
                float r = ((argb >> 16) & 0xFF) / 255.0F;
                float g = ((argb >> 8) & 0xFF) / 255.0F;
                float b = (argb & 0xFF) / 255.0F;
                LevelRenderer.renderLineBox(poseStack, buffer, boxOf(box.pos()), r, g, b, a);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1.0F);
        }
    }

    private static AABB boxOf(BlockPos pos) {
        return new AABB(pos).inflate(0.0015);
    }
}
