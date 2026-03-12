package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceAttributeNormalizationRule;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceNormalizationConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EzBalanceNormalizationConfigScreen extends AbstractEzBalanceScreen {
    private static final int TAB_Y = 18;
    private static final int TAB_W = 118;
    private static final int TAB_H = 24;
    private static final int TAB_GAP = 6;
    private static final int LIST_TOP = 110;
    private static final int ROW_HEIGHT = 36;
    private static final int TRACK_SIZE = 4;
    private static final int TRACK_GAP = 4;
    private static final int CONTENT_WIDTH = 1080;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final String selectedTabId;
    private final List<RowWidgets> attributeRows = new ArrayList<>();
    private final List<TabHitbox> tabHitboxes = new ArrayList<>();

    private EditBox preserveFactorBox;
    private EditBox defaultMinPercentBox;
    private EditBox defaultMaxPercentBox;
    private EditBox defaultMinRawBox;
    private EditBox defaultMaxRawBox;
    private int scrollRow;
    private int scrollX;
    private boolean draggingVerticalScrollbar;
    private boolean draggingHorizontalScrollbar;

    public EzBalanceNormalizationConfigScreen(Screen parent, EzBalanceConfig config, String selectedTabId) {
        super(Component.literal("Normalization Config"));
        this.parent = parent;
        this.config = config;
        this.selectedTabId = selectedTabId == null ? "" : selectedTabId;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.attributeRows.clear();

        EzBalanceNormalizationConfig normalization = this.config.normalization;
        this.preserveFactorBox = addNumberBox(normalization.preserveFactor);
        this.defaultMinPercentBox = addNumberBox(normalization.defaultMinPercent);
        this.defaultMaxPercentBox = addNumberBox(normalization.defaultMaxPercent);
        this.defaultMinRawBox = addNullableNumberBox(normalization.defaultMinRawOffset);
        this.defaultMaxRawBox = addNullableNumberBox(normalization.defaultMaxRawOffset);

        for (EzBalanceAttributeNormalizationRule rule : normalization.attributes) {
            this.attributeRows.add(createRow(rule));
        }

        this.addRenderableWidget(customButton("Save", this.width - 168, this.height - 28, 70, 20, button -> saveAndClose()));
        this.addRenderableWidget(customButton("Back", this.width - 90, this.height - 28, 70, 20, button -> onClose()));
        updateFieldLayout();
    }

    private EditBox addNumberBox(double value) {
        EditBox box = new EditBox(this.font, -2000, -2000, 120, 20, Component.literal("Value"));
        box.setValue(Double.toString(value));
        this.addRenderableWidget(box);
        return box;
    }

    private EditBox addNullableNumberBox(Double value) {
        EditBox box = new EditBox(this.font, -2000, -2000, 120, 20, Component.literal("Value"));
        box.setValue(value == null ? "" : Double.toString(value));
        this.addRenderableWidget(box);
        return box;
    }

    private RowWidgets createRow(EzBalanceAttributeNormalizationRule rule) {
        RowWidgets row = new RowWidgets(rule);
        row.attributeIdBox = new EditBox(this.font, -2000, -2000, 260, 20, Component.literal("Attribute id"));
        row.attributeIdBox.setMaxLength(256);
        row.attributeIdBox.setValue(rule.attributeId == null ? "" : rule.attributeId);
        row.minPercentBox = addNumberBox(rule.minPercent);
        row.maxPercentBox = addNumberBox(rule.maxPercent);
        row.minRawBox = addNullableNumberBox(rule.minRawOffset);
        row.maxRawBox = addNullableNumberBox(rule.maxRawOffset);
        this.addRenderableWidget(row.attributeIdBox);
        return row;
    }

    private void syncConfigFromFields() {
        EzBalanceNormalizationConfig normalization = this.config.normalization;
        normalization.preserveFactor = Math.clamp(parseNullableDouble(this.preserveFactorBox.getValue()) == null ? normalization.preserveFactor : parseNullableDouble(this.preserveFactorBox.getValue()), 0.0D, 1.0D);
        normalization.defaultMinPercent = parseNullableDouble(this.defaultMinPercentBox.getValue()) == null ? normalization.defaultMinPercent : parseNullableDouble(this.defaultMinPercentBox.getValue());
        normalization.defaultMaxPercent = parseNullableDouble(this.defaultMaxPercentBox.getValue()) == null ? normalization.defaultMaxPercent : parseNullableDouble(this.defaultMaxPercentBox.getValue());
        normalization.defaultMinRawOffset = parseNullableDouble(this.defaultMinRawBox.getValue());
        normalization.defaultMaxRawOffset = parseNullableDouble(this.defaultMaxRawBox.getValue());
        normalization.attributes.clear();
        Set<String> seenAttributes = new LinkedHashSet<>();
        for (RowWidgets row : this.attributeRows) {
            String attributeId = EzBalanceRuntime.normalizeAttributeId(row.attributeIdBox.getValue());
            if (attributeId.isBlank() || !seenAttributes.add(attributeId)) {
                continue;
            }
            EzBalanceAttributeNormalizationRule rule = new EzBalanceAttributeNormalizationRule(attributeId);
            Double minPercent = parseNullableDouble(row.minPercentBox.getValue());
            Double maxPercent = parseNullableDouble(row.maxPercentBox.getValue());
            rule.minPercent = minPercent == null ? normalization.defaultMinPercent : minPercent;
            rule.maxPercent = maxPercent == null ? normalization.defaultMaxPercent : maxPercent;
            rule.minRawOffset = parseNullableDouble(row.minRawBox.getValue());
            rule.maxRawOffset = parseNullableDouble(row.maxRawBox.getValue());
            normalization.attributes.add(rule);
        }
    }

    private void saveAndClose() {
        syncConfigFromFields();
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.applyEditedConfig(this.config, this.selectedTabId);
            screen.persistWorkingConfig(this.selectedTabId);
        } else {
            EzBalanceClientPersistence.persist(this.config);
        }
        this.minecraft.setScreen(this.parent);
    }

    private void updateFieldLayout() {
        setGeneralVisibility(false);
        setAttributeVisibility(false);

        int visibleRows = getVisibleRows();
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int actualRow = this.scrollRow + visibleIndex;
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT + 8;
            int x = getListX() + 240 - this.scrollX;
            switch (actualRow) {
                case 0 -> {
                    this.preserveFactorBox.visible = true;
                    this.preserveFactorBox.active = true;
                    positionBox(this.preserveFactorBox, x, rowY);
                }
                case 1 -> {
                    this.defaultMinPercentBox.visible = true;
                    this.defaultMinPercentBox.active = true;
                    positionBox(this.defaultMinPercentBox, x, rowY);
                }
                case 2 -> {
                    this.defaultMaxPercentBox.visible = true;
                    this.defaultMaxPercentBox.active = true;
                    positionBox(this.defaultMaxPercentBox, x, rowY);
                }
                case 3 -> {
                    this.defaultMinRawBox.visible = true;
                    this.defaultMinRawBox.active = true;
                    positionBox(this.defaultMinRawBox, x, rowY);
                }
                case 4 -> {
                    this.defaultMaxRawBox.visible = true;
                    this.defaultMaxRawBox.active = true;
                    positionBox(this.defaultMaxRawBox, x, rowY);
                }
                default -> {
                    int attributeIndex = actualRow - 6;
                    if (attributeIndex >= 0 && attributeIndex < this.attributeRows.size()) {
                        RowWidgets row = this.attributeRows.get(attributeIndex);
                        setRowVisible(row, true);
                        int attrX = getListX() + 32 - this.scrollX;
                        positionBox(row.attributeIdBox, attrX, rowY);
                        positionBox(row.minPercentBox, attrX + 276, rowY);
                        positionBox(row.maxPercentBox, attrX + 412, rowY);
                        positionBox(row.minRawBox, attrX + 548, rowY);
                        positionBox(row.maxRawBox, attrX + 684, rowY);
                    }
                }
            }
        }
    }

    private void positionBox(EditBox box, int x, int y) {
        box.setX(x);
        box.setY(y);
        box.setHeight(20);
    }

    private void setGeneralVisibility(boolean visible) {
        for (EditBox box : List.of(this.preserveFactorBox, this.defaultMinPercentBox, this.defaultMaxPercentBox, this.defaultMinRawBox, this.defaultMaxRawBox)) {
            box.visible = visible;
            box.active = visible;
            if (!visible) {
                box.setX(-2000);
                box.setY(-2000);
            }
        }
    }

    private void setAttributeVisibility(boolean visible) {
        for (RowWidgets row : this.attributeRows) {
            setRowVisible(row, false);
        }
    }

    private void setRowVisible(RowWidgets row, boolean visible) {
        for (EditBox box : List.of(row.attributeIdBox, row.minPercentBox, row.maxPercentBox, row.minRawBox, row.maxRawBox)) {
            box.visible = visible;
            box.active = visible;
            if (!visible) {
                box.setX(-2000);
                box.setY(-2000);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (TabHitbox hitbox : this.tabHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    this.scrollRow = 0;
                    this.scrollX = 0;
                    updateFieldLayout();
                    return true;
                }
            }
            if (isInsideVerticalScrollbar(mouseX, mouseY)) {
                this.draggingVerticalScrollbar = true;
                updateVerticalScrollFromMouse(mouseY);
                updateFieldLayout();
                return true;
            }
            if (isInsideHorizontalScrollbar(mouseX, mouseY)) {
                this.draggingHorizontalScrollbar = true;
                updateHorizontalScrollFromMouse(mouseX);
                updateFieldLayout();
                return true;
            }
            RowHit hit = getRowHit(mouseX, mouseY);
            if (hit != null) {
                if (hit.kind == RowHitKind.DELETE) {
                    RowWidgets row = this.attributeRows.remove(hit.index);
                    removeWidget(row.attributeIdBox);
                    removeWidget(row.minPercentBox);
                    removeWidget(row.maxPercentBox);
                    removeWidget(row.minRawBox);
                    removeWidget(row.maxRawBox);
                    updateFieldLayout();
                    return true;
                }
                if (hit.kind == RowHitKind.PLUS) {
                    this.attributeRows.add(createRow(new EzBalanceAttributeNormalizationRule("")));
                    updateFieldLayout();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingVerticalScrollbar) {
            updateVerticalScrollFromMouse(mouseY);
            updateFieldLayout();
            return true;
        }
        if (this.draggingHorizontalScrollbar) {
            updateHorizontalScrollFromMouse(mouseX);
            updateFieldLayout();
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
            if (hasControlDown()) {
                this.scrollX = Math.clamp(this.scrollX - (int) Math.signum(scrollY) * 28, 0, getMaxScrollX());
            } else {
                this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            }
            updateFieldLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, "Normalization", 24, 28, true);
        drawLabel(graphics, "Configure how targets preserve some original item identity.", 24, 84, false);
        renderTabs(graphics);
        renderContent(graphics, mouseX, mouseY);
        renderVerticalScrollbar(graphics);
        renderHorizontalScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderTextboxTooltips(graphics, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics graphics) {
        this.tabHitboxes.clear();
        int x = 24;
        int y = TAB_Y;
        int h = TAB_H + 8;
        graphics.fill(x, y, x + TAB_W, y + h, COLOR_SURFACE_ALT);
        graphics.fill(x, y, x + TAB_W, y + 1, COLOR_ACCENT);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + TAB_W - 1, y, x + TAB_W, y + h, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + TAB_W, y + h, COLOR_SURFACE_ALT);
        graphics.drawString(this.font, "Normalization", x + 8, y + 10, COLOR_TEXT, false);
        this.tabHitboxes.add(new TabHitbox(x, y, x + TAB_W, y + h));
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int listRight = getListRight();
        int visibleRows = getVisibleRows();
        RowHit hovered = getRowHit(mouseX, mouseY);
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int actualRow = this.scrollRow + visibleIndex;
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            graphics.fill(getListX(), rowY, listRight, rowY + ROW_HEIGHT - 4, visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            if (actualRow < 5) {
                String label = switch (actualRow) {
                    case 0 -> "Preserve factor";
                    case 1 -> "Default min %";
                    case 2 -> "Default max %";
                    case 3 -> "Default min raw";
                    default -> "Default max raw";
                };
                drawLabel(graphics, label, getListX() + 24 - this.scrollX, rowY + 14, false);
                continue;
            }
            if (actualRow == 5) {
                drawLabel(graphics, "Attribute rules", getListX() + 24 - this.scrollX, rowY + 14, true);
                continue;
            }
            int index = actualRow - 6;
            if (index >= this.attributeRows.size()) {
                boolean plusHovered = hovered != null && hovered.kind == RowHitKind.PLUS;
                drawInlineButton(graphics, getListX() + 32 - this.scrollX, rowY + 8, CONTENT_WIDTH - 52, 20, "+", true, plusHovered);
                break;
            }
            boolean deleteHovered = hovered != null && hovered.kind == RowHitKind.DELETE && hovered.index == index;
            drawInlineButton(graphics, getDeleteButtonX(), rowY + 8, 76, 20, "Delete", false, deleteHovered);
        }
    }

    private void renderVerticalScrollbar(GuiGraphics graphics) {
        int x1 = getListRight() + TRACK_GAP;
        int totalRows = Math.max(1, getTotalRows());
        int thumbHeight = Math.max(18, getListHeight() * getVisibleRows() / Math.max(getVisibleRows(), totalRows));
        int maxTravel = Math.max(0, getListHeight() - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        EzBalanceUi.drawVerticalScrollbar(graphics, x1, LIST_TOP, LIST_TOP + getListHeight(), TRACK_SIZE, thumbY, thumbHeight, this.draggingVerticalScrollbar);
    }

    private void renderHorizontalScrollbar(GuiGraphics graphics) {
        int y1 = LIST_TOP + getListHeight() + TRACK_GAP;
        int trackWidth = getListWidth();
        int thumbWidth = Math.max(24, trackWidth * getListWidth() / Math.max(getListWidth(), CONTENT_WIDTH));
        int maxTravel = Math.max(0, trackWidth - thumbWidth);
        int thumbX = getListX() + (getMaxScrollX() == 0 ? 0 : maxTravel * this.scrollX / getMaxScrollX());
        EzBalanceUi.drawHorizontalScrollbar(graphics, getListX(), getListRight(), y1, TRACK_SIZE, thumbX, thumbWidth, this.draggingHorizontalScrollbar);
    }

    private int getListX() {
        return 24;
    }

    private int getListWidth() {
        return Math.max(160, this.width - 48 - TRACK_SIZE - TRACK_GAP);
    }

    private int getListRight() {
        return getListX() + getListWidth();
    }

    private int getListHeight() {
        return Math.max(40, this.height - LIST_TOP - 64 - TRACK_SIZE - TRACK_GAP);
    }

    private int getVisibleRows() {
        return Math.max(1, getListHeight() / ROW_HEIGHT);
    }

    private int getTotalRows() {
        return 7 + this.attributeRows.size();
    }

    private int getMaxScrollRow() {
        return Math.max(0, getTotalRows() - getVisibleRows());
    }

    private int getMaxScrollX() {
        return Math.max(0, CONTENT_WIDTH - getListWidth());
    }

    private int getDeleteButtonX() {
        return getListX() + CONTENT_WIDTH - 88 - this.scrollX;
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= getListX() && mouseX <= getListRight() && mouseY >= LIST_TOP && mouseY <= LIST_TOP + getListHeight();
    }

    private boolean isInsideVerticalScrollbar(double mouseX, double mouseY) {
        int x1 = getListRight() + TRACK_GAP;
        return mouseX >= x1 && mouseX <= x1 + TRACK_SIZE && mouseY >= LIST_TOP && mouseY <= LIST_TOP + getListHeight();
    }

    private boolean isInsideHorizontalScrollbar(double mouseX, double mouseY) {
        int y1 = LIST_TOP + getListHeight() + TRACK_GAP;
        return mouseX >= getListX() && mouseX <= getListRight() && mouseY >= y1 && mouseY <= y1 + TRACK_SIZE;
    }

    private void updateVerticalScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScrollRow();
        if (maxScroll == 0) {
            this.scrollRow = 0;
            return;
        }
        double ratio = (mouseY - LIST_TOP) / Math.max(1.0D, getListHeight());
        this.scrollRow = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private void updateHorizontalScrollFromMouse(double mouseX) {
        int maxScroll = getMaxScrollX();
        if (maxScroll == 0) {
            this.scrollX = 0;
            return;
        }
        double ratio = (mouseX - getListX()) / Math.max(1.0D, getListWidth());
        this.scrollX = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private RowHit getRowHit(double mouseX, double mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return null;
        }
        int visibleIndex = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        int actualRow = this.scrollRow + visibleIndex;
        if (actualRow <= 5) {
            return null;
        }
        int index = actualRow - 6;
        if (index >= this.attributeRows.size()) {
            return new RowHit(RowHitKind.PLUS, Math.max(0, index));
        }
        int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
        int deleteX = getDeleteButtonX();
        if (mouseX >= deleteX && mouseX <= deleteX + 76 && mouseY >= rowY + 8 && mouseY <= rowY + 28) {
            return new RowHit(RowHitKind.DELETE, index);
        }
        return null;
    }

    @Override
    public void onClose() {
        syncConfigFromFields();
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.applyEditedConfig(this.config, this.selectedTabId);
        }
        this.minecraft.setScreen(this.parent);
    }

    private void renderTextboxTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderTooltipForBox(graphics, this.preserveFactorBox, mouseX, mouseY, "Preserve factor", "0.0 means no original bias is kept. 1.0 means keep as much original offset as allowed.")) {
            return;
        }
        if (renderTooltipForBox(graphics, this.defaultMinPercentBox, mouseX, mouseY, "Default min %", "Lowest offset allowed when normalization clamps by percentage of the target value.")) {
            return;
        }
        if (renderTooltipForBox(graphics, this.defaultMaxPercentBox, mouseX, mouseY, "Default max %", "Highest offset allowed when normalization clamps by percentage of the target value.")) {
            return;
        }
        if (renderTooltipForBox(graphics, this.defaultMinRawBox, mouseX, mouseY, "Default min raw", "Optional raw minimum offset. If filled, this overrides the percentage clamp floor.")) {
            return;
        }
        if (renderTooltipForBox(graphics, this.defaultMaxRawBox, mouseX, mouseY, "Default max raw", "Optional raw maximum offset. If filled, this overrides the percentage clamp ceiling.")) {
            return;
        }
        for (RowWidgets row : this.attributeRows) {
            if (renderTooltipForBox(graphics, row.attributeIdBox, mouseX, mouseY, "Attribute id", "Attribute this rule applies to.")) {
                return;
            }
            if (renderTooltipForBox(graphics, row.minPercentBox, mouseX, mouseY, "Min %", "Per-attribute percentage clamp floor.")) {
                return;
            }
            if (renderTooltipForBox(graphics, row.maxPercentBox, mouseX, mouseY, "Max %", "Per-attribute percentage clamp ceiling.")) {
                return;
            }
            if (renderTooltipForBox(graphics, row.minRawBox, mouseX, mouseY, "Min raw", "Optional per-attribute raw minimum offset. Overrides min % when filled.")) {
                return;
            }
            if (renderTooltipForBox(graphics, row.maxRawBox, mouseX, mouseY, "Max raw", "Optional per-attribute raw maximum offset. Overrides max % when filled.")) {
                return;
            }
        }
    }

    private boolean renderTooltipForBox(GuiGraphics graphics, EditBox box, int mouseX, int mouseY, String title, String body) {
        if (box == null || !box.visible || mouseX < box.getX() || mouseX > box.getX() + box.getWidth() || mouseY < box.getY() || mouseY > box.getY() + box.getHeight()) {
            return false;
        }
        graphics.renderTooltip(this.font, List.of(Component.literal(title), Component.literal(body)), java.util.Optional.empty(), mouseX, mouseY);
        return true;
    }

    private static final class RowWidgets {
        private final EzBalanceAttributeNormalizationRule source;
        private EditBox attributeIdBox;
        private EditBox minPercentBox;
        private EditBox maxPercentBox;
        private EditBox minRawBox;
        private EditBox maxRawBox;

        private RowWidgets(EzBalanceAttributeNormalizationRule source) {
            this.source = source;
        }
    }

    private record TabHitbox(int x1, int y1, int x2, int y2) {
        private boolean contains(double x, double y) {
            return x >= this.x1 && x <= this.x2 && y >= this.y1 && y <= this.y2;
        }
    }

    private enum RowHitKind {
        DELETE,
        PLUS
    }

    private record RowHit(RowHitKind kind, int index) {
    }
}
