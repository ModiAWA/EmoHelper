package com.Emo.emohelper.client;

import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tracks ordered-route progression state per group.
 */
public final class OrderedRouteManager {
    private static final double ARRIVAL_DISTANCE_SQUARED = 4.0;
    private static final Map<String, RouteState> ROUTE_STATES = new HashMap<>();
    private static String preferredGroup;

    private OrderedRouteManager() {
    }

    public static void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        CoordinateData data = ConfigManager.getCoordinateData();
        boolean fallbackShowLabels = ConfigManager.getModConfig().shouldShowLabels();
        float fallbackRenderDistance = ConfigManager.getModConfig().getRenderDistance();

        List<String> orderedGroups = new ArrayList<>();
        for (String group : data.getGroups()) {
            CoordinateData.GroupRenderSettings settings = data.getGroupRenderSettings(group, fallbackShowLabels, fallbackRenderDistance);
            if (settings.groupType() != CoordinateData.GroupType.ORDERED) {
                continue;
            }

            orderedGroups.add(group);
            List<CoordinatePoint> points = getEnabledOrderedPoints(data, group);
            if (points.isEmpty()) {
                ROUTE_STATES.remove(group);
                continue;
            }

            RouteState state = ROUTE_STATES.computeIfAbsent(group, key -> createInitialState(settings, points.size()));
            normalizeState(state, settings, points.size());

            int current = Math.max(0, Math.min(state.currentIndex, points.size() - 1));
            int targetIndex = resolveNextIndex(current, points.size(), settings.loopRoute());
            if (targetIndex < 0) {
                continue;
            }
            CoordinatePoint target = points.get(targetIndex);
            double dx = player.getX() - (target.getX() + 0.5);
            double dy = player.getY() - (target.getY() + 0.5);
            double dz = player.getZ() - (target.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= ARRIVAL_DISTANCE_SQUARED) {
                advance(state, points.size(), settings.loopRoute());
            }
        }

