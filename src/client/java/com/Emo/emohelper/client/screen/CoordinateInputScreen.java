package com.Emo.emohelper.client.screen;

import com.Emo.emohelper.client.OrderedRouteManager;
import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 坐标输入屏幕
 */
public class CoordinateInputScreen extends Screen {
    private static final int BASE_START_Y = 50;
    private static final int SCROLL_STEP = 14;
    private Screen parent;
    private CoordinatePoint coordinate;
    private int index;
    private CoordinateData coordinateData;
    private boolean isEdit;
    private String initialGroupName;
    private String currentGroupName;

    private TextFieldWidget labelField;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private TextFieldWidget groupField;
    private TextFieldWidget colorField;
    private ButtonWidget groupSelectButton;
    private ButtonWidget saveButton;
    private ButtonWidget deleteButton;
    private ButtonWidget cancelButton;
    private int defaultX;
    private int defaultY;
    private int defaultZ;
    private int scrollOffset;

    public CoordinateInputScreen(Screen parent, CoordinatePoint coordinate, int index) {
        super(Text.translatable(coordinate == null
            ? "screen.emohelper.add_coordinate"
            : "screen.emohelper.edit_coordinate"));
        this.parent = parent;
        this.coordinate = coordinate;
        this.index = index;
        this.coordinateData = ConfigManager.getCoordinateData();
        this.isEdit = coordinate != null;
        this.initialGroupName = coordinate != null
            ? coordinate.getGroupName()
            : CoordinateData.DEFAULT_GROUP;
        this.currentGroupName = this.initialGroupName;
    }

    public CoordinateInputScreen(Screen parent, CoordinatePoint coordinate, int index, String groupName) {
        this(parent, coordinate, index);
        if (groupName != null && !groupName.isBlank()) {
            this.initialGroupName = groupName;
            this.currentGroupName = groupName;
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = BASE_START_Y;
        int fieldWidth = 150;
        this.defaultX = resolveDefaultX();
        this.defaultY = resolveDefaultY();
        this.defaultZ = resolveDefaultZ();

        // 标签输入框
        this.labelField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, 20,
            Text.translatable("field.emohelper.label"));
        this.labelField.setMaxLength(50);
        if (isEdit) {
            this.labelField.setText(coordinate.getLabel());
        }
        this.addSelectableChild(this.labelField);
        this.setInitialFocus(this.labelField);

        // 分组输入框
        this.groupField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + 30, fieldWidth, 20,
            Text.translatable("field.emohelper.group"));
        this.groupField.setMaxLength(30);
        this.groupField.setText(currentGroupName);
        this.addSelectableChild(this.groupField);

