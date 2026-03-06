package com.Emo.emohelper;

import com.Emo.emohelper.config.ConfigManager;
import net.fabricmc.api.ModInitializer;

public class Emohelper implements ModInitializer {

    @Override
    public void onInitialize() {
        // 初始化配置管理器（在主线程上）
        ConfigManager.load();
    }
}
