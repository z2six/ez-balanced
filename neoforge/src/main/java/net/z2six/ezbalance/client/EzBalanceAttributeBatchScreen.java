package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceItemRule;
import net.z2six.ezbalance.balance.EzBalanceRuleMutations;
import net.z2six.ezbalance.balance.EzBalanceRuntime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EzBalanceAttributeBatchScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 86;
    private static final int ROW_HEIGHT = 44;
    private static final int TRACK_WIDTH = 4;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final Set<String> targetItems;
    private final List<String> attributeIds;
    private final boolean allowLockedEdits;
    private final List<EditBox> valueBoxes = new ArrayList<>();

    private int scrollRow;
    private boolean draggingScrollbar;

    public EzBalanceAttributeBatchScreen(Screen parent, EzBalanceConfig config, Set<String> targetItems, List<String> attributeIds) {
        this(parent, config, targetItems, attributeIds, false);
    }

    public EzBalanceAttributeBatchScreen(Screen parent, EzBalanceConfig config, Set<String> targetItems, List<String> attributeIds, boolean allowLockedEdits) {
        super(Component.literal(allowLockedEdits ? "Edit Item Attributes" : "Change Attributes"));
        this.parent = parent;
        this.config = config;
        this.targetItems = new LinkedHashSet<>(targetItems);
        this.attributeIds = new ArrayList<>(attributeIds);
        this.allowLockedEdits = allowLockedEdits;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.valueBoxes.clear();
        for (int index = 0; index < this.attributeIds.size(); index++) {
            EditBox valueBox = new EditBox(this.font, 0, 0, 120, 20, Component.literal("Value"));
            this.valueBoxes.add(valueBox);
            this.addRenderableWidget(valueBox);
        }

        this.addRenderableWidget(customButton("Apply", 24, this.height - 28, 116, 20, button -> applyFilled()));
        this.addRenderableWidget(customButton("Back", this.width - 90, this.height - 28, 70, 20, button -> this.onClose()));
        updateFieldLayout();
    }

    private void updateFieldLayout() {
        int listLeft = 24;
        int fieldX = this.width - 176;
        int listBottom = this.height - 48;
        int visibleRows = getVisibleRows();

        for (int index = 0; index < this.valueBoxes.size(); index++) {
            EditBox valueBox = this.valueBoxes.get(index);
            int visibleIndex = index - this.scrollRow;
            boolean visible = visibleIndex >= 0 && visibleIndex < visibleRows;
            valueBox.visible = visible;
            valueBox.active = visible;
            if (visible) {
                int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
                valueBox.setX(fieldX);
                valueBox.setY(rowY + 12);
                valueBox.setWidth(120);
                valueBox.setHeight(20);
            } else {
                valueBox.setX(-2000);
                valueBox.setY(-2000);
            }
        }
    }

    private int getVisibleRows() {
        return Math.max(1, (this.height - LIST_TOP - 58) / ROW_HEIGHT);
    }

    private int getMaxScrollRow() {
        return Math.max(0, this.attributeIds.size() - getVisibleRows());
    }

    private void applyFilled() {
        List<String> editableItems = getEditableItems();
        EzBalanceRuntime.captureOriginalAttributes(this.config, editableItems);
        for (String itemId : editableItems) {
            for (int index = 0; index < this.attributeIds.size(); index++) {
                Double value = parseNullableDouble(this.valueBoxes.get(index).getValue());
                String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeIds.get(index));
                if (value != null) {
                    EzBalanceRuleMutations.setAttributeOverride(this.config, itemId, attributeId, value);
                }
            }
        }
    }

    private List<String> getEditableItems() {
        return this.targetItems.stream()
                .filter(itemId -> this.allowLockedEdits || !isLocked(itemId))
                .toList();
    }

    private boolean isLocked(String itemId) {
        EzBalanceItemRule rule = this.config.items.get(itemId);
        return rule != null && rule.locked;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideList(mouseX, mouseY)) {
            this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            updateFieldLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInsideScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
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

    private void updateScrollFromMouse(double mouseY) {
        int trackTop = LIST_TOP;
        int trackHeight = this.height - LIST_TOP - 58;
        if (this.attributeIds.size() <= getVisibleRows()) {
            this.scrollRow = 0;
            updateFieldLayout();
            return;
        }

        double ratio = (mouseY - trackTop) / Math.max(1.0, trackHeight);
        this.scrollRow = Math.clamp((int) Math.round(ratio * getMaxScrollRow()), 0, getMaxScrollRow());
        updateFieldLayout();
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= this.width - 32 && mouseY >= LIST_TOP && mouseY <= this.height - 48;
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int trackX = this.width - 32;
        int trackHeight = this.height - LIST_TOP - 58;
        return mouseX >= trackX && mouseX <= trackX + TRACK_WIDTH && mouseY >= LIST_TOP && mouseY <= LIST_TOP + trackHeight;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, this.title.getString(), 24, 24, true);
        drawLabel(graphics, "Selected items: " + this.targetItems.size(), 24, 40, false);
        drawLabel(graphics, "Editable items: " + getEditableItems().size(), 24, 54, false);
        drawLabel(graphics, "Only filled values are applied.", 24, 68, false);

        renderRows(graphics);
        renderScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics) {
        int listRight = this.width - 48;
        int visibleRows = getVisibleRows();

        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int attributeIndex = this.scrollRow + visibleIndex;
            if (attributeIndex >= this.attributeIds.size()) {
                break;
            }

            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            graphics.fill(24, rowY, listRight, rowY + ROW_HEIGHT - 4, visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            String normalized = EzBalanceRuntime.normalizeAttributeId(this.attributeIds.get(attributeIndex));
            drawLabel(graphics, normalized, 32, rowY + 18, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int trackX = this.width - 32;
        int trackHeight = this.height - LIST_TOP - 58;
        graphics.fill(trackX, LIST_TOP, trackX + TRACK_WIDTH, LIST_TOP + trackHeight, COLOR_BORDER);
        int thumbHeight = this.attributeIds.isEmpty()
                ? trackHeight
                : Math.max(18, trackHeight * getVisibleRows() / Math.max(getVisibleRows(), this.attributeIds.size()));
        int maxTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : (maxTravel * this.scrollRow / getMaxScrollRow()));
        graphics.fill(trackX, thumbY, trackX + TRACK_WIDTH, thumbY + thumbHeight, COLOR_ACCENT);
    }

    @Override
    public void onClose() {
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.refreshItems();
        }
        this.minecraft.setScreen(this.parent);
    }
}