        // 分组选择按钮
        this.groupSelectButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.emohelper.choose_group"), button -> {
                this.client.setScreen(new GroupSelectScreen(this, this, coordinateData.getGroups()));
            }).dimensions(centerX + fieldWidth / 2 + 6, startY + 30, 80, 20).build());

        // 颜色输入框
        this.colorField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + 60, fieldWidth, 20,
            Text.translatable("field.emohelper.color"));
        this.colorField.setMaxLength(10);
        this.colorField.setText(formatColor(isEdit ? coordinate.getColor() : 0xFF00FF00));
        this.addSelectableChild(this.colorField);

        // X坐标输入框
        this.xField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + 90, fieldWidth, 20,
            Text.translatable("field.emohelper.x"));
        this.xField.setMaxLength(10);
        if (isEdit) {
            this.xField.setText(String.valueOf(coordinate.getX()));
        } else {
            this.xField.setPlaceholder(Text.literal(String.valueOf(defaultX)));
        }
        this.addSelectableChild(this.xField);

        // Y坐标输入框
        this.yField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + 120, fieldWidth, 20,
            Text.translatable("field.emohelper.y"));
        this.yField.setMaxLength(10);
        if (isEdit) {
            this.yField.setText(String.valueOf(coordinate.getY()));
        } else {
            this.yField.setPlaceholder(Text.literal(String.valueOf(defaultY)));
        }
        this.addSelectableChild(this.yField);

        // Z坐标输入框
        this.zField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + 150, fieldWidth, 20,
            Text.translatable("field.emohelper.z"));
        this.zField.setMaxLength(10);
        if (isEdit) {
            this.zField.setText(String.valueOf(coordinate.getZ()));
        } else {
            this.zField.setPlaceholder(Text.literal(String.valueOf(defaultZ)));
        }
        this.addSelectableChild(this.zField);

        // 保存按钮
        this.saveButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.save"), button -> {
            saveCoordinate();
        }).dimensions(centerX - 80, startY + 190, 70, 20).build());

        // 删除按钮（仅编辑时显示）
        if (isEdit) {
            this.deleteButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.delete"), button -> {
                coordinateData.removePoint(index);
                ConfigManager.save();
                this.client.setScreen(parent);
            }).dimensions(centerX + 10, startY + 190, 70, 20).build());
        }

        // 取消按钮
        this.cancelButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.cancel"), button -> {
            this.client.setScreen(parent);
        }).dimensions(centerX - 80, startY + 220, 160, 20).build());

        clampScroll();
        applyScrollLayout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int previous = scrollOffset;
        scrollOffset = Math.max(0, Math.min(getMaxScroll(), scrollOffset - (int) (verticalAmount * SCROLL_STEP)));
        if (scrollOffset != previous) {
            applyScrollLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void saveCoordinate() {
        try {
            String label = this.labelField.getText();
            String groupName = this.groupField.getText();
            if (groupName == null || groupName.isBlank()) {
                groupName = currentGroupName;
            }
            String sourceGroup = isEdit ? coordinate.getGroupName() : groupName;
            if (coordinateData.isGroupLocked(sourceGroup) || coordinateData.isGroupLocked(groupName)) {
                if (this.client != null && this.client.player != null) {
                    this.client.player.sendMessage(Text.translatable("message.emohelper.group_locked"), true);
                }
                return;
            }
            OrderedRouteManager.setPreferredGroup(groupName);
            int color = parseColorOrDefault(this.colorField.getText(), isEdit ? coordinate.getColor() : 0xFF00FF00);
            int x = parseCoordinateOrDefault(this.xField.getText(), defaultX);
            int y = parseCoordinateOrDefault(this.yField.getText(), defaultY);
            int z = parseCoordinateOrDefault(this.zField.getText(), defaultZ);

            if (label.isEmpty()) {
                // 显示错误消息
                return;
            }

            if (isEdit) {
                // 编辑现有坐标
                CoordinatePoint updated = new CoordinatePoint(x, y, z, label);
                updated.setGroupName(groupName);
                updated.setColor(color);
                updated.setEnabled(coordinate.isEnabled());
                coordinateData.updatePoint(index, updated);
            } else {
                // 添加新坐标
                CoordinatePoint point = new CoordinatePoint(x, y, z, label);
                point.setGroupName(groupName);
                point.setColor(color);
                if (!coordinateData.addPoint(point)) {
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(Text.translatable("message.emohelper.max_coordinates_reached"), true);
                    }
                    return;
                }
            }

            ConfigManager.save();
            this.client.setScreen(parent);
        } catch (NumberFormatException e) {
            // 显示错误消息
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        applyScrollLayout();
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 绘制标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // 绘制标签
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.label"), this.width / 2 - 155, toScreenY(55), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group"), this.width / 2 - 155, toScreenY(85), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.color"), this.width / 2 - 155, toScreenY(115), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.x"), this.width / 2 - 155, toScreenY(145), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.y"), this.width / 2 - 155, toScreenY(175), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.z"), this.width / 2 - 155, toScreenY(205), 0xAAAAAA);

        // 渲染输入框
        this.labelField.render(context, mouseX, mouseY, delta);
        this.groupField.render(context, mouseX, mouseY, delta);
        this.colorField.render(context, mouseX, mouseY, delta);
        this.xField.render(context, mouseX, mouseY, delta);
        this.yField.render(context, mouseX, mouseY, delta);
        this.zField.render(context, mouseX, mouseY, delta);
    }

    public void setGroupName(String groupName) {
        String normalized = (groupName == null || groupName.isBlank())
            ? CoordinateData.DEFAULT_GROUP
            : groupName;
        this.currentGroupName = normalized;
        OrderedRouteManager.setPreferredGroup(normalized);
        if (this.groupField != null) {
            this.groupField.setText(normalized);
        }
    }

    private int parseCoordinateOrDefault(String input, int fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(input.trim());
    }

    private int resolveDefaultX() {
        if (isEdit) {
            return coordinate.getX();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getBlockX();
        }
        return 0;
    }

    private int resolveDefaultY() {
        if (isEdit) {
            return coordinate.getY();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getBlockY();
        }
        return 64;
    }

    private int resolveDefaultZ() {
        if (isEdit) {
            return coordinate.getZ();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getBlockZ();
        }
        return 0;
    }

    private String formatColor(int color) {
        return String.format("#%08X", color);
    }

    private int parseColorOrDefault(String input, int fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String normalized = input.trim();
        try {
            if (normalized.startsWith("#")) {
                return (int)Long.parseLong(normalized.substring(1), 16);
            }
            if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
                return (int)Long.parseLong(normalized.substring(2), 16);
            }
            return Integer.parseUnsignedInt(normalized);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void close() {
        this.client.setScreen(parent);
    }

    private int toScreenY(int baseY) {
        return baseY - scrollOffset;
    }

    private int getMaxScroll() {
        int contentBottom = BASE_START_Y + 220 + 20 + 12;
        int viewportBottom = this.height - 10;
        return Math.max(0, contentBottom - viewportBottom);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    private void applyScrollLayout() {
        int startY = BASE_START_Y;
        if (labelField != null) {
            labelField.setY(toScreenY(startY));
        }
        if (groupField != null) {
            groupField.setY(toScreenY(startY + 30));
        }
        if (groupSelectButton != null) {
            groupSelectButton.setY(toScreenY(startY + 30));
        }
        if (colorField != null) {
            colorField.setY(toScreenY(startY + 60));
        }
        if (xField != null) {
            xField.setY(toScreenY(startY + 90));
        }
        if (yField != null) {
            yField.setY(toScreenY(startY + 120));
        }
        if (zField != null) {
            zField.setY(toScreenY(startY + 150));
        }
        if (saveButton != null) {
            saveButton.setY(toScreenY(startY + 190));
        }
        if (deleteButton != null) {
            deleteButton.setY(toScreenY(startY + 190));
        }
        if (cancelButton != null) {
            cancelButton.setY(toScreenY(startY + 220));
        }
    }
}