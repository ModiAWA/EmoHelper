package com.Emo.emohelper.client.screen;

import com.Emo.emohelper.client.OrderedRouteManager;
import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class RenameGroupScreen extends Screen {
    private static final int BASE_START_Y = 64;
    private static final int SCROLL_STEP = 14;
    private final Screen parent;
    private final String originalName;
    private TextFieldWidget nameField;
    private TextFieldWidget distanceField;
    private TextFieldWidget currentPointField;
    private ButtonWidget labelToggleButton;
    private ButtonWidget groupTypeButton;
    private ButtonWidget orderedDisplayModeButton;
    private ButtonWidget loopRouteButton;
    private ButtonWidget crosshairGuideLineButton;
    private ButtonWidget routeLineButton;
    private ButtonWidget routeLineGradientButton;
    private ButtonWidget renderModeButton;
    private boolean showLabels;
    private CoordinateData.GroupType selectedGroupType;
    private CoordinateData.OrderedDisplayMode selectedOrderedDisplayMode;
    private boolean loopRoute;
    private boolean crosshairGuideLine;
    private boolean routeLineEnabled;
    private boolean routeLineGradient;
    private TextFieldWidget routeLineAlphaField;
    private TextFieldWidget routeLineBrightnessField;
    private com.Emo.emohelper.config.ModConfig.RenderMode selectedRenderMode;
    private int labelX;
    private int startY;
    private int rowHeight;
    private int scrollOffset;
    private ButtonWidget confirmButton;
    private ButtonWidget cancelButton;

    public RenameGroupScreen(Screen parent, String originalName) {
        super(Text.translatable("screen.emohelper.rename_group"));
        this.parent = parent;
        this.originalName = originalName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.startY = BASE_START_Y;
        this.rowHeight = 28;
        this.labelX = centerX - 128;
        int controlX = centerX - 28;
        int controlWidth = 160;
        CoordinateData data = ConfigManager.getCoordinateData();
        var modConfig = ConfigManager.getModConfig();
        CoordinateData.GroupRenderSettings settings = data.getGroupRenderSettings(
            originalName,
            modConfig.shouldShowLabels(),
            modConfig.getRenderDistance());
        this.showLabels = settings.showLabels();
        this.selectedGroupType = settings.groupType();
        this.selectedOrderedDisplayMode = settings.orderedDisplayMode();
        this.loopRoute = settings.loopRoute();
        this.crosshairGuideLine = settings.crosshairGuideLine();
        this.routeLineEnabled = settings.routeLineEnabled();
        this.routeLineGradient = settings.routeLineGradient();
        this.selectedRenderMode = modConfig.getRenderMode();
        OrderedRouteManager.setPreferredGroup(originalName);

        this.nameField = new TextFieldWidget(this.textRenderer, controlX, startY, controlWidth, 20,
            Text.translatable("field.emohelper.group_name"));
        this.nameField.setMaxLength(30);
        this.nameField.setText(originalName);
        this.addSelectableChild(this.nameField);

        this.labelToggleButton = this.addDrawableChild(ButtonWidget.builder(getLabelToggleText(), button -> {
            showLabels = !showLabels;
            button.setMessage(getLabelToggleText());
        }).dimensions(controlX, startY + rowHeight, controlWidth, 20).build());

        this.distanceField = new TextFieldWidget(this.textRenderer, controlX, startY + rowHeight * 2, controlWidth, 20,
            Text.translatable("field.emohelper.group_render_distance"));
        this.distanceField.setMaxLength(4);
        this.distanceField.setText(String.valueOf(Math.round(settings.renderDistance())));
        this.addSelectableChild(this.distanceField);

        this.currentPointField = new TextFieldWidget(this.textRenderer, controlX, startY + rowHeight * 12, controlWidth, 20,
            Text.translatable("field.emohelper.current_ordered_point"));
        this.currentPointField.setMaxLength(5);
        this.currentPointField.setText(String.valueOf(settings.currentOrderedPoint()));
        this.addSelectableChild(this.currentPointField);

        this.groupTypeButton = this.addDrawableChild(ButtonWidget.builder(getGroupTypeText(), button -> {
            selectedGroupType = selectedGroupType.next();
            button.setMessage(getGroupTypeText());
            refreshOrderedButtonsState();
        }).dimensions(controlX, startY + rowHeight * 3, controlWidth, 20).build());

        this.orderedDisplayModeButton = this.addDrawableChild(ButtonWidget.builder(getOrderedDisplayModeText(), button -> {
            selectedOrderedDisplayMode = selectedOrderedDisplayMode.next();
            button.setMessage(getOrderedDisplayModeText());
        }).dimensions(controlX, startY + rowHeight * 4, controlWidth, 20).build());

        this.loopRouteButton = this.addDrawableChild(ButtonWidget.builder(getLoopRouteText(), button -> {
            loopRoute = !loopRoute;
            button.setMessage(getLoopRouteText());
        }).dimensions(controlX, startY + rowHeight * 5, controlWidth, 20).build());

        this.crosshairGuideLineButton = this.addDrawableChild(ButtonWidget.builder(getCrosshairGuideLineText(), button -> {
            crosshairGuideLine = !crosshairGuideLine;
            button.setMessage(getCrosshairGuideLineText());
        }).dimensions(controlX, startY + rowHeight * 6, controlWidth, 20).build());

        this.routeLineButton = this.addDrawableChild(ButtonWidget.builder(getRouteLineText(), button -> {
            routeLineEnabled = !routeLineEnabled;
            button.setMessage(getRouteLineText());
            refreshOrderedButtonsState();
        }).dimensions(controlX, startY + rowHeight * 7, controlWidth, 20).build());

        this.routeLineGradientButton = this.addDrawableChild(ButtonWidget.builder(getRouteLineGradientText(), button -> {
            routeLineGradient = !routeLineGradient;
            button.setMessage(getRouteLineGradientText());
        }).dimensions(controlX, startY + rowHeight * 8, controlWidth, 20).build());

        this.routeLineAlphaField = new TextFieldWidget(this.textRenderer, controlX, startY + rowHeight * 9, controlWidth, 20,
            Text.translatable("field.emohelper.route_line_alpha"));
        this.routeLineAlphaField.setMaxLength(5);
        this.routeLineAlphaField.setText(String.format(java.util.Locale.ROOT, "%.2f", settings.routeLineAlpha()));
        this.addSelectableChild(this.routeLineAlphaField);

        this.routeLineBrightnessField = new TextFieldWidget(this.textRenderer, controlX, startY + rowHeight * 10, controlWidth, 20,
            Text.translatable("field.emohelper.route_line_brightness"));
        this.routeLineBrightnessField.setMaxLength(5);
        this.routeLineBrightnessField.setText(String.format(java.util.Locale.ROOT, "%.2f", settings.routeLineBrightness()));
        this.addSelectableChild(this.routeLineBrightnessField);

        this.renderModeButton = this.addDrawableChild(ButtonWidget.builder(getRenderModeText(), button -> {
            selectedRenderMode = selectedRenderMode.next();
            button.setMessage(getRenderModeText());
        }).dimensions(controlX, startY + rowHeight * 13, controlWidth, 20).build());

        refreshOrderedButtonsState();

        this.setInitialFocus(this.nameField);

        this.confirmButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.confirm"), button -> {
            renameGroup();
        }).dimensions(centerX - 90, startY + rowHeight * 14 + 8, 80, 20).build());

        this.cancelButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.cancel"), button -> {
            this.client.setScreen(parent);
        }).dimensions(centerX + 10, startY + rowHeight * 14 + 8, 80, 20).build());

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

    private void renameGroup() {
        if (ConfigManager.getCoordinateData().isGroupLocked(originalName)) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.emohelper.group_locked"), true);
            }
            this.client.setScreen(parent);
            return;
        }

        String newName = nameField.getText().trim();
        if (newName.isEmpty()) {
            newName = originalName;
        }

        CoordinateData data = ConfigManager.getCoordinateData();
        String settingsGroup = originalName;
        if (!newName.equals(originalName) && data.renameGroup(originalName, newName)) {
            settingsGroup = newName;
        }

        CoordinateData.GroupRenderSettings currentSettings = data.getGroupRenderSettings(
            settingsGroup,
            ConfigManager.getModConfig().shouldShowLabels(),
            ConfigManager.getModConfig().getRenderDistance());
        float renderDistance = parseAndClampDistance(distanceField.getText(), currentSettings.renderDistance());
        int currentPoint = parseAndClampCurrentPoint(currentPointField.getText(), currentSettings.currentOrderedPoint());
        float routeLineAlpha = parseAndClampRouteLineAlpha(routeLineAlphaField.getText(), currentSettings.routeLineAlpha());
        float routeLineBrightness = parseAndClampRouteLineBrightness(routeLineBrightnessField.getText(), currentSettings.routeLineBrightness());
        data.setGroupRenderSettings(
            settingsGroup,
            showLabels,
            renderDistance,
            selectedGroupType,
            selectedOrderedDisplayMode,
            loopRoute,
            crosshairGuideLine,
            currentPoint,
            routeLineEnabled,
            routeLineGradient,
            routeLineAlpha,
            routeLineBrightness);
        ConfigManager.getModConfig().setRenderMode(selectedRenderMode);
        ConfigManager.save();
        OrderedRouteManager.setPreferredGroup(settingsGroup);
        OrderedRouteManager.resetGroupToConfiguredStart(settingsGroup);
        this.client.setScreen(parent);
    }

    private Text getLabelToggleText() {
        return Text.translatable(showLabels
            ? "button.emohelper.group_labels_on"
            : "button.emohelper.group_labels_off");
    }

    private float parseAndClampDistance(String rawText, float fallback) {
        float value = fallback;
        if (rawText != null && !rawText.isBlank()) {
            try {
                value = Float.parseFloat(rawText.trim());
            } catch (NumberFormatException ignored) {
                value = fallback;
            }
        }
        return Math.max(0.0f, Math.min(512.0f, value));
    }

    private int parseAndClampCurrentPoint(String rawText, int fallback) {
        int value = fallback;
        if (rawText != null && !rawText.isBlank()) {
            try {
                value = Integer.parseInt(rawText.trim());
            } catch (NumberFormatException ignored) {
                value = fallback;
            }
        }
        return Math.max(1, value);
    }

    private float parseAndClampRouteLineAlpha(String rawText, float fallback) {
        float value = fallback;
        if (rawText != null && !rawText.isBlank()) {
            try {
                value = Float.parseFloat(rawText.trim());
            } catch (NumberFormatException ignored) {
                value = fallback;
            }
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private float parseAndClampRouteLineBrightness(String rawText, float fallback) {
        float value = fallback;
        if (rawText != null && !rawText.isBlank()) {
            try {
                value = Float.parseFloat(rawText.trim());
            } catch (NumberFormatException ignored) {
                value = fallback;
            }
        }
        return Math.max(0.1f, Math.min(2.0f, value));
    }

    private Text getGroupTypeText() {
        String modeKey = selectedGroupType == CoordinateData.GroupType.ORDERED
            ? "text.emohelper.group_type.ordered"
            : "text.emohelper.group_type.normal";
        return Text.translatable("button.emohelper.group_type", Text.translatable(modeKey));
    }

    private Text getOrderedDisplayModeText() {
        String modeKey = selectedOrderedDisplayMode == CoordinateData.OrderedDisplayMode.PROGRESSIVE
            ? "text.emohelper.ordered_display_mode.progressive"
            : "text.emohelper.ordered_display_mode.all";
        return Text.translatable("button.emohelper.ordered_display_mode", Text.translatable(modeKey));
    }

    private Text getLoopRouteText() {
        return Text.translatable(loopRoute
            ? "button.emohelper.loop_route_on"
            : "button.emohelper.loop_route_off");
    }

    private Text getCrosshairGuideLineText() {
        return Text.translatable(crosshairGuideLine
            ? "button.emohelper.crosshair_line_on"
            : "button.emohelper.crosshair_line_off");
    }

    private Text getRouteLineText() {
        return Text.translatable(routeLineEnabled
            ? "button.emohelper.route_line_on"
            : "button.emohelper.route_line_off");
    }

    private Text getRouteLineGradientText() {
        return Text.translatable(routeLineGradient
            ? "button.emohelper.route_line_gradient_on"
            : "button.emohelper.route_line_gradient_off");
    }

    private void refreshOrderedButtonsState() {
        boolean ordered = selectedGroupType == CoordinateData.GroupType.ORDERED;
        if (orderedDisplayModeButton != null) {
            orderedDisplayModeButton.active = ordered;
        }
        if (loopRouteButton != null) {
            loopRouteButton.active = ordered;
        }
        if (crosshairGuideLineButton != null) {
            crosshairGuideLineButton.active = ordered;
        }
        if (routeLineButton != null) {
            routeLineButton.active = ordered;
        }
        if (routeLineGradientButton != null) {
            routeLineGradientButton.active = ordered && routeLineEnabled;
        }
        if (routeLineAlphaField != null) {
            routeLineAlphaField.setEditable(ordered && routeLineEnabled);
        }
        if (routeLineBrightnessField != null) {
            routeLineBrightnessField.setEditable(ordered && routeLineEnabled);
        }
        if (currentPointField != null) {
            currentPointField.setEditable(ordered);
        }
    }

    private Text getRenderModeText() {
        String modeKey = switch (selectedRenderMode) {
            case MESH -> "text.emohelper.render_mode.mesh";
            case FULL_BLOCK -> "text.emohelper.render_mode.full";
            default -> "text.emohelper.render_mode.outline";
        };
        return Text.translatable("button.emohelper.render_mode", Text.translatable(modeKey));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (labelToggleButton != null) {
            labelToggleButton.setMessage(getLabelToggleText());
        }
        if (groupTypeButton != null) {
            groupTypeButton.setMessage(getGroupTypeText());
        }
        if (orderedDisplayModeButton != null) {
            orderedDisplayModeButton.setMessage(getOrderedDisplayModeText());
        }
        if (loopRouteButton != null) {
            loopRouteButton.setMessage(getLoopRouteText());
        }
        if (crosshairGuideLineButton != null) {
            crosshairGuideLineButton.setMessage(getCrosshairGuideLineText());
        }
        if (routeLineButton != null) {
            routeLineButton.setMessage(getRouteLineText());
        }
        if (routeLineGradientButton != null) {
            routeLineGradientButton.setMessage(getRouteLineGradientText());
        }
        if (renderModeButton != null) {
            renderModeButton.setMessage(getRenderModeText());
        }
        refreshOrderedButtonsState();
        applyScrollLayout();
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 25, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group_name"), labelX, toScreenY(startY + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group_show_labels"), labelX, toScreenY(startY + rowHeight + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group_render_distance"), labelX, toScreenY(startY + rowHeight * 2 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group_type"), labelX, toScreenY(startY + rowHeight * 3 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.ordered_display_mode"), labelX, toScreenY(startY + rowHeight * 4 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.loop_route"), labelX, toScreenY(startY + rowHeight * 5 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.crosshair_line"), labelX, toScreenY(startY + rowHeight * 6 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.route_line"), labelX, toScreenY(startY + rowHeight * 7 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.route_line_gradient"), labelX, toScreenY(startY + rowHeight * 8 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.route_line_alpha"), labelX, toScreenY(startY + rowHeight * 9 + 6), 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.route_line_brightness"), labelX, toScreenY(startY + rowHeight * 10 + 6), 0xAAAAAA);
        if (selectedGroupType == CoordinateData.GroupType.ORDERED) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.current_ordered_point"), labelX, toScreenY(startY + rowHeight * 12 + 6), 0xAAAAAA);
        }
        context.drawTextWithShadow(this.textRenderer, Text.translatable("field.emohelper.group_render_mode"), labelX, toScreenY(startY + rowHeight * 13 + 6), 0xAAAAAA);
        this.nameField.render(context, mouseX, mouseY, delta);
        this.distanceField.render(context, mouseX, mouseY, delta);
        if (this.routeLineAlphaField != null) {
            this.routeLineAlphaField.render(context, mouseX, mouseY, delta);
        }
        if (this.routeLineBrightnessField != null) {
            this.routeLineBrightnessField.render(context, mouseX, mouseY, delta);
        }
        if (this.currentPointField != null && selectedGroupType == CoordinateData.GroupType.ORDERED) {
            this.currentPointField.render(context, mouseX, mouseY, delta);
        }
    }

    public void close() {
        this.client.setScreen(parent);
    }

    private int toScreenY(int baseY) {
        return baseY - scrollOffset;
    }

    private int getMaxScroll() {
        int contentBottom = startY + rowHeight * 14 + 8 + 20 + 12;
        int viewportBottom = this.height - 10;
        return Math.max(0, contentBottom - viewportBottom);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    private void applyScrollLayout() {
        if (nameField != null) {
            nameField.setY(toScreenY(startY));
        }
        if (labelToggleButton != null) {
            labelToggleButton.setY(toScreenY(startY + rowHeight));
        }
        if (distanceField != null) {
            distanceField.setY(toScreenY(startY + rowHeight * 2));
        }
        if (groupTypeButton != null) {
            groupTypeButton.setY(toScreenY(startY + rowHeight * 3));
        }
        if (orderedDisplayModeButton != null) {
            orderedDisplayModeButton.setY(toScreenY(startY + rowHeight * 4));
        }
        if (loopRouteButton != null) {
            loopRouteButton.setY(toScreenY(startY + rowHeight * 5));
        }
        if (crosshairGuideLineButton != null) {
            crosshairGuideLineButton.setY(toScreenY(startY + rowHeight * 6));
        }
        if (routeLineButton != null) {
            routeLineButton.setY(toScreenY(startY + rowHeight * 7));
        }
        if (routeLineGradientButton != null) {
            routeLineGradientButton.setY(toScreenY(startY + rowHeight * 8));
        }
        if (routeLineAlphaField != null) {
            routeLineAlphaField.setY(toScreenY(startY + rowHeight * 9));
        }
        if (routeLineBrightnessField != null) {
            routeLineBrightnessField.setY(toScreenY(startY + rowHeight * 10));
        }
        if (currentPointField != null) {
            currentPointField.setY(toScreenY(startY + rowHeight * 12));
        }
        if (renderModeButton != null) {
            renderModeButton.setY(toScreenY(startY + rowHeight * 13));
        }
        if (confirmButton != null) {
            confirmButton.setY(toScreenY(startY + rowHeight * 14 + 8));
        }
        if (cancelButton != null) {
            cancelButton.setY(toScreenY(startY + rowHeight * 14 + 8));
        }
    }
}
