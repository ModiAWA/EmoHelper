package com.Emo.emohelper.config;

import com.Emo.emohelper.model.CoordinatePoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理所有坐标数据的类
 */
public class CoordinateData {
    public static final String DEFAULT_GROUP = "Default";
    private final List<CoordinatePoint> points;
    private final List<String> groups;
    private int maxPoints;

    public CoordinateData(int maxPoints) {
        this.maxPoints = maxPoints;
        this.points = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.groups.add(DEFAULT_GROUP);
    }

    // 添加坐标点
    public boolean addPoint(CoordinatePoint point) {
        if (point.getGroupName() == null || point.getGroupName().isBlank()) {
            point.setGroupName(DEFAULT_GROUP);
        }
        if (points.size() < maxPoints) {
            points.add(point);
            ensureGroup(point.getGroupName());
            return true;
        }
        return false;
    }

    // 删除坐标点
    public boolean removePoint(int index) {
        if (index >= 0 && index < points.size()) {
            points.remove(index);
            return true;
        }
        return false;
    }

    // 删除坐标点
    public boolean removePoint(CoordinatePoint point) {
        return points.remove(point);
    }

    // 获取坐标点
    public CoordinatePoint getPoint(int index) {
        if (index >= 0 && index < points.size()) {
            return points.get(index);
        }
        return null;
    }

    // 更新坐标点
    public boolean updatePoint(int index, CoordinatePoint point) {
        if (index >= 0 && index < points.size()) {
            if (point.getGroupName() == null || point.getGroupName().isBlank()) {
                point.setGroupName(DEFAULT_GROUP);
            }
            points.set(index, point);
            ensureGroup(point.getGroupName());
            return true;
        }
        return false;
    }

    public int indexOf(CoordinatePoint point) {
        return points.indexOf(point);
    }

    // 获取所有坐标点
    public List<CoordinatePoint> getPoints() {
        return new ArrayList<>(points);
    }

    public List<CoordinatePoint> getPointsByGroup(String groupName) {
        String normalized = normalizeGroup(groupName);
        List<CoordinatePoint> result = new ArrayList<>();
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName())) {
                result.add(point);
            }
        }
        return result;
    }

    // 清空所有坐标点
    public void clearPoints() {
        points.clear();
    }

    // 获取坐标点数量
    public int getPointCount() {
        return points.size();
    }

    public List<String> getGroups() {
        return new ArrayList<>(groups);
    }

    public void addGroup(String groupName) {
        String normalized = normalizeGroup(groupName);
        ensureGroup(normalized);
    }

    public void removeGroup(String groupName) {
        String normalized = normalizeGroup(groupName);
        groups.remove(normalized);
        points.removeIf(point -> normalized.equals(point.getGroupName()));
    }

    public boolean renameGroup(String oldName, String newName) {
        String oldNormalized = normalizeGroup(oldName);
        String newNormalized = normalizeGroup(newName);
        if (oldNormalized.equals(newNormalized)) {
            return false;
        }
        if (groups.contains(newNormalized)) {
            return false;
        }
        int index = groups.indexOf(oldNormalized);
        if (index < 0) {
            return false;
        }
        groups.set(index, newNormalized);
        for (CoordinatePoint point : points) {
            if (oldNormalized.equals(point.getGroupName())) {
                point.setGroupName(newNormalized);
            }
        }
        return true;
    }

    public void moveGroup(String groupName, int targetIndex) {
        String normalized = normalizeGroup(groupName);
        int fromIndex = groups.indexOf(normalized);
        if (fromIndex < 0) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(targetIndex, groups.size() - 1));
        if (fromIndex == safeIndex) {
            return;
        }
        groups.remove(fromIndex);
        groups.add(safeIndex, normalized);
    }

    public int getGroupEnabledCount(String groupName) {
        String normalized = normalizeGroup(groupName);
        int count = 0;
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName()) && point.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public int getGroupTotalCount(String groupName) {
        String normalized = normalizeGroup(groupName);
        int count = 0;
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName())) {
                count++;
            }
        }
        return count;
    }

    // 设置最大坐标点数
    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
        // 如果当前点数超过新的最大值，删除多余的
        while (points.size() > maxPoints) {
            points.remove(points.size() - 1);
        }
    }

    // 获取最大坐标点数
    public int getMaxPoints() {
        return maxPoints;
    }

    // 转换为JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("maxPoints", maxPoints);
        JsonArray groupsArray = new JsonArray();
        for (String group : groups) {
            groupsArray.add(group);
        }
        json.add("groups", groupsArray);
        JsonArray pointsArray = new JsonArray();
        for (CoordinatePoint point : points) {
            pointsArray.add(point.toJson());
        }
        json.add("points", pointsArray);
        return json;
    }

    public JsonObject toGroupJson(String groupName) {
        JsonObject json = new JsonObject();
        String normalized = normalizeGroup(groupName);
        json.addProperty("group", normalized);
        JsonArray pointsArray = new JsonArray();
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName())) {
                pointsArray.add(point.toJson());
            }
        }
        json.add("points", pointsArray);
        return json;
    }

    // 从JSON加载
    public static CoordinateData fromJson(JsonObject json) {
        int maxPoints = json.has("maxPoints") ? json.get("maxPoints").getAsInt() : 10;
        CoordinateData data = new CoordinateData(maxPoints);

        if (json.has("groups") && json.get("groups").isJsonArray()) {
            JsonArray groupsArray = json.getAsJsonArray("groups");
            data.groups.clear();
            for (int i = 0; i < groupsArray.size(); i++) {
                String group = groupsArray.get(i).getAsString();
                if (!group.isBlank()) {
                    data.groups.add(group);
                }
            }
        }
        if (data.groups.isEmpty()) {
            data.ensureGroup(DEFAULT_GROUP);
        }

        if (json.has("points") && json.get("points").isJsonArray()) {
            JsonArray pointsArray = json.getAsJsonArray("points");
            for (int i = 0; i < pointsArray.size(); i++) {
                try {
                    CoordinatePoint point = CoordinatePoint.fromJson(pointsArray.get(i).getAsJsonObject());
                    data.addPoint(point);
                } catch (Exception e) {
                    System.err.println("Failed to load coordinate point: " + e.getMessage());
                }
            }
        }

        return data;
    }

    private void ensureGroup(String groupName) {
        if (!groups.contains(groupName)) {
            groups.add(groupName);
        }
    }

    private String normalizeGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return DEFAULT_GROUP;
        }
        return groupName.trim();
    }

    public boolean isGroupFullyEnabled(String groupName) {
        String normalized = normalizeGroup(groupName);
        boolean hasPoint = false;
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName())) {
                hasPoint = true;
                if (!point.isEnabled()) {
                    return false;
                }
            }
        }
        return hasPoint;
    }

    public void setGroupEnabled(String groupName, boolean enabled) {
        String normalized = normalizeGroup(groupName);
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName())) {
                point.setEnabled(enabled);
            }
        }
    }
}