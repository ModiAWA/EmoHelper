package com.Emo.emohelper.client.render;

import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class CoordinateRenderer {
    private static final float BOX_EXPAND = 0.01f;
    private static final float MESH_EXPAND = 0.006f;
    private static final float LABEL_SCALE = 0.025f;
    private static final float LABEL_Y_OFFSET = 1.5f;
    // Full-block mode renders solid faces only (no internal grid).

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

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

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

            renderCoordinatePoint(matrixStack, point, cameraX, cameraY, cameraZ, mode);
        }

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

                renderLabel(matrixStack, point, cameraX, cameraY, cameraZ);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void renderCoordinatePoint(MatrixStack matrixStack, CoordinatePoint point,
                                              double cameraX, double cameraY, double cameraZ,
                                              com.Emo.emohelper.config.ModConfig.RenderMode mode) {
        matrixStack.push();
        matrixStack.translate(point.getX() - cameraX, point.getY() - cameraY, point.getZ() - cameraZ);

        int color = point.getColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Tessellator tessellator = Tessellator.getInstance();

        if (mode == com.Emo.emohelper.config.ModConfig.RenderMode.SAFE_FULL_BLOCK) {
            BufferBuilder quadBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            drawSolidFaces(quadBuffer, matrixStack);
            RenderSystem.setShader(ShaderProgramKeys.POSITION);
            RenderSystem.setShaderColor(r, g, b, 0.20f);
            BufferRenderer.drawWithGlobalProgram(quadBuffer.end());
        }

        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        drawOutline(lineBuffer, matrixStack);
        if (mode == com.Emo.emohelper.config.ModConfig.RenderMode.SAFE_MESH) {
            drawFaceMesh(lineBuffer, matrixStack);
        }

        RenderSystem.setShader(ShaderProgramKeys.POSITION);
        RenderSystem.setShaderColor(r, g, b, 0.95f);
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        matrixStack.pop();
    }

    private static void drawSolidFaces(BufferBuilder buffer, MatrixStack matrixStack) {
        float minX = -BOX_EXPAND;
        float minY = -BOX_EXPAND;
        float minZ = -BOX_EXPAND;
        float maxX = 1.0f + BOX_EXPAND;
        float maxY = 1.0f + BOX_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND;

        Matrix4f m = matrixStack.peek().getPositionMatrix();

        // north (-Z)
        addQuad(buffer, m, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        // south (+Z)
        addQuad(buffer, m, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        // west (-X)
        addQuad(buffer, m, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        // east (+X)
        addQuad(buffer, m, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        // up (+Y)
        addQuad(buffer, m, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        // down (-Y)
        addQuad(buffer, m, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
    }

    private static void addQuad(BufferBuilder buffer, Matrix4f m,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4) {
        buffer.vertex(m, x1, y1, z1);
        buffer.vertex(m, x2, y2, z2);
        buffer.vertex(m, x3, y3, z3);
        buffer.vertex(m, x4, y4, z4);
    }

    private static void drawFaceMesh(BufferBuilder buffer, MatrixStack matrixStack) {
        float minX = -BOX_EXPAND - MESH_EXPAND;
        float minY = -BOX_EXPAND - MESH_EXPAND;
        float minZ = -BOX_EXPAND - MESH_EXPAND;
        float maxX = 1.0f + BOX_EXPAND + MESH_EXPAND;
        float maxY = 1.0f + BOX_EXPAND + MESH_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND + MESH_EXPAND;

        Matrix4f m = matrixStack.peek().getPositionMatrix();

        addLine(buffer, m, minX, minY, minZ, maxX, maxY, minZ);
        addLine(buffer, m, minX, maxY, minZ, maxX, minY, minZ);
        addLine(buffer, m, minX, minY, maxZ, maxX, maxY, maxZ);
        addLine(buffer, m, minX, maxY, maxZ, maxX, minY, maxZ);

        addLine(buffer, m, minX, minY, minZ, minX, maxY, maxZ);
        addLine(buffer, m, minX, maxY, minZ, minX, minY, maxZ);
        addLine(buffer, m, maxX, minY, minZ, maxX, maxY, maxZ);
        addLine(buffer, m, maxX, maxY, minZ, maxX, minY, maxZ);

        addLine(buffer, m, minX, maxY, minZ, maxX, maxY, maxZ);
        addLine(buffer, m, minX, maxY, maxZ, maxX, maxY, minZ);
        addLine(buffer, m, minX, minY, minZ, maxX, minY, maxZ);
        addLine(buffer, m, minX, minY, maxZ, maxX, minY, minZ);
    }

    private static void drawOutline(BufferBuilder buffer, MatrixStack matrixStack) {
        float minX = -BOX_EXPAND;
        float minY = -BOX_EXPAND;
        float minZ = -BOX_EXPAND;
        float maxX = 1.0f + BOX_EXPAND;
        float maxY = 1.0f + BOX_EXPAND;
        float maxZ = 1.0f + BOX_EXPAND;

        Matrix4f m = matrixStack.peek().getPositionMatrix();

        addLine(buffer, m, minX, minY, minZ, maxX, minY, minZ);
        addLine(buffer, m, maxX, minY, minZ, maxX, minY, maxZ);
        addLine(buffer, m, maxX, minY, maxZ, minX, minY, maxZ);
        addLine(buffer, m, minX, minY, maxZ, minX, minY, minZ);

        addLine(buffer, m, minX, maxY, minZ, maxX, maxY, minZ);
        addLine(buffer, m, maxX, maxY, minZ, maxX, maxY, maxZ);
        addLine(buffer, m, maxX, maxY, maxZ, minX, maxY, maxZ);
        addLine(buffer, m, minX, maxY, maxZ, minX, maxY, minZ);

        addLine(buffer, m, minX, minY, minZ, minX, maxY, minZ);
        addLine(buffer, m, maxX, minY, minZ, maxX, maxY, minZ);
        addLine(buffer, m, maxX, minY, maxZ, maxX, maxY, maxZ);
        addLine(buffer, m, minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void addLine(BufferBuilder buffer, Matrix4f m,
                                float x1, float y1, float z1, float x2, float y2, float z2) {
        buffer.vertex(m, x1, y1, z1);
        buffer.vertex(m, x2, y2, z2);
    }

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
        matrixStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        float x = -textRenderer.getWidth(label) / 2.0f;
        textRenderer.draw(label, x, 0.0f, 0xFFFFFFFF, false,
            matrixStack.peek().getPositionMatrix(), client.getBufferBuilders().getEntityVertexConsumers(),
            TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        client.getBufferBuilders().getEntityVertexConsumers().draw();

        matrixStack.pop();
    }
}