        pruneMissingGroups(orderedGroups);
    }

    public static void initializeRoutesFromFirstTwo() {
        CoordinateData data = ConfigManager.getCoordinateData();
        boolean fallbackShowLabels = ConfigManager.getModConfig().shouldShowLabels();
        float fallbackRenderDistance = ConfigManager.getModConfig().getRenderDistance();

        for (String group : data.getGroups()) {
            CoordinateData.GroupRenderSettings settings = data.getGroupRenderSettings(group, fallbackShowLabels, fallbackRenderDistance);
            if (settings.groupType() != CoordinateData.GroupType.ORDERED) {
                continue;
            }

            int size = getEnabledOrderedPoints(data, group).size();
            if (size <= 0) {
                ROUTE_STATES.remove(group);
                continue;
            }

            RouteState state = new RouteState();
            state.currentIndex = Math.max(0, Math.min(settings.currentOrderedPoint() - 1, size - 1));
            ROUTE_STATES.put(group, state);
        }
    }

    public static CoordinateData.OrderedDisplayMode togglePreferredOrderedDisplayMode() {
        CoordinateData data = ConfigManager.getCoordinateData();
        boolean fallbackShowLabels = ConfigManager.getModConfig().shouldShowLabels();
        float fallbackRenderDistance = ConfigManager.getModConfig().getRenderDistance();

        String targetGroup = resolveTargetOrderedGroup(data, fallbackShowLabels, fallbackRenderDistance);
        if (targetGroup == null) {
            return null;
        }

        CoordinateData.GroupRenderSettings settings = data.getGroupRenderSettings(targetGroup, fallbackShowLabels, fallbackRenderDistance);
        CoordinateData.OrderedDisplayMode nextMode = settings.orderedDisplayMode().next();
        data.setGroupRenderSettings(
            targetGroup,
            settings.showLabels(),
            settings.renderDistance(),
            settings.groupType(),
            nextMode,
            settings.loopRoute(),
            settings.crosshairGuideLine(),
            settings.currentOrderedPoint(),
            settings.routeLineEnabled(),
            settings.routeLineGradient(),
            settings.routeLineAlpha(),
            settings.routeLineBrightness());

        int size = getEnabledOrderedPoints(data, targetGroup).size();
        RouteState state = ROUTE_STATES.computeIfAbsent(targetGroup, key -> createInitialState(settings, Math.max(1, size)));
        normalizeState(state, data.getGroupRenderSettings(targetGroup, fallbackShowLabels, fallbackRenderDistance), size);

        ConfigManager.save();
        return nextMode;
    }

    public static void setPreferredGroup(String group) {
        preferredGroup = group;
    }

    public static String getPreferredGroup() {
        return preferredGroup;
    }

    public static void resetGroupToConfiguredStart(String group) {
        if (group == null || group.isBlank()) {
            return;
        }

        CoordinateData data = ConfigManager.getCoordinateData();
        boolean fallbackShowLabels = ConfigManager.getModConfig().shouldShowLabels();
        float fallbackRenderDistance = ConfigManager.getModConfig().getRenderDistance();
        CoordinateData.GroupRenderSettings settings = data.getGroupRenderSettings(group, fallbackShowLabels, fallbackRenderDistance);
        if (settings.groupType() != CoordinateData.GroupType.ORDERED) {
            ROUTE_STATES.remove(group);
            return;
        }

        int size = getEnabledOrderedPoints(data, group).size();
        if (size <= 0) {
            ROUTE_STATES.remove(group);
            return;
        }

        RouteState state = ROUTE_STATES.computeIfAbsent(group, key -> new RouteState());
        state.currentIndex = Math.max(0, Math.min(settings.currentOrderedPoint() - 1, size - 1));
        normalizeState(state, settings, size);
    }

    public static boolean shouldRenderOrderedPoint(
        String group,
        int pointIndex,
        int pointCount,
        CoordinateData.GroupRenderSettings settings
    ) {
        if (settings.groupType() != CoordinateData.GroupType.ORDERED) {
            return true;
        }
        if (pointCount <= 0 || pointIndex < 0 || pointIndex >= pointCount) {
            return false;
        }
        if (settings.orderedDisplayMode() == CoordinateData.OrderedDisplayMode.ALL) {
            return true;
        }

        RouteState state = ROUTE_STATES.computeIfAbsent(group, key -> createInitialState(settings, pointCount));
        normalizeState(state, settings, pointCount);
        int current = Math.max(0, Math.min(state.currentIndex, pointCount - 1));
        int next = resolveNextIndex(current, pointCount, settings.loopRoute());
        return pointIndex == current || pointIndex == next;
    }

    public static int getCurrentTargetIndex(String group, int pointCount, CoordinateData.GroupRenderSettings settings) {
        if (settings.groupType() != CoordinateData.GroupType.ORDERED || pointCount <= 0) {
            return -1;
        }
        RouteState state = ROUTE_STATES.computeIfAbsent(group, key -> createInitialState(settings, pointCount));
        normalizeState(state, settings, pointCount);
        int current = Math.max(0, Math.min(state.currentIndex, pointCount - 1));
        return resolveNextIndex(current, pointCount, settings.loopRoute());
    }

    private static int resolveNextIndex(int currentIndex, int pointCount, boolean loopRoute) {
        int next = currentIndex + 1;
        if (next >= pointCount) {
            return loopRoute ? 0 : -1;
        }
        return next;
    }

    private static List<CoordinatePoint> getEnabledOrderedPoints(CoordinateData data, String group) {
        List<CoordinatePoint> points = new ArrayList<>();
        for (CoordinatePoint point : data.getPointsByGroup(group)) {
            if (point.isEnabled()) {
                points.add(point);
            }
        }
        return points;
    }

    private static RouteState createInitialState(CoordinateData.GroupRenderSettings settings, int pointCount) {
        RouteState state = new RouteState();
        state.currentIndex = Math.max(0, Math.min(settings.currentOrderedPoint() - 1, Math.max(0, pointCount - 1)));
        return state;
    }

    private static void normalizeState(RouteState state, CoordinateData.GroupRenderSettings settings, int pointCount) {
        if (pointCount <= 0) {
            state.currentIndex = 0;
            return;
        }

        state.currentIndex = Math.max(0, Math.min(state.currentIndex, pointCount - 1));
    }

    private static String resolveTargetOrderedGroup(CoordinateData data, boolean fallbackShowLabels, float fallbackRenderDistance) {
        if (preferredGroup != null && !preferredGroup.isBlank()) {
            CoordinateData.GroupRenderSettings preferredSettings = data.getGroupRenderSettings(preferredGroup, fallbackShowLabels, fallbackRenderDistance);
            if (preferredSettings.groupType() == CoordinateData.GroupType.ORDERED) {
                return preferredGroup;
            }
        }

        for (String group : data.getGroups()) {
            CoordinateData.GroupRenderSettings settings = data.getGroupRenderSettings(group, fallbackShowLabels, fallbackRenderDistance);
            if (settings.groupType() == CoordinateData.GroupType.ORDERED) {
                return group;
            }
        }
        return null;
    }

    private static void advance(RouteState state, int pointCount, boolean loopRoute) {
        if (pointCount <= 0) {
            state.currentIndex = 0;
            return;
        }

        if (state.currentIndex < pointCount - 1) {
            state.currentIndex++;
            return;
        }

        if (loopRoute) {
            state.currentIndex = 0;
        }
    }

    private static void pruneMissingGroups(List<String> orderedGroups) {
        Iterator<String> iterator = ROUTE_STATES.keySet().iterator();
        while (iterator.hasNext()) {
            String group = iterator.next();
            if (!orderedGroups.contains(group)) {
                iterator.remove();
            }
        }
    }

    private static final class RouteState {
        private int currentIndex;
    }
}
