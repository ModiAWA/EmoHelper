package com.Emo.emohelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.Emo.emohelper.model.CoordinatePoint;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 配置管理器，负责保存和加载配置文件
 */
public class ConfigManager {
    private static final String CONFIG_DIR = "emohelper";
    private static final String CONFIG_FILE = "emohelper.json";
    private static final String GROUP_FILE_EXTENSION = ".json";
    private static final Pattern WINDOWS_INVALID_FILENAME_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");
    private static final Pattern WINDOWS_TRAILING_DOTS_OR_SPACES = Pattern.compile("[.\\s]+$");
    private static final Pattern LEGACY_URL_ENCODED_SEGMENT = Pattern.compile("%[0-9A-Fa-f]{2}");
    private static final Set<String> WINDOWS_RESERVED_FILE_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ModConfig modConfig;
    private static CoordinateData coordinateData;
    private static Path configPath;
    private static Path coordinateDirPath;

    static {
        try {
            initializeConfigPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化配置文件路径
     */
    private static void initializeConfigPath() throws IOException {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR);
        Files.createDirectories(configDir);
        configPath = configDir.resolve(CONFIG_FILE);

        // Group files live under the game root (e.g. .minecraft/emohelper)
        coordinateDirPath = FabricLoader.getInstance().getGameDir().resolve(CONFIG_DIR);
        Files.createDirectories(coordinateDirPath);
    }

    /**
     * 加载配置文件
     */
    public static void load() {
        try {
            JsonObject rootJson = null;
            if (configPath.toFile().exists()) {
                try (FileReader reader = new FileReader(configPath.toFile())) {
                    rootJson = GSON.fromJson(reader, JsonObject.class);
                }
            }

            // Load global config only from config file
            if (rootJson != null && rootJson.has("config") && rootJson.get("config").isJsonObject()) {
                modConfig = ModConfig.fromJson(rootJson.getAsJsonObject("config"));
            } else {
                modConfig = new ModConfig();
            }

            // Prefer the new per-group folder format
            coordinateData = loadCoordinateDataFromGroupFiles(modConfig.getMaxCoordinates());

            // Backward compatibility: old single-file "coordinates" payload migration
            if (coordinateData.getPointCount() == 0 && rootJson != null && rootJson.has("coordinates") && rootJson.get("coordinates").isJsonObject()) {
                JsonObject coordinatesJson = rootJson.getAsJsonObject("coordinates");
                coordinateData = CoordinateData.fromJson(coordinatesJson);
                coordinateData.setMaxPoints(modConfig.getMaxCoordinates());
                if (!coordinatesJson.has("groupSettings")) {
                    initializeGroupSettingsFromGlobalDefaults(coordinateData, modConfig);
                }
                save();
            }

            if (coordinateData == null) {
                coordinateData = new CoordinateData(modConfig.getMaxCoordinates());
            }
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
            modConfig = new ModConfig();
            coordinateData = new CoordinateData(modConfig.getMaxCoordinates());
        }
    }

    /**
     * 保存配置文件
     */
    public static void save() {
        try {
            if (modConfig == null || coordinateData == null) {
                return;
            }

            // Save global config only
            JsonObject json = new JsonObject();
            json.add("config", modConfig.toJson());

            Files.createDirectories(configPath.getParent());
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(json, writer);
            }

            // Save coordinates in per-group files under .minecraft/emohelper
            saveCoordinateGroups();
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * 导出配置为JSON字符串
     */
    public static String exportAsJson() {
        if (coordinateData == null) {
            return "";
        }
        return GSON.toJson(coordinateData.toJson());
    }

    public static String exportGroupAsJson(String groupName) {
        if (coordinateData == null) {
            return "";
        }
        return GSON.toJson(coordinateData.toGroupJson(groupName));
    }

    /**
     * 从JSON字符串导入坐标数据
     */
    public static boolean importFromJson(String jsonString) {
        try {
            JsonObject json = GSON.fromJson(jsonString, JsonObject.class);
            if (json.has("config") && json.has("coordinates")) {
                JsonObject coordinatesJson = json.getAsJsonObject("coordinates");
                CoordinateData importedData = CoordinateData.fromJson(coordinatesJson);
                ModConfig importedConfig = ModConfig.fromJson(json.getAsJsonObject("config"));
                if (!coordinatesJson.has("groupSettings")) {
                    initializeGroupSettingsFromGlobalDefaults(importedData, importedConfig);
                }
                modConfig = importedConfig;
                coordinateData = importedData;
                save();
                return true;
            }

            if (json.has("points") && json.get("points").isJsonArray() && !json.has("group")) {
                CoordinateData importedData = CoordinateData.fromJson(json);
                initializeGroupSettingsFromGlobalDefaults(importedData, getModConfig());
                coordinateData = importedData;
                save();
                return true;
            }

            if (json.has("points") && json.get("points").isJsonArray()) {
                CoordinateData target = getCoordinateData();
                String groupName = json.has("group") ? json.get("group").getAsString() : CoordinateData.DEFAULT_GROUP;
                if (target.isGroupLocked(groupName)) {
                    return false;
                }
                CoordinateData.GroupRenderSettings baseSettings = target.getGroupRenderSettings(
                    groupName,
                    getModConfig().shouldShowLabels(),
                    getModConfig().getRenderDistance());
                boolean showLabels = json.has("showLabels") ? json.get("showLabels").getAsBoolean() : baseSettings.showLabels();
                float renderDistance = json.has("renderDistance") ? json.get("renderDistance").getAsFloat() : baseSettings.renderDistance();
                CoordinateData.GroupType groupType = json.has("groupType")
                    ? parseGroupType(json.get("groupType").getAsString())
                    : baseSettings.groupType();
                CoordinateData.OrderedDisplayMode orderedDisplayMode = json.has("orderedDisplayMode")
                    ? parseOrderedDisplayMode(json.get("orderedDisplayMode").getAsString())
                    : baseSettings.orderedDisplayMode();
                boolean loopRoute = json.has("loopRoute") ? json.get("loopRoute").getAsBoolean() : baseSettings.loopRoute();
                boolean crosshairGuideLine = json.has("crosshairGuideLine") ? json.get("crosshairGuideLine").getAsBoolean() : baseSettings.crosshairGuideLine();
                int currentOrderedPoint = json.has("currentOrderedPoint")
                    ? json.get("currentOrderedPoint").getAsInt()
                    : baseSettings.currentOrderedPoint();
                boolean routeLineEnabled = json.has("routeLineEnabled") ? json.get("routeLineEnabled").getAsBoolean() : baseSettings.routeLineEnabled();
                boolean routeLineGradient = json.has("routeLineGradient") ? json.get("routeLineGradient").getAsBoolean() : baseSettings.routeLineGradient();
                float routeLineAlpha = json.has("routeLineAlpha") ? json.get("routeLineAlpha").getAsFloat() : baseSettings.routeLineAlpha();
                float routeLineBrightness = json.has("routeLineBrightness") ? json.get("routeLineBrightness").getAsFloat() : baseSettings.routeLineBrightness();
                target.setGroupRenderSettings(
                    groupName,
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
                boolean locked = json.has("locked") && json.get("locked").getAsBoolean();
                JsonArray pointsArray = json.getAsJsonArray("points");
                for (int i = 0; i < pointsArray.size(); i++) {
                    try {
                        CoordinatePoint point = CoordinatePoint.fromJson(pointsArray.get(i).getAsJsonObject());
                        point.setGroupName(groupName);
                        target.addPointFromStorage(point);
                    } catch (Exception e) {
                        System.err.println("Failed to load coordinate point: " + e.getMessage());
                    }
                }
                target.setGroupLocked(groupName, locked);
                save();
                return true;
            }

            return false;
        } catch (Exception e) {
            System.err.println("Failed to import coordinates: " + e.getMessage());
            return false;
        }
    }

    // Getters
    public static ModConfig getModConfig() {
        if (modConfig == null) {
            load();
        }
        return modConfig;
    }

    public static CoordinateData getCoordinateData() {
        if (coordinateData == null) {
            load();
        }
        return coordinateData;
    }

    /**
     * 获取配置文件路径
     */
    public static Path getConfigPath() {
        return configPath;
    }

    private static CoordinateData loadCoordinateDataFromGroupFiles(int maxPoints) throws IOException {
        CoordinateData data = new CoordinateData(maxPoints);

        Files.createDirectories(coordinateDirPath);
        List<Path> groupFiles = new ArrayList<>();
        try (var stream = Files.list(coordinateDirPath)) {
            stream
                .filter(path -> path.getFileName().toString().endsWith(GROUP_FILE_EXTENSION))
                .forEach(groupFiles::add);
        }

        if (groupFiles.isEmpty()) {
            return data;
        }

        List<GroupFilePayload> payloads = new ArrayList<>();
        for (Path groupFile : groupFiles) {
            try (FileReader reader = new FileReader(groupFile.toFile(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) {
                    continue;
                }
                String group = json.has("group") ? normalizeGroupName(json.get("group").getAsString()) : decodeGroupNameFromFile(groupFile);
                int order = json.has("order") ? json.get("order").getAsInt() : Integer.MAX_VALUE;
                payloads.add(new GroupFilePayload(group, order, json));
            } catch (Exception e) {
                System.err.println("Failed to load group file " + groupFile.getFileName() + ": " + e.getMessage());
            }
        }

        payloads.sort(Comparator
            .comparingInt(GroupFilePayload::order)
            .thenComparing(GroupFilePayload::group));

        data.removeGroup(CoordinateData.DEFAULT_GROUP);
        for (GroupFilePayload payload : payloads) {
            data.addGroup(payload.group());
            boolean showLabels = payload.json().has("showLabels")
                ? payload.json().get("showLabels").getAsBoolean()
                : modConfig.shouldShowLabels();
            float renderDistance = payload.json().has("renderDistance")
                ? payload.json().get("renderDistance").getAsFloat()
                : modConfig.getRenderDistance();
            CoordinateData.GroupType groupType = payload.json().has("groupType")
                ? parseGroupType(payload.json().get("groupType").getAsString())
                : CoordinateData.GroupType.NORMAL;
            CoordinateData.OrderedDisplayMode orderedDisplayMode = payload.json().has("orderedDisplayMode")
                ? parseOrderedDisplayMode(payload.json().get("orderedDisplayMode").getAsString())
                : CoordinateData.OrderedDisplayMode.ALL;
            boolean loopRoute = payload.json().has("loopRoute") && payload.json().get("loopRoute").getAsBoolean();
            boolean crosshairGuideLine = payload.json().has("crosshairGuideLine") && payload.json().get("crosshairGuideLine").getAsBoolean();
            int currentOrderedPoint = payload.json().has("currentOrderedPoint")
                ? payload.json().get("currentOrderedPoint").getAsInt()
                : 1;
            boolean routeLineEnabled = payload.json().has("routeLineEnabled") ? payload.json().get("routeLineEnabled").getAsBoolean() : true;
            boolean routeLineGradient = payload.json().has("routeLineGradient") && payload.json().get("routeLineGradient").getAsBoolean();
            float routeLineAlpha = payload.json().has("routeLineAlpha") ? payload.json().get("routeLineAlpha").getAsFloat() : 0.95f;
            float routeLineBrightness = payload.json().has("routeLineBrightness") ? payload.json().get("routeLineBrightness").getAsFloat() : 1.0f;
            boolean locked = payload.json().has("locked") && payload.json().get("locked").getAsBoolean();
            data.setGroupRenderSettings(
                payload.group(),
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
            data.setGroupLocked(payload.group(), locked);
            if (payload.json().has("points") && payload.json().get("points").isJsonArray()) {
                JsonArray pointsArray = payload.json().getAsJsonArray("points");
                for (JsonElement element : pointsArray) {
                    try {
                        CoordinatePoint point = CoordinatePoint.fromJson(element.getAsJsonObject());
                        point.setGroupName(payload.group());
                        data.addPointFromStorage(point);
                    } catch (Exception e) {
                        System.err.println("Failed to load coordinate point from group " + payload.group() + ": " + e.getMessage());
                    }
                }
            }
        }

        if (data.getGroups().isEmpty()) {
            data.addGroup(CoordinateData.DEFAULT_GROUP);
        }
        return data;
    }

    private static void saveCoordinateGroups() throws IOException {
        Files.createDirectories(coordinateDirPath);

        List<String> groups = coordinateData.getGroups();
        Map<String, Path> expectedFiles = new HashMap<>();

        for (int i = 0; i < groups.size(); i++) {
            String group = normalizeGroupName(groups.get(i));
            Path filePath = resolveUniqueGroupFilePath(expectedFiles, group);
            expectedFiles.put(group, filePath);

            JsonObject groupJson = coordinateData.toGroupJson(group);
            groupJson.addProperty("order", i);
            try (FileWriter writer = new FileWriter(filePath.toFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(groupJson, writer);
            }
        }

        try (var stream = Files.list(coordinateDirPath)) {
            stream
                .filter(path -> path.getFileName().toString().endsWith(GROUP_FILE_EXTENSION))
                .filter(path -> !expectedFiles.containsValue(path))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete stale group file " + path.getFileName() + ": " + e.getMessage());
                    }
                });
        }
    }

    private static Path resolveUniqueGroupFilePath(Map<String, Path> expectedFiles, String group) {
        String fileBaseName = toReadableFileBaseName(group);
        Path preferred = coordinateDirPath.resolve(fileBaseName + GROUP_FILE_EXTENSION);
        if (!expectedFiles.containsValue(preferred)) {
            return preferred;
        }

        int index = 1;
        while (true) {
            Path candidate = coordinateDirPath.resolve(fileBaseName + "_" + index + GROUP_FILE_EXTENSION);
            if (!expectedFiles.containsValue(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private static String toReadableFileBaseName(String groupName) {
        String normalized = normalizeGroupName(groupName);
        String safeName = WINDOWS_INVALID_FILENAME_CHARS.matcher(normalized).replaceAll("_");
        safeName = WINDOWS_TRAILING_DOTS_OR_SPACES.matcher(safeName).replaceAll("");

        if (safeName.isBlank()) {
            safeName = CoordinateData.DEFAULT_GROUP;
        }

        String upper = safeName.toUpperCase(Locale.ROOT);
        if (WINDOWS_RESERVED_FILE_NAMES.contains(upper)) {
            safeName = safeName + "_";
        }

        return safeName;
    }

    private static String decodeGroupNameFromFile(Path groupFile) {
        String fileName = groupFile.getFileName().toString();
        if (fileName.endsWith(GROUP_FILE_EXTENSION)) {
            fileName = fileName.substring(0, fileName.length() - GROUP_FILE_EXTENSION.length());
        }

        if (LEGACY_URL_ENCODED_SEGMENT.matcher(fileName).find()) {
            try {
                return java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // Keep raw file name when legacy decode fails.
            }
        }

        return fileName;
    }

    private static String normalizeGroupName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return CoordinateData.DEFAULT_GROUP;
        }
        return groupName.trim();
    }

    private static void initializeGroupSettingsFromGlobalDefaults(CoordinateData data, ModConfig config) {
        boolean showLabels = config != null ? config.shouldShowLabels() : true;
        float renderDistance = config != null ? config.getRenderDistance() : 256.0f;
        for (String group : data.getGroups()) {
            data.setGroupRenderSettings(group, showLabels, renderDistance);
        }
    }

    private static CoordinateData.GroupType parseGroupType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return CoordinateData.GroupType.NORMAL;
        }
        try {
            return CoordinateData.GroupType.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return CoordinateData.GroupType.NORMAL;
        }
    }

    private static CoordinateData.OrderedDisplayMode parseOrderedDisplayMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return CoordinateData.OrderedDisplayMode.ALL;
        }
        try {
            return CoordinateData.OrderedDisplayMode.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return CoordinateData.OrderedDisplayMode.ALL;
        }
    }

    private record GroupFilePayload(String group, int order, JsonObject json) { }
}
