package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EzBalanceItemGroupConflictScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 78;
    private static final int ROW_HEIGHT = 42;
    private static final int TRACK_SIZE = 4;
    private static final int TRACK_GAP = 4;
    private static final int CONTENT_WIDTH = 1120;

    private final Screen parent;
    private final String itemGroupLabel;
    private final List<ConflictRow> rows = new ArrayList<>();

    private int scrollRow;
    private int scrollX;
    private boolean draggingVerticalScrollbar;
    private boolean draggingHorizontalScrollbar;

    public EzBalanceItemGroupConflictScreen(Screen parent, String itemGroupLabel, Map<String, List<String>> conflicts) {
        super(Component.literal("Item Group Conflicts"));
        this.parent = parent;
        this.itemGroupLabel = itemGroupLabel;
        conflicts.forEach((itemId, reasons) -> this.rows.add(new ConflictRow(itemId, List.copyOf(reasons))));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.addRenderableWidget(customButton("Back", this.width - 90, this.height - 28, 70, 20, button -> this.onClose()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideVerticalScrollbar(mouseX, mouseY)) {
            this.draggingVerticalScrollbar = true;
            updateVerticalScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && isInsideHorizontalScrollbar(mouseX, mouseY)) {
            this.draggingHorizontalScrollbar = true;
            updateHorizontalScrollFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingVerticalScrollbar) {
            updateVerticalScrollFromMouse(mouseY);
            return true;
        }
        if (this.draggingHorizontalScrollbar) {
            updateHorizontalScrollFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingVerticalScrollbar = false;
        this.draggingHorizontalScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideList(mouseX, mouseY)) {
            if (hasShiftDown() || hasControlDown()) {
                this.scrollX = Math.clamp(this.scrollX - (int) Math.signum(scrollY) * 24, 0, getMaxHorizontalScroll());
            } else {
                this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, this.title.getString(), 24, 24, true);
        drawLabel(graphics, "Item group: " + this.itemGroupLabel, 24, 40, false);
        drawLabel(graphics, "Conflicting items: " + this.rows.size(), 24, 54, false);
        renderRows(graphics, mouseX, mouseY);
        renderScrollbars(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int viewportLeft = 24;
        int viewportRight = getViewportRight();
        int visibleRows = getVisibleRows();
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int rowIndex = this.scrollRow + visibleIndex;
            if (rowIndex >= this.rows.size()) {
                break;
            }
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean hovered = mouseX >= viewportLeft && mouseX <= viewportRight && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 4;
            graphics.fill(viewportLeft, rowY, viewportRight, rowY + ROW_HEIGHT - 4, hovered ? 0xFF202020 : (visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT));
            ConflictRow row = this.rows.get(rowIndex);
            int contentX = viewportLeft + 8 - this.scrollX;
            drawLabel(graphics, row.itemId(), contentX, rowY + 8, false);
            drawLabel(graphics, String.join(" | ", row.reasons()), contentX + 12, rowY + 22, false);
        }
    }

    private void renderScrollbars(GuiGraphics graphics) {
        int viewportRight = getViewportRight();
        int trackHeight = getViewportBottom() - LIST_TOP;
        int verticalThumbHeight = this.rows.isEmpty()
                ? trackHeight
                : Math.max(18, trackHeight * getVisibleRows() / Math.max(getVisibleRows(), this.rows.size()));
        int verticalMaxTravel = Math.max(0, trackHeight - verticalThumbHeight);
        int verticalThumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : verticalMaxTravel * this.scrollRow / getMaxScrollRow());
        EzBalanceUi.drawVerticalScrollbar(graphics, viewportRight + TRACK_GAP, LIST_TOP, getViewportBottom(), TRACK_SIZE, verticalThumbY, verticalThumbHeight);

        int trackTop = getViewportBottom() + TRACK_GAP;
        int trackWidth = getViewportRight() - 24;
        int horizontalThumbWidth = Math.max(18, trackWidth * trackWidth / Math.max(trackWidth, CONTENT_WIDTH));
        int horizontalMaxTravel = Math.max(0, trackWidth - horizontalThumbWidth);
        int horizontalThumbX = 24 + (getMaxHorizontalScroll() == 0 ? 0 : horizontalMaxTravel * this.scrollX / getMaxHorizontalScroll());
        EzBalanceUi.drawHorizontalScrollbar(graphics, 24, getViewportRight(), trackTop, TRACK_SIZE, horizontalThumbX, horizontalThumbWidth);
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= getViewportRight() && mouseY >= LIST_TOP && mouseY <= getViewportBottom();
    }

    private boolean isInsideVerticalScrollbar(double mouseX, double mouseY) {
        int scrollbarX = getViewportRight() + TRACK_GAP;
        return mouseX >= scrollbarX && mouseX <= scrollbarX + TRACK_SIZE && mouseY >= LIST_TOP && mouseY <= getViewportBottom();
    }

    private boolean isInsideHorizontalScrollbar(double mouseX, double mouseY) {
        int scrollbarY = getViewportBottom() + TRACK_GAP;
        return mouseX >= 24 && mouseX <= getViewportRight() && mouseY >= scrollbarY && mouseY <= scrollbarY + TRACK_SIZE;
    }

    private void updateVerticalScrollFromMouse(double mouseY) {
        int trackHeight = getViewportBottom() - LIST_TOP;
        if (this.rows.size() <= getVisibleRows()) {
            this.scrollRow = 0;
            return;
        }
        double ratio = (mouseY - LIST_TOP) / Math.max(1.0D, trackHeight);
        this.scrollRow = Math.clamp((int) Math.round(ratio * getMaxScrollRow()), 0, getMaxScrollRow());
    }

    private void updateHorizontalScrollFromMouse(double mouseX) {
        int trackWidth = getViewportRight() - 24;
        if (getMaxHorizontalScroll() <= 0) {
            this.scrollX = 0;
            return;
        }
        double ratio = (mouseX - 24) / Math.max(1.0D, trackWidth);
        this.scrollX = Math.clamp((int) Math.round(ratio * getMaxHorizontalScroll()), 0, getMaxHorizontalScroll());
    }

    private int getVisibleRows() {
        return Math.max(1, (getViewportBottom() - LIST_TOP) / ROW_HEIGHT);
    }

    private int getMaxScrollRow() {
        return Math.max(0, this.rows.size() - getVisibleRows());
    }

    private int getMaxHorizontalScroll() {
        return Math.max(0, CONTENT_WIDTH - (getViewportRight() - 24));
    }

    private int getViewportRight() {
        return this.width - 24 - TRACK_SIZE - TRACK_GAP;
    }

    private int getViewportBottom() {
        return this.height - 54;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private record ConflictRow(String itemId, List<String> reasons) {
    }
}
