package com.Emo.emohelper.client;

import com.Emo.emohelper.client.keybind.KeyBindingRegistry;
import com.Emo.emohelper.client.render.CoordinateRenderer;
import com.Emo.emohelper.client.screen.CoordinateListScreen;
import com.Emo.emohelper.config.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;

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

            while (KeyBindingRegistry.openConfigKey.wasPressed()) {
                client.setScreen(new CoordinateListScreen(null));
            }
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
}