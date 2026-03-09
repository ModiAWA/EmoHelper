package com.Emo.emohelper.client.render;

import com.Emo.emohelper.client.OrderedRouteManager;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        boolean fallbackShowLabels = ConfigManager.getModConfig().shouldShowLabels();
        float fallbackRenderDistance = ConfigManager.getModConfig().getRenderDistance();
        var mode = ConfigManager.getModConfig().getRenderMode();
        Map<String, List<CoordinatePoint>> orderedEnabledByGroup = new HashMap<>();

        for (CoordinatePoint point : coordinateData.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }
            CoordinateData.GroupRenderSettings settings = coordinateData.getGroupRenderSettings(
                point.getGroupName(), fallbackShowLabels, fallbackRenderDistance);
            if (settings.groupType() == CoordinateData.GroupType.ORDERED) {
                orderedEnabledByGroup.computeIfAbsent(point.getGroupName(), key -> new ArrayList<>()).add(point);
            }
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        for (CoordinatePoint point : coordinateData.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }

            CoordinateData.GroupRenderSettings settings = coordinateData.getGroupRenderSettings(
                point.getGroupName(), fallbackShowLabels, fallbackRenderDistance);
            if (!shouldRenderByOrderedRoute(point, settings, orderedEnabledByGroup)) {
                continue;
            }

            double dx = point.getX() - cameraX;
            double dy = point.getY() - cameraY;
            double dz = point.getZ() - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > settings.renderDistance() * settings.renderDistance()) {
                continue;
            }

            renderCoordinatePoint(matrixStack, point, cameraX, cameraY, cameraZ, mode);
        }

        for (CoordinatePoint point : coordinateData.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }

            CoordinateData.GroupRenderSettings settings = coordinateData.getGroupRenderSettings(
                point.getGroupName(), fallbackShowLabels, fallbackRenderDistance);
            if (!settings.showLabels()) {
                continue;
            }
            if (!shouldRenderByOrderedRoute(point, settings, orderedEnabledByGroup)) {
                continue;
            }

            double dx = point.getX() - cameraX;
            double dy = point.getY() - cameraY;
            double dz = point.getZ() - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > settings.renderDistance() * settings.renderDistance()) {
                continue;
            }

            renderLabel(matrixStack, point, cameraX, cameraY, cameraZ);
        }

        renderOrderedGroupPathLines(matrixStack, orderedEnabledByGroup, coordinateData, fallbackShowLabels, fallbackRenderDistance, cameraX, cameraY, cameraZ);

        for (Map.Entry<String, List<CoordinatePoint>> entry : orderedEnabledByGroup.entrySet()) {
            String group = entry.getKey();
            List<CoordinatePoint> orderedPoints = entry.getValue();
            if (orderedPoints.isEmpty()) {
                continue;
            }

            CoordinateData.GroupRenderSettings settings = coordinateData.getGroupRenderSettings(group, fallbackShowLabels, fallbackRenderDistance);
            if (!settings.crosshairGuideLine()) {
                continue;
            }

            int targetIndex = OrderedRouteManager.getCurrentTargetIndex(group, orderedPoints.size(), settings);
            if (targetIndex < 0 || targetIndex >= orderedPoints.size()) {
                continue;
            }

            CoordinatePoint target = orderedPoints.get(targetIndex);
            renderGuideLineToTarget(matrixStack, target, cameraX, cameraY, cameraZ);
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

        if (mode == com.Emo.emohelper.config.ModConfig.RenderMode.FULL_BLOCK) {
            BufferBuilder quadBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            drawSolidFaces(quadBuffer, matrixStack);
            RenderSystem.setShader(ShaderProgramKeys.POSITION);
            RenderSystem.setShaderColor(r, g, b, 0.20f);
            BufferRenderer.drawWithGlobalProgram(quadBuffer.end());
        }

        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        drawOutline(lineBuffer, matrixStack);
        if (mode == com.Emo.emohelper.config.ModConfig.RenderMode.MESH) {
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

    private static boolean shouldRenderByOrderedRoute(
        CoordinatePoint point,
        CoordinateData.GroupRenderSettings settings,
        Map<String, List<CoordinatePoint>> orderedEnabledByGroup
    ) {
        if (settings.groupType() != CoordinateData.GroupType.ORDERED) {
            return true;
        }
        List<CoordinatePoint> orderedPoints = orderedEnabledByGroup.get(point.getGroupName());
        if (orderedPoints == null || orderedPoints.isEmpty()) {
            return false;
        }
        int pointIndex = orderedPoints.indexOf(point);
        return OrderedRouteManager.shouldRenderOrderedPoint(point.getGroupName(), pointIndex, orderedPoints.size(), settings);
    }

    private static void renderOrderedGroupPathLines(
        MatrixStack matrixStack,
        Map<String, List<CoordinatePoint>> orderedEnabledByGroup,
        CoordinateData coordinateData,
        boolean fallbackShowLabels,
        float fallbackRenderDistance,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        Matrix4f m = matrixStack.peek().getPositionMatrix();

        for (Map.Entry<String, List<CoordinatePoint>> entry : orderedEnabledByGroup.entrySet()) {
            String group = entry.getKey();
            List<CoordinatePoint> orderedPoints = entry.getValue();
            if (orderedPoints.size() < 2) {
                continue;
            }

            CoordinateData.GroupRenderSettings settings = coordinateData.getGroupRenderSettings(group, fallbackShowLabels, fallbackRenderDistance);
            if (settings.groupType() != CoordinateData.GroupType.ORDERED || !settings.routeLineEnabled()) {
                continue;
            }

            double maxDistance = settings.renderDistance();
            double maxDistanceSq = maxDistance * maxDistance;
            if (settings.orderedDisplayMode() == CoordinateData.OrderedDisplayMode.ALL) {
                for (int i = 0; i < orderedPoints.size() - 1; i++) {
                    CoordinatePoint from = orderedPoints.get(i);
                    CoordinatePoint to = orderedPoints.get(i + 1);
                    renderSegmentIfVisible(m, from, to, cameraX, cameraY, cameraZ, maxDistanceSq, settings.routeLineGradient(), settings.routeLineAlpha(), settings.routeLineBrightness());
                }

                // For two-point loops, the consecutive segment already draws the only needed connection.
                if (settings.loopRoute() && orderedPoints.size() > 2) {
                    CoordinatePoint last = orderedPoints.get(orderedPoints.size() - 1);
                    CoordinatePoint first = orderedPoints.get(0);
                    renderSegmentIfVisible(m, last, first, cameraX, cameraY, cameraZ, maxDistanceSq, settings.routeLineGradient(), settings.routeLineAlpha(), settings.routeLineBrightness());
                }
            } else {
                List<CoordinatePoint> progressiveVisible = new ArrayList<>();
                for (int i = 0; i < orderedPoints.size(); i++) {
                    if (OrderedRouteManager.shouldRenderOrderedPoint(group, i, orderedPoints.size(), settings)) {
                        progressiveVisible.add(orderedPoints.get(i));
                        if (progressiveVisible.size() >= 2) {
                            break;
                        }
                    }
                }
                if (progressiveVisible.size() >= 2) {
                    renderSegmentIfVisible(
                        m,
                        progressiveVisible.get(0),
                        progressiveVisible.get(1),
                        cameraX,
                        cameraY,
                        cameraZ,
                        maxDistanceSq,
                        settings.routeLineGradient(),
                        settings.routeLineAlpha(),
                        settings.routeLineBrightness());
                }
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static boolean isPointNearCamera(
        CoordinatePoint point,
        double cameraX,
        double cameraY,
        double cameraZ,
        double maxDistanceSq
    ) {
        double dx = point.getX() - cameraX;
        double dy = point.getY() - cameraY;
        double dz = point.getZ() - cameraZ;
        return dx * dx + dy * dy + dz * dz <= maxDistanceSq;
    }

    private static void renderSegmentIfVisible(
        Matrix4f m,
        CoordinatePoint from,
        CoordinatePoint to,
        double cameraX,
        double cameraY,
        double cameraZ,
        double maxDistanceSq,
        boolean gradient,
        float alpha,
        float brightness
    ) {
        if (!isPointNearCamera(from, cameraX, cameraY, cameraZ, maxDistanceSq)
            && !isPointNearCamera(to, cameraX, cameraY, cameraZ, maxDistanceSq)) {
            return;
        }

        float fromX = (float) (from.getX() - cameraX + 0.5);
        float fromY = (float) (from.getY() - cameraY + 0.5);
        float fromZ = (float) (from.getZ() - cameraZ + 0.5);
        float toX = (float) (to.getX() - cameraX + 0.5);
        float toY = (float) (to.getY() - cameraY + 0.5);
        float toZ = (float) (to.getZ() - cameraZ + 0.5);

        float[] fromColor = colorFromPoint(from, brightness);
        if (!gradient) {
            drawColoredLine(m, fromX, fromY, fromZ, toX, toY, toZ, fromColor[0], fromColor[1], fromColor[2], alpha);
            return;
        }

        float[] toColor = colorFromPoint(to, brightness);
        float midX = (fromX + toX) * 0.5f;
        float midY = (fromY + toY) * 0.5f;
        float midZ = (fromZ + toZ) * 0.5f;
        float midR = (fromColor[0] + toColor[0]) * 0.5f;
        float midG = (fromColor[1] + toColor[1]) * 0.5f;
        float midB = (fromColor[2] + toColor[2]) * 0.5f;

        drawColoredLine(m, fromX, fromY, fromZ, midX, midY, midZ, fromColor[0], fromColor[1], fromColor[2], alpha);
        drawColoredLine(m, midX, midY, midZ, toX, toY, toZ, midR, midG, midB, alpha);
    }

    private static float[] colorFromPoint(CoordinatePoint point, float brightness) {
        int color = point.getColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[] {
            Math.min(1.0f, r * brightness),
            Math.min(1.0f, g * brightness),
            Math.min(1.0f, b * brightness)
        };
    }

    private static void drawColoredLine(
        Matrix4f m,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        float r,
        float g,
        float b,
        float a
    ) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        lineBuffer.vertex(m, fromX, fromY, fromZ);
        lineBuffer.vertex(m, toX, toY, toZ);
        RenderSystem.setShader(ShaderProgramKeys.POSITION);
        RenderSystem.setShaderColor(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
    }

    private static void renderGuideLineToTarget(
        MatrixStack matrixStack,
        CoordinatePoint target,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }

        Matrix4f m = matrixStack.peek().getPositionMatrix();
        Vector3f forward = client.gameRenderer.getCamera().getRotation().transform(new Vector3f(0.0f, 0.0f, -1.0f));
        float startX = forward.x() * 0.2f;
        float startY = forward.y() * 0.2f;
        float startZ = forward.z() * 0.2f;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        lineBuffer.vertex(m, startX, startY, startZ);
        lineBuffer.vertex(
            m,
            (float) (target.getX() - cameraX + 0.5),
            (float) (target.getY() - cameraY + 0.5),
            (float) (target.getZ() - cameraZ + 0.5));
        RenderSystem.setShader(ShaderProgramKeys.POSITION);
        RenderSystem.setShaderColor(1.0f, 1.0f, 0.3f, 0.95f);
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
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