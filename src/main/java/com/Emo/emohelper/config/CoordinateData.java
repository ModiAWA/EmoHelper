package com.Emo.emohelper.config;

import com.Emo.emohelper.model.CoordinatePoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理所有坐标数据的类
 */
public class CoordinateData {
    public static final String DEFAULT_GROUP = "Default";
    private static final boolean DEFAULT_SHOW_LABELS = true;
    private static final float DEFAULT_RENDER_DISTANCE = 256.0f;
    private static final float MIN_RENDER_DISTANCE = 0.0f;
    private static final float MAX_RENDER_DISTANCE = 512.0f;
    private static final GroupType DEFAULT_GROUP_TYPE = GroupType.NORMAL;
    private static final OrderedDisplayMode DEFAULT_ORDERED_DISPLAY_MODE = OrderedDisplayMode.ALL;
    private static final boolean DEFAULT_LOOP_ROUTE = false;
    private static final boolean DEFAULT_CROSSHAIR_GUIDE_LINE = false;
    private static final int DEFAULT_CURRENT_ORDERED_POINT = 1;
    private static final boolean DEFAULT_ROUTE_LINE_ENABLED = true;
    private static final boolean DEFAULT_ROUTE_LINE_GRADIENT = false;
    private static final float DEFAULT_ROUTE_LINE_ALPHA = 0.95f;
    private static final float DEFAULT_ROUTE_LINE_BRIGHTNESS = 1.0f;
    private static final int MAX_POINTS_PER_GROUP = 200;
    private final List<CoordinatePoint> points;
    private final List<String> groups;
    private final Map<String, GroupRenderSettings> groupSettings;
    private final Map<String, Boolean> groupLocks;
    private int maxPoints;

    public CoordinateData(int maxPoints) {
        this.maxPoints = MAX_POINTS_PER_GROUP;
        this.points = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.groupSettings = new HashMap<>();
        this.groupLocks = new HashMap<>();
        this.groups.add(DEFAULT_GROUP);
        this.groupLocks.put(DEFAULT_GROUP, false);
        this.groupSettings.put(DEFAULT_GROUP, new GroupRenderSettings(
            DEFAULT_SHOW_LABELS,
            DEFAULT_RENDER_DISTANCE,
            DEFAULT_GROUP_TYPE,
            DEFAULT_ORDERED_DISPLAY_MODE,
            DEFAULT_LOOP_ROUTE,
            DEFAULT_CROSSHAIR_GUIDE_LINE,
            DEFAULT_CURRENT_ORDERED_POINT,
            DEFAULT_ROUTE_LINE_ENABLED,
            DEFAULT_ROUTE_LINE_GRADIENT,
            DEFAULT_ROUTE_LINE_ALPHA,
            DEFAULT_ROUTE_LINE_BRIGHTNESS));
    }

    // 添加坐标点
    public boolean addPoint(CoordinatePoint point) {
        return addPointInternal(point, true);
    }

    public boolean addPointFromStorage(CoordinatePoint point) {
        return addPointInternal(point, false);
    }

    private boolean addPointInternal(CoordinatePoint point, boolean enforceLock) {
        if (point == null) {
            return false;
        }
        String normalizedGroup = normalizeGroup(point.getGroupName());
        point.setGroupName(normalizedGroup);
        ensureGroup(normalizedGroup);
        if (enforceLock && isGroupLocked(normalizedGroup)) {
            return false;
        }
        if (getGroupTotalCount(normalizedGroup) < MAX_POINTS_PER_GROUP) {
            points.add(point);
            return true;
        }
        return false;
    }

    // 删除坐标点
    public boolean removePoint(int index) {
        if (index >= 0 && index < points.size()) {
            CoordinatePoint point = points.get(index);
            if (point != null && isGroupLocked(point.getGroupName())) {
                return false;
            }
            points.remove(index);
            return true;
        }
        return false;
    }

