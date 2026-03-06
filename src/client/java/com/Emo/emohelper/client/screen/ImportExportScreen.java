package com.Emo.emohelper.client.screen;

import com.Emo.emohelper.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 导入导出屏幕
 */
public class ImportExportScreen extends Screen {
    private Screen parent;
    private boolean isImport;
    private String groupName;
    private TextFieldWidget textField;
    private Text message = Text.empty();
    private int messageColor = 0xAAAAAA;

    public ImportExportScreen(Screen parent, boolean isImport) {
        super(Text.translatable(isImport
            ? "screen.emohelper.import_coordinates"
            : "screen.emohelper.export_coordinates"));
        this.parent = parent;
        this.isImport = isImport;
        this.groupName = null;
    }

    public ImportExportScreen(Screen parent, boolean isImport, String groupName) {
        this(parent, isImport);
        this.groupName = (groupName == null || groupName.isBlank()) ? null : groupName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 60;
        int fieldWidth = this.width - 40;

        // 文本输入框 - TextFieldWidget只支持单行，所以改用较小高度
        this.textField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, 20,
            Text.translatable("field.emohelper.json"));
        this.textField.setMaxLength(65536);
        
        if (!isImport) {
            // 导出模式：显示现有配置
            if (groupName == null) {
                this.textField.setText(ConfigManager.exportAsJson());
            } else {
                this.textField.setText(ConfigManager.exportGroupAsJson(groupName));
            }
            this.textField.setEditable(false);
        }
        
        this.addSelectableChild(this.textField);
        this.setInitialFocus(this.textField);

        // 确认按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable(isImport ? "button.emohelper.import" : "button.emohelper.copy"), button -> {
                if (isImport) {
                    importData();
                } else {
                    // 将文本复制到剪贴板
                    this.client.keyboard.setClipboard(this.textField.getText());
                    message = Text.translatable("message.emohelper.copied");
                    messageColor = 0x00FF00;
                }
            }).dimensions(centerX - 80, startY + 40, 70, 20).build());

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.back"), button -> {
            this.client.setScreen(parent);
        }).dimensions(centerX + 10, startY + 40, 70, 20).build());
    }

    private void importData() {
        String jsonText = this.textField.getText();
        if (jsonText.isEmpty()) {
            message = Text.translatable("message.emohelper.empty_json");
            messageColor = 0xFF0000;
            return;
        }

        if (ConfigManager.importFromJson(jsonText)) {
            message = Text.translatable("message.emohelper.import_success");
            messageColor = 0x00FF00;
            // 几秒后自动返回
            this.client.setScreen(parent);
        } else {
            message = Text.translatable("message.emohelper.import_failed");
            messageColor = 0xFF0000;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 绘制标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // 绘制文本框标签
        context.drawTextWithShadow(this.textRenderer,
            Text.translatable(isImport ? "text.emohelper.paste_json" : "text.emohelper.json_output"),
            30, 45, 0xAAAAAA);

        // 渲染输入框
        this.textField.render(context, mouseX, mouseY, delta);

        // 显示消息
        if (!message.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, message, this.width / 2, this.height - 30, messageColor);
        }
    }

    public void close() {
        this.client.setScreen(parent);
    }
}
