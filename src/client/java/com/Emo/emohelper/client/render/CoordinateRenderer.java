package com.Emo.emohelper.client.render;

import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import java.util.OptionalDouble;

public class CoordinateRenderer {
    private static final float BOX_EXPAND = 0.01f;
    private static final float MESH_EXPAND = 0.006f;
    private static final float LABEL_SCALE = 0.025f;
    private static final float LABEL_Y_OFFSET = 1.5f;
    private static final int SOLID_GRID_STEPS = 4;
    private static final RenderLayer NO_DEPTH_LINES = RenderLayer.of(
        "emohelper_no_depth_lines",
        VertexFormats.LINES,
        VertexFormat.DrawMode.LINES,
        256,
        false,
        false,
        RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.LINES_PROGRAM)
            .lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty()))
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
            .writeMaskState(RenderPhase.COLOR_MASK)
            .build(false)
    );

    /**
     * 渲染所有启用的坐标点
     */
    public static void render(MatrixStack matrixStack, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        if (!ConfigManager.getModConfig().isRenderingEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        CoordinateData coordinateData = ConfigManager.getCoordinateData();
        float renderDistance = ConfigManager.getModConfig().getRenderDistance();
        boolean showLabels = ConfigManager.getModConfig().shouldShowLabels();
        var mode = ConfigManager.getModConfig().getRenderMode();

        // 设置全局渲染状态用于穿墙
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // 获取immediate buffer用于框架渲染
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();

        // 渲染所有点的框架
        for (CoordinatePoint point : coordinateData.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }

            double dx = point.getX() - cameraX;
            double dy = point.getY() - cameraY;
            double dz = point.getZ() - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            
            if (distanceSquared > renderDistance * renderDistance) {
                continue;
            }

            // 渲染点的框架
            renderCoordinatePoint(matrixStack, immediate, point, cameraX, cameraY, cameraZ, mode);
        }

        // 确保深度测试在绘制前仍然禁用
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        
        // 绘制所有框架
        immediate.draw();
        
        // 绘制后再次确保深度测试禁用（为标签渲染准备）
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // 渲染所有标签（如果开关打开）
        if (showLabels) {
            for (CoordinatePoint point : coordinateData.getPoints()) {
                if (!point.isEnabled()) {
                    continue;
                }

                double dx = point.getX() - cameraX;
                double dy = point.getY() - cameraY;
                double dz = point.getZ() - cameraZ;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                
                if (distanceSquared > renderDistance * renderDistance) {
                    continue;
                }

                // 渲染标签
                renderLabel(matrixStack, point, cameraX, cameraY, cameraZ);
            }
        }

        // 恢复渲染状态
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * 渲染单个坐标点
     */
    private static void renderCoordinatePoint(MatrixStack matrixStack, VertexConsumerProvider.Immediate immediate,
                                              CoordinatePoint point, double cameraX, double cameraY, double cameraZ,
                                              com.Emo.emohelper.config.ModConfig.RenderMode mode) {
        matrixStack.push();
        matrixStack.translate(point.getX() - cameraX, point.getY() - cameraY, point.getZ() - cameraZ);

        int color = point.getColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // 使用无深度测试的线条渲染层，保证穿墙可见
        VertexConsumer buffer = immediate.getBuffer(NO_DEPTH_LINES);

        drawOutline(buffer, matrixStack, r, g, b, 0.95f);
        if (mode == com.Emo.emohelper.config.ModConfig.RenderMode.SAFE_MESH
            || mode == com.Emo.emohelper.config.ModConfig.RenderMode.SAFE_FULL_BLOCK) {
            drawFaceMesh(buffer, matrixStack, r, g, b, 0.6f);
        }
        if (mode == com.Emo.emohelper.config.ModConfig.RenderMode.SAFE_FULL_BLOCK) {
            drawSolidGridVolume(buffer, matrixStack, r, g, b, 0.35f);
        }

        matrixStack.pop();
    }

    private static void drawSolidGridVolume(VertexConsumer buffer, MatrixStack matrixStack,
                                            float r, float g, float b, float a) {
        float minX = -BOX_EXPAND;
        float minY = -BOX_EXPAND;
        float minZ = -BOX_EXPAND;
        float maxX = 1.0f + BOX_EXPAND;
        float maxY = 1.0f + BOX_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND;

        Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();

        for (int yi = 0; yi <= SOLID_GRID_STEPS; yi++) {
            float y = lerp(minY, maxY, yi, SOLID_GRID_STEPS);
            for (int zi = 0; zi <= SOLID_GRID_STEPS; zi++) {
                float z = lerp(minZ, maxZ, zi, SOLID_GRID_STEPS);
                addLine(buffer, positionMatrix, minX, y, z, maxX, y, z, r, g, b, a);
            }
        }

        for (int xi = 0; xi <= SOLID_GRID_STEPS; xi++) {
            float x = lerp(minX, maxX, xi, SOLID_GRID_STEPS);
            for (int zi = 0; zi <= SOLID_GRID_STEPS; zi++) {
                float z = lerp(minZ, maxZ, zi, SOLID_GRID_STEPS);
                addLine(buffer, positionMatrix, x, minY, z, x, maxY, z, r, g, b, a);
            }
        }

        for (int xi = 0; xi <= SOLID_GRID_STEPS; xi++) {
            float x = lerp(minX, maxX, xi, SOLID_GRID_STEPS);
            for (int yi = 0; yi <= SOLID_GRID_STEPS; yi++) {
                float y = lerp(minY, maxY, yi, SOLID_GRID_STEPS);
                addLine(buffer, positionMatrix, x, y, minZ, x, y, maxZ, r, g, b, a);
            }
        }
    }

    private static float lerp(float min, float max, int i, int steps) {
        return min + (max - min) * ((float) i / (float) steps);
    }

    private static void drawFaceMesh(VertexConsumer buffer, MatrixStack matrixStack,
                                     float r, float g, float b, float a) {
        float minX = -BOX_EXPAND - MESH_EXPAND;
        float minY = -BOX_EXPAND - MESH_EXPAND;
        float minZ = -BOX_EXPAND - MESH_EXPAND;
        float maxX = 1.0f + BOX_EXPAND + MESH_EXPAND;
        float maxY = 1.0f + BOX_EXPAND + MESH_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND + MESH_EXPAND;

        Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();

        // north / south
        addLine(buffer, positionMatrix, minX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);

        // west / east
        addLine(buffer, positionMatrix, minX, minY, minZ, minX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, maxY, minZ, minX, minY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, maxY, minZ, maxX, minY, maxZ, r, g, b, a);

        // up / down
        addLine(buffer, positionMatrix, minX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, minY, maxZ, maxX, minY, minZ, r, g, b, a);
    }

    private static void drawOutline(VertexConsumer buffer, MatrixStack matrixStack,
                                    float r, float g, float b, float a) {
        float minX = -BOX_EXPAND;
        float minY = -BOX_EXPAND;
        float minZ = -BOX_EXPAND;
        float maxX = 1.0f + BOX_EXPAND;
        float maxY = 1.0f + BOX_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND;

        Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();

        addLine(buffer, positionMatrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        addLine(buffer, positionMatrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        addLine(buffer, positionMatrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(buffer, positionMatrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(buffer, positionMatrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void addLine(VertexConsumer buffer, Matrix4f positionMatrix,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        buffer.vertex(positionMatrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(positionMatrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }

    /**
     * 渲染坐标标签（文本）
     */
    private static void renderLabel(MatrixStack matrixStack, CoordinatePoint point, 
                                   double cameraX, double cameraY, double cameraZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.gameRenderer == null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        var camera = client.gameRenderer.getCamera();
        String label = point.getLabel();
        if (label == null || label.isBlank()) {
            label = String.format("(%d,%d,%d)", point.getX(), point.getY(), point.getZ());
        }

        matrixStack.push();
        matrixStack.translate(point.getX() - cameraX + 0.5, point.getY() - cameraY + LABEL_Y_OFFSET, point.getZ() - cameraZ + 0.5);
        matrixStack.multiply(camera.getRotation());
        // Y轴负向以正确显示文字（否则上下颠倒）
        matrixStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        // 由于文字渲染坐标系统，X需要居中对齐
        float x = -textRenderer.getWidth(label) / 2.0f;
        
        // 使用即时模式渲染文本
        textRenderer.draw(label, x, 0.0f, 0xFFFFFFFF, false,
            matrixStack.peek().getPositionMatrix(), client.getBufferBuilders().getEntityVertexConsumers(),
            TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        
        // 立即绘制文本缓冲
        client.getBufferBuilders().getEntityVertexConsumers().draw();

        matrixStack.pop();
    }
}