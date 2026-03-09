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
    public static KeyBinding toggleOrderedDisplayModeKey;
    public static KeyBinding initializeOrderedRouteKey;
    public static KeyBinding quickCreatePointKey;

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

        // 切换有序组显示模式 (默认为 N 键)
        toggleOrderedDisplayModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.emohelper.toggle_ordered_display_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.emohelper"
        ));

        // 初始化有序路线 (默认为 M 键)
        initializeOrderedRouteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.emohelper.init_ordered_route",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.emohelper"
        ));

        // 快速创建坐标点 (默认为 J 键)
        quickCreatePointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.emohelper.quick_create_point",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.emohelper"
        ));
    }
}
