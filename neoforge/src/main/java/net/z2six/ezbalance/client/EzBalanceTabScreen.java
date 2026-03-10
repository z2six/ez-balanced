package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import net.z2six.ezbalance.balance.EzBalanceTabDefinition;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EzBalanceTabScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 110;
    private static final int ROW_HEIGHT = 36;
    private static final int TRACK_SIZE = 4;
    private static final int TRACK_GAP = 4;
    private static final int CONTENT_WIDTH = 900;
    private static final int TEXT_BOX_WIDTH = 560;
    private static final int BUTTON_WIDTH = 76;
    private static final int HANDLE_WIDTH = 18;
    private static final int SUGGESTION_VISIBLE_ROWS = 8;
    private static final int SUGGESTION_ROW_HEIGHT = 18;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final String originalTabId;
    private final List<String> allAttributeIds;
    private final List<RowEntry> filterRows = new ArrayList<>();
    private final List<RowEntry> columnRows = new ArrayList<>();
    private final List<String> visibleSuggestions = new ArrayList<>();

    private EditBox idBox;
    private EditBox titleBox;
    private int scrollRow;
    private int scrollX;
    private boolean draggingVerticalScrollbar;
    private boolean draggingHorizontalScrollbar;
    private RowSection plusMenuSection = RowSection.NONE;
    private int plusMenuX;
    private int plusMenuY;
    private RowSection suggestionSection = RowSection.NONE;
    private int suggestionRowIndex = -1;
    private int suggestionScroll;
    private int suggestionMatchCount;
    private String suggestionQuery = "";
    private RowSection suppressedSuggestionSection = RowSection.NONE;
    private int suppressedSuggestionRow = -1;
    private String suppressedSuggestionValue = "";
    private DragState dragState;

    public EzBalanceTabScreen(Screen parent, EzBalanceConfig config, String tabId) {
        super(Component.literal(tabId == null || tabId.isBlank() ? "Create Tab" : "Edit Tab"));
        this.parent = parent;
        this.config = config;
        this.originalTabId = tabId == null ? "" : tabId;
        this.allAttributeIds = EzBalanceClientCatalog.getAllAttributeIds();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.filterRows.clear();
        this.columnRows.clear();
        this.visibleSuggestions.clear();
        this.suggestionSection = RowSection.NONE;
        this.suggestionRowIndex = -1;
        this.suggestionScroll = 0;
        this.suggestionMatchCount = 0;
        this.suggestionQuery = "";
        this.suppressedSuggestionSection = RowSection.NONE;
        this.suppressedSuggestionRow = -1;
        this.suppressedSuggestionValue = "";
        this.plusMenuSection = RowSection.NONE;
        this.dragState = null;

        this.idBox = new EditBox(this.font, 24, 56, 200, 20, Component.literal("Tab id"));
        this.idBox.setMaxLength(128);
        this.titleBox = new EditBox(this.font, 234, 56, 220, 20, Component.literal("Tab title"));
        this.titleBox.setMaxLength(128);
        this.addRenderableWidget(this.idBox);
        this.addRenderableWidget(this.titleBox);

        this.addRenderableWidget(customButton("Add Found Attributes", this.width - 380, 56, 150, 20, button -> addFoundAttributes()));
        this.addRenderableWidget(customButton("Save", this.width - 224, 56, 62, 20, button -> saveTab()));
        this.addRenderableWidget(customButton("Delete", this.width - 154, 56, 62, 20, button -> deleteTab()));
        this.addRenderableWidget(customButton("Back", this.width - 84, 56, 62, 20, button -> onClose()));

        loadCurrentTab();
        updateFieldLayout();
        refreshSuggestions();
    }

    private void loadCurrentTab() {
        EzBalanceTabDefinition tab = this.config.tabs.get(this.originalTabId);
        if (tab == null) {
            this.idBox.setValue("");
            this.titleBox.setValue("");
            return;
        }

        this.idBox.setValue(tab.id);
        this.titleBox.setValue(tab.title);

        for (String attributeId : tab.requiredAttributes) {
            addRow(RowSection.FILTERS, RowType.FILTER_ATTRIBUTE, attributeId);
        }
        for (String nameFilter : tab.nameFilters) {
            addRow(RowSection.FILTERS, RowType.FILTER_ITEM_NAME, nameFilter);
        }
        for (String tagId : tab.includeTags) {
            addRow(RowSection.FILTERS, RowType.FILTER_TAG, tagId);
        }
        for (String display : tab.displayAttributes) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(display);
            if (EzBalanceRuntime.DPS_COLUMN_ID.equals(normalized)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_DPS, "DPS");
            } else if (EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID.equals(normalized)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ENCHANT_RULES, "Enchantment rules");
            } else {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, normalized);
            }
        }
    }

    private void addRow(RowSection section, RowType type, String value) {
        RowEntry entry = new RowEntry(section, type);
        if (type.editable()) {
            EditBox box = new EditBox(this.font, 0, 0, TEXT_BOX_WIDTH, 20, Component.literal(type.placeholder()));
            box.setMaxLength(256);
            box.setValue(value == null ? "" : value);
            entry.textBox = box;
            this.addRenderableWidget(box);
        } else {
            entry.label = value == null ? type.label() : value;
        }
        rowsFor(section).add(entry);
    }

    private List<RowEntry> rowsFor(RowSection section) {
        return section == RowSection.FILTERS ? this.filterRows : this.columnRows;
    }

    private void removeRow(RowSection section, int rowIndex) {
        List<RowEntry> rows = rowsFor(section);
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return;
        }
        RowEntry removed = rows.remove(rowIndex);
        if (removed.textBox != null) {
            removeWidget(removed.textBox);
        }
        if (this.suggestionSection == section && this.suggestionRowIndex == rowIndex) {
            clearSuggestions();
        } else if (this.suggestionSection == section && this.suggestionRowIndex > rowIndex) {
            this.suggestionRowIndex--;
        }
        if (this.suppressedSuggestionSection == section && this.suppressedSuggestionRow == rowIndex) {
            this.suppressedSuggestionSection = RowSection.NONE;
            this.suppressedSuggestionRow = -1;
            this.suppressedSuggestionValue = "";
        } else if (this.suppressedSuggestionSection == section && this.suppressedSuggestionRow > rowIndex) {
            this.suppressedSuggestionRow--;
        }
        this.scrollRow = Math.clamp(this.scrollRow, 0, getMaxScrollRow());
        updateFieldLayout();
        refreshSuggestions();
    }

    private void saveTab() {
        String id = sanitizeId(this.idBox.getValue());
        if (id.isBlank()) {
            id = sanitizeId(this.titleBox.getValue());
        }
        if (id.isBlank()) {
            id = generateFallbackTabId();
        }

        EzBalanceTabDefinition existing = this.config.tabs.get(this.originalTabId);
        EzBalanceTabDefinition tab = new EzBalanceTabDefinition();
        tab.id = id;
        tab.title = this.titleBox.getValue().isBlank() ? id : this.titleBox.getValue().trim();
        tab.iconItemId = existing == null ? "minecraft:book" : existing.iconItemId;

        for (RowEntry row : this.filterRows) {
            String value = row.value();
            if (value.isBlank()) {
                continue;
            }
            switch (row.type) {
                case FILTER_ATTRIBUTE -> tab.requiredAttributes.add(EzBalanceRuntime.normalizeAttributeId(value));
                case FILTER_ITEM_NAME -> tab.nameFilters.add(value.trim());
                case FILTER_TAG -> tab.includeTags.add(value.trim());
                default -> {
                }
            }
        }

        for (RowEntry row : this.columnRows) {
            String value = row.value();
            if (row.type == RowType.COLUMN_DPS) {
                tab.displayAttributes.add(EzBalanceRuntime.DPS_COLUMN_ID);
            } else if (row.type == RowType.COLUMN_ENCHANT_RULES) {
                tab.displayAttributes.add(EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID);
            } else if (!value.isBlank()) {
                tab.displayAttributes.add(EzBalanceRuntime.normalizeAttributeId(value));
            }
        }

        if (!this.originalTabId.isBlank() && !this.originalTabId.equals(id)) {
            this.config.tabs.remove(this.originalTabId);
        }
        this.config.tabs.put(id, tab);
        closeToParent(id);
    }

    private String generateFallbackTabId() {
        int index = 1;
        while (this.config.tabs.containsKey("tab_" + index)) {
            index++;
        }
        return "tab_" + index;
    }

    private void deleteTab() {
        if (!this.originalTabId.isBlank()) {
            this.config.tabs.remove(this.originalTabId);
        }
        closeToParent(this.config.tabs.keySet().stream().findFirst().orElse(""));
    }

    private void closeToParent(String selectedTabId) {
        if (this.parent instanceof EzBalanceScreen screen) {
            screen.applyEditedConfig(this.config, selectedTabId);
        }
        this.minecraft.setScreen(this.parent);
    }

    private void addFoundAttributes() {
        LinkedHashSet<String> discovered = new LinkedHashSet<>();
        List<String> sourceItems = this.parent instanceof EzBalanceScreen screen
                ? screen.getVisibleItemsSnapshot()
                : List.of();
        for (String itemId : sourceItems) {
            discovered.addAll(EzBalanceRuntime.collectBaseAttributes(itemId).keySet());
        }
        Set<String> existing = new LinkedHashSet<>();
        for (RowEntry row : this.columnRows) {
            if (row.type == RowType.COLUMN_ATTRIBUTE) {
                existing.add(EzBalanceRuntime.normalizeAttributeId(row.value()));
            }
        }
        for (String attributeId : discovered) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
            if (!normalized.isBlank() && !existing.contains(normalized)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, normalized);
                existing.add(normalized);
            }
        }
        updateFieldLayout();
        refreshSuggestions();
    }

    private void clearSuggestions() {
        this.suggestionSection = RowSection.NONE;
        this.suggestionRowIndex = -1;
        this.suggestionScroll = 0;
        this.suggestionMatchCount = 0;
        this.suggestionQuery = "";
        this.visibleSuggestions.clear();
    }

    private void updateFieldLayout() {
        int visibleRows = getVisibleRows();
        int baseX = getListX() + 32 - this.scrollX;
        List<RenderedRow> renderedRows = getRenderedRows();
        for (int index = 0; index < renderedRows.size(); index++) {
            RenderedRow rendered = renderedRows.get(index);
            RowEntry row = rendered.entry();
            if (row == null || row.textBox == null) {
                continue;
            }
            boolean visible = index >= this.scrollRow && index < this.scrollRow + visibleRows;
            row.textBox.visible = visible;
            row.textBox.active = visible;
            if (visible) {
                int visibleIndex = index - this.scrollRow;
                int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT + 8;
                row.textBox.setX(baseX);
                row.textBox.setY(rowY);
                row.textBox.setWidth(TEXT_BOX_WIDTH);
                row.textBox.setHeight(20);
            } else {
                row.textBox.setX(-2000);
                row.textBox.setY(-2000);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideSuggestionPopup(mouseX, mouseY)) {
            int suggestionIndex = getSuggestionIndex(mouseY);
            if (suggestionIndex >= 0 && suggestionIndex < this.visibleSuggestions.size()) {
                RowEntry row = getSuggestionRow();
                if (row != null && row.textBox != null) {
                    String selectedValue = this.visibleSuggestions.get(suggestionIndex);
                    row.textBox.setValue(selectedValue);
                    this.suppressedSuggestionSection = this.suggestionSection;
                    this.suppressedSuggestionRow = this.suggestionRowIndex;
                    this.suppressedSuggestionValue = selectedValue;
                    clearSuggestions();
                    return true;
                }
            }
        }

        if (button == 0 && isInsideVerticalScrollbar(mouseX, mouseY)) {
            this.draggingVerticalScrollbar = true;
            updateVerticalScrollFromMouse(mouseY);
            updateFieldLayout();
            return true;
        }
        if (button == 0 && isInsideHorizontalScrollbar(mouseX, mouseY)) {
            this.draggingHorizontalScrollbar = true;
            updateHorizontalScrollFromMouse(mouseX);
            updateFieldLayout();
            return true;
        }

        if (button == 0 && isInsidePlusMenu(mouseX, mouseY)) {
            handlePlusMenuClick(mouseX, mouseY);
            return true;
        }

        RenderedRowHit hit = getRenderedRowHit(mouseX, mouseY);
        if (button == 0 && hit != null) {
            if (hit.kind == HitKind.DELETE && hit.renderedRow.entry() != null) {
                removeRow(hit.renderedRow.section(), hit.renderedRow.rowIndexInSection());
                return true;
            }
            if (hit.kind == HitKind.PLUS) {
                this.plusMenuSection = hit.renderedRow.section();
                this.plusMenuX = getListX() + 32 - this.scrollX;
                this.plusMenuY = hit.renderedRow.y() + 30;
                clearSuggestions();
                return true;
            }
            if (hit.kind == HitKind.HANDLE && hit.renderedRow.entry() != null) {
                this.dragState = new DragState(hit.renderedRow.section(), hit.renderedRow.rowIndexInSection(), hit.renderedRow.rowIndexInSection());
                clearSuggestions();
                return true;
            }
        }

        this.plusMenuSection = RowSection.NONE;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        syncSuggestionTarget();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideSuggestionPopup(mouseX, mouseY)) {
            int maxScroll = Math.max(0, this.suggestionMatchCount - SUGGESTION_VISIBLE_ROWS);
            this.suggestionScroll = Math.clamp(this.suggestionScroll - (int) Math.signum(scrollY), 0, maxScroll);
            refreshSuggestions();
            return true;
        }
        if (isInsideList(mouseX, mouseY)) {
            if (hasControlDown()) {
                this.scrollX = Math.clamp(this.scrollX - (int) Math.signum(scrollY) * 28, 0, getMaxScrollX());
            } else {
                this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, getMaxScrollRow());
            }
            updateFieldLayout();
            refreshSuggestions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingVerticalScrollbar) {
            updateVerticalScrollFromMouse(mouseY);
            updateFieldLayout();
            refreshSuggestions();
            return true;
        }
        if (this.draggingHorizontalScrollbar) {
            updateHorizontalScrollFromMouse(mouseX);
            updateFieldLayout();
            refreshSuggestions();
            return true;
        }
        if (button == 0 && this.dragState != null) {
            updateDragTarget(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingVerticalScrollbar = false;
        this.draggingHorizontalScrollbar = false;
        if (button == 0 && this.dragState != null) {
            applyDrag();
            this.dragState = null;
            updateFieldLayout();
            refreshSuggestions();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        syncSuggestionTarget();
        if (this.suggestionRowIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                int maxScroll = Math.max(0, this.suggestionMatchCount - SUGGESTION_VISIBLE_ROWS);
                this.suggestionScroll = Math.clamp(this.suggestionScroll + 1, 0, maxScroll);
                refreshSuggestions();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                int maxScroll = Math.max(0, this.suggestionMatchCount - SUGGESTION_VISIBLE_ROWS);
                this.suggestionScroll = Math.clamp(this.suggestionScroll - 1, 0, maxScroll);
                refreshSuggestions();
                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        syncSuggestionTarget();
        return handled;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, this.originalTabId.isBlank() ? "Create Tab" : "Edit Tab", 24, 28, true);
        drawLabel(graphics, "Configure filters and displayed columns for this tab.", 24, 84, false);

        renderRows(graphics, mouseX, mouseY);
        renderVerticalScrollbar(graphics);
        renderHorizontalScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderPlusMenu(graphics, mouseX, mouseY);
        renderSuggestionPopup(graphics, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int listRight = getListRight();
        int visibleRows = getVisibleRows();
        List<RenderedRow> renderedRows = getRenderedRows();
        RenderedRowHit hovered = getRenderedRowHit(mouseX, mouseY);
        int visibleCount = Math.min(visibleRows, Math.max(0, renderedRows.size() - this.scrollRow));
        for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
            int actualIndex = this.scrollRow + visibleIndex;
            RenderedRow rendered = renderedRows.get(actualIndex);
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean hoveredRow = hovered != null && hovered.renderedRow == rendered;
            boolean dropTarget = this.dragState != null
                    && this.dragState.section == rendered.section()
                    && this.dragState.dropIndex == rendered.rowIndexInSection()
                    && rendered.entry() != null;
            int background = hoveredRow ? (0x22111111 | COLOR_ACCENT) : (visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            if (dropTarget) {
                background = 0x22111111 | COLOR_ACCENT;
            }
            graphics.fill(getListX(), rowY, listRight, rowY + ROW_HEIGHT - 4, background);

            if (rendered.kind() == RenderedKind.HEADER) {
                drawLabel(graphics, rendered.label(), getListX() + 8, rowY + 12, rendered.section() == RowSection.FILTERS);
                continue;
            }

            int handleX = getListX() + 8 - this.scrollX;
            boolean handleHovered = hovered != null && hovered.kind == HitKind.HANDLE && hovered.renderedRow == rendered;
            drawInlineButton(graphics, handleX, rowY + 8, HANDLE_WIDTH, 20, "::", false, handleHovered);

            if (rendered.kind() == RenderedKind.PLUS) {
                int plusX = getListX() + 32 - this.scrollX;
                boolean plusHovered = hovered != null && hovered.kind == HitKind.PLUS && hovered.renderedRow == rendered;
                drawInlineButton(graphics, plusX, rowY + 8, CONTENT_WIDTH - 52, 20, "+", true, plusHovered);
                continue;
            }

            if (rendered.entry() != null && !rendered.entry().type.editable()) {
                drawLabel(graphics, rendered.entry().label, getListX() + 40 - this.scrollX, rowY + 14, false);
            }

            int deleteX = getDeleteButtonX();
            boolean deleteHovered = hovered != null && hovered.kind == HitKind.DELETE && hovered.renderedRow == rendered;
            drawInlineButton(graphics, deleteX, rowY + 8, BUTTON_WIDTH, 20, "Delete", false, deleteHovered);
        }
    }

    private void renderPlusMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.plusMenuSection == RowSection.NONE) {
            return;
        }
        List<String> options = getPlusMenuOptions();
        int width = 150;
        int height = 8 + options.size() * 20;
        int x1 = Math.clamp(this.plusMenuX, 20, this.width - width - 20);
        int y1 = Math.clamp(this.plusMenuY, 40, this.height - height - 40);
        graphics.fill(x1, y1, x1 + width, y1 + height, COLOR_SURFACE);
        graphics.fill(x1, y1, x1 + width, y1 + 1, COLOR_ACCENT);
        graphics.fill(x1, y1, x1 + 1, y1 + height, COLOR_BORDER);
        graphics.fill(x1 + width - 1, y1, x1 + width, y1 + height, COLOR_BORDER);
        graphics.fill(x1, y1 + height - 1, x1 + width, y1 + height, COLOR_BORDER);
        for (int index = 0; index < options.size(); index++) {
            int rowY = y1 + 4 + index * 20;
            boolean hovered = mouseX >= x1 + 2 && mouseX <= x1 + width - 2 && mouseY >= rowY && mouseY <= rowY + 18;
            graphics.fill(x1 + 2, rowY, x1 + width - 2, rowY + 18, hovered ? (0x22111111 | COLOR_ACCENT) : (index % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT));
            drawLabel(graphics, options.get(index), x1 + 8, rowY + 6, false);
        }
    }

    private void renderSuggestionPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.suggestionRowIndex < 0 || this.visibleSuggestions.isEmpty()) {
            return;
        }
        RowEntry target = getSuggestionRow();
        if (target == null || target.textBox == null || !target.textBox.visible) {
            return;
        }
        SuggestionBounds bounds = getSuggestionBounds();
        graphics.fill(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(), COLOR_SURFACE);
        graphics.fill(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y1() + 1, COLOR_ACCENT);
        graphics.fill(bounds.x1(), bounds.y2() - 1, bounds.x2(), bounds.y2(), COLOR_BORDER);
        graphics.fill(bounds.x1(), bounds.y1(), bounds.x1() + 1, bounds.y2(), COLOR_BORDER);
        graphics.fill(bounds.x2() - 1, bounds.y1(), bounds.x2(), bounds.y2(), COLOR_BORDER);
        for (int index = 0; index < this.visibleSuggestions.size(); index++) {
            int y = bounds.y1() + 4 + index * SUGGESTION_ROW_HEIGHT;
            boolean hovered = mouseX >= bounds.x1() + 2
                    && mouseX <= bounds.x2() - 8
                    && mouseY >= y
                    && mouseY <= y + SUGGESTION_ROW_HEIGHT - 1;
            graphics.fill(bounds.x1() + 2, y, bounds.x2() - 8, y + SUGGESTION_ROW_HEIGHT - 1, hovered ? (0x22111111 | COLOR_ACCENT) : (index % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT));
            graphics.drawString(this.font, trimToWidth(this.visibleSuggestions.get(index), bounds.width() - 16), bounds.x1() + 8, y + 5, COLOR_TEXT, false);
        }
    }

    private void renderVerticalScrollbar(GuiGraphics graphics) {
        int x1 = getListRight() + TRACK_GAP;
        int x2 = x1 + TRACK_SIZE;
        graphics.fill(x1, LIST_TOP, x2, LIST_TOP + getListHeight(), COLOR_BORDER);
        int totalRows = Math.max(1, getRenderedRows().size());
        int thumbHeight = Math.max(18, getListHeight() * getVisibleRows() / Math.max(getVisibleRows(), totalRows));
        int maxTravel = Math.max(0, getListHeight() - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        graphics.fill(x1, thumbY, x2, thumbY + thumbHeight, COLOR_ACCENT);
    }

    private void renderHorizontalScrollbar(GuiGraphics graphics) {
        int y1 = LIST_TOP + getListHeight() + TRACK_GAP;
        int y2 = y1 + TRACK_SIZE;
        graphics.fill(getListX(), y1, getListRight(), y2, COLOR_BORDER);
        int trackWidth = getListWidth();
        int thumbWidth = Math.max(24, trackWidth * getListWidth() / Math.max(getListWidth(), CONTENT_WIDTH));
        int maxTravel = Math.max(0, trackWidth - thumbWidth);
        int thumbX = getListX() + (getMaxScrollX() == 0 ? 0 : maxTravel * this.scrollX / getMaxScrollX());
        graphics.fill(thumbX, y1, thumbX + thumbWidth, y2, COLOR_ACCENT);
    }

    private List<String> getPlusMenuOptions() {
        if (this.plusMenuSection == RowSection.FILTERS) {
            return List.of("Attribute", "Item name", "Tags");
        }
        return List.of("Attribute", "Projected DPS", "Enchantment rules");
    }

    private void handlePlusMenuClick(double mouseX, double mouseY) {
        List<String> options = getPlusMenuOptions();
        int width = 150;
        int height = 8 + options.size() * 20;
        int x1 = Math.clamp(this.plusMenuX, 20, this.width - width - 20);
        int y1 = Math.clamp(this.plusMenuY, 40, this.height - height - 40);
        int index = (int) ((mouseY - y1 - 4) / 20);
        if (mouseX < x1 || mouseX > x1 + width || index < 0 || index >= options.size()) {
            this.plusMenuSection = RowSection.NONE;
            return;
        }
        String choice = options.get(index);
        if (this.plusMenuSection == RowSection.FILTERS) {
            if ("Attribute".equals(choice)) {
                addRow(RowSection.FILTERS, RowType.FILTER_ATTRIBUTE, "");
            } else if ("Item name".equals(choice)) {
                addRow(RowSection.FILTERS, RowType.FILTER_ITEM_NAME, "");
            } else {
                addRow(RowSection.FILTERS, RowType.FILTER_TAG, "");
            }
        } else {
            if ("Attribute".equals(choice)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, "");
            } else if ("Projected DPS".equals(choice)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_DPS, "DPS");
            } else {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ENCHANT_RULES, "Enchantment rules");
            }
        }
        this.plusMenuSection = RowSection.NONE;
        updateFieldLayout();
        refreshSuggestions();
    }

    private void updateDragTarget(double mouseY) {
        RenderedRowHit hit = getRenderedRowHit(getListX() + 12, mouseY);
        if (hit == null || hit.renderedRow.entry() == null || hit.renderedRow.section() != this.dragState.section) {
            return;
        }
        int targetIndex = hit.renderedRow.rowIndexInSection();
        if (targetIndex == this.dragState.rowIndex || targetIndex == this.dragState.dropIndex) {
            return;
        }
        List<RowEntry> rows = rowsFor(this.dragState.section);
        RowEntry moved = rows.remove(this.dragState.rowIndex);
        rows.add(targetIndex, moved);
        this.dragState = new DragState(this.dragState.section, targetIndex, targetIndex);
        updateFieldLayout();
    }

    private void applyDrag() {
        updateFieldLayout();
    }

    private List<RenderedRow> getRenderedRows() {
        List<RenderedRow> rows = new ArrayList<>();
        rows.add(new RenderedRow(RowSection.FILTERS, RenderedKind.HEADER, -1, null, "Filters"));
        for (int index = 0; index < this.filterRows.size(); index++) {
            rows.add(new RenderedRow(RowSection.FILTERS, RenderedKind.ENTRY, index, this.filterRows.get(index), ""));
        }
        rows.add(new RenderedRow(RowSection.FILTERS, RenderedKind.PLUS, this.filterRows.size(), null, ""));
        rows.add(new RenderedRow(RowSection.COLUMNS, RenderedKind.HEADER, -1, null, "Displayed columns"));
        for (int index = 0; index < this.columnRows.size(); index++) {
            rows.add(new RenderedRow(RowSection.COLUMNS, RenderedKind.ENTRY, index, this.columnRows.get(index), ""));
        }
        rows.add(new RenderedRow(RowSection.COLUMNS, RenderedKind.PLUS, this.columnRows.size(), null, ""));
        return rows;
    }

    private RenderedRowHit getRenderedRowHit(double mouseX, double mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return null;
        }
        int visibleIndex = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        int actualIndex = this.scrollRow + visibleIndex;
        List<RenderedRow> rows = getRenderedRows();
        if (actualIndex < 0 || actualIndex >= rows.size()) {
            return null;
        }
        RenderedRow base = rows.get(actualIndex);
        RenderedRow rendered = new RenderedRow(base.section(), base.kind(), base.rowIndexInSection(), base.entry(), base.label(), LIST_TOP + visibleIndex * ROW_HEIGHT);
        if (rendered.kind() == RenderedKind.HEADER) {
            return new RenderedRowHit(rendered, HitKind.ROW);
        }
        int handleX = getListX() + 8 - this.scrollX;
        if (mouseX >= handleX && mouseX <= handleX + HANDLE_WIDTH && mouseY >= rendered.y() + 8 && mouseY <= rendered.y() + 28) {
            return new RenderedRowHit(rendered, HitKind.HANDLE);
        }
        if (rendered.kind() == RenderedKind.PLUS) {
            return new RenderedRowHit(rendered, HitKind.PLUS);
        }
        int deleteX = getDeleteButtonX();
        if (mouseX >= deleteX && mouseX <= deleteX + BUTTON_WIDTH && mouseY >= rendered.y() + 8 && mouseY <= rendered.y() + 28) {
            return new RenderedRowHit(rendered, HitKind.DELETE);
        }
        return new RenderedRowHit(rendered, HitKind.ROW);
    }

    private void syncSuggestionTarget() {
        RowSection foundSection = RowSection.NONE;
        int foundIndex = -1;
        for (int index = 0; index < this.filterRows.size(); index++) {
            RowEntry row = this.filterRows.get(index);
            if (row.type == RowType.FILTER_ATTRIBUTE && row.textBox != null && row.textBox.isFocused()) {
                foundSection = RowSection.FILTERS;
                foundIndex = index;
                break;
            }
        }
        if (foundIndex < 0) {
            for (int index = 0; index < this.columnRows.size(); index++) {
                RowEntry row = this.columnRows.get(index);
                if (row.type == RowType.COLUMN_ATTRIBUTE && row.textBox != null && row.textBox.isFocused()) {
                    foundSection = RowSection.COLUMNS;
                    foundIndex = index;
                    break;
                }
            }
        }

        if (foundSection != this.suggestionSection || foundIndex != this.suggestionRowIndex) {
            this.suggestionSection = foundSection;
            this.suggestionRowIndex = foundIndex;
            this.suggestionScroll = 0;
            this.suggestionQuery = "";
        }

        RowEntry row = getSuggestionRow();
        if (row != null
                && this.suppressedSuggestionSection == this.suggestionSection
                && this.suppressedSuggestionRow == this.suggestionRowIndex
                && row.textBox != null
                && row.textBox.getValue().equals(this.suppressedSuggestionValue)) {
            clearSuggestions();
            return;
        }

        if (this.suppressedSuggestionSection != this.suggestionSection || this.suppressedSuggestionRow != this.suggestionRowIndex) {
            this.suppressedSuggestionSection = RowSection.NONE;
            this.suppressedSuggestionRow = -1;
            this.suppressedSuggestionValue = "";
        }
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        this.visibleSuggestions.clear();
        RowEntry row = getSuggestionRow();
        if (row == null || row.textBox == null || !row.textBox.visible) {
            this.suggestionMatchCount = 0;
            return;
        }
        String query = row.textBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.suggestionQuery = query;
        int start = this.suggestionScroll;
        int endExclusive = start + SUGGESTION_VISIBLE_ROWS;
        int matchIndex = 0;
        for (String attributeId : this.allAttributeIds) {
            if (!query.isBlank() && !attributeId.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (matchIndex >= start && matchIndex < endExclusive) {
                this.visibleSuggestions.add(attributeId);
            }
            matchIndex++;
        }
        this.suggestionMatchCount = matchIndex;
        int maxScroll = Math.max(0, this.suggestionMatchCount - SUGGESTION_VISIBLE_ROWS);
        if (this.suggestionScroll > maxScroll) {
            this.suggestionScroll = maxScroll;
            refreshSuggestions();
        }
    }

    private RowEntry getSuggestionRow() {
        if (this.suggestionSection == RowSection.NONE || this.suggestionRowIndex < 0) {
            return null;
        }
        List<RowEntry> rows = rowsFor(this.suggestionSection);
        return this.suggestionRowIndex >= rows.size() ? null : rows.get(this.suggestionRowIndex);
    }

    private SuggestionBounds getSuggestionBounds() {
        RowEntry row = getSuggestionRow();
        if (row == null || row.textBox == null) {
            return new SuggestionBounds(0, 0, 0, 0);
        }
        int rows = Math.min(SUGGESTION_VISIBLE_ROWS, Math.max(1, this.visibleSuggestions.size()));
        int width = row.textBox.getWidth();
        int height = 8 + rows * SUGGESTION_ROW_HEIGHT;
        int x1 = Math.clamp(row.textBox.getX(), 20, this.width - width - 20);
        int below = row.textBox.getY() + row.textBox.getHeight() + 4;
        int above = row.textBox.getY() - height - 4;
        int y1 = below + height <= this.height - 40 ? below : Math.max(40, above);
        return new SuggestionBounds(x1, y1, x1 + width, y1 + height);
    }

    private int getSuggestionIndex(double mouseY) {
        SuggestionBounds bounds = getSuggestionBounds();
        return (int) ((mouseY - bounds.y1() - 4) / SUGGESTION_ROW_HEIGHT);
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

    private int getMaxScrollRow() {
        return Math.max(0, getRenderedRows().size() - getVisibleRows());
    }

    private int getMaxScrollX() {
        return Math.max(0, CONTENT_WIDTH - getListWidth());
    }

    private int getDeleteButtonX() {
        return getListX() + CONTENT_WIDTH - BUTTON_WIDTH - 12 - this.scrollX;
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

    private boolean isInsidePlusMenu(double mouseX, double mouseY) {
        if (this.plusMenuSection == RowSection.NONE) {
            return false;
        }
        List<String> options = getPlusMenuOptions();
        int width = 150;
        int height = 8 + options.size() * 20;
        int x1 = Math.clamp(this.plusMenuX, 20, this.width - width - 20);
        int y1 = Math.clamp(this.plusMenuY, 40, this.height - height - 40);
        return mouseX >= x1 && mouseX <= x1 + width && mouseY >= y1 && mouseY <= y1 + height;
    }

    private boolean isInsideSuggestionPopup(double mouseX, double mouseY) {
        if (this.suggestionRowIndex < 0 || this.visibleSuggestions.isEmpty()) {
            return false;
        }
        SuggestionBounds bounds = getSuggestionBounds();
        return mouseX >= bounds.x1() && mouseX <= bounds.x2() && mouseY >= bounds.y1() && mouseY <= bounds.y2();
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

    private enum RowSection {
        NONE,
        FILTERS,
        COLUMNS
    }

    private enum RowType {
        FILTER_ATTRIBUTE(true, "Attribute id", "Attribute"),
        FILTER_ITEM_NAME(true, "Item name fragment", "Item name"),
        FILTER_TAG(true, "Tag id", "Tags"),
        COLUMN_ATTRIBUTE(true, "Attribute id", "Attribute"),
        COLUMN_DPS(false, "", "DPS"),
        COLUMN_ENCHANT_RULES(false, "", "Enchantment rules");

        private final boolean editable;
        private final String placeholder;
        private final String label;

        RowType(boolean editable, String placeholder, String label) {
            this.editable = editable;
            this.placeholder = placeholder;
            this.label = label;
        }

        boolean editable() {
            return this.editable;
        }

        String placeholder() {
            return this.placeholder;
        }

        String label() {
            return this.label;
        }
    }

    private enum RenderedKind {
        HEADER,
        ENTRY,
        PLUS
    }

    private enum HitKind {
        ROW,
        HANDLE,
        DELETE,
        PLUS
    }

    private static final class RowEntry {
        private final RowSection section;
        private final RowType type;
        private EditBox textBox;
        private String label = "";

        private RowEntry(RowSection section, RowType type) {
            this.section = section;
            this.type = type;
        }

        private String value() {
            if (this.textBox != null) {
                return this.textBox.getValue().trim();
            }
            return this.label == null ? "" : this.label.trim();
        }
    }

    private record RenderedRow(RowSection section, RenderedKind kind, int rowIndexInSection, RowEntry entry, String label, int y) {
        private RenderedRow(RowSection section, RenderedKind kind, int rowIndexInSection, RowEntry entry, String label) {
            this(section, kind, rowIndexInSection, entry, label, 0);
        }
    }

    private record RenderedRowHit(RenderedRow renderedRow, HitKind kind) {
    }

    private record SuggestionBounds(int x1, int y1, int x2, int y2) {
        private int width() {
            return this.x2 - this.x1;
        }
    }

    private record DragState(RowSection section, int rowIndex, int dropIndex) {
    }
}
