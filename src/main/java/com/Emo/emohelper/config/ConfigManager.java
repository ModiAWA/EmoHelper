package com.Emo.emohelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.Emo.emohelper.model.CoordinatePoint;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置管理器，负责保存和加载配置文件
 */
public class ConfigManager {
    private static final String CONFIG_DIR = "emohelper";
    private static final String CONFIG_FILE = "emohelper.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ModConfig modConfig;
    private static CoordinateData coordinateData;
    private static Path configPath;

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
    }

    /**
     * 加载配置文件
     */
    public static void load() {
        try {
            if (configPath.toFile().exists()) {
                JsonObject json = GSON.fromJson(new FileReader(configPath.toFile()), JsonObject.class);
                
                // 加载全局配置
                if (json.has("config")) {
                    modConfig = ModConfig.fromJson(json.getAsJsonObject("config"));
                } else {
                    modConfig = new ModConfig();
                }
                
                // 加载坐标数据
                if (json.has("coordinates")) {
                    coordinateData = CoordinateData.fromJson(json.getAsJsonObject("coordinates"));
                } else {
                    coordinateData = new CoordinateData(modConfig.getMaxCoordinates());
                }
            } else {
                modConfig = new ModConfig();
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
            
            JsonObject json = new JsonObject();
            json.add("config", modConfig.toJson());
            json.add("coordinates", coordinateData.toJson());
            
            Files.createDirectories(configPath.getParent());
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(json, writer);
            }
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
                CoordinateData importedData = CoordinateData.fromJson(json.getAsJsonObject("coordinates"));
                ModConfig importedConfig = ModConfig.fromJson(json.getAsJsonObject("config"));
                modConfig = importedConfig;
                coordinateData = importedData;
                save();
                return true;
            }

            if (json.has("points") && json.get("points").isJsonArray() && !json.has("group")) {
                CoordinateData importedData = CoordinateData.fromJson(json);
                coordinateData = importedData;
                save();
                return true;
            }

            if (json.has("points") && json.get("points").isJsonArray()) {
                CoordinateData target = getCoordinateData();
                String groupName = json.has("group") ? json.get("group").getAsString() : CoordinateData.DEFAULT_GROUP;
                JsonArray pointsArray = json.getAsJsonArray("points");
                for (int i = 0; i < pointsArray.size(); i++) {
                    try {
                        CoordinatePoint point = CoordinatePoint.fromJson(pointsArray.get(i).getAsJsonObject());
                        point.setGroupName(groupName);
                        target.addPoint(point);
                    } catch (Exception e) {
                        System.err.println("Failed to load coordinate point: " + e.getMessage());
                    }
                }
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
}
