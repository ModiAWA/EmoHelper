package com.Emo.emohelper.config;

import com.google.gson.JsonObject;

/**
 * 全局模组配置类
 */
public class ModConfig {
    private int maxCoordinates;
    private boolean renderingEnabled;
    private float boxLineWidth;
    private float renderDistance;
    private boolean showLabels;

    public ModConfig() {
        this.maxCoordinates = 10;
        this.renderingEnabled = true;
        this.boxLineWidth = 1.0f;
        this.renderDistance = 256.0f;
        this.showLabels = true;
    }

    // Getters
    public int getMaxCoordinates() { return maxCoordinates; }
    public boolean isRenderingEnabled() { return renderingEnabled; }
    public float getBoxLineWidth() { return boxLineWidth; }
    public float getRenderDistance() { return renderDistance; }
    public boolean shouldShowLabels() { return showLabels; }

    // Setters
    public void setMaxCoordinates(int maxCoordinates) { this.maxCoordinates = Math.max(1, maxCoordinates); }
    public void setRenderingEnabled(boolean renderingEnabled) { this.renderingEnabled = renderingEnabled; }
    public void setBoxLineWidth(float boxLineWidth) { this.boxLineWidth = Math.max(0.1f, boxLineWidth); }
    public void setRenderDistance(float renderDistance) { this.renderDistance = Math.max(1.0f, renderDistance); }
    public void setShowLabels(boolean showLabels) { this.showLabels = showLabels; }

    // 转换为JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("maxCoordinates", maxCoordinates);
        json.addProperty("renderingEnabled", renderingEnabled);
        json.addProperty("boxLineWidth", boxLineWidth);
        json.addProperty("renderDistance", renderDistance);
        json.addProperty("showLabels", showLabels);
        return json;
    }

    // 从JSON加载
    public static ModConfig fromJson(JsonObject json) {
        ModConfig config = new ModConfig();
        if (json.has("maxCoordinates")) {
            config.setMaxCoordinates(json.get("maxCoordinates").getAsInt());
        }
        if (json.has("renderingEnabled")) {
            config.setRenderingEnabled(json.get("renderingEnabled").getAsBoolean());
        }
        if (json.has("boxLineWidth")) {
            config.setBoxLineWidth(json.get("boxLineWidth").getAsFloat());
        }
        if (json.has("renderDistance")) {
            config.setRenderDistance(json.get("renderDistance").getAsFloat());
        }
        if (json.has("showLabels")) {
            config.setShowLabels(json.get("showLabels").getAsBoolean());
        }
        return config;
    }
}
