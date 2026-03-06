package com.Emo.emohelper.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class GroupSelectScreen extends Screen {
    private final Screen parent;
    private final CoordinateInputScreen target;
    private final List<String> groups;
    private int scrollOffset = 0;
    private static final int LIST_Y = 50;
    private static final int ENTRY_HEIGHT = 22;
    private static final int ENTRIES_PER_PAGE = 8;

    public GroupSelectScreen(Screen parent, CoordinateInputScreen target, List<String> groups) {
        super(Text.translatable("screen.emohelper.select_group"));
        this.parent = parent;
        this.target = target;
        this.groups = groups;
    }

    @Override
    protected void init() {
        int width = 180;
        int x = (this.width - width) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.emohelper.cancel"), button -> {
            this.client.setScreen(parent);
        }).dimensions(x, this.height - 40, width, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int width = 180;
        int x = (this.width - width) / 2;
        int y = LIST_Y;
        int totalEntries = groups.size();
        int startIndex = Math.max(0, Math.min(scrollOffset, Math.max(0, totalEntries - ENTRIES_PER_PAGE)));
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, totalEntries);

        for (int i = startIndex; i < endIndex; i++) {
            String group = groups.get(i);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            int bg = hovered ? 0xFF555555 : 0xFF3F3F3F;
            context.fill(x, y, x + width, y + ENTRY_HEIGHT, bg);
            context.drawTextWithShadow(this.textRenderer, Text.literal(group), x + 6, y + 6, 0xFFFFFF);
            y += ENTRY_HEIGHT;
        }

        if (totalEntries > ENTRIES_PER_PAGE) {
            int listHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;
            int scrollBarHeight = (listHeight * ENTRIES_PER_PAGE) / totalEntries;
            int scrollBarY = LIST_Y + (startIndex * listHeight) / totalEntries;
            context.fill(x + width - 4, scrollBarY, x + width - 2, scrollBarY + scrollBarHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int width = 180;
            int x = (this.width - width) / 2;
            int y = LIST_Y;
            int totalEntries = groups.size();
            int startIndex = Math.max(0, Math.min(scrollOffset, Math.max(0, totalEntries - ENTRIES_PER_PAGE)));
            int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, totalEntries);

            for (int i = startIndex; i < endIndex; i++) {
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
                if (hovered) {
                    String group = groups.get(i);
                    target.setGroupName(group);
                    this.client.setScreen(parent);
                    return true;
                }
                y += ENTRY_HEIGHT;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalEntries = groups.size();
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount,
            Math.max(0, totalEntries - ENTRIES_PER_PAGE)));
        return true;
    }

    public void close() {
        this.client.setScreen(parent);
    }
}