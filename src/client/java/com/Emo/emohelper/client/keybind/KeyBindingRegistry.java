package com.Emo.emohelper.client.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定注册类
 */
public class KeyBindingRegistry {
    public static KeyBinding toggleRenderingKey;
    public static KeyBinding openConfigKey;

    /**
     * 注册所有按键绑定
     */
    public static void register() {
        // 切换渲染状态 (默认为 V 键)
        toggleRenderingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.emohelper.toggle_rendering",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.emohelper"
        ));

        // 打开配置界面 (默认为 B 键)
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.emohelper.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.emohelper"
        ));
    }
}
