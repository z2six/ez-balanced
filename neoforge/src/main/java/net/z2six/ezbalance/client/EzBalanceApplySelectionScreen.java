package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EzBalanceApplySelectionScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 108;
    private static final int ROW_HEIGHT = 28;
    private static final int TRACK_WIDTH = 4;

    private final EzBalanceScreen parent;
    private final EzBalanceApplyMode mode;
    private final String selectedId;
    private final String selectedLabel;
    private final Set<String> targetItems;
    private final List<String> attributes = new ArrayList<>();
    private final Map<String, Boolean> selectedAttributes = new LinkedHashMap<>();
    private final boolean showEnchantOverrideToggle;

    private int scrollRow;
    private boolean draggingScrollbar;
    private boolean overrideEnchants = true;

    public EzBalanceApplySelectionScreen(
            EzBalanceScreen parent,
            EzBalanceApplyMode mode,
            String selectedId,
            String selectedLabel,
            Set<String> targetItems,
            Set<String> attributes,
            boolean showEnchantOverrideToggle
    ) {
        super(Component.literal(mode == EzBalanceApplyMode.RARITY ? "Apply Rarity" : "Apply Item Group"));
        this.parent = parent;
        this.mode = mode;
        this.selectedId = selectedId;
        this.selectedLabel = selectedLabel;
        this.targetItems = new LinkedHashSet<>(targetItems);
        this.attributes.addAll(attributes);
        this.showEnchantOverrideToggle = showEnchantOverrideToggle;
        for (String attributeId : this.attributes) {
            this.selectedAttributes.put(attributeId, true);
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.addRenderableWidget(customButton("Select all", 24, 56, 88, 20, button -> setAll(true)));
        this.addRenderableWidget(customButton("Deselect all", 118, 56, 96, 20, button -> setAll(false)));
        this.addRenderableWidget(customButton("Confirm", this.width - 176, this.height - 28, 74, 20, button -> confirm()));
        this.addRenderableWidget(customButton("Cancel", this.width - 94, this.height - 28, 70, 20, button -> this.onClose()));
    }

    private void setAll(boolean checked) {
        this.selectedAttributes.replaceAll((attributeId, ignored) -> checked);
    }

    private void confirm() {
        Set<String> attributes = new LinkedHashSet<>();
        this.selectedAttributes.forEach((attributeId, checked) -> {
            if (Boolean.TRUE.equals(checked)) {
                attributes.add(attributeId);
            }
        });
        if (this.mode == EzBalanceApplyMode.RARITY) {
            this.parent.applyRarityWithScope(this.selectedId, this.targetItems, attributes);
        } else {
            this.parent.applyItemGroupWithScope(this.selectedId, this.targetItems, attributes, this.overrideEnchants);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && isInsideList(mouseX, mouseY)) {
            int row = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
            int index = this.scrollRow + row;
            if (index >= 0 && index < this.attributes.size()) {
                String attributeId = this.attributes.get(index);
                this.selectedAttributes.put(attributeId, !Boolean.TRUE.equals(this.selectedAttributes.get(attributeId)));
                return true;
            }
        }
        if (button == 0 && this.showEnchantOverrideToggle && isInsideEnchantToggle(mouseX, mouseY)) {
            this.overrideEnchants = !this.overrideEnchants;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideList(mouseX, mouseY)) {
            this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, this.title.getString(), 24, 24, true);
        drawLabel(graphics, (this.mode == EzBalanceApplyMode.RARITY ? "Rarity: " : "Item group: ") + this.selectedLabel, 24, 40, false);
        drawLabel(graphics, "Target items: " + this.targetItems.size(), 24, 84, false);
        if (this.showEnchantOverrideToggle) {
            renderSimpleCheckbox(graphics, 24, 84, this.overrideEnchants);
            drawLabel(graphics, "Override enchants", 42, 86, false);
        }
        renderRows(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int listRight = this.width - 32;
        int visibleRows = getVisibleRows();
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int index = this.scrollRow + visibleIndex;
            if (index >= this.attributes.size()) {
                break;
            }
            String attributeId = this.attributes.get(index);
            int y = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean hovered = mouseX >= 24 && mouseX <= listRight && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2;
            graphics.fill(24, y, listRight, y + ROW_HEIGHT - 2, hovered ? (0x22111111 | COLOR_ACCENT) : (visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT));
            renderSimpleCheckbox(graphics, 32, y + 8, Boolean.TRUE.equals(this.selectedAttributes.get(attributeId)));
            graphics.drawString(this.font, trimToWidth(attributeId, this.width - 104), 52, y + 10, COLOR_TEXT, false);
        }
    }

    private void renderSimpleCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 12, y + 12, COLOR_BACKGROUND);
        graphics.fill(x, y, x + 12, y + 1, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x, y + 11, x + 12, y + 12, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + 12, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x + 11, y, x + 12, y + 12, checked ? COLOR_ACCENT : COLOR_BORDER);
        if (checked) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, COLOR_ACCENT);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int trackX = this.width - 24;
        int trackHeight = this.height - LIST_TOP - 44;
        int rowCount = Math.max(getVisibleRows(), this.attributes.size());
        int thumbHeight = Math.max(18, trackHeight * getVisibleRows() / rowCount);
        int maxTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        EzBalanceUi.drawVerticalScrollbar(graphics, trackX, LIST_TOP, LIST_TOP + trackHeight, TRACK_WIDTH, thumbY, thumbHeight);
    }

    private int getVisibleRows() {
        return Math.max(1, (this.height - LIST_TOP - 52) / ROW_HEIGHT);
    }

    private int getMaxScrollRow() {
        return Math.max(0, this.attributes.size() - getVisibleRows());
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= this.width - 32 && mouseY >= LIST_TOP && mouseY <= this.height - 44;
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int trackX = this.width - 24;
        return mouseX >= trackX && mouseX <= trackX + TRACK_WIDTH && mouseY >= LIST_TOP && mouseY <= this.height - 44;
    }

    private boolean isInsideEnchantToggle(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= 200 && mouseY >= 82 && mouseY <= 98;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScrollRow();
        if (maxScroll == 0) {
            this.scrollRow = 0;
            return;
        }
        double ratio = (mouseY - LIST_TOP) / Math.max(1.0D, this.height - LIST_TOP - 44);
        this.scrollRow = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
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
        this.minecraft.setScreen(this.parent);
    }
}
