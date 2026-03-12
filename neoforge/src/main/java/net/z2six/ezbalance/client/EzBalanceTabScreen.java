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
    private static final int INVERT_CHECKBOX_SIZE = 12;
    private static final int INVERT_BLOCK_WIDTH = 90;
    private static final int SUGGESTION_VISIBLE_ROWS = 8;
    private static final int SUGGESTION_ROW_HEIGHT = 18;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final String originalTabId;
    private final List<String> allAttributeIds;
    private final List<String> allItemTagIds;
    private final List<RowEntry> filterRows = new ArrayList<>();
    private final List<RowEntry> columnRows = new ArrayList<>();
    private final List<String> visibleSuggestions = new ArrayList<>();

    private EditBox idBox;
    private EditBox titleBox;
    private int scrollRow;
    private int scrollX;
    private boolean draggingVerticalScrollbar;
    private boolean draggingHorizontalScrollbar;
    private boolean matchAnyTagFilters;
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
        this.allItemTagIds = EzBalanceClientCatalog.getAllItemTagIds();
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
        this.matchAnyTagFilters = false;

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
        this.matchAnyTagFilters = tab.matchAnyTags;

        for (String attributeId : tab.requiredAttributes) {
            addRow(RowSection.FILTERS, RowType.FILTER_ATTRIBUTE, attributeId, false);
        }
        for (String attributeId : tab.excludedAttributes) {
            addRow(RowSection.FILTERS, RowType.FILTER_ATTRIBUTE, attributeId, true);
        }
        for (String nameFilter : tab.nameFilters) {
            addRow(RowSection.FILTERS, RowType.FILTER_ITEM_NAME, nameFilter, false);
        }
        for (String nameFilter : tab.excludedNameFilters) {
            addRow(RowSection.FILTERS, RowType.FILTER_ITEM_NAME, nameFilter, true);
        }
        for (String tagId : tab.includeTags) {
            addRow(RowSection.FILTERS, RowType.FILTER_TAG, tagId, false);
        }
        for (String tagId : tab.excludeTags) {
            addRow(RowSection.FILTERS, RowType.FILTER_TAG, tagId, true);
        }
        for (String display : tab.displayAttributes) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(display);
            if (EzBalanceRuntime.DPS_COLUMN_ID.equals(normalized)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_DPS, "DPS", false);
            } else if (EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID.equals(normalized)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ENCHANT_RULES, "Enchantment rules", false);
            } else {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, normalized, false);
            }
        }
    }

    private void addRow(RowSection section, RowType type, String value) {
        addRow(section, type, value, false);
    }

    private void addRow(RowSection section, RowType type, String value, boolean inverted) {
        RowEntry entry = new RowEntry(section, type);
        entry.inverted = inverted;
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
        Set<String> seenRequiredAttributes = new LinkedHashSet<>();
        Set<String> seenExcludedAttributes = new LinkedHashSet<>();
        Set<String> seenNameFilters = new LinkedHashSet<>();
        Set<String> seenExcludedNameFilters = new LinkedHashSet<>();
        Set<String> seenIncludeTags = new LinkedHashSet<>();
        Set<String> seenExcludeTags = new LinkedHashSet<>();

        for (RowEntry row : this.filterRows) {
            String value = row.value();
            if (value.isBlank()) {
                continue;
            }
            switch (row.type) {
                case FILTER_ATTRIBUTE -> {
                    String normalized = EzBalanceRuntime.normalizeAttributeId(value);
                    if (normalized.isBlank()) {
                        continue;
                    }
                    if (row.inverted) {
                        if (seenExcludedAttributes.add(normalized)) {
                            tab.excludedAttributes.add(normalized);
                        }
                    } else {
                        if (seenRequiredAttributes.add(normalized)) {
                            tab.requiredAttributes.add(normalized);
                        }
                    }
                }
                case FILTER_ITEM_NAME -> {
                    String trimmed = value.trim();
                    if (row.inverted) {
                        if (!trimmed.isBlank() && seenExcludedNameFilters.add(trimmed.toLowerCase(Locale.ROOT))) {
                            tab.excludedNameFilters.add(trimmed);
                        }
                    } else {
                        if (!trimmed.isBlank() && seenNameFilters.add(trimmed.toLowerCase(Locale.ROOT))) {
                            tab.nameFilters.add(trimmed);
                        }
                    }
                }
                case FILTER_TAG -> {
                    String trimmed = value.trim();
                    if (row.inverted) {
                        if (!trimmed.isBlank() && seenExcludeTags.add(trimmed)) {
                            tab.excludeTags.add(trimmed);
                        }
                    } else {
                        if (!trimmed.isBlank() && seenIncludeTags.add(trimmed)) {
                            tab.includeTags.add(trimmed);
                        }
                    }
                }
                default -> {
                }
            }
        }
        tab.matchAnyTags = this.matchAnyTagFilters;

        Set<String> seenDisplayColumns = new LinkedHashSet<>();
        for (RowEntry row : this.columnRows) {
            String value = row.value();
            if (row.type == RowType.COLUMN_DPS) {
                if (seenDisplayColumns.add(EzBalanceRuntime.DPS_COLUMN_ID)) {
                    tab.displayAttributes.add(EzBalanceRuntime.DPS_COLUMN_ID);
                }
            } else if (row.type == RowType.COLUMN_ENCHANT_RULES) {
                if (seenDisplayColumns.add(EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID)) {
                    tab.displayAttributes.add(EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID);
                }
            } else if (!value.isBlank()) {
                String normalized = EzBalanceRuntime.normalizeAttributeId(value);
                if (!normalized.isBlank() && seenDisplayColumns.add(normalized)) {
                    tab.displayAttributes.add(normalized);
                }
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
            screen.persistWorkingConfig(selectedTabId);
        } else {
            EzBalanceClientPersistence.persist(this.config);
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
                addRow(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, normalized, false);
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
                row.textBox.setWidth(getTextBoxWidth(row));
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
            if (hit.kind == HitKind.TAG_MATCH_MODE) {
                this.matchAnyTagFilters = !this.matchAnyTagFilters;
                return true;
            }
            if (hit.kind == HitKind.DELETE && hit.renderedRow.entry() != null) {
                removeRow(hit.renderedRow.section(), hit.renderedRow.rowIndexInSection());
                return true;
            }
            if (hit.kind == HitKind.INVERT && hit.renderedRow.entry() != null) {
                hit.renderedRow.entry().inverted = !hit.renderedRow.entry().inverted;
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
                if (rendered.section() == RowSection.FILTERS) {
                    int toggleX = getListRight() - 168;
                    boolean toggleHovered = hovered != null && hovered.kind == HitKind.TAG_MATCH_MODE && hovered.renderedRow == rendered;
                    if (toggleHovered) {
                        graphics.fill(toggleX - 6, rowY + 7, toggleX + 132, rowY + 29, 0x18111111 | COLOR_ACCENT);
                    }
                    renderSimpleCheckbox(graphics, toggleX, rowY + 12, this.matchAnyTagFilters);
                    drawLabel(graphics, "Match any tags", toggleX + 18, rowY + 14, false);
                }
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

            if (rendered.entry() != null && rendered.entry().section == RowSection.FILTERS) {
                int invertX = getInvertBlockX();
                boolean invertHovered = hovered != null && hovered.kind == HitKind.INVERT && hovered.renderedRow == rendered;
                if (invertHovered) {
                    graphics.fill(invertX - 4, rowY + 7, invertX + INVERT_BLOCK_WIDTH, rowY + 29, 0x18111111 | COLOR_ACCENT);
                }
                renderSimpleCheckbox(graphics, invertX, rowY + 12, rendered.entry().inverted);
                drawLabel(graphics, "Invert", invertX + 18, rowY + 14, false);
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
        int totalRows = Math.max(1, getRenderedRows().size());
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
                addRow(RowSection.FILTERS, RowType.FILTER_ATTRIBUTE, "", false);
            } else if ("Item name".equals(choice)) {
                addRow(RowSection.FILTERS, RowType.FILTER_ITEM_NAME, "", false);
            } else {
                addRow(RowSection.FILTERS, RowType.FILTER_TAG, "", false);
            }
        } else {
            if ("Attribute".equals(choice)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, "", false);
            } else if ("Projected DPS".equals(choice)) {
                addRow(RowSection.COLUMNS, RowType.COLUMN_DPS, "DPS", false);
            } else {
                addRow(RowSection.COLUMNS, RowType.COLUMN_ENCHANT_RULES, "Enchantment rules", false);
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
            if (rendered.section() == RowSection.FILTERS) {
                int toggleX = getListRight() - 168;
                if (mouseX >= toggleX - 6 && mouseX <= toggleX + 132 && mouseY >= rendered.y() + 8 && mouseY <= rendered.y() + 28) {
                    return new RenderedRowHit(rendered, HitKind.TAG_MATCH_MODE);
                }
            }
            return new RenderedRowHit(rendered, HitKind.ROW);
        }
        int handleX = getListX() + 8 - this.scrollX;
        if (mouseX >= handleX && mouseX <= handleX + HANDLE_WIDTH && mouseY >= rendered.y() + 8 && mouseY <= rendered.y() + 28) {
            return new RenderedRowHit(rendered, HitKind.HANDLE);
        }
        if (rendered.kind() == RenderedKind.PLUS) {
            return new RenderedRowHit(rendered, HitKind.PLUS);
        }
        int invertX = getInvertBlockX();
        if (base.entry() != null
                && base.entry().section == RowSection.FILTERS
                && mouseX >= invertX - 4
                && mouseX <= invertX + INVERT_BLOCK_WIDTH
                && mouseY >= rendered.y() + 8
                && mouseY <= rendered.y() + 28) {
            return new RenderedRowHit(rendered, HitKind.INVERT);
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
            if ((row.type == RowType.FILTER_ATTRIBUTE || row.type == RowType.FILTER_TAG)
                    && row.textBox != null
                    && row.textBox.isFocused()) {
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
        List<String> sourceValues = getSuggestionSourceValues(row);
        if (row == null || row.textBox == null || !row.textBox.visible || sourceValues.isEmpty()) {
            this.suggestionMatchCount = 0;
            return;
        }
        String query = row.textBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.suggestionQuery = query;
        int start = this.suggestionScroll;
        int endExclusive = start + SUGGESTION_VISIBLE_ROWS;
        int matchIndex = 0;
        for (String value : sourceValues) {
            if (!query.isBlank() && !value.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            if (matchIndex >= start && matchIndex < endExclusive) {
                this.visibleSuggestions.add(value);
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

    private List<String> getSuggestionSourceValues(RowEntry row) {
        if (row == null) {
            return List.of();
        }
        return switch (row.type) {
            case FILTER_ATTRIBUTE -> filterUnusedValues(this.allAttributeIds, getUsedRowValues(RowSection.FILTERS, RowType.FILTER_ATTRIBUTE, row));
            case COLUMN_ATTRIBUTE -> filterUnusedValues(this.allAttributeIds, getUsedRowValues(RowSection.COLUMNS, RowType.COLUMN_ATTRIBUTE, row));
            case FILTER_TAG -> filterUnusedValues(this.allItemTagIds, getUsedRowValues(RowSection.FILTERS, RowType.FILTER_TAG, row));
            default -> List.of();
        };
    }

    private List<String> filterUnusedValues(List<String> sourceValues, Set<String> usedValues) {
        if (usedValues.isEmpty()) {
            return sourceValues;
        }
        List<String> filtered = new ArrayList<>(sourceValues.size());
        for (String sourceValue : sourceValues) {
            String comparable = normalizeSuggestionValue(sourceValue);
            if (!usedValues.contains(comparable)) {
                filtered.add(sourceValue);
            }
        }
        return filtered;
    }

    private Set<String> getUsedRowValues(RowSection section, RowType type, RowEntry excludedRow) {
        Set<String> used = new LinkedHashSet<>();
        for (RowEntry row : rowsFor(section)) {
            if (row == excludedRow || row.type != type) {
                continue;
            }
            String value = normalizeSuggestionValue(row.value());
            if (!value.isBlank()) {
                used.add(value);
            }
        }
        return used;
    }

    private String normalizeSuggestionValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.contains(":") || trimmed.startsWith("ezbalance:")
                ? EzBalanceRuntime.normalizeAttributeId(trimmed)
                : trimmed.toLowerCase(Locale.ROOT);
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

    private int getInvertBlockX() {
        return getDeleteButtonX() - INVERT_BLOCK_WIDTH - 16;
    }

    private int getTextBoxWidth(RowEntry row) {
        if (row != null && row.section == RowSection.FILTERS) {
            return Math.max(180, getInvertBlockX() - (getListX() + 32 - this.scrollX) - 16);
        }
        return TEXT_BOX_WIDTH;
    }

    private void renderSimpleCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + INVERT_CHECKBOX_SIZE, y + INVERT_CHECKBOX_SIZE, COLOR_BACKGROUND);
        graphics.fill(x, y, x + INVERT_CHECKBOX_SIZE, y + 1, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x, y + INVERT_CHECKBOX_SIZE - 1, x + INVERT_CHECKBOX_SIZE, y + INVERT_CHECKBOX_SIZE, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + INVERT_CHECKBOX_SIZE, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x + INVERT_CHECKBOX_SIZE - 1, y, x + INVERT_CHECKBOX_SIZE, y + INVERT_CHECKBOX_SIZE, checked ? COLOR_ACCENT : COLOR_BORDER);
        if (checked) {
            graphics.fill(x + 3, y + 3, x + INVERT_CHECKBOX_SIZE - 3, y + INVERT_CHECKBOX_SIZE - 3, COLOR_ACCENT);
        }
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
        INVERT,
        TAG_MATCH_MODE,
        PLUS
    }

    private static final class RowEntry {
        private final RowSection section;
        private final RowType type;
        private EditBox textBox;
        private String label = "";
        private boolean inverted;

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
