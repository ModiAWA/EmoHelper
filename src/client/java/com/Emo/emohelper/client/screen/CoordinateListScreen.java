package com.Emo.emohelper.client.screen;

import com.Emo.emohelper.config.ConfigManager;
import com.Emo.emohelper.config.CoordinateData;
import com.Emo.emohelper.model.CoordinatePoint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主配置屏幕
 */
public class CoordinateListScreen extends Screen {
    private Screen parent;
    private CoordinateData coordinateData;
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 30;
    private static final int ENTRIES_PER_PAGE = 6;
    private static final int LIST_Y = 60;
    private static final int LIST_BOTTOM_PADDING = 50;
    private static final int MIN_ENTRIES_PER_PAGE = 3;
    private static final int LIST_HEIGHT = ENTRIES_PER_PAGE * ENTRY_HEIGHT;
    private String selectedGroup = CoordinateData.DEFAULT_GROUP;
    private final Set<String> collapsedGroups = new HashSet<>();
    private final Map<String, GroupActionBounds> groupActions = new HashMap<>();
    private final Map<CoordinatePoint, PointActionBounds> pointActions = new HashMap<>();
    private List<ListEntry> lastEntries = new ArrayList<>();
    private String pendingDeleteGroup = null;
    private static final int GROUP_INDICATOR_X = 20;
    private static final int POINT_TEXT_X = 40;
    private long lastGroupClickTime = 0L;
    private String lastClickedGroup = null;
    private String draggingGroup = null;
    private int dragStartY = 0;
    private final Map<String, Integer> groupHeaderY = new HashMap<>();
    private CoordinatePoint draggingPoint = null;
    private boolean draggedPoint = false;
    private int dragStartX = 0;
    private int dragStartPointY = 0;
    private CoordinatePoint pendingOpenPoint = null;
    private String pendingOpenGroup = null;

    public CoordinateListScreen(Screen parent) {
        super(Text.translatable("screen.emohelper.coordinates_manager"));
        this.parent = parent;
        this.coordinateData = ConfigManager.getCoordinateData();
    }

