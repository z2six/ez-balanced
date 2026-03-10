package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceItemGroupDefinition;
import net.z2six.ezbalance.balance.EzBalanceItemRule;

import java.util.ArrayList;
import java.util.List;

public class EzBalanceItemGroupScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 82;
    private static final int ROW_HEIGHT = 34;
    private static final int TRACK_SIZE = 4;
    private static final int TRACK_GAP = 4;
    private static final int ACTION_BUTTON_WIDTH = 62;

    private final Screen parent;
    private final EzBalanceConfig config;

    private boolean draggingScrollbar;
    private int scrollRow;

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
        if (isInsideList(mouseX, mouseY) || isInsideVerticalScrollbar(mouseX, mouseY)) {
            this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
        renderWidgets(graphics, mouseX, mouseY, partialTick);
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
        int x2 = x1 + TRACK_SIZE;
        int y1 = LIST_TOP;
        int y2 = LIST_TOP + getListHeight();
        graphics.fill(x1, y1, x2, y2, COLOR_BORDER);

        int totalRows = getGroupIds().size() + 1;
        int thumbHeight = Math.max(18, getListHeight() * getVisibleRows() / Math.max(getVisibleRows(), totalRows));
        int maxTravel = Math.max(0, getListHeight() - thumbHeight);
        int thumbY = y1 + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        graphics.fill(x1, thumbY, x2, thumbY + thumbHeight, COLOR_ACCENT);
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
            if (groupId.equals(rule.itemGroupId)) {
                rule.itemGroupId = "";
                rule.appliedItemGroupAttributes.clear();
            }
        }
        this.scrollRow = Math.clamp(this.scrollRow, 0, getMaxScrollRow());
    }

    private List<String> getGroupIds() {
        return new ArrayList<>(this.config.itemGroups.keySet());
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
}