    // 删除坐标点
    public boolean removePoint(CoordinatePoint point) {
        if (point != null && isGroupLocked(point.getGroupName())) {
            return false;
        }
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
            CoordinatePoint existing = points.get(index);
            if (existing != null && isGroupLocked(existing.getGroupName())) {
                return false;
            }
            if (point.getGroupName() == null || point.getGroupName().isBlank()) {
                point.setGroupName(DEFAULT_GROUP);
            }
            if (isGroupLocked(point.getGroupName())) {
                return false;
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
        groupSettings.remove(normalized);
        groupLocks.remove(normalized);
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
        GroupRenderSettings settings = groupSettings.remove(oldNormalized);
        if (settings != null) {
            groupSettings.put(newNormalized, settings);
        }
        Boolean locked = groupLocks.remove(oldNormalized);
        if (locked != null) {
            groupLocks.put(newNormalized, locked);
        }
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

    public void movePointWithinGroup(String groupName, CoordinatePoint point, int targetIndexInGroup) {
        if (point == null) {
            return;
        }

        String normalized = normalizeGroup(groupName);
        if (isGroupLocked(normalized)) {
            return;
        }
        int firstGroupIndex = -1;
        List<CoordinatePoint> groupPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            CoordinatePoint current = points.get(i);
            if (normalized.equals(current.getGroupName())) {
                if (firstGroupIndex < 0) {
                    firstGroupIndex = i;
                }
                groupPoints.add(current);
            }
        }

        if (firstGroupIndex < 0 || groupPoints.size() <= 1) {
            return;
        }
        if (!groupPoints.remove(point)) {
            return;
        }

        int safeTarget = Math.max(0, Math.min(targetIndexInGroup, groupPoints.size()));
        groupPoints.add(safeTarget, point);

        List<CoordinatePoint> rebuilt = new ArrayList<>(points.size());
        for (CoordinatePoint current : points) {
            if (!normalized.equals(current.getGroupName())) {
                rebuilt.add(current);
            }
        }
        rebuilt.addAll(firstGroupIndex, groupPoints);

        points.clear();
        points.addAll(rebuilt);
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
        this.maxPoints = MAX_POINTS_PER_GROUP;
    }

    // 获取最大坐标点数
    public int getMaxPoints() {
        return MAX_POINTS_PER_GROUP;
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

        JsonObject locksJson = new JsonObject();
        for (String group : groups) {
            locksJson.addProperty(group, isGroupLocked(group));
        }
        json.add("groupLocks", locksJson);

        JsonObject settingsJson = new JsonObject();
        for (String group : groups) {
            GroupRenderSettings settings = getGroupRenderSettings(group, DEFAULT_SHOW_LABELS, DEFAULT_RENDER_DISTANCE);
            JsonObject groupJson = new JsonObject();
            groupJson.addProperty("showLabels", settings.showLabels());
            groupJson.addProperty("renderDistance", settings.renderDistance());
            groupJson.addProperty("groupType", settings.groupType().name());
            groupJson.addProperty("orderedDisplayMode", settings.orderedDisplayMode().name());
            groupJson.addProperty("loopRoute", settings.loopRoute());
            groupJson.addProperty("crosshairGuideLine", settings.crosshairGuideLine());
            groupJson.addProperty("currentOrderedPoint", settings.currentOrderedPoint());
            groupJson.addProperty("routeLineEnabled", settings.routeLineEnabled());
            groupJson.addProperty("routeLineGradient", settings.routeLineGradient());
            groupJson.addProperty("routeLineAlpha", settings.routeLineAlpha());
            groupJson.addProperty("routeLineBrightness", settings.routeLineBrightness());
            settingsJson.add(group, groupJson);
        }
        json.add("groupSettings", settingsJson);

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
        json.addProperty("locked", isGroupLocked(normalized));
        GroupRenderSettings settings = getGroupRenderSettings(normalized, DEFAULT_SHOW_LABELS, DEFAULT_RENDER_DISTANCE);
        json.addProperty("showLabels", settings.showLabels());
        json.addProperty("renderDistance", settings.renderDistance());
        json.addProperty("groupType", settings.groupType().name());
        json.addProperty("orderedDisplayMode", settings.orderedDisplayMode().name());
        json.addProperty("loopRoute", settings.loopRoute());
        json.addProperty("crosshairGuideLine", settings.crosshairGuideLine());
        json.addProperty("currentOrderedPoint", settings.currentOrderedPoint());
        json.addProperty("routeLineEnabled", settings.routeLineEnabled());
        json.addProperty("routeLineGradient", settings.routeLineGradient());
        json.addProperty("routeLineAlpha", settings.routeLineAlpha());
        json.addProperty("routeLineBrightness", settings.routeLineBrightness());
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
            data.groupSettings.clear();
            data.groupLocks.clear();
            for (int i = 0; i < groupsArray.size(); i++) {
                String group = groupsArray.get(i).getAsString();
                if (!group.isBlank()) {
                    data.groups.add(group);
                    data.groupLocks.put(group, false);
                    data.groupSettings.put(group, new GroupRenderSettings(
                        DEFAULT_SHOW_LABELS,
                        DEFAULT_RENDER_DISTANCE,
                        DEFAULT_GROUP_TYPE,
                        DEFAULT_ORDERED_DISPLAY_MODE,
                        DEFAULT_LOOP_ROUTE,
                        DEFAULT_CROSSHAIR_GUIDE_LINE,
                        DEFAULT_CURRENT_ORDERED_POINT,
                        DEFAULT_ROUTE_LINE_ENABLED,
                        DEFAULT_ROUTE_LINE_GRADIENT,
                        DEFAULT_ROUTE_LINE_ALPHA,
                        DEFAULT_ROUTE_LINE_BRIGHTNESS));
                }
            }
        }
        if (data.groups.isEmpty()) {
            data.ensureGroup(DEFAULT_GROUP);
        }

        if (json.has("groupLocks") && json.get("groupLocks").isJsonObject()) {
            JsonObject locksJson = json.getAsJsonObject("groupLocks");
            for (String group : data.groups) {
                if (locksJson.has(group)) {
                    data.groupLocks.put(group, locksJson.get(group).getAsBoolean());
                }
            }
        }

        if (json.has("groupSettings") && json.get("groupSettings").isJsonObject()) {
            JsonObject settingsJson = json.getAsJsonObject("groupSettings");
            for (String group : data.groups) {
                if (!settingsJson.has(group) || !settingsJson.get(group).isJsonObject()) {
                    continue;
                }
                JsonObject groupJson = settingsJson.getAsJsonObject(group);
                boolean showLabels = groupJson.has("showLabels") ? groupJson.get("showLabels").getAsBoolean() : DEFAULT_SHOW_LABELS;
                float renderDistance = groupJson.has("renderDistance") ? groupJson.get("renderDistance").getAsFloat() : DEFAULT_RENDER_DISTANCE;
                GroupType groupType = groupJson.has("groupType")
                    ? parseGroupType(groupJson.get("groupType").getAsString())
                    : DEFAULT_GROUP_TYPE;
                OrderedDisplayMode orderedDisplayMode = groupJson.has("orderedDisplayMode")
                    ? parseOrderedDisplayMode(groupJson.get("orderedDisplayMode").getAsString())
                    : DEFAULT_ORDERED_DISPLAY_MODE;
                boolean loopRoute = groupJson.has("loopRoute") && groupJson.get("loopRoute").getAsBoolean();
                boolean crosshairGuideLine = groupJson.has("crosshairGuideLine") && groupJson.get("crosshairGuideLine").getAsBoolean();
                int currentOrderedPoint = groupJson.has("currentOrderedPoint")
                    ? groupJson.get("currentOrderedPoint").getAsInt()
                    : DEFAULT_CURRENT_ORDERED_POINT;
                boolean routeLineEnabled = groupJson.has("routeLineEnabled")
                    ? groupJson.get("routeLineEnabled").getAsBoolean()
                    : DEFAULT_ROUTE_LINE_ENABLED;
                boolean routeLineGradient = groupJson.has("routeLineGradient")
                    ? groupJson.get("routeLineGradient").getAsBoolean()
                    : DEFAULT_ROUTE_LINE_GRADIENT;
                float routeLineAlpha = groupJson.has("routeLineAlpha")
                    ? groupJson.get("routeLineAlpha").getAsFloat()
                    : DEFAULT_ROUTE_LINE_ALPHA;
                float routeLineBrightness = groupJson.has("routeLineBrightness")
                    ? groupJson.get("routeLineBrightness").getAsFloat()
                    : DEFAULT_ROUTE_LINE_BRIGHTNESS;
                data.setGroupRenderSettings(
                    group,
                    showLabels,
                    renderDistance,
                    groupType,
                    orderedDisplayMode,
                    loopRoute,
                    crosshairGuideLine,
                    currentOrderedPoint,
                    routeLineEnabled,
                    routeLineGradient,
                    routeLineAlpha,
                    routeLineBrightness);
            }
        }

        if (json.has("points") && json.get("points").isJsonArray()) {
            JsonArray pointsArray = json.getAsJsonArray("points");
            for (int i = 0; i < pointsArray.size(); i++) {
                try {
                    CoordinatePoint point = CoordinatePoint.fromJson(pointsArray.get(i).getAsJsonObject());
                    data.addPointFromStorage(point);
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
        groupLocks.putIfAbsent(groupName, false);
        groupSettings.putIfAbsent(groupName, new GroupRenderSettings(
            DEFAULT_SHOW_LABELS,
            DEFAULT_RENDER_DISTANCE,
            DEFAULT_GROUP_TYPE,
            DEFAULT_ORDERED_DISPLAY_MODE,
            DEFAULT_LOOP_ROUTE,
            DEFAULT_CROSSHAIR_GUIDE_LINE,
            DEFAULT_CURRENT_ORDERED_POINT,
            DEFAULT_ROUTE_LINE_ENABLED,
            DEFAULT_ROUTE_LINE_GRADIENT,
            DEFAULT_ROUTE_LINE_ALPHA,
            DEFAULT_ROUTE_LINE_BRIGHTNESS));
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
        if (isGroupLocked(normalized)) {
            return;
        }
        for (CoordinatePoint point : points) {
            if (normalized.equals(point.getGroupName())) {
                point.setEnabled(enabled);
            }
        }
    }

    public boolean setPointEnabled(CoordinatePoint point, boolean enabled) {
        if (point == null || isGroupLocked(point.getGroupName())) {
            return false;
        }
        point.setEnabled(enabled);
        return true;
    }

    public boolean movePointToGroup(CoordinatePoint point, String targetGroupName) {
        if (point == null) {
            return false;
        }
        String sourceGroup = normalizeGroup(point.getGroupName());
        String targetGroup = normalizeGroup(targetGroupName);
        if (isGroupLocked(sourceGroup) || isGroupLocked(targetGroup)) {
            return false;
        }
        point.setGroupName(targetGroup);
        ensureGroup(targetGroup);
        return true;
    }

    public boolean isGroupLocked(String groupName) {
        return groupLocks.getOrDefault(normalizeGroup(groupName), false);
    }

    public void setGroupLocked(String groupName, boolean locked) {
        String normalized = normalizeGroup(groupName);
        ensureGroup(normalized);
        groupLocks.put(normalized, locked);
    }

    public GroupRenderSettings getGroupRenderSettings(String groupName, boolean fallbackShowLabels, float fallbackRenderDistance) {
        String normalized = normalizeGroup(groupName);
        GroupRenderSettings settings = groupSettings.get(normalized);
        if (settings == null) {
            settings = new GroupRenderSettings(
                fallbackShowLabels,
                clampRenderDistance(fallbackRenderDistance),
                DEFAULT_GROUP_TYPE,
                DEFAULT_ORDERED_DISPLAY_MODE,
                DEFAULT_LOOP_ROUTE,
                DEFAULT_CROSSHAIR_GUIDE_LINE,
                DEFAULT_CURRENT_ORDERED_POINT,
                DEFAULT_ROUTE_LINE_ENABLED,
                DEFAULT_ROUTE_LINE_GRADIENT,
                DEFAULT_ROUTE_LINE_ALPHA,
                DEFAULT_ROUTE_LINE_BRIGHTNESS);
            groupSettings.put(normalized, settings);
        }
        return settings;
    }

    public void setGroupRenderSettings(String groupName, boolean showLabels, float renderDistance) {
        String normalized = normalizeGroup(groupName);
        GroupRenderSettings existing = groupSettings.get(normalized);
        setGroupRenderSettings(
            normalized,
            showLabels,
            renderDistance,
            existing != null ? existing.groupType() : DEFAULT_GROUP_TYPE,
            existing != null ? existing.orderedDisplayMode() : DEFAULT_ORDERED_DISPLAY_MODE,
            existing != null && existing.loopRoute(),
            existing != null && existing.crosshairGuideLine(),
            existing != null ? existing.currentOrderedPoint() : DEFAULT_CURRENT_ORDERED_POINT,
            existing == null || existing.routeLineEnabled(),
            existing != null && existing.routeLineGradient(),
            existing != null ? existing.routeLineAlpha() : DEFAULT_ROUTE_LINE_ALPHA,
            existing != null ? existing.routeLineBrightness() : DEFAULT_ROUTE_LINE_BRIGHTNESS);
    }

    public void setGroupRenderSettings(
        String groupName,
        boolean showLabels,
        float renderDistance,
        GroupType groupType,
        OrderedDisplayMode orderedDisplayMode,
        boolean loopRoute,
        boolean crosshairGuideLine,
        int currentOrderedPoint,
        boolean routeLineEnabled,
        boolean routeLineGradient,
        float routeLineAlpha,
        float routeLineBrightness
    ) {
        String normalized = normalizeGroup(groupName);
        ensureGroup(normalized);
        groupSettings.put(normalized, new GroupRenderSettings(
            showLabels,
            clampRenderDistance(renderDistance),
            groupType == null ? DEFAULT_GROUP_TYPE : groupType,
            orderedDisplayMode == null ? DEFAULT_ORDERED_DISPLAY_MODE : orderedDisplayMode,
            loopRoute,
            crosshairGuideLine,
            clampCurrentOrderedPoint(currentOrderedPoint),
            routeLineEnabled,
            routeLineGradient,
            clampRouteLineAlpha(routeLineAlpha),
            clampRouteLineBrightness(routeLineBrightness)));
    }

    private int clampCurrentOrderedPoint(int currentOrderedPoint) {
        return Math.max(1, currentOrderedPoint);
    }

    private float clampRenderDistance(float renderDistance) {
        return Math.max(MIN_RENDER_DISTANCE, Math.min(MAX_RENDER_DISTANCE, renderDistance));
    }

    private float clampRouteLineAlpha(float alpha) {
        return Math.max(0.0f, Math.min(1.0f, alpha));
    }

    private float clampRouteLineBrightness(float brightness) {
        return Math.max(0.1f, Math.min(2.0f, brightness));
    }

    private static GroupType parseGroupType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_GROUP_TYPE;
        }
        try {
            return GroupType.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_GROUP_TYPE;
        }
    }

    private static OrderedDisplayMode parseOrderedDisplayMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_ORDERED_DISPLAY_MODE;
        }
        try {
            return OrderedDisplayMode.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_ORDERED_DISPLAY_MODE;
        }
    }

    public enum GroupType {
        NORMAL,
        ORDERED;

        public GroupType next() {
            return this == NORMAL ? ORDERED : NORMAL;
        }
    }

    public enum OrderedDisplayMode {
        ALL,
        PROGRESSIVE;

        public OrderedDisplayMode next() {
            return this == ALL ? PROGRESSIVE : ALL;
        }
    }

    public record GroupRenderSettings(
        boolean showLabels,
        float renderDistance,
        GroupType groupType,
        OrderedDisplayMode orderedDisplayMode,
        boolean loopRoute,
        boolean crosshairGuideLine,
        int currentOrderedPoint,
        boolean routeLineEnabled,
        boolean routeLineGradient,
        float routeLineAlpha,
        float routeLineBrightness
    ) {
    }
}