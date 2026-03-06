package com.Emo.emohelper.model;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

/**
 * 代表一个坐标点的数据类
 */
public class CoordinatePoint {
    private int x;
    private int y;
    private int z;
    private String label;
    private String groupName;
    private int color; // ARGB format
    private boolean enabled;

    public CoordinatePoint(int x, int y, int z, String label) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.groupName = "Default";
        this.color = 0xFF00FF00; // 默认绿色
        this.enabled = true;
    }

    public CoordinatePoint(int x, int y, int z, String label, int color, boolean enabled) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.groupName = "Default";
        this.color = color;
        this.enabled = enabled;
    }

    public CoordinatePoint(int x, int y, int z, String label, String groupName, int color, boolean enabled) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.groupName = (groupName == null || groupName.isBlank()) ? "Default" : groupName;
        this.color = color;
        this.enabled = enabled;
    }

    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getLabel() { return label; }
    public String getGroupName() { return groupName; }
    public int getColor() { return color; }
    public boolean isEnabled() { return enabled; }

    // Setters
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setZ(int z) { this.z = z; }
    public void setLabel(String label) { this.label = label; }
    public void setGroupName(String groupName) {
        this.groupName = (groupName == null || groupName.isBlank()) ? "Default" : groupName;
    }
    public void setColor(int color) { this.color = color; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("label", label);
        json.addProperty("group", groupName);
        json.addProperty("color", color);
        json.addProperty("enabled", enabled);
        return json;
    }

    public static CoordinatePoint fromJson(JsonObject json) {
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        String label = json.get("label").getAsString();
        String group = json.has("group") ? json.get("group").getAsString() : "Default";
        int color = json.has("color") ? json.get("color").getAsInt() : 0xFF00FF00;
        boolean enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : true;
        return new CoordinatePoint(x, y, z, label, group, color, enabled);
    }

    @Override
    public String toString() {
        return String.format("CoordinatePoint{x=%d, y=%d, z=%d, label='%s'}", x, y, z, label);
    }
}
