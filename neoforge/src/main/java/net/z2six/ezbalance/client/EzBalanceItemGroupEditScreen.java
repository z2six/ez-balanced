package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceItemGroupDefinition;
import net.z2six.ezbalance.balance.EzBalanceItemRule;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EzBalanceItemGroupEditScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 112;
    private static final int ROW_HEIGHT = 36;
    private static final int TRACK_SIZE = 4;
    private static final int TRACK_GAP = 4;
    private static final int CONTENT_WIDTH = 860;
    private static final int ATTRIBUTE_BOX_WIDTH = 500;
    private static final int VALUE_BOX_WIDTH = 150;
    private static final int DELETE_BUTTON_WIDTH = 76;
    private static final int SUGGESTION_VISIBLE_ROWS = 8;
    private static final int SUGGESTION_ROW_HEIGHT = 18;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final List<String> allAttributeIds;
    private final List<EditBox> attributeBoxes = new ArrayList<>();
    private final List<EditBox> valueBoxes = new ArrayList<>();
    private final List<String> visibleSuggestions = new ArrayList<>();

    private EditBox idBox;
    private EditBox nameBox;
    private String currentGroupId;
    private int scrollRow;
    private int scrollX;
    private boolean draggingVerticalScrollbar;
    private boolean draggingHorizontalScrollbar;
    private int suggestionTargetRow = -1;
    private int suggestionScroll;
    private int suggestionMatchCount;
    private String suppressedSuggestionValue = "";
    private int suppressedSuggestionRow = -1;

    public EzBalanceItemGroupEditScreen(Screen parent, EzBalanceConfig config, String currentGroupId) {
        super(Component.literal("Edit Item Group"));
        this.parent = parent;
        this.config = config;
        this.currentGroupId = currentGroupId;
        this.allAttributeIds = EzBalanceClientCatalog.getAllAttributeIds();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.attributeBoxes.clear();
        this.valueBoxes.clear();
        this.visibleSuggestions.clear();
        this.suggestionTargetRow = -1;
        this.suggestionScroll = 0;
        this.suggestionMatchCount = 0;
        this.suppressedSuggestionRow = -1;
        this.suppressedSuggestionValue = "";

        this.idBox = new EditBox(this.font, 24, 56, 180, 20, Component.literal("Group id"));
        this.nameBox = new EditBox(this.font, 214, 56, 220, 20, Component.literal("Group name"));
        this.addRenderableWidget(this.idBox);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(customButton("Enchantment rules", 444, 56, 130, 20, button -> openEnchantmentRules()));
        this.addRenderableWidget(customButton("Save", this.width - 248, 56, 70, 20, button -> saveItemGroup()));
        this.addRenderableWidget(customButton("Delete", this.width - 170, 56, 70, 20, button -> deleteItemGroup()));
        this.addRenderableWidget(customButton("Back", this.width - 92, 56, 70, 20, button -> this.onClose()));

        loadCurrentGroup();
        updateFieldLayout();
        refreshSuggestions();
    }

    private void loadCurrentGroup() {
        EzBalanceItemGroupDefinition group = this.config.itemGroups.get(this.currentGroupId);
        if (group == null) {
            this.idBox.setValue("");
            this.nameBox.setValue("");
            return;
        }

        this.idBox.setValue(group.id);
        this.nameBox.setValue(group.name);
        group.attributeValues.forEach(this::addAttributeRow);
    }

    private void addAttributeRow(String attributeId, Double value) {
        EditBox attributeBox = new EditBox(this.font, 0, 0, ATTRIBUTE_BOX_WIDTH, 20, Component.literal("Attribute id"));
        attributeBox.setMaxLength(256);
        attributeBox.setValue(attributeId == null ? "" : attributeId);
        this.attributeBoxes.add(attributeBox);
        this.addRenderableWidget(attributeBox);

        EditBox valueBox = new EditBox(this.font, 0, 0, VALUE_BOX_WIDTH, 20, Component.literal("Value"));
        valueBox.setMaxLength(32);
        valueBox.setValue(value == null ? "" : formatValue(value));
        this.valueBoxes.add(valueBox);
        this.addRenderableWidget(valueBox);
    }

    private void removeAttributeRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= this.attributeBoxes.size()) {
            return;
        }
        EditBox attributeBox = this.attributeBoxes.remove(rowIndex);
        EditBox valueBox = this.valueBoxes.remove(rowIndex);
        removeWidget(attributeBox);
        removeWidget(valueBox);
        if (this.suggestionTargetRow == rowIndex) {
            this.suggestionTargetRow = -1;
            this.visibleSuggestions.clear();
            this.suggestionScroll = 0;
            this.suggestionMatchCount = 0;
        } else if (this.suggestionTargetRow > rowIndex) {
            this.suggestionTargetRow--;
        }
        if (this.suppressedSuggestionRow == rowIndex) {
            this.suppressedSuggestionRow = -1;
            this.suppressedSuggestionValue = "";
        } else if (this.suppressedSuggestionRow > rowIndex) {
            this.suppressedSuggestionRow--;
        }
        this.scrollRow = Math.clamp(this.scrollRow, 0, getMaxScrollRow());
        updateFieldLayout();
        refreshSuggestions();
    }

    private void updateFieldLayout() {
        int visibleRows = getVisibleRows();
        int baseX = getListX() + 10 - this.scrollX;
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
                attributeBox.setX(baseX);
                attributeBox.setY(rowY);
                attributeBox.setWidth(ATTRIBUTE_BOX_WIDTH);
                valueBox.setX(baseX + ATTRIBUTE_BOX_WIDTH + 12);
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

    private void saveItemGroup() {
        String id = sanitizeId(this.idBox.getValue());
        if (id.isBlank()) {
            return;
        }

        String previousId = this.currentGroupId;
        EzBalanceItemGroupDefinition existing = this.config.itemGroups.get(previousId);
        EzBalanceItemGroupDefinition group = new EzBalanceItemGroupDefinition();
        group.id = id;
        group.name = this.nameBox.getValue().isBlank() ? id : this.nameBox.getValue();
        if (existing != null) {
            group.forceDisabledEnchants = existing.forceDisabledEnchants;
            group.allowedEnchantments.addAll(existing.allowedEnchantments);
        }
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeBoxes.get(index).getValue());
            Double value = parseNullableDouble(this.valueBoxes.get(index).getValue());
            if (!attributeId.isBlank() && value != null) {
                group.attributeValues.put(attributeId, value);
            }
        }

        if (!previousId.isBlank() && !previousId.equals(id)) {
            this.config.itemGroups.remove(previousId);
            for (EzBalanceItemRule rule : this.config.items.values()) {
                if (rule.itemGroupIds.remove(previousId)) {
                    rule.itemGroupIds.add(id);
                }
                if (rule.appliedItemGroupAttributesByGroup.containsKey(previousId)) {
                    rule.appliedItemGroupAttributesByGroup.put(id, rule.appliedItemGroupAttributesByGroup.remove(previousId));
                }
                if (rule.itemGroupEnchantRuleIds.remove(previousId)) {
                    rule.itemGroupEnchantRuleIds.add(id);
                }
            }
        }
        this.config.itemGroups.put(id, group);
        this.currentGroupId = id;
        persistChanges();
        this.minecraft.setScreen(this.parent);
    }

    private void duplicateItemGroup() {
        EzBalanceItemGroupDefinition source = buildGroupFromFields();
        if (source == null) {
            return;
        }
        String duplicateId = generateDuplicateId(source.id.isBlank() ? sanitizeId(this.currentGroupId) : source.id);
        source.id = duplicateId;
        if (source.name == null || source.name.isBlank()) {
            source.name = duplicateId;
        } else {
            source.name = source.name + " Copy";
        }
        this.config.itemGroups.put(duplicateId, source);
        this.currentGroupId = duplicateId;
        persistChanges();
        this.minecraft.setScreen(new EzBalanceItemGroupEditScreen(this.parent, this.config, duplicateId));
    }

    private void deleteItemGroup() {
        if (!this.currentGroupId.isBlank()) {
            this.config.itemGroups.remove(this.currentGroupId);
            for (EzBalanceItemRule rule : this.config.items.values()) {
                rule.itemGroupIds.remove(this.currentGroupId);
                rule.appliedItemGroupAttributesByGroup.remove(this.currentGroupId);
                rule.itemGroupEnchantRuleIds.remove(this.currentGroupId);
            }
        }
        persistChanges();
        this.minecraft.setScreen(this.parent);
    }

    private void openEnchantmentRules() {
        String currentId = sanitizeId(this.idBox.getValue());
        if (currentId.isBlank()) {
            currentId = sanitizeId(this.currentGroupId);
        }
        if (currentId.isBlank()) {
            return;
        }
        saveWorkingCopyForNavigation(currentId);
        this.minecraft.setScreen(new EzBalanceItemGroupEnchantmentScreen(this, this.config, currentId));
    }

    private void saveWorkingCopyForNavigation(String id) {
        EzBalanceItemGroupDefinition group = this.config.itemGroups.computeIfAbsent(id, key -> new EzBalanceItemGroupDefinition());
        group.id = id;
        group.name = this.nameBox.getValue().isBlank() ? id : this.nameBox.getValue();
        group.attributeValues.clear();
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeBoxes.get(index).getValue());
            Double value = parseNullableDouble(this.valueBoxes.get(index).getValue());
            if (!attributeId.isBlank() && value != null) {
                group.attributeValues.put(attributeId, value);
            }
        }
        this.currentGroupId = id;
    }

    private EzBalanceItemGroupDefinition buildGroupFromFields() {
        String baseId = sanitizeId(this.idBox.getValue());
        if (baseId.isBlank()) {
            baseId = sanitizeId(this.currentGroupId);
        }
        if (baseId.isBlank()) {
            return null;
        }
        EzBalanceItemGroupDefinition existing = this.config.itemGroups.get(this.currentGroupId);
        EzBalanceItemGroupDefinition group = new EzBalanceItemGroupDefinition();
        group.id = baseId;
        group.name = this.nameBox.getValue().isBlank() ? baseId : this.nameBox.getValue();
        if (existing != null) {
            group.forceDisabledEnchants = existing.forceDisabledEnchants;
            group.allowedEnchantments.addAll(existing.allowedEnchantments);
        }
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeBoxes.get(index).getValue());
            Double value = parseNullableDouble(this.valueBoxes.get(index).getValue());
            if (!attributeId.isBlank() && value != null) {
                group.attributeValues.put(attributeId, value);
            }
        }
        return group;
    }

    private String generateDuplicateId(String baseId) {
        String root = sanitizeId(baseId);
        if (root.isBlank()) {
            root = "item_group";
        }
        String candidate = root + "_copy";
        int index = 2;
        while (this.config.itemGroups.containsKey(candidate)) {
            candidate = root + "_copy_" + index++;
        }
        return candidate;
    }

    void persistChanges() {
        if (this.parent instanceof EzBalanceItemGroupScreen screen) {
            screen.persistChanges();
        } else if (this.parent instanceof EzBalanceScreen screen) {
            screen.persistWorkingConfig();
        } else {
            EzBalanceClientPersistence.persist(this.config);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideSuggestionPopup(mouseX, mouseY)) {
            int suggestionIndex = getSuggestionIndex(mouseY);
            if (suggestionIndex >= 0 && suggestionIndex < this.visibleSuggestions.size() && this.suggestionTargetRow >= 0) {
                EditBox targetBox = this.attributeBoxes.get(this.suggestionTargetRow);
                String selectedValue = this.visibleSuggestions.get(suggestionIndex);
                targetBox.setValue(selectedValue);
                this.suppressedSuggestionRow = this.suggestionTargetRow;
                this.suppressedSuggestionValue = selectedValue;
                this.suggestionTargetRow = -1;
                this.visibleSuggestions.clear();
                this.suggestionMatchCount = 0;
                this.suggestionScroll = 0;
                return true;
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

        RowHitbox rowHitbox = getRowHitbox(mouseX, mouseY);
        if (button == 0 && rowHitbox != null) {
            if (rowHitbox.plusRow()) {
                addAttributeRow("", null);
                updateFieldLayout();
                refreshSuggestions();
                return true;
            }
            if (rowHitbox.deleteButton()) {
                removeAttributeRow(rowHitbox.rowIndex());
                return true;
            }
        }

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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingVerticalScrollbar = false;
        this.draggingHorizontalScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        syncSuggestionTarget();
        if (this.suggestionTargetRow >= 0) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                int maxScroll = Math.max(0, this.suggestionMatchCount - SUGGESTION_VISIBLE_ROWS);
                this.suggestionScroll = Math.clamp(this.suggestionScroll + 1, 0, maxScroll);
                refreshSuggestions();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                this.suggestionScroll = Math.clamp(this.suggestionScroll - 1, 0, Math.max(0, this.suggestionMatchCount - SUGGESTION_VISIBLE_ROWS));
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
        drawLabel(graphics, "Item Group: " + (this.currentGroupId.isBlank() ? "<new>" : this.currentGroupId), 24, 28, true);
        drawLabel(graphics, "Attributes", 24, 86, false);

        renderRows(graphics, mouseX, mouseY);
        renderVerticalScrollbar(graphics);
        renderHorizontalScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderSuggestionPopup(graphics, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int listRight = getListRight();
        int visibleRows = getVisibleRows();
        int plusWidth = CONTENT_WIDTH - 20;
        RowHitbox hovered = getRowHitbox(mouseX, mouseY);
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int rowIndex = this.scrollRow + visibleIndex;
            int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean rowHovered = hovered != null && hovered.rowIndex() == rowIndex;
            int background = rowHovered ? (0x22111111 | COLOR_ACCENT) : (visibleIndex % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            graphics.fill(getListX(), rowY, listRight, rowY + ROW_HEIGHT - 4, background);
            if (rowIndex < this.attributeBoxes.size()) {
                int deleteX = getDeleteButtonX();
                boolean deleteHovered = mouseX >= deleteX
                        && mouseX <= deleteX + DELETE_BUTTON_WIDTH
                        && mouseY >= rowY + 8
                        && mouseY <= rowY + 28;
                drawInlineButton(graphics, deleteX, rowY + 8, DELETE_BUTTON_WIDTH, 20, "Delete", false, deleteHovered);
            } else if (rowIndex == this.attributeBoxes.size()) {
                int plusX = getListX() + 10 - this.scrollX;
                boolean plusHovered = mouseX >= plusX
                        && mouseX <= plusX + plusWidth
                        && mouseY >= rowY + 8
                        && mouseY <= rowY + 28;
                drawInlineButton(graphics, plusX, rowY + 8, plusWidth, 20, "+", true, plusHovered);
            }
        }
    }

    private void renderSuggestionPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.suggestionTargetRow < 0 || this.visibleSuggestions.isEmpty()) {
            return;
        }
        EditBox target = this.attributeBoxes.get(this.suggestionTargetRow);
        if (!target.visible) {
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
        int totalRows = this.attributeBoxes.size() + 1;
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

    private int getMaxScrollRow() {
        return Math.max(0, this.attributeBoxes.size() + 1 - getVisibleRows());
    }

    private int getMaxScrollX() {
        return Math.max(0, CONTENT_WIDTH - getListWidth());
    }

    private int getDeleteButtonX() {
        return getListX() + 10 - this.scrollX + ATTRIBUTE_BOX_WIDTH + 12 + VALUE_BOX_WIDTH + 12;
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

    private boolean isInsideSuggestionPopup(double mouseX, double mouseY) {
        if (this.suggestionTargetRow < 0 || this.visibleSuggestions.isEmpty()) {
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
        int rowY = LIST_TOP + visibleIndex * ROW_HEIGHT;
        int deleteX = getDeleteButtonX();
        boolean deleteButton = mouseX >= deleteX
                && mouseX <= deleteX + DELETE_BUTTON_WIDTH
                && mouseY >= rowY + 8
                && mouseY <= rowY + 28;
        return new RowHitbox(rowIndex, false, deleteButton);
    }

    private void syncSuggestionTarget() {
        int focusedRow = -1;
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            EditBox box = this.attributeBoxes.get(index);
            if (box.isFocused()) {
                focusedRow = index;
                break;
            }
        }
        this.suggestionTargetRow = focusedRow;
        if (focusedRow >= 0
                && focusedRow == this.suppressedSuggestionRow
                && this.attributeBoxes.get(focusedRow).getValue().equals(this.suppressedSuggestionValue)) {
            this.suggestionTargetRow = -1;
            this.visibleSuggestions.clear();
            this.suggestionMatchCount = 0;
            return;
        }
        if (focusedRow != this.suppressedSuggestionRow
                || focusedRow >= 0 && !this.attributeBoxes.get(focusedRow).getValue().equals(this.suppressedSuggestionValue)) {
            this.suppressedSuggestionRow = -1;
            this.suppressedSuggestionValue = "";
        }
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        this.visibleSuggestions.clear();
        if (this.suggestionTargetRow < 0 || this.suggestionTargetRow >= this.attributeBoxes.size()) {
            this.suggestionMatchCount = 0;
            return;
        }
        EditBox box = this.attributeBoxes.get(this.suggestionTargetRow);
        if (!box.visible) {
            this.suggestionMatchCount = 0;
            return;
        }

        String query = box.getValue().trim().toLowerCase(Locale.ROOT);
        Set<String> usedAttributes = getUsedAttributeIdsExcluding(this.suggestionTargetRow);
        int start = this.suggestionScroll;
        int endExclusive = start + SUGGESTION_VISIBLE_ROWS;
        int matchIndex = 0;
        for (String attributeId : this.allAttributeIds) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
            if (usedAttributes.contains(normalized)) {
                continue;
            }
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

    private Set<String> getUsedAttributeIdsExcluding(int excludedRow) {
        Set<String> used = new LinkedHashSet<>();
        for (int index = 0; index < this.attributeBoxes.size(); index++) {
            if (index == excludedRow) {
                continue;
            }
            String attributeId = EzBalanceRuntime.normalizeAttributeId(this.attributeBoxes.get(index).getValue());
            if (!attributeId.isBlank()) {
                used.add(attributeId);
            }
        }
        return used;
    }

    private SuggestionBounds getSuggestionBounds() {
        EditBox target = this.attributeBoxes.get(this.suggestionTargetRow);
        int rows = Math.min(SUGGESTION_VISIBLE_ROWS, Math.max(1, this.visibleSuggestions.size()));
        int x1 = target.getX();
        int width = target.getWidth();
        int height = 8 + rows * SUGGESTION_ROW_HEIGHT;
        int preferredBelow = target.getY() + target.getHeight() + 4;
        int preferredAbove = target.getY() - height - 4;
        int clampedX = Math.clamp(x1, 20, this.width - width - 20);
        int clampedY = preferredBelow + height <= this.height - 40 ? preferredBelow : Math.max(40, preferredAbove);
        return new SuggestionBounds(clampedX, clampedY, clampedX + width, clampedY + height);
    }

    private int getSuggestionIndex(double mouseY) {
        SuggestionBounds bounds = getSuggestionBounds();
        return (int) ((mouseY - bounds.y1() - 4) / SUGGESTION_ROW_HEIGHT);
    }

    private String formatValue(Double value) {
        if (value == null) {
            return "";
        }
        return Math.abs(value - Math.rint(value)) < 0.005D
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
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

    private record RowHitbox(int rowIndex, boolean plusRow, boolean deleteButton) {
    }

    private record SuggestionBounds(int x1, int y1, int x2, int y2) {
        int width() {
            return this.x2 - this.x1;
        }
    }
}
