package com.Emo.emohelper.client;

import com.Emo.emohelper.client.keybind.KeyBindingRegistry;
import com.Emo.emohelper.client.render.CoordinateRenderer;
import com.Emo.emohelper.client.screen.CoordinateListScreen;
import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class EmohelperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 加载配置
        ConfigManager.load();

        // 注册按键绑定
        KeyBindingRegistry.register();

        // 注册按键事件监听
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBindingRegistry.toggleRenderingKey.wasPressed()) {
                ConfigManager.getModConfig().setRenderingEnabled(
                    !ConfigManager.getModConfig().isRenderingEnabled()
                );
                ConfigManager.save();
            }

            while (KeyBindingRegistry.toggleOrderedDisplayModeKey.wasPressed()) {
                CoordinateData.OrderedDisplayMode mode = OrderedRouteManager.togglePreferredOrderedDisplayMode();
                if (client.player != null) {
                    if (mode == null) {
                        client.player.sendMessage(Text.translatable("message.emohelper.no_ordered_group"), true);
                    } else {
                        String modeKey = mode == CoordinateData.OrderedDisplayMode.PROGRESSIVE
                            ? "text.emohelper.ordered_display_mode.progressive"
                            : "text.emohelper.ordered_display_mode.all";
                        client.player.sendMessage(Text.translatable("message.emohelper.ordered_mode_switched", Text.translatable(modeKey)), true);
                    }
                }
            }

            while (KeyBindingRegistry.initializeOrderedRouteKey.wasPressed()) {
                OrderedRouteManager.initializeRoutesFromFirstTwo();
                if (client.player != null) {
                    client.player.sendMessage(Text.translatable("message.emohelper.route_reset"), true);
                }
            }

            while (KeyBindingRegistry.openConfigKey.wasPressed()) {
                client.setScreen(new CoordinateListScreen(null));
            }

            while (KeyBindingRegistry.quickCreatePointKey.wasPressed()) {
                createQuickPointAtPlayer(client);
            }

            OrderedRouteManager.tick(client);
        });

        // 使用 LAST 事件以便完全控制深度状态
        WorldRenderEvents.LAST.register(context -> {
            var client_ref = MinecraftClient.getInstance();
            if (client_ref.cameraEntity != null && client_ref.gameRenderer.getCamera() != null) {
                var camera = client_ref.gameRenderer.getCamera();
                CoordinateRenderer.render(
                    context.matrixStack(),
                    camera.getPos().x,
                    camera.getPos().y,
                    camera.getPos().z,
                    context.tickCounter().getTickDelta(true)
                );
            }
        });
    }

    private static void createQuickPointAtPlayer(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        CoordinateData data = ConfigManager.getCoordinateData();
        String targetGroup = resolveQuickCreateGroup(data);
        String tempLabelPrefix = Text.translatable("text.emohelper.temp_label_prefix").getString();
        CoordinatePoint point = new CoordinatePoint(
            client.player.getBlockX(),
            client.player.getBlockY(),
            client.player.getBlockZ(),
            findNextTempLabel(data, tempLabelPrefix)
        );
        point.setGroupName(targetGroup);

        if (data.isGroupLocked(targetGroup)) {
            client.player.sendMessage(Text.translatable("message.emohelper.group_locked"), true);
            return;
        }

        if (data.addPoint(point)) {
            ConfigManager.save();
            client.player.sendMessage(Text.translatable("message.emohelper.quick_point_created", point.getLabel()), true);
        } else {
            client.player.sendMessage(Text.translatable("message.emohelper.max_coordinates_reached"), true);
        }
    }

    private static String findNextTempLabel(CoordinateData data, String tempLabelPrefix) {
        Pattern tempLabelPattern = Pattern.compile("^" + Pattern.quote(tempLabelPrefix) + "(\\d+)$");
        Set<Integer> usedIndexes = new HashSet<>();
        for (CoordinatePoint point : data.getPoints()) {
            if (point == null || point.getLabel() == null) {
                continue;
            }
            var matcher = tempLabelPattern.matcher(point.getLabel().trim());
            if (matcher.matches()) {
                usedIndexes.add(Integer.parseInt(matcher.group(1)));
            }
        }

        int index = 1;
        while (usedIndexes.contains(index)) {
            index++;
        }
        return tempLabelPrefix + index;
    }

    private static String resolveQuickCreateGroup(CoordinateData data) {
        String preferredGroup = OrderedRouteManager.getPreferredGroup();
        if (preferredGroup != null && !preferredGroup.isBlank() && data.getGroups().contains(preferredGroup)) {
            return preferredGroup;
        }
        var groups = data.getGroups();
        if (!groups.isEmpty()) {
            return groups.get(0);
        }
        return CoordinateData.DEFAULT_GROUP;
    }
}