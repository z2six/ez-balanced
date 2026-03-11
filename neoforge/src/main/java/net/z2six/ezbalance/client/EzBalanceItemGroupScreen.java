package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceItemGroupDefinition;
import net.z2six.ezbalance.balance.EzBalanceItemRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EzBalanceItemGroupScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 82;
    private static final int ROW_HEIGHT = 34;
    private static final int TRACK_SIZE = 4;
    private static final int TRACK_GAP = 4;
    private static final int ACTION_BUTTON_WIDTH = 62;
    private static final int TOOLTIP_WIDTH = 332;
    private static final int TOOLTIP_PADDING = 8;
    private static final int TOOLTIP_LINE_HEIGHT = 12;
    private static final int TOOLTIP_MAX_HEIGHT = 236;
    private static final int TOOLTIP_TRACK_SIZE = 4;

    private final Screen parent;
    private final EzBalanceConfig config;

    private boolean draggingScrollbar;
    private boolean draggingTooltipScrollbar;
    private int scrollRow;
    private int tooltipScrollLine;
    private String activeTooltipGroupId = "";
    private int tooltipAnchorX;
    private int tooltipAnchorY;

    public EzBalanceItemGroupScreen(Screen parent, EzBalanceConfig config) {
        super(Component.literal("Item Groups"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.addRenderableWidget(customButton("Back", this.width - 92, 28, 70, 20, button -> this.onClose()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideTooltipScrollbar(mouseX, mouseY)) {
            this.draggingTooltipScrollbar = true;
            updateTooltipScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && isInsideActiveTooltip(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && isInsideVerticalScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        if (button == 0) {
            RowHitbox hitbox = getRowHitbox(mouseX, mouseY);
            if (hitbox != null) {
                if (hitbox.plusRow()) {
                    this.minecraft.setScreen(new EzBalanceItemGroupEditScreen(this, this.config, ""));
                } else if (hitbox.deleteButton()) {
                    deleteItemGroup(hitbox.groupId());
                } else if (hitbox.editButton()) {
                    this.minecraft.setScreen(new EzBalanceItemGroupEditScreen(this, this.config, hitbox.groupId()));
                } else {
                    return false;
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideActiveTooltip(mouseX, mouseY)) {
            TooltipData tooltip = getActiveTooltipData();
            if (tooltip != null) {
                this.tooltipScrollLine = Math.clamp(this.tooltipScrollLine - (int) Math.signum(scrollY), 0, getTooltipMaxScrollLine(tooltip));
                return true;
            }
        }
        if (isInsideList(mouseX, mouseY) || isInsideVerticalScrollbar(mouseX, mouseY)) {
            this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingTooltipScrollbar) {
            updateTooltipScrollFromMouse(mouseY);
            return true;
        }
        if (this.draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingTooltipScrollbar = false;
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, "Item Groups", 24, 28, true);
        drawLabel(graphics, "Edit an item group or create a new one below.", 24, 46, false);

        renderRows(graphics, mouseX, mouseY);
        renderVerticalScrollbar(graphics);
        updateTooltipState(mouseX, mouseY);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderActiveTooltip(graphics, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> groupIds = getGroupIds();
        int visibleRows = getVisibleRows();
        int listX = getListX();
        int listRight = getListRight();
        RowHitbox hovered = getRowHitbox(mouseX, mouseY);

        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int rowIndex = this.scrollRow + visibleIndex;
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean rowHovered = hovered != null && hovered.rowIndex() == rowIndex;
            int background = rowHovered ? (0x22111111 | COLOR_ACCENT) : (visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            graphics.fill(listX, rowY, listRight, rowY + ROW_HEIGHT - 4, background);
            if (rowIndex < groupIds.size()) {
                String groupId = groupIds.get(rowIndex);
                EzBalanceItemGroupDefinition group = this.config.itemGroups.get(groupId);
                String label = group == null || group.name.isBlank() || group.name.equals(groupId)
                        ? groupId
                        : group.name + " (" + groupId + ")";
                drawTrimmed(graphics, label, listX + 12, rowY + 7, listRight - listX - ACTION_BUTTON_WIDTH * 2 - 32, COLOR_TEXT);
                drawTrimmed(
                        graphics,
                        (group == null ? 0 : group.attributeValues.size()) + " attrs",
                        listX + 12,
                        rowY + 19,
                        listRight - listX - ACTION_BUTTON_WIDTH * 2 - 32,
                        COLOR_MUTED
                );

                int deleteX = listRight - ACTION_BUTTON_WIDTH * 2 - 16;
                int editX = listRight - ACTION_BUTTON_WIDTH - 10;
                boolean deleteHovered = mouseX >= deleteX && mouseX <= deleteX + ACTION_BUTTON_WIDTH && mouseY >= rowY + 7 && mouseY <= rowY + 27;
                boolean editHovered = mouseX >= editX && mouseX <= editX + ACTION_BUTTON_WIDTH && mouseY >= rowY + 7 && mouseY <= rowY + 27;
                drawInlineButton(graphics, deleteX, rowY + 7, ACTION_BUTTON_WIDTH, 20, "Delete", false, deleteHovered);
                drawInlineButton(graphics, editX, rowY + 7, ACTION_BUTTON_WIDTH, 20, "Edit", false, editHovered);
            } else if (rowIndex == groupIds.size()) {
                int plusX = listX + 10;
                int plusWidth = listRight - listX - 20;
                boolean plusHovered = mouseX >= plusX && mouseX <= plusX + plusWidth && mouseY >= rowY + 7 && mouseY <= rowY + 27;
                drawInlineButton(graphics, plusX, rowY + 7, plusWidth, 20, "+", true, plusHovered);
            }
        }
    }

    private void renderVerticalScrollbar(GuiGraphics graphics) {
        int x1 = getListRight() + TRACK_GAP;
        int totalRows = getGroupIds().size() + 1;
        int thumbHeight = Math.max(18, getListHeight() * getVisibleRows() / Math.max(getVisibleRows(), totalRows));
        int maxTravel = Math.max(0, getListHeight() - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        EzBalanceUi.drawVerticalScrollbar(graphics, x1, LIST_TOP, LIST_TOP + getListHeight(), TRACK_SIZE, thumbY, thumbHeight);
    }

    private int getListX() {
        return 24;
    }

    private int getListWidth() {
        return Math.max(180, this.width - 48 - TRACK_SIZE - TRACK_GAP);
    }

    private int getListRight() {
        return getListX() + getListWidth();
    }

    private int getListHeight() {
        return Math.max(40, this.height - LIST_TOP - 50);
    }

    private int getVisibleRows() {
        return Math.max(1, getListHeight() / ROW_HEIGHT);
    }

    private int getMaxScrollRow() {
        return Math.max(0, getGroupIds().size() + 1 - getVisibleRows());
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= getListX() && mouseX <= getListRight() && mouseY >= LIST_TOP && mouseY <= LIST_TOP + getListHeight();
    }

    private boolean isInsideVerticalScrollbar(double mouseX, double mouseY) {
        int x1 = getListRight() + TRACK_GAP;
        return mouseX >= x1 && mouseX <= x1 + TRACK_SIZE && mouseY >= LIST_TOP && mouseY <= LIST_TOP + getListHeight();
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScrollRow();
        if (maxScroll == 0) {
            this.scrollRow = 0;
            return;
        }
        double ratio = (mouseY - LIST_TOP) / Math.max(1.0D, getListHeight());
        this.scrollRow = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private RowHitbox getRowHitbox(double mouseX, double mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return null;
        }

        List<String> groupIds = getGroupIds();
        int visibleIndex = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        int rowIndex = this.scrollRow + visibleIndex;
        if (rowIndex > groupIds.size()) {
            return null;
        }
        if (rowIndex == groupIds.size()) {
            return new RowHitbox("", rowIndex, true, false, false);
        }

        int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
        int listRight = getListRight();
        int deleteX = listRight - ACTION_BUTTON_WIDTH * 2 - 16;
        int editX = listRight - ACTION_BUTTON_WIDTH - 10;
        boolean deleteButton = mouseX >= deleteX
                && mouseX <= deleteX + ACTION_BUTTON_WIDTH
                && mouseY >= rowY + 7
                && mouseY <= rowY + 27;
        boolean editButton = mouseX >= editX
                && mouseX <= editX + ACTION_BUTTON_WIDTH
                && mouseY >= rowY + 7
                && mouseY <= rowY + 27;
        return new RowHitbox(groupIds.get(rowIndex), rowIndex, false, deleteButton, editButton);
    }

    private void deleteItemGroup(String groupId) {
        if (groupId.isBlank()) {
            return;
        }

        this.config.itemGroups.remove(groupId);
        for (EzBalanceItemRule rule : this.config.items.values()) {
            rule.itemGroupIds.remove(groupId);
            rule.appliedItemGroupAttributesByGroup.remove(groupId);
            rule.itemGroupEnchantRuleIds.remove(groupId);
        }
        this.scrollRow = Math.clamp(this.scrollRow, 0, getMaxScrollRow());
        persistChanges();
    }

    void persistChanges() {
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.persistWorkingConfig();
        } else {
            EzBalanceClientPersistence.persist(this.config);
        }
    }

    private List<String> getGroupIds() {
        return new ArrayList<>(this.config.itemGroups.keySet());
    }

    private void updateTooltipState(int mouseX, int mouseY) {
        RowHitbox hovered = getRowHitbox(mouseX, mouseY);
        if (hovered != null && !hovered.plusRow() && !hovered.deleteButton() && !hovered.editButton()) {
            if (!hovered.groupId().equals(this.activeTooltipGroupId)) {
                this.tooltipScrollLine = 0;
            }
            this.activeTooltipGroupId = hovered.groupId();
            this.tooltipAnchorX = mouseX;
            this.tooltipAnchorY = mouseY;
            return;
        }
        if (this.draggingTooltipScrollbar || isInsideActiveTooltip(mouseX, mouseY)) {
            return;
        }
        this.activeTooltipGroupId = "";
        this.tooltipScrollLine = 0;
    }

    private void renderActiveTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        TooltipData tooltip = getActiveTooltipData();
        if (tooltip == null) {
            return;
        }
        TooltipBounds bounds = tooltip.bounds();
        drawPanel(graphics, bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(), true);
        int x = bounds.x1() + TOOLTIP_PADDING;
        int y = bounds.y1() + TOOLTIP_PADDING;
        int visibleLineCount = getTooltipVisibleLineCount(bounds);
        for (int index = 0; index < visibleLineCount; index++) {
            int lineIndex = this.tooltipScrollLine + index;
            if (lineIndex >= tooltip.lines().size()) {
                break;
            }
            TooltipLine line = tooltip.lines().get(lineIndex);
            graphics.drawString(this.font, line.text(), x, y + index * TOOLTIP_LINE_HEIGHT, line.color(), false);
        }
        if (tooltip.lines().size() > visibleLineCount) {
            int thumbHeight = Math.max(18, getTooltipTrackHeight(bounds) * visibleLineCount / Math.max(visibleLineCount, tooltip.lines().size()));
            int maxTravel = Math.max(0, getTooltipTrackHeight(bounds) - thumbHeight);
            int thumbY = bounds.y1() + TOOLTIP_PADDING + (getTooltipMaxScrollLine(tooltip) == 0 ? 0 : maxTravel * this.tooltipScrollLine / getTooltipMaxScrollLine(tooltip));
            EzBalanceUi.drawVerticalScrollbar(
                    graphics,
                    bounds.x2() - TOOLTIP_PADDING - TOOLTIP_TRACK_SIZE,
                    bounds.y1() + TOOLTIP_PADDING,
                    bounds.y2() - TOOLTIP_PADDING,
                    TOOLTIP_TRACK_SIZE,
                    thumbY,
                    thumbHeight
            );
        }
    }

    private TooltipData getActiveTooltipData() {
        if (this.activeTooltipGroupId.isBlank()) {
            return null;
        }
        EzBalanceItemGroupDefinition group = this.config.itemGroups.get(this.activeTooltipGroupId);
        if (group == null) {
            return null;
        }
        List<TooltipLine> lines = buildTooltipLines(group);
        TooltipBounds bounds = getTooltipBounds(lines.size());
        int maxScroll = Math.max(0, lines.size() - getTooltipVisibleLineCount(bounds));
        this.tooltipScrollLine = Math.clamp(this.tooltipScrollLine, 0, maxScroll);
        return new TooltipData(lines, bounds);
    }

    private List<TooltipLine> buildTooltipLines(EzBalanceItemGroupDefinition group) {
        List<TooltipLine> lines = new ArrayList<>();
        String title = group.name == null || group.name.isBlank() ? group.id : group.name + " (" + group.id + ")";
        addTooltipWrapped(lines, title, COLOR_ACCENT);

        if (!group.attributeValues.isEmpty()) {
            addTooltipBlank(lines);
            addTooltipWrapped(lines, "Attributes", COLOR_ACCENT);
            for (Map.Entry<String, Double> entry : group.attributeValues.entrySet()) {
                addTooltipWrapped(lines, entry.getKey() + ": " + formatDouble(entry.getValue()), COLOR_TEXT);
            }
        }

        if (!group.allowedEnchantments.isEmpty()) {
            addTooltipBlank(lines);
            addTooltipWrapped(lines, "Enabled enchants", COLOR_ACCENT);
            for (String enchantmentId : group.allowedEnchantments) {
                addTooltipWrapped(lines, enchantmentId, COLOR_TEXT);
            }
        }

        if (group.forceDisabledEnchants) {
            addTooltipBlank(lines);
            addTooltipWrapped(lines, "Disabled enchants", COLOR_ACCENT);
            addTooltipWrapped(lines, "All other default-compatible enchants", COLOR_TEXT);
        }

        if (lines.size() == 1) {
            addTooltipBlank(lines);
            addTooltipWrapped(lines, "No group changes.", COLOR_MUTED);
        }
        return lines;
    }

    private void addTooltipWrapped(List<TooltipLine> lines, String text, int color) {
        String value = text == null ? "" : text;
        int maxWidth = TOOLTIP_WIDTH - TOOLTIP_PADDING * 2 - TOOLTIP_TRACK_SIZE - TRACK_GAP;
        if (value.isBlank()) {
            lines.add(new TooltipLine("", color));
            return;
        }
        String remaining = value;
        while (!remaining.isEmpty()) {
            String part = this.font.plainSubstrByWidth(remaining, maxWidth);
            if (part.isEmpty()) {
                break;
            }
            lines.add(new TooltipLine(part, color));
            remaining = remaining.substring(part.length());
        }
    }

    private void addTooltipBlank(List<TooltipLine> lines) {
        lines.add(new TooltipLine("", COLOR_TEXT));
    }

    private TooltipBounds getTooltipBounds(int lineCount) {
        int contentHeight = TOOLTIP_PADDING * 2 + Math.max(1, lineCount) * TOOLTIP_LINE_HEIGHT;
        int height = Math.min(TOOLTIP_MAX_HEIGHT, Math.max(44, contentHeight));
        int x = this.tooltipAnchorX + 16;
        if (x + TOOLTIP_WIDTH > this.width - 16) {
            x = this.tooltipAnchorX - TOOLTIP_WIDTH - 16;
        }
        x = Math.max(20, Math.min(x, this.width - TOOLTIP_WIDTH - 20));
        int y = Math.max(20, Math.min(this.tooltipAnchorY - 8, this.height - height - 40));
        return new TooltipBounds(x, y, x + TOOLTIP_WIDTH, y + height);
    }

    private int getTooltipVisibleLineCount(TooltipBounds bounds) {
        return Math.max(1, (bounds.y2() - bounds.y1() - TOOLTIP_PADDING * 2) / TOOLTIP_LINE_HEIGHT);
    }

    private int getTooltipTrackHeight(TooltipBounds bounds) {
        return Math.max(1, bounds.y2() - bounds.y1() - TOOLTIP_PADDING * 2);
    }

    private int getTooltipMaxScrollLine(TooltipData tooltip) {
        return Math.max(0, tooltip.lines().size() - getTooltipVisibleLineCount(tooltip.bounds()));
    }

    private boolean isInsideActiveTooltip(double mouseX, double mouseY) {
        TooltipData tooltip = getActiveTooltipData();
        if (tooltip == null) {
            return false;
        }
        TooltipBounds bounds = tooltip.bounds();
        return mouseX >= bounds.x1() && mouseX <= bounds.x2() && mouseY >= bounds.y1() && mouseY <= bounds.y2();
    }

    private boolean isInsideTooltipScrollbar(double mouseX, double mouseY) {
        TooltipData tooltip = getActiveTooltipData();
        if (tooltip == null || getTooltipMaxScrollLine(tooltip) == 0) {
            return false;
        }
        TooltipBounds bounds = tooltip.bounds();
        int x = bounds.x2() - TOOLTIP_PADDING - TOOLTIP_TRACK_SIZE;
        return mouseX >= x
                && mouseX <= x + TOOLTIP_TRACK_SIZE
                && mouseY >= bounds.y1() + TOOLTIP_PADDING
                && mouseY <= bounds.y2() - TOOLTIP_PADDING;
    }

    private void updateTooltipScrollFromMouse(double mouseY) {
        TooltipData tooltip = getActiveTooltipData();
        if (tooltip == null) {
            this.tooltipScrollLine = 0;
            return;
        }
        int maxScroll = getTooltipMaxScrollLine(tooltip);
        if (maxScroll == 0) {
            this.tooltipScrollLine = 0;
            return;
        }
        TooltipBounds bounds = tooltip.bounds();
        double ratio = (mouseY - (bounds.y1() + TOOLTIP_PADDING)) / Math.max(1.0D, getTooltipTrackHeight(bounds));
        this.tooltipScrollLine = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private String formatDouble(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private void drawTrimmed(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(this.font, trimToWidth(text, width), x, y, color, false);
    }

    private String trimToWidth(String text, int width) {
        if (text == null || this.font.width(text) <= width) {
            return text == null ? "" : text;
        }

        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    @Override
    public void onClose() {
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.refreshItems();
        }
        this.minecraft.setScreen(this.parent);
    }

    private record RowHitbox(String groupId, int rowIndex, boolean plusRow, boolean deleteButton, boolean editButton) {
    }

    private record TooltipLine(String text, int color) {
    }

    private record TooltipBounds(int x1, int y1, int x2, int y2) {
    }

    private record TooltipData(List<TooltipLine> lines, TooltipBounds bounds) {
    }
}
