package com.Emo.emohelper.client.screen;

import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class RenameGroupScreen extends Screen {
    private final Screen parent;
    private final String originalName;
    private TextFieldWidget nameField;

    public RenameGroupScreen(Screen parent, String originalName) {
        super(Text.translatable("screen.emohelper.rename_group"));
        this.parent = parent;
        this.originalName = originalName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 70;
        int fieldWidth = 180;

        this.nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, 20,
            Text.translatable("field.emohelper.group_name"));
        this.nameField.setMaxLength(30);
        this.nameField.setText(originalName);
        this.addSelectableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.rename"), button -> {
            renameGroup();
        }).dimensions(centerX - 90, startY + 40, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.cancel"), button -> {
            this.client.setScreen(parent);
        }).dimensions(centerX + 10, startY + 40, 80, 20).build());
    }

    private void renameGroup() {
        String newName = nameField.getText().trim();
        if (newName.isEmpty()) {
            return;
        }
        CoordinateData data = ConfigManager.getCoordinateData();
        if (data.renameGroup(originalName, newName)) {
            ConfigManager.save();
        }
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 25, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group_name"),
            this.width / 2 - 120, 52, 0xAAAAAA);
        this.nameField.render(context, mouseX, mouseY, delta);
    }

    public void close() {
        this.client.setScreen(parent);
    }
}