    @Override
    protected void init() {
        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.back"), button -> {
            this.client.setScreen(parent);
        }).dimensions(10, this.height - 35, 100, 20).build());

        // 新增坐标按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.add_coordinate"), button -> {
            this.client.setScreen(new CoordinateInputScreen(this, null, coordinateData.getPointCount(), selectedGroup));
        }).dimensions(this.width - 220, this.height - 35, 100, 20).build());

        // 导入按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.import"), button -> {
            this.client.setScreen(new ImportExportScreen(this, true));
        }).dimensions(120, this.height - 35, 80, 20).build());

        // 导出全部按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.export_all"), button -> {
             this.client.setScreen(new ImportExportScreen(this, false));
         }).dimensions(210, this.height - 35, 80, 20).build());

        // 新增分组按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.group_add"), button -> {
             String newGroup = createUniqueGroupName(Text.translatable("text.emohelper.new_group_default").getString());
             coordinateData.addGroup(newGroup);
             selectedGroup = newGroup;
             ConfigManager.save();
        }).dimensions(this.width - 110, this.height - 35, 100, 20).build());

        // 显示名字开关
        this.addDrawableChild(ButtonWidget.builder(getLabelToggleText(), button -> {
            ConfigManager.getModConfig().setShowLabels(!ConfigManager.getModConfig().shouldShowLabels());
            ConfigManager.save();
            button.setMessage(getLabelToggleText());
        }).dimensions(10, 30, 140, 20).build());
    }

    private Text getLabelToggleText() {
        return Text.translatable(
            ConfigManager.getModConfig().shouldShowLabels()
                ? "button.emohelper.labels_on"
                : "button.emohelper.labels_off"
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 绘制标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // 绘制坐标列表
        renderCoordinateList(context, mouseX, mouseY);
    }

    private void renderCoordinateList(DrawContext context, int mouseX, int mouseY) {
        groupActions.clear();
        pointActions.clear();
        groupHeaderY.clear();
        lastEntries = buildEntries();
        int totalEntries = lastEntries.size();
        int listHeight = getListHeight(totalEntries);

        // 背景
        context.fill(10, LIST_Y - 5, this.width - 10, LIST_Y + listHeight + 5, 0xFF3F3F3F);
        // 边框
        context.fill(10, LIST_Y - 5, this.width - 10, LIST_Y - 4, 0xFFAAAAAA); // 上
        context.fill(10, LIST_Y + listHeight + 4, this.width - 10, LIST_Y + listHeight + 5, 0xFFAAAAAA); // 下
        context.fill(10, LIST_Y - 5, 11, LIST_Y + listHeight + 5, 0xFFAAAAAA); // 左
        context.fill(this.width - 11, LIST_Y - 5, this.width - 10, LIST_Y + listHeight + 5, 0xFFAAAAAA); // 右

        int y = LIST_Y;
        int entriesPerPage = getEntriesPerPage(totalEntries);
        int startIndex = Math.max(0, Math.min(scrollOffset, Math.max(0, totalEntries - entriesPerPage)));
        int endIndex = Math.min(startIndex + entriesPerPage, totalEntries);

        for (int i = startIndex; i < endIndex; i++) {
            ListEntry entry = lastEntries.get(i);
            boolean hovered = mouseX >= 15 && mouseX < this.width - 15 &&
                             mouseY >= y && mouseY < y + ENTRY_HEIGHT;

            if (entry.isGroup) {
                int enabledCount = coordinateData.getGroupEnabledCount(entry.groupName);
                int totalCount = coordinateData.getGroupTotalCount(entry.groupName);
                boolean allDisabled = totalCount > 0 && enabledCount == 0;
                int baseColor = entry.groupName.equals(selectedGroup) ? 0xFF4A4A6A : 0xFF2F2F2F;
                int bg = allDisabled ? 0xFF2A2A2A : baseColor;
                context.fill(15, y, this.width - 15, y + ENTRY_HEIGHT, bg);
                String indicator = collapsedGroups.contains(entry.groupName) ? "+" : "-";
                Text header = Text.translatable("text.emohelper.group_header", entry.groupName, enabledCount, totalCount);
                int indicatorWidth = this.textRenderer.getWidth(indicator);

                int textColor = allDisabled ? 0xFF888888 : 0xFFFFFF;
                context.drawTextWithShadow(this.textRenderer, indicator, GROUP_INDICATOR_X, y + 6, textColor);
                context.drawTextWithShadow(this.textRenderer, header, GROUP_INDICATOR_X + indicatorWidth + 6, y + 6, textColor);
                groupHeaderY.put(entry.groupName, y);

                GroupActionBounds bounds = drawGroupActions(context, entry.groupName, y, indicatorWidth);
                groupActions.put(entry.groupName, bounds);
            } else if (entry.point != null) {
                int bg = hovered ? 0xFF555555 : 0xFF3F3F3F;
                if (entry.point == draggingPoint) {
                    bg = 0xFF4E4E75;
                }
                context.fill(15, y, this.width - 15, y + ENTRY_HEIGHT, bg);

                Text line = Text.translatable(
                    "text.emohelper.coordinate_entry_simple",
                    entry.point.getLabel(),
                    entry.point.getX(),
                    entry.point.getY(),
                    entry.point.getZ()
                );
                int textColor = entry.point.isEnabled() ? 0xFFFFFF : 0xFF9C9C9C;
                context.drawTextWithShadow(this.textRenderer, line, POINT_TEXT_X, y + 6, textColor);

                PointActionBounds bounds = drawPointActions(context, y);
                pointActions.put(entry.point, bounds);

                // Disabled points are shown in gray text.
             }
             y += ENTRY_HEIGHT;
         }

        if (totalEntries > entriesPerPage) {
            int scrollBarHeight = (listHeight * entriesPerPage) / totalEntries;
            int scrollBarY = LIST_Y + (startIndex * listHeight) / totalEntries;
            context.fill(this.width - 8, scrollBarY, this.width - 4, scrollBarY + scrollBarHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalEntries = buildEntries().size();
        int entriesPerPage = getEntriesPerPage(totalEntries);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount,
            Math.max(0, totalEntries - entriesPerPage)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = LIST_Y;
            int totalEntries = lastEntries.size();
            int entriesPerPage = getEntriesPerPage(totalEntries);
            int startIndex = Math.max(0, Math.min(scrollOffset, Math.max(0, totalEntries - entriesPerPage)));
            int endIndex = Math.min(startIndex + entriesPerPage, totalEntries);

            for (int i = startIndex; i < endIndex; i++) {
                boolean hovered = mouseX >= 15 && mouseX < this.width - 15 && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
                if (hovered) {
                    ListEntry entry = lastEntries.get(i);
                    if (entry.isGroup) {
                        GroupActionBounds bounds = groupActions.get(entry.groupName);
                        if (bounds != null && bounds.contains(mouseX, mouseY)) {
                            if (bounds.isIndicator(mouseX)) {
                                toggleGroupCollapse(entry.groupName);
                                return true;
                            }
                            if (bounds.isAdd(mouseX)) {
                                pendingDeleteGroup = null;
                                this.client.setScreen(new CoordinateInputScreen(this, null, coordinateData.getPointCount(), entry.groupName));
                                return true;
                            }
                            if (bounds.isToggle(mouseX)) {
                                pendingDeleteGroup = null;
                                boolean enable = !coordinateData.isGroupFullyEnabled(entry.groupName);
                                coordinateData.setGroupEnabled(entry.groupName, enable);
                                ConfigManager.save();
                                return true;
                            }
                            if (bounds.isDelete(mouseX)) {
                                if (entry.groupName.equals(pendingDeleteGroup)) {
                                    coordinateData.removeGroup(entry.groupName);
                                    if (entry.groupName.equals(selectedGroup)) {
                                        selectedGroup = getFallbackGroup();
                                    }
                                    ConfigManager.save();
                                    pendingDeleteGroup = null;
                                } else {
                                    pendingDeleteGroup = entry.groupName;
                                }
                                return true;
                            }
                            if (bounds.isExport(mouseX)) {
                                pendingDeleteGroup = null;
                                this.client.setScreen(new ImportExportScreen(this, false, entry.groupName));
                                return true;
                            }
                        }
                        pendingDeleteGroup = null;
                        selectedGroup = entry.groupName;
                        if (isDoubleClick(entry.groupName)) {
                            this.client.setScreen(new RenameGroupScreen(this, entry.groupName));
                            return true;
                        }
                        recordGroupClick(entry.groupName);
                        draggingGroup = entry.groupName;
                        dragStartY = (int) mouseY;
                        return true;
                    }

                    if (entry.point != null) {
                        PointActionBounds bounds = pointActions.get(entry.point);
                        if (bounds != null && bounds.contains(mouseX)) {
                            if (bounds.isToggle(mouseX)) {
                                entry.point.setEnabled(!entry.point.isEnabled());
                                ConfigManager.save();
                                return true;
                            }
                            if (bounds.isDelete(mouseX)) {
                                int pointIndex = coordinateData.indexOf(entry.point);
                                if (pointIndex >= 0) {
                                    coordinateData.removePoint(pointIndex);
                                    ConfigManager.save();
                                }
                                return true;
                            }
                        }
                        pendingOpenPoint = entry.point;
                        pendingOpenGroup = entry.groupName;
                        draggingPoint = entry.point;
                        draggedPoint = false;
                        dragStartX = (int) mouseX;
                        dragStartPointY = (int) mouseY;
                        return true;
                    }
                }
                y += ENTRY_HEIGHT;
            }

            int listBottom = LIST_Y + getListHeight(totalEntries);
            boolean insideList = mouseX >= 10 && mouseX <= this.width - 10 && mouseY >= LIST_Y && mouseY <= listBottom;
            if (insideList) {
                // Clicked empty list area: clear selection.
                selectedGroup = getFallbackGroup();
                pendingDeleteGroup = null;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String getFallbackGroup() {
        List<String> groups = coordinateData.getGroups();
        if (!groups.isEmpty()) {
            return groups.get(0);
        }
        return CoordinateData.DEFAULT_GROUP;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingGroup != null) {
            String target = resolveGroupAtY((int) mouseY);
            if (target != null && !target.equals(draggingGroup)) {
                int targetIndex = coordinateData.getGroups().indexOf(target);
                coordinateData.moveGroup(draggingGroup, targetIndex);
                ConfigManager.save();
            }
            return true;
        }
        if (button == 0 && draggingPoint != null) {
            if (Math.abs(mouseX - dragStartX) > 3 || Math.abs(mouseY - dragStartPointY) > 3) {
                draggedPoint = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (draggingPoint != null) {
                if (draggedPoint) {
                    String targetGroup = resolveGroupAtY((int) mouseY);
                    if (targetGroup != null) {
                        draggingPoint.setGroupName(targetGroup);
                        ConfigManager.save();
                    }
                } else if (pendingOpenPoint != null) {
                    int pointIndex = coordinateData.indexOf(pendingOpenPoint);
                    if (pointIndex >= 0) {
                        this.client.setScreen(new CoordinateInputScreen(this, pendingOpenPoint, pointIndex, pendingOpenGroup));
                    }
                }
                pendingOpenPoint = null;
                pendingOpenGroup = null;
                draggingPoint = null;
                draggedPoint = false;
            }
            draggingGroup = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private List<ListEntry> buildEntries() {
        List<ListEntry> entries = new ArrayList<>();
        for (String group : coordinateData.getGroups()) {
            entries.add(ListEntry.group(group));
            if (!collapsedGroups.contains(group)) {
                List<CoordinatePoint> points = coordinateData.getPointsByGroup(group);
                points.sort((a, b) -> {
                    if (a.isEnabled() != b.isEnabled()) {
                        return a.isEnabled() ? -1 : 1;
                    }
                    return a.getLabel().compareToIgnoreCase(b.getLabel());
                });
                for (CoordinatePoint point : points) {
                    entries.add(ListEntry.point(group, point));
                }
            }
        }
        return entries;
    }

    private void toggleGroupCollapse(String groupName) {
        if (collapsedGroups.contains(groupName)) {
            collapsedGroups.remove(groupName);
        } else {
            collapsedGroups.add(groupName);
        }
        selectedGroup = groupName;
    }

    private String resolveGroupAtY(int y) {
        for (Map.Entry<String, Integer> entry : groupHeaderY.entrySet()) {
            int headerY = entry.getValue();
            if (y >= headerY && y < headerY + ENTRY_HEIGHT) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void recordGroupClick(String groupName) {
        lastClickedGroup = groupName;
        lastGroupClickTime = System.currentTimeMillis();
    }

    private boolean isDoubleClick(String groupName) {
        if (!groupName.equals(lastClickedGroup)) {
            return false;
        }
        return System.currentTimeMillis() - lastGroupClickTime < 400;
    }

    private GroupActionBounds drawGroupActions(DrawContext context, String groupName, int y, int indicatorWidth) {
        int right = this.width - 20;
        int actionY = y + 6;

        Text addText = Text.translatable("button.emohelper.group_add_short");
        Text toggleText = Text.translatable("button.emohelper.group_toggle_short");
        Text deleteText = Text.translatable("button.emohelper.group_delete_short");
        Text exportText = Text.translatable("button.emohelper.group_export_short");

        int exportWidth = this.textRenderer.getWidth(exportText);
        int deleteWidth = this.textRenderer.getWidth(deleteText);
        int toggleWidth = this.textRenderer.getWidth(toggleText);
        int addWidth = this.textRenderer.getWidth(addText);

        int exportX = right - exportWidth;
        int deleteX = exportX - 8 - deleteWidth;
        int toggleX = deleteX - 8 - toggleWidth;
        int addX = toggleX - 8 - addWidth;

        context.drawTextWithShadow(this.textRenderer, addText, addX, actionY, 0xFFDDDDDD);
        context.drawTextWithShadow(this.textRenderer, toggleText, toggleX, actionY, 0xFFDDDDDD);
        int deleteColor = groupName.equals(pendingDeleteGroup) ? 0xFFFF4444 : 0xFFDDDDDD;
        context.drawTextWithShadow(this.textRenderer, deleteText, deleteX, actionY, deleteColor);
        context.drawTextWithShadow(this.textRenderer, exportText, exportX, actionY, 0xFFDDDDDD);

        return new GroupActionBounds(addX, toggleX, deleteX, exportX, y, ENTRY_HEIGHT,
            addWidth, toggleWidth, deleteWidth, exportWidth,
            GROUP_INDICATOR_X, indicatorWidth);
    }

    private PointActionBounds drawPointActions(DrawContext context, int y) {
        int right = this.width - 20;
        int actionY = y + 6;

        Text toggleText = Text.translatable("button.emohelper.point_toggle");
        Text deleteText = Text.translatable("button.emohelper.point_delete");

        int deleteWidth = this.textRenderer.getWidth(deleteText);
        int toggleWidth = this.textRenderer.getWidth(toggleText);

        int deleteX = right - deleteWidth;
        int toggleX = deleteX - 8 - toggleWidth;

        context.drawTextWithShadow(this.textRenderer, toggleText, toggleX, actionY, 0xFFDDDDDD);
        context.drawTextWithShadow(this.textRenderer, deleteText, deleteX, actionY, 0xFFDDDDDD);

        return new PointActionBounds(toggleX, deleteX, toggleWidth, deleteWidth, y, ENTRY_HEIGHT);
    }

    private String createUniqueGroupName(String baseName) {
        List<String> groups = coordinateData.getGroups();
        if (!groups.contains(baseName)) {
            return baseName;
        }
        int index = 2;
        while (groups.contains(baseName + " " + index)) {
            index++;
        }
        return baseName + " " + index;
    }

    private int getListHeight(int totalEntries) {
        int bottomButtonsTop = this.height - 35;
        int bottom = Math.min(bottomButtonsTop - 10, this.height - LIST_BOTTOM_PADDING);
        int maxHeight = Math.max(ENTRY_HEIGHT * MIN_ENTRIES_PER_PAGE, bottom - LIST_Y);
        int contentHeight = Math.max(ENTRY_HEIGHT, totalEntries * ENTRY_HEIGHT);
        return Math.min(maxHeight, contentHeight);
    }

    private int getEntriesPerPage(int totalEntries) {
        int height = getListHeight(totalEntries);
        return Math.max(MIN_ENTRIES_PER_PAGE, height / ENTRY_HEIGHT);
    }

    private static class ListEntry {
        private final boolean isGroup;
        private final String groupName;
        private final CoordinatePoint point;

        private ListEntry(boolean isGroup, String groupName, CoordinatePoint point) {
            this.isGroup = isGroup;
            this.groupName = groupName;
            this.point = point;
        }

        private static ListEntry group(String groupName) {
            return new ListEntry(true, groupName, null);
        }

        private static ListEntry point(String groupName, CoordinatePoint point) {
            return new ListEntry(false, groupName, point);
        }
    }

    private static class GroupActionBounds {
        private final int addX;
        private final int toggleX;
        private final int deleteX;
        private final int exportX;
        private final int y;
        private final int height;
        private final int addWidth;
        private final int toggleWidth;
        private final int deleteWidth;
        private final int exportWidth;
        private final int indicatorX;
        private final int indicatorWidth;

        private GroupActionBounds(int addX, int toggleX, int deleteX, int exportX, int y, int height,
                                  int addWidth, int toggleWidth, int deleteWidth, int exportWidth,
                                  int indicatorX, int indicatorWidth) {
            this.addX = addX;
            this.toggleX = toggleX;
            this.deleteX = deleteX;
            this.exportX = exportX;
            this.y = y;
            this.height = height;
            this.addWidth = addWidth;
            this.toggleWidth = toggleWidth;
            this.deleteWidth = deleteWidth;
            this.exportWidth = exportWidth;
            this.indicatorX = indicatorX;
            this.indicatorWidth = indicatorWidth;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseY >= y && mouseY < y + height;
        }

        private boolean isAdd(double mouseX) {
            return mouseX >= addX && mouseX <= addX + addWidth;
        }

        private boolean isToggle(double mouseX) {
            return mouseX >= toggleX && mouseX <= toggleX + toggleWidth;
        }

        private boolean isDelete(double mouseX) {
            return mouseX >= deleteX && mouseX <= deleteX + deleteWidth;
        }

        private boolean isExport(double mouseX) {
            return mouseX >= exportX && mouseX <= exportX + exportWidth;
        }

        private boolean isIndicator(double mouseX) {
            return mouseX >= indicatorX && mouseX <= indicatorX + indicatorWidth;
        }
    }

    private static class PointActionBounds {
        private final int toggleX;
        private final int deleteX;
        private final int toggleWidth;
        private final int deleteWidth;
        private final int y;
        private final int height;

        private PointActionBounds(int toggleX, int deleteX, int toggleWidth, int deleteWidth, int y, int height) {
            this.toggleX = toggleX;
            this.deleteX = deleteX;
            this.toggleWidth = toggleWidth;
            this.deleteWidth = deleteWidth;
            this.y = y;
            this.height = height;
        }

        private boolean contains(double mouseX) {
            return mouseX >= toggleX && mouseX <= deleteX + deleteWidth;
        }

        private boolean isToggle(double mouseX) {
            return mouseX >= toggleX && mouseX <= toggleX + toggleWidth;
        }

        private boolean isDelete(double mouseX) {
            return mouseX >= deleteX && mouseX <= deleteX + deleteWidth;
        }
    }
}
