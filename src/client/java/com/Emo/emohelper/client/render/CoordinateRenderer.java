package com.Emo.emohelper.client.render;

import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class CoordinateRenderer {
    private static final float BOX_EXPAND = 0.01f;
    private static final float MESH_EXPAND = 0.006f;
    private static final float LABEL_SCALE = 0.06f;
    private static final float LABEL_Y_OFFSET = 2.2f;
    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png");

    // Use one dedicated immediate provider for all custom world overlays.
    // This avoids BufferBuilder 'Not building' when event consumers don't own a layer.

    /**
     * 渲染所有启用的坐标点
     */
    public static void render(MatrixStack matrixStack, VertexConsumerProvider vertexConsumers,
                              double cameraX, double cameraY, double cameraZ, float tickDelta) {
        if (!ConfigManager.getModConfig().isRenderingEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        VertexConsumerProvider.Immediate immediateConsumers = client.getBufferBuilders().getEntityVertexConsumers();

        CoordinateData coordinateData = ConfigManager.getCoordinateData();
        float renderDistance = ConfigManager.getModConfig().getRenderDistance();
        boolean labelPass = ConfigManager.getModConfig().shouldShowLabels();
        boolean drewAny = false;

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

            renderCoordinatePoint(matrixStack, immediateConsumers, point, cameraX, cameraY, cameraZ);
            drewAny = true;

            if (labelPass) {
                renderLabel(matrixStack, immediateConsumers, point, cameraX, cameraY, cameraZ);
            }
        }

        if (drewAny) {
            immediateConsumers.draw();
        }
    }

    /**
     * 渲染单个坐标点（使用内置 drawBox）
     */
    private static void renderCoordinatePoint(MatrixStack matrixStack, VertexConsumerProvider vertexConsumers,
                                              CoordinatePoint point, double cameraX, double cameraY, double cameraZ) {
        matrixStack.push();
        matrixStack.translate(point.getX() - cameraX, point.getY() - cameraY, point.getZ() - cameraZ);

        // Use vanilla layers here: custom layers in LAST can hit BufferBuilder 'Not building'.
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        int color = point.getColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = 0.45f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            drawOutline(lineConsumer, matrixStack, r, g, b, 0.9f);
            drawFaceMesh(lineConsumer, matrixStack, r, g, b, 0.55f);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        matrixStack.pop();
    }

    // Draw face diagonals as a crash-safe pseudo fill, so the box reads as complete.
    private static void drawFaceMesh(VertexConsumer vertexConsumer, MatrixStack matrixStack,
                                     float r, float g, float b, float a) {
        float minX = -BOX_EXPAND - MESH_EXPAND;
        float minY = -BOX_EXPAND - MESH_EXPAND;
        float minZ = -BOX_EXPAND - MESH_EXPAND;
        float maxX = 1.0f + BOX_EXPAND + MESH_EXPAND;
        float maxY = 1.0f + BOX_EXPAND + MESH_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND + MESH_EXPAND;

        MatrixStack.Entry entry = matrixStack.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        // north / south
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);

        // west / east
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, minZ, minX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, minZ, minX, minY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, maxY, minZ, maxX, minY, maxZ, r, g, b, a);

        // up / down
        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, maxZ, maxX, minY, minZ, r, g, b, a);
    }

    private static void addQuad(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f matrix,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float r, float g, float b, float a,
                                int overlay, int light,
                                float nx, float ny, float nz) {
        addVertex(vc, entry, matrix, x1, y1, z1, 0.0f, 0.0f, r, g, b, a, overlay, light, nx, ny, nz);
        addVertex(vc, entry, matrix, x2, y2, z2, 1.0f, 0.0f, r, g, b, a, overlay, light, nx, ny, nz);
        addVertex(vc, entry, matrix, x3, y3, z3, 1.0f, 1.0f, r, g, b, a, overlay, light, nx, ny, nz);
        addVertex(vc, entry, matrix, x1, y1, z1, 0.0f, 0.0f, r, g, b, a, overlay, light, nx, ny, nz);
        addVertex(vc, entry, matrix, x3, y3, z3, 1.0f, 1.0f, r, g, b, a, overlay, light, nx, ny, nz);
        addVertex(vc, entry, matrix, x4, y4, z4, 0.0f, 1.0f, r, g, b, a, overlay, light, nx, ny, nz);
    }

    private static void addVertex(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f matrix,
                                  float x, float y, float z, float u, float v,
                                  float r, float g, float b, float a,
                                  int overlay, int light, float nx, float ny, float nz) {
        vc.vertex(matrix, x, y, z)
            .color(r, g, b, a)
            .texture(u, v)
            .overlay(overlay)
            .light(light)
            .normal(entry, nx, ny, nz);
    }

    private static void drawOutline(VertexConsumer vertexConsumer, MatrixStack matrixStack,
                                    float r, float g, float b, float a) {
        float minX = -BOX_EXPAND;
        float minY = -BOX_EXPAND;
        float minZ = -BOX_EXPAND;
        float maxX = 1.0f + BOX_EXPAND;
        float maxY = 1.0f + BOX_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND;

        MatrixStack.Entry entry = matrixStack.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        addLine(vertexConsumer, entry, positionMatrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        addLine(vertexConsumer, entry, positionMatrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(vertexConsumer, entry, positionMatrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void addLine(VertexConsumer vertexConsumer, MatrixStack.Entry entry, Matrix4f positionMatrix,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        vertexConsumer.vertex(positionMatrix, x1, y1, z1)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);
        vertexConsumer.vertex(positionMatrix, x2, y2, z2)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);
    }

    /**
     * 渲染坐标标签（文本）
     */
    private static boolean renderLabel(MatrixStack matrixStack, VertexConsumerProvider labelConsumers,
                                   CoordinatePoint point, double cameraX, double cameraY, double cameraZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.gameRenderer == null) {
            return false;
        }

        TextRenderer textRenderer = client.textRenderer;
        var camera = client.gameRenderer.getCamera();
        String label = point.getLabel();
        if (label == null || label.isBlank()) {
            return false;
        }

        matrixStack.push();
        matrixStack.translate(point.getX() - cameraX + 0.5f, point.getY() - cameraY + LABEL_Y_OFFSET, point.getZ() - cameraZ + 0.5f);
        matrixStack.multiply(camera.getRotation());
        matrixStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        float x = -textRenderer.getWidth(label) / 2.0f;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        textRenderer.draw(label, x, 0.0f, 0xFFFFFFFF, false,
            matrixStack.peek().getPositionMatrix(), labelConsumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        matrixStack.pop();
        return true;
    }
}