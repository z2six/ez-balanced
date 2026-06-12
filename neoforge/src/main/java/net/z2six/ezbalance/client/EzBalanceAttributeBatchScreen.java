package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceAttributeValue;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceItemRule;
import net.z2six.ezbalance.balance.EzBalanceRuleMutations;
import net.z2six.ezbalance.balance.EzBalanceRuntime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EzBalanceAttributeBatchScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 92;
    private static final int ROW_HEIGHT = 46;
    private static final int TRACK_WIDTH = 4;
    private static final int ATTRIBUTE_BOX_WIDTH = 520;
    private static final int VALUE_BOX_WIDTH = 120;
    private static final int DELETE_BUTTON_WIDTH = 76;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final Set<String> targetItems;
    private final boolean allowLockedEdits;
    private final List<RowSeed> pendingRows = new ArrayList<>();
    private final List<EditBox> attributeBoxes = new ArrayList<>();
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
        this.allowLockedEdits = allowLockedEdits;
        EzBalanceRuntime.captureOriginalAttributes(this.config, this.targetItems);
        LinkedHashSet<String> unique = new LinkedHashSet<>(attributeIds);
        unique.forEach(attributeId -> addRow(attributeId, ""));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        if (this.attributeBoxes.isEmpty() && !this.pendingRows.isEmpty()) {
            List<RowSeed> seeds = List.copyOf(this.pendingRows);
            this.pendingRows.clear();
            seeds.forEach(seed -> createRow(seed.attributeId(), seed.value()));
        }
        for (EditBox attributeBox : this.attributeBoxes) {
            this.addRenderableWidget(attributeBox);
        }
        for (EditBox valueBox : this.valueBoxes) {
            this.addRenderableWidget(valueBox);
        }
        this.addRenderableWidget(customButton("Apply", 24, this.height - 28, 116, 20, button -> applyFilled()));
        this.addRenderableWidget(customButton("Back", this.width - 90, this.height - 28, 70, 20, button -> this.onClose()));
        updateFieldLayout();
    }

    private void addRow(String attributeId, String value) {
        if (this.font == null) {
            this.pendingRows.add(new RowSeed(attributeId, value));
            return;
        }
        createRow(attributeId, value);
    }

    private void createRow(String attributeId, String value) {
        EditBox attributeBox = new EditBox(this.font, 0, 0, ATTRIBUTE_BOX_WIDTH, 20, Component.literal("Attribute id"));
        attributeBox.setMaxLength(256);
        attributeBox.setValue(attributeId == null ? "" : attributeId);
        this.attributeBoxes.add(attributeBox);

        EditBox valueBox = new EditBox(this.font, 0, 0, VALUE_BOX_WIDTH, 20, Component.literal("Value"));
        valueBox.setMaxLength(32);
        valueBox.setValue(value == null ? "" : value);
        this.valueBoxes.add(valueBox);
    }

    private void removeRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= this.attributeBoxes.size()) {
            return;
        }
        EditBox attributeBox = this.attributeBoxes.remove(rowIndex);
        EditBox valueBox = this.valueBoxes.remove(rowIndex);
        removeWidget(attributeBox);
        removeWidget(valueBox);
        this.scrollRow = Math.clamp(this.scrollRow, 0, getMaxScrollRow());
        updateFieldLayout();
    }

    private void updateFieldLayout() {
        int visibleRows = getVisibleRows();
        int attributeX = 24;
        int valueX = attributeX + ATTRIBUTE_BOX_WIDTH + 12;
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            EditBox attributeBox = this.attributeBoxes.get(index);
            EditBox valueBox = this.valueBoxes.get(index);
            int visibleIndex = index - this.scrollRow;
            boolean visible = visibleIndex >= 0 && visibleIndex < visibleRows;
            attributeBox.visible = visible;
            attributeBox.active = visible;
            valueBox.visible = visible;
            valueBox.active = visible;
            if (visible) {
                int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT + 8;
                attributeBox.setX(attributeX);
                attributeBox.setY(rowY);
                attributeBox.setWidth(ATTRIBUTE_BOX_WIDTH);
                valueBox.setX(valueX);
                valueBox.setY(rowY);
                valueBox.setWidth(VALUE_BOX_WIDTH);
            } else {
                attributeBox.setX(-2000);
                attributeBox.setY(-2000);
                valueBox.setX(-2000);
                valueBox.setY(-2000);
            }
        }
    }

    private void applyFilled() {
        List<String> editableItems = getEditableItems();
        EzBalanceRuntime.captureOriginalAttributes(this.config, editableItems);
        Map<String, Double> overrides = collectUniqueFilledOverrides();
        for (String itemId : editableItems) {
            for (Map.Entry<String, Double> entry : overrides.entrySet()) {
                if (this.config.normalization != null && this.config.normalization.enabled) {
                    EzBalanceRuleMutations.setAttributeOverrideNormalized(this.config, itemId, entry.getKey(), entry.getValue());
                } else {
                    EzBalanceRuleMutations.setAttributeOverride(this.config, itemId, entry.getKey(), entry.getValue());
                }
            }
        }
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.persistWorkingConfig();
        } else {
            EzBalanceClientPersistence.persist(this.config);
        }
        this.minecraft.setScreen(this.parent);
    }

    private Map<String, Double> collectUniqueFilledOverrides() {
        Map<String, Double> overrides = new LinkedHashMap<>();
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeBoxes.get(index).getValue());
            Double value = parseNullableDouble(this.valueBoxes.get(index).getValue());
            if (!attributeId.isBlank() && value != null) {
                overrides.put(attributeId, value);
            }
        }
        return overrides;
    }

    private List<String> getEditableItems() {
        return this.targetItems.stream()
                .filter(itemId -> this.allowLockedEdits || !isLocked(itemId))
                .toList();
    }

    private String getFirstEditableItem() {
        return getEditableItems().stream().findFirst().orElse("");
    }

    private boolean isLocked(String itemId) {
        EzBalanceItemRule rule = this.config.items.get(itemId);
        return rule != null && rule.locked;
    }

    private String formatAttributeValueSummary(String itemId, String attributeId) {
        EzBalanceAttributeValue value = EzBalanceRuntime.getAttributeValue(this.config, itemId, attributeId);
        return "Original: " + formatValue(value.originalValue()) + "  Current: " + formatValue(value.currentValue());
    }

    private String formatValue(Double value) {
        if (value == null) {
            return "-";
        }
        return Math.abs(value - Math.rint(value)) < 0.005D
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && isInsideList(mouseX, mouseY)) {
            RowHitbox hitbox = getRowHitbox(mouseX, mouseY);
            if (hitbox != null) {
                if (hitbox.plusRow()) {
                    addRow("", "");
                    EditBox attributeBox = this.attributeBoxes.getLast();
                    this.addRenderableWidget(attributeBox);
                    this.addRenderableWidget(this.valueBoxes.getLast());
                    updateFieldLayout();
                    attributeBox.setFocused(true);
                    return true;
                }
                if (hitbox.deleteButton()) {
                    removeRow(hitbox.rowIndex());
                    return true;
                }
            }
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
            updateFieldLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, this.title.getString(), 24, 24, true);
        drawLabel(graphics, "Selected items: " + this.targetItems.size(), 24, 40, false);
        drawLabel(graphics, "Editable items: " + getEditableItems().size(), 24, 54, false);
        drawLabel(graphics, "Only filled values are applied. Add rows to modify extra attributes.", 24, 68, false);
        renderRows(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int listRight = this.width - 48;
        int visibleRows = getVisibleRows();
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int index = this.scrollRow + visibleIndex;
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean hovered = mouseX >= 24 && mouseX <= listRight && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 4;
            graphics.fill(24, rowY, listRight, rowY + ROW_HEIGHT - 4, hovered ? (0x22111111 | COLOR_ACCENT) : (visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT));
            if (index < this.attributeBoxes.size()) {
                String firstItem = getFirstEditableItem();
                String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeBoxes.get(index).getValue());
                if (!firstItem.isBlank() && !attributeId.isBlank()) {
                    drawLabel(graphics, formatAttributeValueSummary(firstItem, attributeId), 34, rowY + 31, false);
                }
                int deleteX = listRight - DELETE_BUTTON_WIDTH - 8;
                boolean deleteHovered = mouseX >= deleteX && mouseX <= deleteX + DELETE_BUTTON_WIDTH && mouseY >= rowY + 8 && mouseY <= rowY + 28;
                drawInlineButton(graphics, deleteX, rowY + 8, DELETE_BUTTON_WIDTH, 20, "Delete", false, deleteHovered);
            } else if (index == this.attributeBoxes.size()) {
                int plusX = 24 + 10;
                int plusWidth = listRight - 20 - 24;
                boolean plusHovered = mouseX >= plusX && mouseX <= plusX + plusWidth && mouseY >= rowY + 8 && mouseY <= rowY + 28;
                drawInlineButton(graphics, plusX, rowY + 8, plusWidth, 20, "+", true, plusHovered);
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int trackX = this.width - 32;
        int trackHeight = this.height - LIST_TOP - 58;
        int totalRows = Math.max(getVisibleRows(), this.attributeBoxes.size() + 1);
        int thumbHeight = Math.max(18, trackHeight * getVisibleRows() / totalRows);
        int maxTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        EzBalanceUi.drawVerticalScrollbar(graphics, trackX, LIST_TOP, LIST_TOP + trackHeight, TRACK_WIDTH, thumbY, thumbHeight, this.draggingScrollbar);
    }

    private int getVisibleRows() {
        return Math.max(1, (this.height - LIST_TOP - 58) / ROW_HEIGHT);
    }

    private int getMaxScrollRow() {
        return Math.max(0, this.attributeBoxes.size() + 1 - getVisibleRows());
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= this.width - 32 && mouseY >= LIST_TOP && mouseY <= this.height - 48;
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int trackX = this.width - 32;
        int trackHeight = this.height - LIST_TOP - 58;
        return mouseX >= trackX && mouseX <= trackX + TRACK_WIDTH && mouseY >= LIST_TOP && mouseY <= LIST_TOP + trackHeight;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScrollRow();
        if (maxScroll == 0) {
            this.scrollRow = 0;
            updateFieldLayout();
            return;
        }
        double ratio = (mouseY - LIST_TOP) / Math.max(1.0D, this.height - LIST_TOP - 58);
        this.scrollRow = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
        updateFieldLayout();
    }

    private RowHitbox getRowHitbox(double mouseX, double mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return null;
        }
        int visibleIndex = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        int rowIndex = this.scrollRow + visibleIndex;
        if (rowIndex > this.attributeBoxes.size()) {
            return null;
        }
        if (rowIndex == this.attributeBoxes.size()) {
            return new RowHitbox(rowIndex, true, false);
        }
        int deleteX = this.width - 48 - DELETE_BUTTON_WIDTH - 8;
        boolean deleteButton = mouseX >= deleteX && mouseX <= deleteX + DELETE_BUTTON_WIDTH;
        return new RowHitbox(rowIndex, false, deleteButton);
    }

    @Override
    public void onClose() {
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.refreshItems();
        }
        this.minecraft.setScreen(this.parent);
    }

    private record RowHitbox(int rowIndex, boolean plusRow, boolean deleteButton) {
    }

    private record RowSeed(String attributeId, String value) {
    }
}
