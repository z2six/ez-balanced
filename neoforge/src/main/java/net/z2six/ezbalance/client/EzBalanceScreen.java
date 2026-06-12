package net.z2six.ezbalance.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.z2six.ezbalance.balance.EzBalanceAttributeValue;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceItemGroupDefinition;
import net.z2six.ezbalance.balance.EzBalanceItemRule;
import net.z2six.ezbalance.balance.EzBalanceRarityDefinition;
import net.z2six.ezbalance.balance.EzBalanceRuleMutations;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EzBalanceScreen extends AbstractEzBalanceScreen {
    private static final long DOUBLE_CLICK_WINDOW_MS = 250L;
    private static final long SEARCH_DEBOUNCE_MS = 450L;
    private static final int LEFT_X = 16;
    private static final int LEFT_W = 280;
    private static final int RIGHT_X = 308;
    private static final int RIGHT_PANEL_TOP = 42;
    private static final int TAB_Y = 18;
    private static final int TAB_W = 118;
    private static final int TAB_H = 24;
    private static final int TAB_GAP = 6;
    private static final int TABLE_HEADER_H = 22;
    private static final int TABLE_ROW_H = 20;
    private static final int TABLE_SCROLLBAR_SIZE = 4;
    private static final int TABLE_SCROLLBAR_GAP = 4;
    private static final int PANEL_HORIZONTAL_SCROLLBAR_Y_OFFSET = 44;
    private static final int LEFT_VERTICAL_SCROLLBAR_MARGIN = 6;
    private static final int TABLE_PAGE_SIZE = 48;
    private static final int TABLE_PAGE_BUFFER = 1;
    private static final int RESET_SCOPE_SIZE = 12;
    private static final int LEFT_HEADER_X1 = 30;
    private static final int LEFT_HEADER_Y1 = 28;
    private static final int LEFT_HEADER_X2 = LEFT_X + LEFT_W - 14;
    private static final int LEFT_HEADER_Y2 = 72;
    private static final int LEFT_CONTENT_TOP = 96;
    private static final int LEFT_CONTENT_PADDING = 18;
    private static final int LEFT_CONTENT_WIDTH = 240;
    private static final int LEFT_ROW_GAP = 12;
    private static final int LEFT_BUTTON_W = 240;
    private static final int LEFT_BUTTON_H = 24;
    private static final int LEFT_RESET_LABEL_X = 0;
    private static final int LEFT_RESET_ROW_Y = 72;
    private static final int LEFT_NORMALIZE_ROW_Y = 108;
    private static final int LEFT_RESET_BUTTON_Y = 144;
    private static final int LEFT_RESET_TOGGLE_Y = LEFT_RESET_BUTTON_Y + 6;
    private static final int LEFT_ENCHANT_ROW_Y = 144;

    private final EzBalanceConfig workingConfig;
    private final Set<String> selectedItems = new LinkedHashSet<>();
    private final List<String> visibleItems = new ArrayList<>();
    private final List<TabHitbox> tabHitboxes = new ArrayList<>();
    private final List<RarityHitbox> rarityHitboxes = new ArrayList<>();
    private final List<ItemGroupHitbox> itemGroupHitboxes = new ArrayList<>();
    private final Map<Integer, Map<String, ItemViewData>> loadedItemPages = new LinkedHashMap<>();
    private final Map<String, ItemViewData> itemDataCache = new LinkedHashMap<>();
    private EzBalanceConfig savedConfigSnapshot;

    private EditBox searchBox;
    private EditBox inlineEditBox;
    private Button applyRarityButton;
    private Button applyItemGroupButton;
    private Button changeAttributeButton;
    private Button normalizeToggleButton;
    private Button resetButton;
    private Button enchantRulesButton;
    private String selectedTabId;
    private String selectionAnchorItemId = "";
    private String selectionLeadItemId = "";
    private int tabOffset;
    private int itemScroll;
    private boolean rarityPickerOpen;
    private int rarityPickerScroll;
    private int rarityPickerX = 34;
    private int rarityPickerY = 244;
    private int rarityPickerWidth = 240;
    private String rarityPickerTargetItemId = "";
    private boolean itemGroupPickerOpen;
    private int itemGroupPickerScroll;
    private int itemGroupPickerX = 34;
    private int itemGroupPickerY = 280;
    private int itemGroupPickerWidth = 240;
    private String itemGroupPickerTargetItemId = "";
    private String inlineEditItemId = "";
    private String inlineEditAttributeId = "";
    private long lastTableClickAt;
    private String lastTableClickKey = "";
    private int tableScrollX;
    private boolean draggingVerticalScrollbar;
    private boolean draggingHorizontalScrollbar;
    private int leftPanelScrollY;
    private int leftPanelScrollX;
    private boolean draggingLeftVerticalScrollbar;
    private boolean draggingLeftHorizontalScrollbar;
    private boolean resetVisibleItems;
    private SortMode sortMode = SortMode.NONE;
    private boolean sortDescending;
    private String sortAttributeId = "";
    private String resizingColumnKey = "";
    private int resizeStartWidth;
    private double resizeStartMouseX;
    private TableLayout cachedLayout = new TableLayout(42, 160, 0, 0, 88, 88, List.of());
    private TableColumns cachedLayoutColumns = new TableColumns(false, false, List.of());
    private boolean layoutDirty = true;
    private long lastSearchEditAt;
    private boolean pendingSearchRefresh;
    private String appliedSearchValue = "";

    public EzBalanceScreen() {
        super(Component.literal("EZ Balance"));
        this.workingConfig = EzBalanceConfig.fromJson(EzBalanceClientState.getConfig().toJson());
        this.savedConfigSnapshot = EzBalanceConfig.fromJson(EzBalanceClientState.getConfig().toJson());
        this.selectedTabId = this.workingConfig.tabs.keySet().stream().findFirst().orElse("weapons");
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int rightWidth = this.width - RIGHT_X - 16;
        this.searchBox = new EditBox(this.font, RIGHT_X + 22, 72, Math.max(140, rightWidth - 298), 20, Component.literal("Search"));
        this.searchBox.setValue(EzBalanceClientLayoutConfig.get().getSavedSearchText());
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        this.inlineEditBox = new EditBox(this.font, -2000, -2000, 96, 16, Component.literal("Value"));
        this.inlineEditBox.visible = false;
        this.inlineEditBox.active = false;
        this.addRenderableWidget(this.inlineEditBox);

        this.addRenderableWidget(customButton("<", this.width - 146, 26, 28, 20, button -> shiftTabs(-1)));
        this.addRenderableWidget(customButton(">", this.width - 114, 26, 28, 20, button -> shiftTabs(1)));
        this.addRenderableWidget(customButton("Rarities", this.width - 490, this.height - 28, 76, 20, button -> this.minecraft.setScreen(new EzBalanceRarityScreen(this, this.workingConfig))));
        this.addRenderableWidget(customButton("Item Groups", this.width - 408, this.height - 28, 78, 20, button -> this.minecraft.setScreen(new EzBalanceItemGroupScreen(this, this.workingConfig))));
        this.addRenderableWidget(customButton("Config", this.width - 324, this.height - 28, 72, 20, button -> this.minecraft.setScreen(new EzBalanceNormalizationConfigScreen(this, this.workingConfig, this.selectedTabId))));
        this.addRenderableWidget(customButton("Edit Tab", this.width - 246, this.height - 28, 72, 20, button -> this.minecraft.setScreen(new EzBalanceTabScreen(this, this.workingConfig, this.selectedTabId))));
        this.addRenderableWidget(customButton("Save", this.width - 168, this.height - 28, 70, 20, button -> persistWorkingConfig()));
        this.addRenderableWidget(customButton("Close", this.width - 90, this.height - 28, 70, 20, button -> this.onClose()));
        this.applyRarityButton = this.addRenderableWidget(customButton("Apply Rarity", 34, 216, LEFT_BUTTON_W, LEFT_BUTTON_H, button -> openBatchRarityPicker()));
        this.applyItemGroupButton = this.addRenderableWidget(customButton("Apply Item Group", 34, 252, LEFT_BUTTON_W, LEFT_BUTTON_H, button -> openBatchItemGroupPicker()));
        this.changeAttributeButton = this.addRenderableWidget(customButton("Change Attribute", 34, 288, LEFT_BUTTON_W, LEFT_BUTTON_H, button -> openAttributeEditor()));
        this.normalizeToggleButton = this.addRenderableWidget(customButton(getNormalizationToggleLabel(), 34, 324, LEFT_BUTTON_W, LEFT_BUTTON_H, button -> toggleNormalization()));
        this.resetButton = this.addRenderableWidget(customButton("Reset", 176, 322, 112, LEFT_BUTTON_H, button -> resetCurrentScope()));
        this.enchantRulesButton = this.addRenderableWidget(customButton("Enchant Rules", 34, 358, LEFT_BUTTON_W, LEFT_BUTTON_H, button -> openEnchantmentEditor()));

        clampTabOffset();
        positionLeftPanelWidgets();
        refreshItems();
    }

    private void onSearchChanged(String value) {
        EzBalanceClientLayoutConfig.get().setSavedSearchText(value);
        this.lastSearchEditAt = Util.getMillis();
        this.pendingSearchRefresh = !value.equals(this.appliedSearchValue);
    }

    private void positionLeftPanelWidgets() {
        positionLeftPanelWidget(this.applyRarityButton, 0, 0, LEFT_BUTTON_W);
        positionLeftPanelWidget(this.applyItemGroupButton, 0, LEFT_BUTTON_H + LEFT_ROW_GAP, LEFT_BUTTON_W);
        positionLeftPanelWidget(this.changeAttributeButton, 0, (LEFT_BUTTON_H + LEFT_ROW_GAP) * 2, LEFT_BUTTON_W);
        positionLeftPanelWidget(this.normalizeToggleButton, 0, LEFT_NORMALIZE_ROW_Y, LEFT_BUTTON_W);
        positionLeftPanelWidget(this.resetButton, LEFT_CONTENT_WIDTH - 112, LEFT_RESET_BUTTON_Y, 112);
        positionLeftPanelWidget(this.enchantRulesButton, 0, LEFT_ENCHANT_ROW_Y + 36, LEFT_BUTTON_W);
    }

    private void positionLeftPanelWidget(Button button, int localX, int localY, int width) {
        if (button == null) {
            return;
        }
        button.setX(getLeftViewportX() + localX - this.leftPanelScrollX);
        button.setY(getLeftViewportY() + localY - this.leftPanelScrollY);
        button.setWidth(width);
    }

    private void shiftTabs(int delta) {
        this.tabOffset = Math.clamp(this.tabOffset + delta, 0, Math.max(0, this.workingConfig.tabs.size() - getTabsPerPage()));
    }

    private void toggleNormalization() {
        this.workingConfig.normalization.enabled = !this.workingConfig.normalization.enabled;
        if (this.normalizeToggleButton != null) {
            this.normalizeToggleButton.setMessage(Component.literal(getNormalizationToggleLabel()));
        }
        persistWorkingConfig();
    }

    private String getNormalizationToggleLabel() {
        return "Normalize: " + (this.workingConfig.normalization != null && this.workingConfig.normalization.enabled ? "Enabled" : "Disabled");
    }

    private int getTabsPerPage() {
        int availableWidth = this.width - RIGHT_X - 170;
        return Math.max(1, (availableWidth - 44) / (TAB_W + TAB_GAP));
    }

    private void clampTabOffset() {
        this.tabOffset = Math.clamp(this.tabOffset, 0, Math.max(0, this.workingConfig.tabs.size() - getTabsPerPage()));
    }

    private void openBatchRarityPicker() {
        if (getActionTargetItems().isEmpty()) {
            return;
        }
        if (this.rarityPickerOpen && this.rarityPickerTargetItemId.isBlank()) {
            closeRarityPicker();
            return;
        }

        stopInlineEdit(false);
        this.rarityPickerOpen = true;
        this.rarityPickerTargetItemId = "";
        this.rarityPickerX = this.applyRarityButton.getX();
        this.rarityPickerY = getAnchoredPopupY(this.applyRarityButton.getY(), this.applyRarityButton.getHeight(), getPickerPopupHeight(getRarityOptions().size()));
        this.rarityPickerWidth = 240;
        this.rarityPickerScroll = 0;
    }

    private void openBatchItemGroupPicker() {
        if (getActionTargetItems().isEmpty()) {
            return;
        }
        if (this.itemGroupPickerOpen && this.itemGroupPickerTargetItemId.isBlank()) {
            closeItemGroupPicker();
            return;
        }

        stopInlineEdit(false);
        this.itemGroupPickerOpen = true;
        this.itemGroupPickerTargetItemId = "";
        this.itemGroupPickerX = this.applyItemGroupButton.getX();
        this.itemGroupPickerY = getAnchoredPopupY(this.applyItemGroupButton.getY(), this.applyItemGroupButton.getHeight(), getPickerPopupHeight(getItemGroupOptions().size()));
        this.itemGroupPickerWidth = 240;
        this.itemGroupPickerScroll = 0;
    }

    private void openCellRarityPicker(TableCellHitbox cell) {
        stopInlineEdit(false);
        this.rarityPickerOpen = true;
        this.rarityPickerTargetItemId = cell.itemId();
        this.rarityPickerX = cell.x1();
        this.rarityPickerY = getAnchoredPopupY(cell.y1(), cell.y2() - cell.y1(), getPickerPopupHeight(getRarityOptions().size()));
        this.rarityPickerWidth = Math.max(150, cell.x2() - cell.x1());
        this.rarityPickerScroll = 0;
    }

    private void closeRarityPicker() {
        this.rarityPickerOpen = false;
        this.rarityPickerScroll = 0;
        this.rarityPickerTargetItemId = "";
        this.rarityHitboxes.clear();
    }

    private void openCellItemGroupPicker(TableCellHitbox cell) {
        stopInlineEdit(false);
        this.itemGroupPickerOpen = true;
        this.itemGroupPickerTargetItemId = cell.itemId();
        this.itemGroupPickerX = cell.x1();
        this.itemGroupPickerY = getAnchoredPopupY(cell.y1(), cell.y2() - cell.y1(), getPickerPopupHeight(getItemGroupOptions().size()));
        this.itemGroupPickerWidth = Math.max(150, cell.x2() - cell.x1());
        this.itemGroupPickerScroll = 0;
    }

    private int getAnchoredPopupY(int anchorY, int anchorHeight, int popupHeight) {
        int below = anchorY + anchorHeight + 4;
        if (below + popupHeight <= this.height - 44) {
            return below;
        }
        return Math.max(40, anchorY - popupHeight - 4);
    }

    private int getPickerPopupHeight(int optionCount) {
        int visibleRows = Math.min(6, Math.max(1, optionCount));
        return 10 + visibleRows * 20 + 38;
    }

    private void closeItemGroupPicker() {
        this.itemGroupPickerOpen = false;
        this.itemGroupPickerScroll = 0;
        this.itemGroupPickerTargetItemId = "";
        this.itemGroupHitboxes.clear();
    }

    private void openAttributeEditor() {
        LinkedHashSet<String> targetItems = getActionTargetItems();
        if (targetItems.isEmpty()) {
            return;
        }
        stopInlineEdit(true);
        closeRarityPicker();
        closeItemGroupPicker();
        EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, targetItems);
        this.minecraft.setScreen(new EzBalanceAttributeBatchScreen(this, this.workingConfig, targetItems, getEditableTabAttributes()));
    }

    private void openEnchantmentEditor() {
        LinkedHashSet<String> targetItems = getSelectedTargetItems();
        if (targetItems.isEmpty()) {
            return;
        }
        stopInlineEdit(true);
        closeRarityPicker();
        closeItemGroupPicker();
        this.minecraft.setScreen(new EzBalanceEnchantmentScreen(this, this.workingConfig, targetItems));
    }

    private void openFullAttributeEditor(String itemId) {
        stopInlineEdit(true);
        closeRarityPicker();
        closeItemGroupPicker();
        EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, List.of(itemId));
        this.minecraft.setScreen(new EzBalanceAttributeBatchScreen(this, this.workingConfig, Set.of(itemId), getEditableAttributesForItem(itemId), true));
    }

    private List<String> getEditableAttributesForItem(String itemId) {
        ItemViewData data = getItemData(itemId);
        LinkedHashSet<String> attributeIds = new LinkedHashSet<>();
        attributeIds.addAll(data.baseAttributes().keySet());
        attributeIds.addAll(EzBalanceRuntime.getOriginalAttributes(this.workingConfig, itemId).keySet());
        attributeIds.addAll(data.resolvedAttributes().keySet());
        if (attributeIds.isEmpty()) {
            attributeIds.addAll(getEditableTabAttributes());
        }
        return new ArrayList<>(attributeIds);
    }

    public void persistWorkingConfig() {
        persistWorkingConfig(this.selectedTabId);
    }

    public void persistWorkingConfig(String selectedTabId) {
        EzBalanceConfig normalized = EzBalanceClientPersistence.persist(this.workingConfig);
        replaceWorkingConfig(normalized);
        if (selectedTabId != null && !selectedTabId.isBlank()) {
            this.selectedTabId = selectedTabId;
        }
        this.savedConfigSnapshot = EzBalanceConfig.fromJson(normalized.toJson());
        invalidateTableData();
        refreshItems();
    }

    private void replaceWorkingConfig(EzBalanceConfig source) {
        EzBalanceClientPersistence.copyInto(this.workingConfig, source);
        if (this.normalizeToggleButton != null) {
            this.normalizeToggleButton.setMessage(Component.literal(getNormalizationToggleLabel()));
        }
    }

    private void applyRaritySelection(String rarityId) {
        closeRarityPicker();
        LinkedHashSet<String> targetItems = !this.rarityPickerTargetItemId.isBlank()
                ? new LinkedHashSet<>(Set.of(this.rarityPickerTargetItemId))
                : new LinkedHashSet<>(getActionEditableItems());
        if (targetItems.isEmpty()) {
            return;
        }
        if (rarityId == null || rarityId.isBlank()) {
            EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, targetItems);
            for (String itemId : targetItems) {
                EzBalanceRuleMutations.clearRarity(this.workingConfig, itemId);
            }
            persistWorkingConfig();
            return;
        }
        this.minecraft.setScreen(new EzBalanceApplySelectionScreen(this, EzBalanceApplyMode.RARITY, rarityId, getRarityOptionLabel(rarityId), targetItems, getRarityAttributeOptions(rarityId), false));
    }

    private void applyItemGroupSelection(String itemGroupId) {
        closeItemGroupPicker();
        LinkedHashSet<String> targetItems = !this.itemGroupPickerTargetItemId.isBlank()
                ? new LinkedHashSet<>(Set.of(this.itemGroupPickerTargetItemId))
                : new LinkedHashSet<>(getActionEditableItems());
        if (targetItems.isEmpty()) {
            return;
        }
        if (itemGroupId == null || itemGroupId.isBlank()) {
            EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, targetItems);
            for (String itemId : targetItems) {
                EzBalanceRuleMutations.clearItemGroup(this.workingConfig, itemId);
            }
            persistWorkingConfig();
            return;
        }
        boolean hasEnchantRules = itemGroupHasEnchantRules(itemGroupId);
        this.minecraft.setScreen(new EzBalanceApplySelectionScreen(this, EzBalanceApplyMode.ITEM_GROUP, itemGroupId, getItemGroupOptionLabel(itemGroupId), targetItems, getItemGroupAttributeOptions(itemGroupId), hasEnchantRules));
    }

    void applyRarityWithScope(String rarityId, Set<String> targetItems, Set<String> selectedAttributes) {
        EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, targetItems);
        for (String itemId : targetItems) {
            EzBalanceRuleMutations.applyScopedRarity(this.workingConfig, itemId, rarityId, selectedAttributes);
        }
        persistWorkingConfig();
        this.minecraft.setScreen(this);
    }

    void applyItemGroupWithScope(String itemGroupId, Set<String> targetItems, Set<String> selectedAttributes, boolean overrideEnchants) {
        EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, targetItems);
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        for (String itemId : targetItems) {
            List<String> conflictReasons = EzBalanceRuntime.getItemGroupConflictReasons(this.workingConfig, itemId, itemGroupId, selectedAttributes);
            if (!conflictReasons.isEmpty()) {
                conflicts.put(itemId, conflictReasons);
                continue;
            }
            EzBalanceRuleMutations.applyScopedItemGroup(this.workingConfig, itemId, itemGroupId, selectedAttributes, overrideEnchants);
        }
        persistWorkingConfig();
        this.minecraft.setScreen(this);
        if (!conflicts.isEmpty()) {
            this.minecraft.setScreen(new EzBalanceItemGroupConflictScreen(this, getItemGroupOptionLabel(itemGroupId), conflicts));
        }
    }

    private List<String> getActionEditableItems() {
        return getActionTargetItems().stream()
                .filter(itemId -> !isLocked(itemId))
                .toList();
    }

    private void cleanupRule(String itemId, EzBalanceItemRule rule) {
        EzBalanceRuleMutations.cleanup(this.workingConfig, itemId, rule);
    }

    public void refreshItems() {
        stopInlineEdit(false);
        closeRarityPicker();
        closeItemGroupPicker();
        this.appliedSearchValue = this.searchBox == null ? "" : this.searchBox.getValue();
        this.pendingSearchRefresh = false;
        if (!this.workingConfig.tabs.containsKey(this.selectedTabId)) {
            this.selectedTabId = this.workingConfig.tabs.keySet().stream().findFirst().orElse("weapons");
        }
        this.visibleItems.clear();
        this.visibleItems.addAll(EzBalanceClientCatalog.getVisibleItemIds(
                this.workingConfig,
                this.selectedTabId,
                this.searchBox == null ? "" : this.searchBox.getValue()
        ));
        invalidateTableData();
        if (this.sortMode == SortMode.ATTRIBUTE) {
            ensureAllVisibleDataLoaded();
        }
        sortVisibleItems();
        this.itemScroll = Math.clamp(this.itemScroll, 0, Math.max(0, this.visibleItems.size() - getVisibleRows()));
        ensureViewportDataLoaded();
        this.tableScrollX = Math.clamp(this.tableScrollX, 0, getMaxHorizontalScroll());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.pendingSearchRefresh && Util.getMillis() - this.lastSearchEditAt >= SEARCH_DEBOUNCE_MS) {
            refreshItems();
        }
    }

    public void setSelectedTabAndRefresh(String tabId) {
        if (tabId != null && !tabId.isBlank()) {
            this.selectedTabId = tabId;
        }
        refreshItems();
    }

    public void applyEditedConfig(EzBalanceConfig source, String tabId) {
        replaceWorkingConfig(EzBalanceConfig.fromJson(source.toJson()));
        if (tabId != null && !tabId.isBlank()) {
            this.selectedTabId = tabId;
        }
        refreshItems();
    }

    public List<String> getVisibleItemsSnapshot() {
        return new ArrayList<>(this.visibleItems);
    }

    private LinkedHashSet<String> getRarityAttributeOptions(String rarityId) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        EzBalanceRarityDefinition rarity = this.workingConfig.rarities.get(rarityId);
        if (rarity != null && rarity.attributeModifiers != null) {
            rarity.attributeModifiers.keySet().stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .filter(attributeId -> !attributeId.isBlank())
                    .forEach(attributes::add);
        }
        return attributes;
    }

    private LinkedHashSet<String> getItemGroupAttributeOptions(String itemGroupId) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        EzBalanceItemGroupDefinition itemGroup = this.workingConfig.itemGroups.get(itemGroupId);
        if (itemGroup != null && itemGroup.attributeValues != null) {
            itemGroup.attributeValues.keySet().stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .filter(attributeId -> !attributeId.isBlank())
                    .forEach(attributes::add);
        }
        return attributes;
    }

    private boolean itemGroupHasEnchantRules(String itemGroupId) {
        EzBalanceItemGroupDefinition itemGroup = this.workingConfig.itemGroups.get(itemGroupId);
        return itemGroup != null && (itemGroup.forceDisabledEnchants || !itemGroup.allowedEnchantments.isEmpty());
    }

    private int getVisibleRows() {
        return Math.max(1, getTableViewportHeight() / TABLE_ROW_H);
    }

    private int getTableX() {
        return RIGHT_X + 18;
    }

    private int getTableHeaderY() {
        return 102;
    }

    private int getTableViewWidth() {
        return Math.max(120, this.width - getTableX() - 24 - TABLE_SCROLLBAR_SIZE - TABLE_SCROLLBAR_GAP);
    }

    private int getTableViewportHeight() {
        return Math.max(20, getBottomScrollbarY() - TABLE_SCROLLBAR_GAP - getTableTop());
    }

    private int getTableViewportRight() {
        return getTableX() + getTableViewWidth();
    }

    private int getTableViewportBottom() {
        return getTableTop() + getTableViewportHeight();
    }

    private int getMaxHorizontalScroll() {
        return Math.max(0, getLayout(getCurrentTableColumns()).contentWidth() - getTableViewWidth());
    }

    private void invalidateTableData() {
        this.loadedItemPages.clear();
        this.itemDataCache.clear();
        this.cachedLayoutColumns = new TableColumns(false, false, List.of());
        this.layoutDirty = true;
    }

    private void ensureAllVisibleDataLoaded() {
        if (!this.visibleItems.isEmpty()) {
            ensureItemDataRange(0, this.visibleItems.size() - 1);
        }
    }

    private void ensureViewportDataLoaded() {
        if (this.visibleItems.isEmpty()) {
            return;
        }
        int firstIndex = Math.max(0, this.itemScroll - TABLE_PAGE_SIZE * TABLE_PAGE_BUFFER);
        int lastIndex = Math.min(
                this.visibleItems.size() - 1,
                this.itemScroll + getVisibleRows() - 1 + TABLE_PAGE_SIZE * TABLE_PAGE_BUFFER
        );
        ensureItemDataRange(firstIndex, lastIndex);
    }

    private void ensureItemDataRange(int firstIndex, int lastIndex) {
        if (firstIndex > lastIndex || this.visibleItems.isEmpty()) {
            return;
        }

        int firstPage = firstIndex / TABLE_PAGE_SIZE;
        int lastPage = lastIndex / TABLE_PAGE_SIZE;
        for (int pageIndex = firstPage; pageIndex <= lastPage; pageIndex++) {
            if (!this.loadedItemPages.containsKey(pageIndex)) {
                loadItemPage(pageIndex);
            }
        }
        trimLoadedPages(firstPage, lastPage);
    }

    private void loadItemPage(int pageIndex) {
        int startIndex = pageIndex * TABLE_PAGE_SIZE;
        int endIndex = Math.min(this.visibleItems.size(), startIndex + TABLE_PAGE_SIZE);
        Map<String, ItemViewData> pageData = new LinkedHashMap<>();
        for (int index = startIndex; index < endIndex; index++) {
            String itemId = this.visibleItems.get(index);
            ItemViewData data = buildItemData(itemId);
            pageData.put(itemId, data);
            this.itemDataCache.put(itemId, data);
        }
        this.loadedItemPages.put(pageIndex, pageData);
        this.layoutDirty = true;
    }

    private void trimLoadedPages(int firstPage, int lastPage) {
        int keepFirst = Math.max(0, firstPage - TABLE_PAGE_BUFFER);
        int keepLast = lastPage + TABLE_PAGE_BUFFER;
        this.loadedItemPages.entrySet().removeIf(entry -> {
            boolean remove = entry.getKey() < keepFirst || entry.getKey() > keepLast;
            if (remove) {
                entry.getValue().keySet().forEach(this.itemDataCache::remove);
            }
            return remove;
        });
    }

    private ItemViewData getItemData(String itemId) {
        ItemViewData cached = this.itemDataCache.get(itemId);
        if (cached != null) {
            return cached;
        }

        int visibleIndex = this.visibleItems.indexOf(itemId);
        if (visibleIndex >= 0) {
            loadItemPage(visibleIndex / TABLE_PAGE_SIZE);
            ItemViewData pageData = this.itemDataCache.get(itemId);
            if (pageData != null) {
                return pageData;
            }
        }

        ItemViewData data = buildItemData(itemId);
        this.itemDataCache.put(itemId, data);
        this.layoutDirty = true;
        return data;
    }

    private ItemViewData buildItemData(String itemId) {
        Item item = EzBalanceClientCatalog.getItem(itemId);
        Map<String, Double> baseAttributes = EzBalanceRuntime.collectBaseAttributes(item.getDefaultInstance());
        Map<String, Double> resolvedAttributes = EzBalanceRuntime.resolveAttributeOverrides(this.workingConfig, itemId);
        return new ItemViewData(item, baseAttributes, resolvedAttributes, getConfiguredRarityLabel(itemId), getConfiguredItemGroupLabel(itemId), isLocked(itemId));
    }

    private void resetCurrentScope() {
        Set<String> targetItems = new LinkedHashSet<>(this.resetVisibleItems ? getAllItemsInSelectedTab() : getSelectedItemsInDisplayOrder());
        targetItems.removeIf(this::isLocked);
        if (targetItems.isEmpty()) {
            return;
        }

        EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, targetItems);
        for (String itemId : targetItems) {
            EzBalanceRuleMutations.restoreOriginalState(this.workingConfig, itemId);
        }
        persistWorkingConfig();
    }

    private List<String> getAllItemsInSelectedTab() {
        return EzBalanceClientCatalog.getVisibleItemIds(this.workingConfig, this.selectedTabId, "");
    }

    private List<String> getCurrentColumns() {
        var tab = this.workingConfig.tabs.get(this.selectedTabId);
        return tab == null ? List.of() : tab.displayAttributes.stream()
                .map(EzBalanceRuntime::normalizeAttributeId)
                .filter(attributeId -> !attributeId.isBlank())
                .distinct()
                .toList();
    }

    private List<String> getEditableTabAttributes() {
        return getCurrentColumns().stream()
                .filter(column -> !EzBalanceRuntime.DPS_COLUMN_ID.equals(column))
                .filter(column -> !EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID.equals(column))
                .toList();
    }

    private TableColumns getCurrentTableColumns() {
        List<String> displayColumns = getCurrentColumns();
        boolean hasDps = displayColumns.contains(EzBalanceRuntime.DPS_COLUMN_ID);
        boolean hasEnchantRules = displayColumns.contains(EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID);
        List<String> attributes = displayColumns.stream()
                .filter(column -> !EzBalanceRuntime.DPS_COLUMN_ID.equals(column))
                .filter(column -> !EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID.equals(column))
                .toList();
        return new TableColumns(hasDps, hasEnchantRules, attributes);
    }

    private void sortVisibleItems() {
        if (this.sortMode == SortMode.NONE) {
            return;
        }

        this.visibleItems.sort((left, right) -> {
            int result = switch (this.sortMode) {
                case ITEM -> left.compareToIgnoreCase(right);
                case RARITY -> getRarityLabel(left).compareToIgnoreCase(getRarityLabel(right));
                case ITEM_GROUP -> getItemGroupLabel(left).compareToIgnoreCase(getItemGroupLabel(right));
                case ATTRIBUTE -> compareAttributeValues(left, right, this.sortAttributeId);
                case NONE -> 0;
            };
            return this.sortDescending ? -result : result;
        });
    }

    private int compareAttributeValues(String leftItemId, String rightItemId, String attributeId) {
        Double leftValue = getDisplayValue(leftItemId, attributeId);
        Double rightValue = getDisplayValue(rightItemId, attributeId);
        if (leftValue == null && rightValue == null) {
            return leftItemId.compareToIgnoreCase(rightItemId);
        }
        if (leftValue == null) {
            return 1;
        }
        if (rightValue == null) {
            return -1;
        }
        int result = Double.compare(leftValue, rightValue);
        return result != 0 ? result : leftItemId.compareToIgnoreCase(rightItemId);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.inlineEditBox != null && this.inlineEditBox.visible && !isInsideInlineEditor(mouseX, mouseY)) {
            if (!stopInlineEdit(true)) {
                return true;
            }
        }

        if (this.rarityPickerOpen) {
            PopupBounds bounds = getRarityPickerBounds();
            for (RarityHitbox hitbox : this.rarityHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    applyRaritySelection(hitbox.rarityId());
                    return true;
                }
            }
            if (bounds.contains(mouseX, mouseY)) {
                return true;
            }
            closeRarityPicker();
        }

        if (this.itemGroupPickerOpen) {
            PopupBounds bounds = getItemGroupPickerBounds();
            for (ItemGroupHitbox hitbox : this.itemGroupHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    applyItemGroupSelection(hitbox.groupId());
                    return true;
                }
            }
            if (bounds.contains(mouseX, mouseY)) {
                return true;
            }
            closeItemGroupPicker();
        }

        if (button == 0 && isInsideResetScopeToggle(mouseX, mouseY)) {
            this.resetVisibleItems = !this.resetVisibleItems;
            return true;
        }

        if (button == 0 && isInsideLeftVerticalScrollbar(mouseX, mouseY)) {
            this.draggingLeftVerticalScrollbar = true;
            updateLeftVerticalScrollFromMouse(mouseY);
            positionLeftPanelWidgets();
            return true;
        }

        if (button == 0 && isInsideLeftHorizontalScrollbar(mouseX, mouseY)) {
            this.draggingLeftHorizontalScrollbar = true;
            updateLeftHorizontalScrollFromMouse(mouseX);
            positionLeftPanelWidgets();
            return true;
        }

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

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (TabHitbox hitbox : this.tabHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                if (hitbox.plus()) {
                    this.minecraft.setScreen(new EzBalanceTabScreen(this, this.workingConfig, ""));
                } else {
                    this.selectedTabId = hitbox.tabId();
                    refreshItems();
                }
                return true;
            }
        }

        ColumnResizeHitbox resizeHitbox = getColumnResizeHitbox(mouseX, mouseY);
        if (button == 0 && resizeHitbox != null) {
            this.resizingColumnKey = resizeHitbox.columnKey();
            this.resizeStartWidth = resizeHitbox.currentWidth();
            this.resizeStartMouseX = mouseX;
            return true;
        }

        TableHeaderHitbox header = getTableHeader(mouseX, mouseY);
        if (button == 0 && header != null) {
            applySort(header);
            return true;
        }

        TableCellHitbox cell = getTableCell(mouseX, mouseY);
        if (cell != null) {
            return handleTableClick(cell, button);
        }

        return false;
    }

    private boolean handleTableClick(TableCellHitbox cell, int button) {
        if (button == 1) {
            if (!this.selectedItems.contains(cell.itemId())) {
                selectOnly(cell.itemId());
            }
            openFullAttributeEditor(cell.itemId());
            return true;
        }
        if (button != 0) {
            return false;
        }

        boolean doubleClick = isDoubleClick(cell, button);
        rememberTableClick(cell, button);

        if (cell.kind() == CellKind.LOCK) {
            toggleLocked(cell.itemId());
            return true;
        }

        applySelectionClick(cell.itemId());
        if (!doubleClick) {
            return true;
        }

        if (cell.kind() == CellKind.RARITY) {
            openCellRarityPicker(cell);
            return true;
        }
        if (cell.kind() == CellKind.ITEM_GROUP) {
            openCellItemGroupPicker(cell);
            return true;
        }
        if (cell.kind() == CellKind.ENCHANT_RULES) {
            this.minecraft.setScreen(new EzBalanceEnchantmentScreen(this, this.workingConfig, Set.of(cell.itemId())));
            return true;
        }
        if (cell.kind() == CellKind.ATTRIBUTE && cell.attributeId() != null) {
            startInlineEdit(cell);
            return true;
        }
        return true;
    }

    private void applySelectionClick(String itemId) {
        if (hasShiftDown() && !this.selectionAnchorItemId.isBlank() && this.visibleItems.contains(this.selectionAnchorItemId)) {
            int anchorIndex = this.visibleItems.indexOf(this.selectionAnchorItemId);
            int clickedIndex = this.visibleItems.indexOf(itemId);
            if (anchorIndex >= 0 && clickedIndex >= 0) {
                this.selectedItems.clear();
                int start = Math.min(anchorIndex, clickedIndex);
                int end = Math.max(anchorIndex, clickedIndex);
                for (int index = start; index <= end; index++) {
                    this.selectedItems.add(this.visibleItems.get(index));
                }
                this.selectedItems.add(this.selectionAnchorItemId);
                this.selectedItems.add(itemId);
                this.selectionLeadItemId = itemId;
                return;
            }
        }

        if (hasControlDown()) {
            if (this.selectedItems.contains(itemId)) {
                this.selectedItems.remove(itemId);
                if (itemId.equals(this.selectionAnchorItemId)) {
                    this.selectionAnchorItemId = this.selectedItems.isEmpty() ? "" : this.selectedItems.iterator().next();
                }
                if (itemId.equals(this.selectionLeadItemId)) {
                    this.selectionLeadItemId = this.selectedItems.isEmpty() ? "" : this.selectionAnchorItemId;
                }
            } else {
                this.selectedItems.add(itemId);
                if (this.selectionAnchorItemId.isBlank()) {
                    this.selectionAnchorItemId = itemId;
                }
                this.selectionLeadItemId = itemId;
            }
            return;
        }

        selectOnly(itemId);
    }

    private void selectOnly(String itemId) {
        this.selectedItems.clear();
        this.selectedItems.add(itemId);
        this.selectionAnchorItemId = itemId;
        this.selectionLeadItemId = itemId;
    }

    private List<String> getSelectedItemsInDisplayOrder() {
        LinkedHashSet<String> orderedSet = new LinkedHashSet<>();
        for (String itemId : this.visibleItems) {
            if (this.selectedItems.contains(itemId)) {
                orderedSet.add(itemId);
            }
        }
        for (String itemId : this.selectedItems) {
            orderedSet.add(itemId);
        }
        return new ArrayList<>(orderedSet);
    }

    private LinkedHashSet<String> getSelectedTargetItems() {
        return new LinkedHashSet<>(getSelectedItemsInDisplayOrder());
    }

    private LinkedHashSet<String> getActionTargetItems() {
        List<String> selected = getSelectedItemsInDisplayOrder();
        if (!selected.isEmpty()) {
            return new LinkedHashSet<>(selected);
        }
        if (this.searchBox != null && !this.searchBox.getValue().isBlank()) {
            return new LinkedHashSet<>(this.visibleItems);
        }
        return new LinkedHashSet<>();
    }

    private void toggleLocked(String itemId) {
        EzBalanceRuleMutations.setLocked(this.workingConfig, itemId, !isLocked(itemId));
        persistWorkingConfig();
    }

    private boolean isLocked(String itemId) {
        EzBalanceItemRule rule = this.workingConfig.items.get(itemId);
        return rule != null && rule.locked;
    }

    private void startInlineEdit(TableCellHitbox cell) {
        closeRarityPicker();
        this.inlineEditItemId = cell.itemId();
        this.inlineEditAttributeId = EzBalanceRuntime.normalizeAttributeId(cell.attributeId());
        Double currentValue = getDisplayValue(cell.itemId(), this.inlineEditAttributeId);
        this.inlineEditBox.setValue(currentValue == null ? "" : formatEditableValue(currentValue));
        this.inlineEditBox.setCursorPosition(this.inlineEditBox.getValue().length());
        this.inlineEditBox.setHighlightPos(this.inlineEditBox.getValue().length());
        this.inlineEditBox.visible = true;
        this.inlineEditBox.active = true;
        positionInlineEditor(cell);
        setFocused(this.inlineEditBox);
    }

    private void positionInlineEditor(TableCellHitbox cell) {
        this.inlineEditBox.setX(cell.x1() + 2);
        this.inlineEditBox.setY(cell.y1() + 2);
        this.inlineEditBox.setWidth(Math.max(40, cell.x2() - cell.x1() - 4));
        this.inlineEditBox.setHeight(TABLE_ROW_H - 4);
    }

    private boolean stopInlineEdit(boolean commit) {
        if (this.inlineEditBox == null || !this.inlineEditBox.visible) {
            return true;
        }

        if (commit && !commitInlineEdit()) {
            return false;
        }

        this.inlineEditBox.visible = false;
        this.inlineEditBox.active = false;
        this.inlineEditBox.setX(-2000);
        this.inlineEditBox.setY(-2000);
        this.inlineEditBox.setValue("");
        this.inlineEditItemId = "";
        this.inlineEditAttributeId = "";
        setFocused(null);
        return true;
    }

    private boolean commitInlineEdit() {
        if (this.inlineEditItemId.isBlank() || this.inlineEditAttributeId.isBlank()) {
            return true;
        }

        String text = this.inlineEditBox.getValue().trim();
        EzBalanceRuntime.captureOriginalAttributes(this.workingConfig, List.of(this.inlineEditItemId));
        if (text.isBlank()) {
            EzBalanceRuleMutations.setAttributeOverride(this.workingConfig, this.inlineEditItemId, this.inlineEditAttributeId, null);
            persistWorkingConfig();
            return true;
        }

        Double value = parseNullableDouble(text);
        if (value == null) {
            return false;
        }

        EzBalanceRuleMutations.setAttributeOverride(this.workingConfig, this.inlineEditItemId, this.inlineEditAttributeId, value);
        persistWorkingConfig();
        return true;
    }

    private boolean isInsideInlineEditor(double mouseX, double mouseY) {
        return mouseX >= this.inlineEditBox.getX()
                && mouseX <= this.inlineEditBox.getX() + this.inlineEditBox.getWidth()
                && mouseY >= this.inlineEditBox.getY()
                && mouseY <= this.inlineEditBox.getY() + this.inlineEditBox.getHeight();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.inlineEditBox != null && this.inlineEditBox.visible && !stopInlineEdit(true)) {
            return true;
        }

        if (this.rarityPickerOpen) {
            PopupBounds bounds = getRarityPickerBounds();
            if (bounds.contains(mouseX, mouseY)) {
                int maxScroll = Math.max(0, getRarityOptions().size() - 6);
                this.rarityPickerScroll = Math.clamp(this.rarityPickerScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }

        if (this.itemGroupPickerOpen) {
            PopupBounds bounds = getItemGroupPickerBounds();
            if (bounds.contains(mouseX, mouseY)) {
                int maxScroll = Math.max(0, getItemGroupOptions().size() - 6);
                this.itemGroupPickerScroll = Math.clamp(this.itemGroupPickerScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }

        if (isInsideLeftViewport(mouseX, mouseY)) {
            if (hasControlDown()) {
                this.leftPanelScrollX = Math.clamp(this.leftPanelScrollX - (int) Math.signum(scrollY) * 24, 0, getLeftPanelMaxScrollX());
            } else {
                this.leftPanelScrollY = Math.clamp(this.leftPanelScrollY - (int) Math.signum(scrollY), 0, getLeftPanelMaxScrollY());
            }
            positionLeftPanelWidgets();
            return true;
        }

        if (isInsideTableRegion(mouseX, mouseY)) {
            if (hasControlDown()) {
                this.tableScrollX = Math.clamp(this.tableScrollX - (int) Math.signum(scrollY) * 36, 0, getMaxHorizontalScroll());
                return true;
            }
            int maxScroll = Math.max(0, this.visibleItems.size() - getVisibleRows());
            this.itemScroll = Math.clamp(this.itemScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.resizingColumnKey.isBlank()) {
            int newWidth = (int) Math.round(this.resizeStartWidth + (mouseX - this.resizeStartMouseX));
            EzBalanceClientLayoutConfig.get().setColumnWidth(this.resizingColumnKey, newWidth);
            this.layoutDirty = true;
            this.tableScrollX = Math.clamp(this.tableScrollX, 0, getMaxHorizontalScroll());
            return true;
        }
        if (this.draggingLeftVerticalScrollbar) {
            updateLeftVerticalScrollFromMouse(mouseY);
            positionLeftPanelWidgets();
            return true;
        }
        if (this.draggingLeftHorizontalScrollbar) {
            updateLeftHorizontalScrollFromMouse(mouseX);
            positionLeftPanelWidgets();
            return true;
        }
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
        this.resizingColumnKey = "";
        this.draggingLeftVerticalScrollbar = false;
        this.draggingLeftHorizontalScrollbar = false;
        this.draggingVerticalScrollbar = false;
        this.draggingHorizontalScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_F && this.searchBox != null) {
            stopInlineEdit(true);
            closeRarityPicker();
            setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            this.searchBox.setHighlightPos(0);
            this.searchBox.setCursorPosition(this.searchBox.getValue().length());
            return true;
        }

        if (this.inlineEditBox != null && this.inlineEditBox.visible) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                return stopInlineEdit(true);
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopInlineEdit(false);
                return true;
            }
        }

        if (this.rarityPickerOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeRarityPicker();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isInsideTable(double mouseX, double mouseY) {
        return mouseX >= getTableX()
                && mouseX <= getTableViewportRight()
                && mouseY >= getTableTop()
                && mouseY <= getTableViewportBottom();
    }

    private boolean isInsideTableRegion(double mouseX, double mouseY) {
        return mouseX >= getTableX()
                && mouseX <= getTableViewportRight()
                && mouseY >= getTableHeaderY()
                && mouseY <= getTableViewportBottom();
    }

    private boolean isInsideVerticalScrollbar(double mouseX, double mouseY) {
        int x1 = getTableViewportRight() + TABLE_SCROLLBAR_GAP;
        int x2 = x1 + TABLE_SCROLLBAR_SIZE;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= getTableTop() && mouseY <= getTableViewportBottom();
    }

    private boolean isInsideHorizontalScrollbar(double mouseX, double mouseY) {
        int y1 = getBottomScrollbarY();
        int y2 = y1 + TABLE_SCROLLBAR_SIZE;
        return mouseX >= getTableX() && mouseX <= getTableViewportRight() && mouseY >= y1 && mouseY <= y2;
    }

    private boolean isInsideLeftViewport(double mouseX, double mouseY) {
        return mouseX >= getLeftViewportX()
                && mouseX <= getLeftViewportRight()
                && mouseY >= getLeftViewportY()
                && mouseY <= getLeftViewportBottom();
    }

    private boolean isInsideLeftVerticalScrollbar(double mouseX, double mouseY) {
        int x1 = getLeftVerticalScrollbarX();
        int x2 = x1 + TABLE_SCROLLBAR_SIZE;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= getLeftViewportY() && mouseY <= getLeftViewportBottom();
    }

    private boolean isInsideLeftHorizontalScrollbar(double mouseX, double mouseY) {
        int y1 = getBottomScrollbarY();
        int y2 = y1 + TABLE_SCROLLBAR_SIZE;
        return mouseX >= getLeftViewportX() && mouseX <= getLeftViewportRight() && mouseY >= y1 && mouseY <= y2;
    }

    private boolean isInsideResetScopeToggle(double mouseX, double mouseY) {
        int x = getLeftViewportX() + LEFT_RESET_LABEL_X - this.leftPanelScrollX;
        int y = getLeftViewportY() + LEFT_RESET_TOGGLE_Y - this.leftPanelScrollY;
        return mouseX >= x
                && mouseX <= x + RESET_SCOPE_SIZE
                && mouseY >= y
                && mouseY <= y + RESET_SCOPE_SIZE;
    }

    private void updateLeftVerticalScrollFromMouse(double mouseY) {
        int maxScroll = getLeftPanelMaxScrollY();
        if (maxScroll == 0) {
            this.leftPanelScrollY = 0;
            return;
        }

        int trackTop = getLeftViewportY();
        int trackHeight = getLeftViewportHeight();
        int thumbHeight = Math.max(18, trackHeight * getLeftViewportHeight() / Math.max(getLeftViewportHeight(), getLeftPanelContentHeight()));
        int maxTravel = Math.max(1, trackHeight - thumbHeight);
        double ratio = (mouseY - trackTop - thumbHeight / 2.0D) / maxTravel;
        this.leftPanelScrollY = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private void updateLeftHorizontalScrollFromMouse(double mouseX) {
        int maxScroll = getLeftPanelMaxScrollX();
        if (maxScroll == 0) {
            this.leftPanelScrollX = 0;
            return;
        }

        int trackLeft = getLeftViewportX();
        int trackWidth = getLeftViewportWidth();
        int thumbWidth = Math.max(24, trackWidth * getLeftViewportWidth() / Math.max(getLeftViewportWidth(), getLeftPanelContentWidth()));
        int maxTravel = Math.max(1, trackWidth - thumbWidth);
        double ratio = (mouseX - trackLeft - thumbWidth / 2.0D) / maxTravel;
        this.leftPanelScrollX = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private void updateVerticalScrollFromMouse(double mouseY) {
        int maxScroll = Math.max(0, this.visibleItems.size() - getVisibleRows());
        if (maxScroll == 0) {
            this.itemScroll = 0;
            return;
        }

        int trackTop = getTableTop();
        int trackHeight = getTableViewportHeight();
        int thumbHeight = Math.max(18, trackHeight * getVisibleRows() / Math.max(getVisibleRows(), this.visibleItems.size()));
        int maxTravel = Math.max(1, trackHeight - thumbHeight);
        double ratio = (mouseY - trackTop - thumbHeight / 2.0D) / maxTravel;
        this.itemScroll = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private void updateHorizontalScrollFromMouse(double mouseX) {
        int maxScroll = getMaxHorizontalScroll();
        if (maxScroll == 0) {
            this.tableScrollX = 0;
            return;
        }

        int trackLeft = getTableX();
        int trackWidth = getTableViewWidth();
        int thumbWidth = Math.max(24, trackWidth * getTableViewWidth() / Math.max(getTableViewWidth(), getLayout(getCurrentTableColumns()).contentWidth()));
        int maxTravel = Math.max(1, trackWidth - thumbWidth);
        double ratio = (mouseX - trackLeft - thumbWidth / 2.0D) / maxTravel;
        this.tableScrollX = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private int getTableTop() {
        return getTableHeaderY() + TABLE_HEADER_H;
    }

    private int getLeftViewportX() {
        return LEFT_X + LEFT_CONTENT_PADDING;
    }

    private int getLeftViewportY() {
        return LEFT_CONTENT_TOP;
    }

    private int getLeftViewportWidth() {
        return Math.max(80, getLeftVerticalScrollbarX() - TABLE_SCROLLBAR_GAP - getLeftViewportX());
    }

    private int getLeftViewportHeight() {
        return Math.max(40, getBottomScrollbarY() - TABLE_SCROLLBAR_GAP - getLeftViewportY());
    }

    private int getLeftViewportRight() {
        return getLeftViewportX() + getLeftViewportWidth();
    }

    private int getLeftViewportBottom() {
        return getLeftViewportY() + getLeftViewportHeight();
    }

    private int getLeftPanelContentWidth() {
        return LEFT_CONTENT_WIDTH;
    }

    private int getLeftPanelContentHeight() {
        return LEFT_ENCHANT_ROW_Y + LEFT_BUTTON_H + 64;
    }

    private int getLeftPanelMaxScrollX() {
        return Math.max(0, getLeftPanelContentWidth() - getLeftViewportWidth());
    }

    private int getLeftPanelMaxScrollY() {
        return Math.max(0, getLeftPanelContentHeight() - getLeftViewportHeight());
    }

    private int getBottomScrollbarY() {
        return this.height - PANEL_HORIZONTAL_SCROLLBAR_Y_OFFSET;
    }

    private int getLeftVerticalScrollbarX() {
        return LEFT_X + LEFT_W - LEFT_VERTICAL_SCROLLBAR_MARGIN - TABLE_SCROLLBAR_SIZE;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, LEFT_X, 16, LEFT_X + LEFT_W, this.height - 36, true);
        drawPanel(graphics, RIGHT_X, RIGHT_PANEL_TOP, this.width - 16, this.height - 36, false);

        positionLeftPanelWidgets();
        renderLeftPanel(graphics);
        renderRightPanel(graphics, mouseX, mouseY);
        updateInlineEditorBounds();
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        if (this.rarityPickerOpen) {
            renderRarityPicker(graphics);
        }
        if (this.itemGroupPickerOpen) {
            renderItemGroupPicker(graphics);
        }
        renderHoveredItemTooltip(graphics, mouseX, mouseY);
        renderResetScopeTooltip(graphics, mouseX, mouseY);
    }

    private void updateInlineEditorBounds() {
        if (this.inlineEditBox == null || !this.inlineEditBox.visible || this.inlineEditItemId.isBlank() || this.inlineEditAttributeId.isBlank()) {
            return;
        }

        TableCellHitbox cell = getVisibleAttributeCell(this.inlineEditItemId, this.inlineEditAttributeId);
        if (cell == null) {
            stopInlineEdit(false);
            return;
        }
        positionInlineEditor(cell);
    }

    private void renderLeftPanel(GuiGraphics graphics) {
        drawPanel(graphics, LEFT_HEADER_X1, LEFT_HEADER_Y1, LEFT_HEADER_X2, LEFT_HEADER_Y2, false);
        drawLabel(graphics, "EZ Balance", LEFT_HEADER_X1 + 12, LEFT_HEADER_Y1 + 14, true);
        graphics.enableScissor(getLeftViewportX(), getLeftViewportY(), getLeftViewportRight(), getLeftViewportBottom());
        renderCheckbox(
                graphics,
                getLeftViewportX() + LEFT_RESET_LABEL_X - this.leftPanelScrollX,
                getLeftViewportY() + LEFT_RESET_TOGGLE_Y - this.leftPanelScrollY,
                this.resetVisibleItems
        );
        drawLabel(
                graphics,
                "All items in tab",
                getLeftViewportX() + LEFT_RESET_LABEL_X + 20 - this.leftPanelScrollX,
                getLeftViewportY() + LEFT_RESET_TOGGLE_Y + 2 - this.leftPanelScrollY,
                false
        );
        graphics.disableScissor();
        renderLeftVerticalScrollbar(graphics);
        renderLeftHorizontalScrollbar(graphics);
    }

    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        renderTabStrip(graphics);
        renderTable(graphics, mouseX, mouseY);
        drawLabel(graphics, "Search", RIGHT_X + 22, 60, false);
        drawLabel(graphics, "Selected rows: " + this.selectedItems.size(), RIGHT_X + 18, this.height - 28, false);
        drawLabel(graphics, "Locked rows: " + this.selectedItems.stream().filter(this::isLocked).count(), RIGHT_X + 146, this.height - 28, false);
    }

    private void renderTabStrip(GuiGraphics graphics) {
        this.tabHitboxes.clear();
        List<String> tabIds = new ArrayList<>(this.workingConfig.tabs.keySet());
        int tabsPerPage = getTabsPerPage();
        clampTabOffset();

        int start = this.tabOffset;
        int end = Math.min(tabIds.size(), start + tabsPerPage);
        int x = RIGHT_X + 18;
        for (int index = start; index < end; index++) {
            String tabId = tabIds.get(index);
            boolean selected = tabId.equals(this.selectedTabId);
            int y = selected ? TAB_Y : TAB_Y + 4;
            int h = selected ? TAB_H + 8 : TAB_H;
            graphics.fill(x, y, x + TAB_W, y + h, selected ? COLOR_SURFACE_ALT : COLOR_SURFACE);
            graphics.fill(x, y, x + TAB_W, y + 1, selected ? COLOR_ACCENT : COLOR_BORDER);
            graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
            graphics.fill(x + TAB_W - 1, y, x + TAB_W, y + h, COLOR_BORDER);
            if (selected) {
                graphics.fill(x, y + h - 1, x + TAB_W, y + h, COLOR_SURFACE_ALT);
            } else {
                graphics.fill(x, y + h - 1, x + TAB_W, y + h, COLOR_BORDER);
            }
            graphics.drawString(this.font, trimToWidth(getTabTitle(tabId), TAB_W - 14), x + 8, y + (selected ? 10 : 8), selected ? COLOR_TEXT : COLOR_MUTED, false);
            this.tabHitboxes.add(new TabHitbox(tabId, x, y, x + TAB_W, y + h, false));
            x += TAB_W + TAB_GAP;
        }

        graphics.fill(x, TAB_Y + 4, x + 34, TAB_Y + TAB_H + 4, COLOR_SURFACE);
        graphics.fill(x, TAB_Y + 4, x + 34, TAB_Y + 5, COLOR_ACCENT);
        graphics.fill(x, TAB_Y + 4, x + 1, TAB_Y + TAB_H + 4, COLOR_BORDER);
        graphics.fill(x + 33, TAB_Y + 4, x + 34, TAB_Y + TAB_H + 4, COLOR_BORDER);
        graphics.fill(x, TAB_Y + TAB_H + 3, x + 34, TAB_Y + TAB_H + 4, COLOR_BORDER);
        graphics.drawString(this.font, "+", x + 13, TAB_Y + 12, COLOR_TEXT, false);
        this.tabHitboxes.add(new TabHitbox("", x, TAB_Y + 4, x + 34, TAB_Y + TAB_H + 4, true));
    }

    private String getTabTitle(String tabId) {
        var tab = this.workingConfig.tabs.get(tabId);
        return tab == null || tab.title.isBlank() ? tabId : tab.title;
    }

    private void renderTable(GuiGraphics graphics, int mouseX, int mouseY) {
        int tableX = getTableX();
        int tableWidth = getTableViewWidth();
        int headerY = getTableHeaderY();
        int rowsY = getTableTop();
        TableColumns columns = getCurrentTableColumns();
        ensureViewportDataLoaded();
        TableLayout layout = getLayout(columns);
        ColumnResizeHitbox hoveredHandle = getColumnResizeHitbox(mouseX, mouseY);

        graphics.fill(tableX, headerY, tableX + tableWidth, headerY + TABLE_HEADER_H, COLOR_SURFACE_ALT);
        graphics.fill(tableX, headerY, tableX + tableWidth, headerY + 1, COLOR_ACCENT);
        graphics.enableScissor(tableX, headerY, tableX + tableWidth, headerY + TABLE_HEADER_H);
        drawTrimmed(graphics, "Lock", tableX + layout.lockX() - this.tableScrollX + 4, headerY + 7, layout.lockWidth() - 8, COLOR_TEXT);
        drawTrimmed(graphics, withSortIndicator("Item", SortMode.ITEM, null), tableX + layout.itemX() - this.tableScrollX + 4, headerY + 7, layout.itemWidth() - 8, COLOR_TEXT);
        if (columns.hasEnchantRules()) {
            drawTrimmed(graphics, "Enchants", tableX + layout.enchantRulesX() - this.tableScrollX + 4, headerY + 7, layout.enchantRulesWidth() - 8, COLOR_TEXT);
        }
        if (columns.hasDps()) {
            drawTrimmed(graphics, withSortIndicator("DPS", SortMode.ATTRIBUTE, EzBalanceRuntime.DPS_COLUMN_ID), tableX + layout.dpsX() - this.tableScrollX + 4, headerY + 7, layout.dpsWidth() - 8, COLOR_TEXT);
        }
        drawTrimmed(graphics, withSortIndicator("Rarity", SortMode.RARITY, null), tableX + layout.rarityX() - this.tableScrollX + 4, headerY + 7, layout.rarityWidth() - 8, COLOR_TEXT);
        drawTrimmed(graphics, withSortIndicator("Group", SortMode.ITEM_GROUP, null), tableX + layout.itemGroupX() - this.tableScrollX + 4, headerY + 7, layout.itemGroupWidth() - 8, COLOR_TEXT);
        for (int index = 0; index < columns.attributes().size(); index++) {
            drawTrimmed(
                    graphics,
                    withSortIndicator(humanize(columns.attributes().get(index)), SortMode.ATTRIBUTE, columns.attributes().get(index)),
                    tableX + layout.metricX(index) - this.tableScrollX + 4,
                    headerY + 7,
                    layout.metricWidth(index) - 8,
                    COLOR_TEXT
            );
        }
        renderColumnHandle(graphics, tableX + layout.lockX() + layout.lockWidth() - this.tableScrollX, headerY, "lock", hoveredHandle);
        renderColumnHandle(graphics, tableX + layout.itemX() + layout.itemWidth() - this.tableScrollX, headerY, "item", hoveredHandle);
        if (columns.hasEnchantRules()) {
            renderColumnHandle(graphics, tableX + layout.enchantRulesX() + layout.enchantRulesWidth() - this.tableScrollX, headerY, "enchants", hoveredHandle);
        }
        if (columns.hasDps()) {
            renderColumnHandle(graphics, tableX + layout.dpsX() + layout.dpsWidth() - this.tableScrollX, headerY, "dps", hoveredHandle);
        }
        renderColumnHandle(graphics, tableX + layout.rarityX() + layout.rarityWidth() - this.tableScrollX, headerY, "rarity", hoveredHandle);
        renderColumnHandle(graphics, tableX + layout.itemGroupX() + layout.itemGroupWidth() - this.tableScrollX, headerY, "item_group", hoveredHandle);
        for (int index = 0; index < columns.attributes().size(); index++) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(columns.attributes().get(index));
            renderColumnHandle(graphics, tableX + layout.metricX(index) + layout.metricWidth(index) - this.tableScrollX, headerY, "attr:" + normalized, hoveredHandle);
        }
        graphics.disableScissor();

        if (this.visibleItems.isEmpty()) {
            graphics.drawString(this.font, "No items matched this tab/filter.", tableX + 4, rowsY + 6, COLOR_MUTED, false);
            renderVerticalScrollbar(graphics);
            renderHorizontalScrollbar(graphics, layout.contentWidth());
            return;
        }

        graphics.enableScissor(tableX, rowsY, tableX + tableWidth, getTableViewportBottom());
        int visibleRows = getVisibleRows();
        for (int row = 0; row < visibleRows; row++) {
            int index = this.itemScroll + row;
            if (index >= this.visibleItems.size()) {
                break;
            }

            String itemId = this.visibleItems.get(index);
            ItemViewData data = getItemData(itemId);
            int y = rowsY + row * TABLE_ROW_H;
            boolean selected = this.selectedItems.contains(itemId);
            boolean locked = data.locked();
            graphics.fill(tableX, y, tableX + tableWidth, y + TABLE_ROW_H - 1, selected ? (0x22111111 | COLOR_ACCENT) : (row % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT));
            if (locked) {
                graphics.fill(tableX, y, tableX + 2, y + TABLE_ROW_H - 1, COLOR_ACCENT);
            }
            Map<String, Double> resolved = data.resolvedAttributes();
            Map<String, Double> base = data.baseAttributes();

            renderCheckbox(graphics, tableX + layout.lockX() - this.tableScrollX + 12, y + 5, locked);
            graphics.renderItem(data.item().getDefaultInstance(), tableX + layout.itemX() - this.tableScrollX + 4, y + 2);
            drawTrimmed(graphics, itemId, tableX + layout.itemX() - this.tableScrollX + 24, y + 6, layout.itemWidth() - 28, COLOR_TEXT);
            if (columns.hasEnchantRules() && hasCustomEnchantmentRules(itemId)) {
                graphics.renderItem(Items.BOOK.getDefaultInstance(), tableX + layout.enchantRulesX() - this.tableScrollX + 14, y + 2);
            }
            if (columns.hasDps()) {
                drawTrimmed(
                        graphics,
                        formatEditableValue(EzBalanceRuntime.calculateProjectedDps(getEffectiveAttributes(data))),
                        tableX + layout.dpsX() - this.tableScrollX + 4,
                        y + 6,
                        layout.dpsWidth() - 8,
                        COLOR_TEXT
                );
            }
            drawTrimmed(graphics, data.rarityLabel(), tableX + layout.rarityX() - this.tableScrollX + 4, y + 6, layout.rarityWidth() - 8, getRarityTextColor(itemId, locked));
            drawTrimmed(graphics, data.itemGroupLabel(), tableX + layout.itemGroupX() - this.tableScrollX + 4, y + 6, layout.itemGroupWidth() - 8, locked ? COLOR_ACCENT : COLOR_TEXT);
            for (int columnIndex = 0; columnIndex < columns.attributes().size(); columnIndex++) {
                String attributeId = EzBalanceRuntime.normalizeAttributeId(columns.attributes().get(columnIndex));
                Double value = resolved.getOrDefault(attributeId, base.get(attributeId));
                boolean overridden = isEditedValue(itemId, attributeId, resolved, base);
                drawTrimmed(
                        graphics,
                        formatValue(value, overridden),
                        tableX + layout.metricX(columnIndex) - this.tableScrollX + 4,
                        y + 6,
                        layout.metricWidth(columnIndex) - 8,
                        getMetricColor(selected, overridden)
                );
            }
        }
        graphics.disableScissor();
        renderVerticalScrollbar(graphics);
        renderHorizontalScrollbar(graphics, layout.contentWidth());
    }

    private void renderCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 10, y + 10, COLOR_BACKGROUND);
        graphics.fill(x, y, x + 10, y + 1, checked ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x, y + 9, x + 10, y + 10, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + 10, COLOR_BORDER);
        graphics.fill(x + 9, y, x + 10, y + 10, COLOR_BORDER);
        if (checked) {
            graphics.fill(x + 2, y + 2, x + 8, y + 8, COLOR_ACCENT);
        }
    }

    private void renderColumnHandle(GuiGraphics graphics, int x, int headerY, String columnKey, ColumnResizeHitbox hoveredHandle) {
        int color = columnKey.equals(this.resizingColumnKey)
                || hoveredHandle != null && columnKey.equals(hoveredHandle.columnKey())
                ? COLOR_ACCENT
                : COLOR_BORDER;
        graphics.fill(x - 1, headerY + 4, x, headerY + TABLE_HEADER_H - 4, color);
    }

    private void renderVerticalScrollbar(GuiGraphics graphics) {
        int x1 = getTableViewportRight() + TABLE_SCROLLBAR_GAP;
        int y1 = getTableTop();
        int y2 = getTableViewportBottom();
        int maxScroll = Math.max(0, this.visibleItems.size() - getVisibleRows());
        int trackHeight = Math.max(1, y2 - y1);
        int thumbHeight = this.visibleItems.isEmpty()
                ? trackHeight
                : Math.max(18, trackHeight * getVisibleRows() / Math.max(getVisibleRows(), this.visibleItems.size()));
        int maxTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = y1 + (maxScroll == 0 ? 0 : maxTravel * this.itemScroll / maxScroll);
        EzBalanceUi.drawVerticalScrollbar(graphics, x1, y1, y2, TABLE_SCROLLBAR_SIZE, thumbY, thumbHeight, this.draggingVerticalScrollbar);
    }

    private void renderHorizontalScrollbar(GuiGraphics graphics, int contentWidth) {
        int x1 = getTableX();
        int x2 = getTableViewportRight();
        int y1 = getBottomScrollbarY();
        int maxScroll = Math.max(0, contentWidth - getTableViewWidth());
        int trackWidth = Math.max(1, x2 - x1);
        int thumbWidth = contentWidth <= 0
                ? trackWidth
                : Math.max(24, trackWidth * getTableViewWidth() / Math.max(getTableViewWidth(), contentWidth));
        int maxTravel = Math.max(0, trackWidth - thumbWidth);
        int thumbX = x1 + (maxScroll == 0 ? 0 : maxTravel * this.tableScrollX / maxScroll);
        EzBalanceUi.drawHorizontalScrollbar(graphics, x1, x2, y1, TABLE_SCROLLBAR_SIZE, thumbX, thumbWidth, this.draggingHorizontalScrollbar);
    }

    private void renderLeftVerticalScrollbar(GuiGraphics graphics) {
        int x1 = getLeftVerticalScrollbarX();
        int y1 = getLeftViewportY();
        int y2 = getLeftViewportBottom();
        int maxScroll = getLeftPanelMaxScrollY();
        int trackHeight = Math.max(1, y2 - y1);
        int thumbHeight = Math.max(18, trackHeight * getLeftViewportHeight() / Math.max(getLeftViewportHeight(), getLeftPanelContentHeight()));
        int maxTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = y1 + (maxScroll == 0 ? 0 : maxTravel * this.leftPanelScrollY / maxScroll);
        EzBalanceUi.drawVerticalScrollbar(graphics, x1, y1, y2, TABLE_SCROLLBAR_SIZE, thumbY, thumbHeight, this.draggingLeftVerticalScrollbar);
    }

    private void renderLeftHorizontalScrollbar(GuiGraphics graphics) {
        int x1 = getLeftViewportX();
        int x2 = getLeftViewportRight();
        int y1 = getBottomScrollbarY();
        int maxScroll = getLeftPanelMaxScrollX();
        int trackWidth = Math.max(1, x2 - x1);
        int thumbWidth = Math.max(24, trackWidth * getLeftViewportWidth() / Math.max(getLeftViewportWidth(), getLeftPanelContentWidth()));
        int maxTravel = Math.max(0, trackWidth - thumbWidth);
        int thumbX = x1 + (maxScroll == 0 ? 0 : maxTravel * this.leftPanelScrollX / maxScroll);
        EzBalanceUi.drawHorizontalScrollbar(graphics, x1, x2, y1, TABLE_SCROLLBAR_SIZE, thumbX, thumbWidth, this.draggingLeftHorizontalScrollbar);
    }

    private void renderResetScopeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isInsideResetScopeToggle(mouseX, mouseY)) {
            return;
        }

        graphics.renderTooltip(
                this.font,
                List.of(
                        Component.literal("All items in tab"),
                        Component.literal("When enabled, Reset affects every item"),
                        Component.literal("that matches the current tab.")
                ),
                java.util.Optional.empty(),
                mouseX,
                mouseY
        );
    }

    private TableLayout getLayout(int columnCount) {
        return getLayout(getCurrentTableColumns());
    }

    private TableLayout getLayout(TableColumns columns) {
        if (!this.layoutDirty && this.cachedLayoutColumns.equals(columns)) {
            return this.cachedLayout;
        }

        EzBalanceClientLayoutConfig layoutConfig = EzBalanceClientLayoutConfig.get();
        int lockWidth = layoutConfig.getColumnWidth("lock", 42);
        int itemWidth = layoutConfig.getColumnWidth("item", getAutoWidth("Item", null, 160, 28));
        int enchantRulesWidth = columns.hasEnchantRules() ? layoutConfig.getColumnWidth("enchants", 68) : 0;
        int dpsWidth = columns.hasDps() ? layoutConfig.getColumnWidth("dps", getAutoWidth("DPS", EzBalanceRuntime.DPS_COLUMN_ID, 116, 0)) : 0;
        int rarityWidth = layoutConfig.getColumnWidth("rarity", getAutoWidth("Rarity", null, 88, 0));
        int itemGroupWidth = layoutConfig.getColumnWidth("item_group", getAutoWidth("Group", "__item_group__", 88, 0));
        List<Integer> metricWidths = new ArrayList<>();
        for (String column : columns.attributes()) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(column);
            String key = "attr:" + normalized;
            int fallback = getAutoWidth(humanize(normalized), normalized, 72, 0);
            metricWidths.add(layoutConfig.getColumnWidth(key, fallback));
        }
        this.cachedLayout = new TableLayout(lockWidth, itemWidth, enchantRulesWidth, dpsWidth, rarityWidth, itemGroupWidth, metricWidths);
        this.cachedLayoutColumns = columns;
        this.layoutDirty = false;
        return this.cachedLayout;
    }

    private int getAutoWidth(String header, String attributeId, int minimum, int extraPadding) {
        int width = this.font.width(header) + 20 + extraPadding;
        List<String> loadedItems = new ArrayList<>(this.itemDataCache.keySet());
        if (attributeId == null) {
            for (String itemId : loadedItems) {
                width = Math.max(width, this.font.width(itemId) + 16 + extraPadding);
            }
            if ("Rarity".equals(header)) {
                for (String itemId : loadedItems) {
                    width = Math.max(width, this.font.width(getRarityLabel(itemId)) + 16);
                }
            } else if ("Group".equals(header) || "__item_group__".equals(attributeId)) {
                for (String itemId : loadedItems) {
                    width = Math.max(width, this.font.width(getItemGroupLabel(itemId)) + 16);
                }
            }
        } else {
            String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
            for (String itemId : loadedItems) {
                ItemViewData data = getItemData(itemId);
                Double value;
                if (EzBalanceRuntime.DPS_COLUMN_ID.equals(normalized)) {
                    value = EzBalanceRuntime.calculateProjectedDps(getEffectiveAttributes(data));
                } else if (data.resolvedAttributes().containsKey(normalized)) {
                    value = data.resolvedAttributes().get(normalized);
                } else {
                    value = data.baseAttributes().get(normalized);
                }
                boolean overridden = isEditedValue(
                        itemId,
                        normalized,
                        data.resolvedAttributes(),
                        data.baseAttributes()
                );
                width = Math.max(width, this.font.width(formatValue(value, overridden)) + 16);
            }
        }
        return Math.max(minimum, width);
    }

    private ColumnResizeHitbox getColumnResizeHitbox(double mouseX, double mouseY) {
        int headerY = getTableHeaderY();
        if (mouseY < headerY || mouseY > headerY + TABLE_HEADER_H) {
            return null;
        }

        TableColumns columns = getCurrentTableColumns();
        TableLayout layout = getLayout(columns);
        int tableX = getTableX();
        int threshold = 4;

        int rightEdge = tableX + layout.lockWidth() - this.tableScrollX;
        if (Math.abs(mouseX - rightEdge) <= threshold) {
            return new ColumnResizeHitbox("lock", layout.lockWidth());
        }

        rightEdge = tableX + layout.itemX() + layout.itemWidth() - this.tableScrollX;
        if (Math.abs(mouseX - rightEdge) <= threshold) {
            return new ColumnResizeHitbox("item", layout.itemWidth());
        }

        if (columns.hasEnchantRules()) {
            rightEdge = tableX + layout.enchantRulesX() + layout.enchantRulesWidth() - this.tableScrollX;
            if (Math.abs(mouseX - rightEdge) <= threshold) {
                return new ColumnResizeHitbox("enchants", layout.enchantRulesWidth());
            }
        }

        if (columns.hasDps()) {
            rightEdge = tableX + layout.dpsX() + layout.dpsWidth() - this.tableScrollX;
            if (Math.abs(mouseX - rightEdge) <= threshold) {
                return new ColumnResizeHitbox("dps", layout.dpsWidth());
            }
        }

        rightEdge = tableX + layout.rarityX() + layout.rarityWidth() - this.tableScrollX;
        if (Math.abs(mouseX - rightEdge) <= threshold) {
            return new ColumnResizeHitbox("rarity", layout.rarityWidth());
        }

        rightEdge = tableX + layout.itemGroupX() + layout.itemGroupWidth() - this.tableScrollX;
        if (Math.abs(mouseX - rightEdge) <= threshold) {
            return new ColumnResizeHitbox("item_group", layout.itemGroupWidth());
        }

        for (int index = 0; index < columns.attributes().size(); index++) {
            rightEdge = tableX + layout.metricX(index) + layout.metricWidth(index) - this.tableScrollX;
            if (Math.abs(mouseX - rightEdge) <= threshold) {
                String normalized = EzBalanceRuntime.normalizeAttributeId(columns.attributes().get(index));
                return new ColumnResizeHitbox("attr:" + normalized, layout.metricWidth(index));
            }
        }
        return null;
    }

    private void renderRarityPicker(GuiGraphics graphics) {
        this.rarityHitboxes.clear();
        List<String> options = getRarityOptions();
        PopupBounds bounds = getRarityPickerBounds();
        int rowHeight = 20;
        drawPanel(graphics, bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(), false);
        drawLabel(graphics, "Select rarity", bounds.x1() + 10, bounds.y1() + 10, true);

        for (int row = 0; row < bounds.visibleRows(); row++) {
            int index = this.rarityPickerScroll + row;
            if (index >= options.size()) {
                break;
            }
            String rarityId = options.get(index);
            int y = bounds.y1() + 28 + row * rowHeight;
            graphics.fill(bounds.x1() + 8, y, bounds.x2() - 8, y + rowHeight - 2, row % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            graphics.drawString(
                    this.font,
                    getRarityOptionLabel(rarityId),
                    bounds.x1() + 16,
                    y + 6,
                    getRarityOptionColor(rarityId, rarityId.equals(getPickerSelectedRarity()) || (rarityId.isBlank() && getPickerSelectedRarity().isBlank())),
                    false
            );
            this.rarityHitboxes.add(new RarityHitbox(rarityId, bounds.x1() + 8, y, bounds.x2() - 8, y + rowHeight - 2));
        }
    }

    private void renderItemGroupPicker(GuiGraphics graphics) {
        this.itemGroupHitboxes.clear();
        List<String> options = getItemGroupOptions();
        PopupBounds bounds = getItemGroupPickerBounds();
        int rowHeight = 20;
        drawPanel(graphics, bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(), false);
        drawLabel(graphics, "Select item group", bounds.x1() + 10, bounds.y1() + 10, true);

        for (int row = 0; row < bounds.visibleRows(); row++) {
            int index = this.itemGroupPickerScroll + row;
            if (index >= options.size()) {
                break;
            }
            String itemGroupId = options.get(index);
            int y = bounds.y1() + 28 + row * rowHeight;
            graphics.fill(bounds.x1() + 8, y, bounds.x2() - 8, y + rowHeight - 2, row % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            graphics.drawString(
                    this.font,
                    getItemGroupOptionLabel(itemGroupId),
                    bounds.x1() + 16,
                    y + 6,
                    itemGroupId.equals(getPickerSelectedItemGroup()) || (itemGroupId.isBlank() && getPickerSelectedItemGroup().isBlank()) ? COLOR_TEXT : COLOR_MUTED,
                    false
            );
            this.itemGroupHitboxes.add(new ItemGroupHitbox(itemGroupId, bounds.x1() + 8, y, bounds.x2() - 8, y + rowHeight - 2));
        }
    }

    private PopupBounds getRarityPickerBounds() {
        int visibleRows = Math.min(6, Math.max(1, getRarityOptions().size()));
        int height = 10 + visibleRows * 20 + 38;
        int x1 = Math.clamp(this.rarityPickerX, 20, this.width - this.rarityPickerWidth - 20);
        int y1 = Math.clamp(this.rarityPickerY, 40, this.height - height - 44);
        return new PopupBounds(x1, y1, x1 + this.rarityPickerWidth, y1 + height, visibleRows);
    }

    private List<String> getRarityOptions() {
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(this.workingConfig.rarities.keySet());
        return options;
    }

    private PopupBounds getItemGroupPickerBounds() {
        int visibleRows = Math.min(6, Math.max(1, getItemGroupOptions().size()));
        int height = 10 + visibleRows * 20 + 38;
        int x1 = Math.clamp(this.itemGroupPickerX, 20, this.width - this.itemGroupPickerWidth - 20);
        int y1 = Math.clamp(this.itemGroupPickerY, 40, this.height - height - 44);
        return new PopupBounds(x1, y1, x1 + this.itemGroupPickerWidth, y1 + height, visibleRows);
    }

    private List<String> getItemGroupOptions() {
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(this.workingConfig.itemGroups.keySet());
        return options;
    }

    private String getPickerSelectedRarity() {
        if (!this.rarityPickerTargetItemId.isBlank()) {
            EzBalanceItemRule rule = this.workingConfig.items.get(this.rarityPickerTargetItemId);
            return rule == null ? "" : rule.rarityId;
        }
        if (this.selectedItems.size() != 1) {
            return "";
        }
        String itemId = this.selectedItems.iterator().next();
        EzBalanceItemRule rule = this.workingConfig.items.get(itemId);
        return rule == null ? "" : rule.rarityId;
    }

    private String getPickerSelectedItemGroup() {
        if (!this.itemGroupPickerTargetItemId.isBlank()) {
            return "";
        }
        List<String> targets = new ArrayList<>(getActionTargetItems());
        if (targets.size() != 1) {
            return "";
        }
        return "";
    }

    private String getRarityOptionLabel(String rarityId) {
        if (rarityId.isBlank()) {
            return "None";
        }
        EzBalanceRarityDefinition rarity = this.workingConfig.rarities.get(rarityId);
        return rarity == null || rarity.name.isBlank() ? rarityId : rarity.name;
    }

    private int getRarityOptionColor(String rarityId, boolean selected) {
        if (rarityId.isBlank()) {
            return selected ? COLOR_TEXT : COLOR_MUTED;
        }
        EzBalanceRarityDefinition rarity = this.workingConfig.rarities.get(rarityId);
        int color = rarity == null ? COLOR_TEXT : 0xFF000000 | rarity.color;
        return selected ? color : color;
    }

    private String getConfiguredRarityLabel(String itemId) {
        EzBalanceItemRule rule = this.workingConfig.items.get(itemId);
        if (rule == null || rule.rarityId.isBlank()) {
            return "-";
        }
        return getRarityOptionLabel(rule.rarityId);
    }

    private String getConfiguredItemGroupLabel(String itemId) {
        List<String> groupIds = new ArrayList<>(EzBalanceRuntime.getAssignedItemGroupIds(this.workingConfig, itemId));
        if (groupIds.isEmpty()) {
            return "-";
        }
        List<String> labels = new ArrayList<>();
        for (String groupId : groupIds) {
            labels.add(getItemGroupOptionLabel(groupId));
        }
        return String.join(", ", labels);
    }

    private String getRarityLabel(String itemId) {
        ItemViewData data = this.itemDataCache.get(itemId);
        return data == null ? getConfiguredRarityLabel(itemId) : data.rarityLabel();
    }

    private String getItemGroupLabel(String itemId) {
        ItemViewData data = this.itemDataCache.get(itemId);
        return data == null ? getConfiguredItemGroupLabel(itemId) : data.itemGroupLabel();
    }

    private List<Component> getItemGroupTooltip(String itemId) {
        List<String> groupIds = new ArrayList<>(EzBalanceRuntime.getAssignedItemGroupIds(this.workingConfig, itemId));
        if (groupIds.isEmpty()) {
            return List.of(Component.literal("No item groups"));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Item Groups"));
        for (String groupId : groupIds) {
            lines.add(Component.literal(getItemGroupOptionLabel(groupId)));
        }
        return lines;
    }

    private String getItemGroupOptionLabel(String itemGroupId) {
        if (itemGroupId.isBlank()) {
            return "None";
        }
        EzBalanceItemGroupDefinition itemGroup = this.workingConfig.itemGroups.get(itemGroupId);
        return itemGroup == null || itemGroup.name.isBlank() ? itemGroupId : itemGroup.name;
    }

    private boolean hasCustomEnchantmentRules(String itemId) {
        return EzBalanceRuntime.hasCustomEnchantmentRules(this.workingConfig, itemId);
    }

    private int getRarityTextColor(String itemId, boolean locked) {
        EzBalanceItemRule rule = this.workingConfig.items.get(itemId);
        if (rule == null || rule.rarityId.isBlank()) {
            return locked ? COLOR_ACCENT : COLOR_MUTED;
        }
        EzBalanceRarityDefinition rarity = this.workingConfig.rarities.get(rule.rarityId);
        if (rarity == null) {
            return locked ? COLOR_ACCENT : COLOR_TEXT;
        }
        return 0xFF000000 | rarity.color;
    }

    private String withSortIndicator(String label, SortMode mode, String attributeId) {
        boolean active = mode == this.sortMode
                && (mode != SortMode.ATTRIBUTE || (attributeId != null && attributeId.equals(this.sortAttributeId)));
        if (!active) {
            return label;
        }
        return label + (this.sortDescending ? " v" : " ^");
    }

    private String humanize(String attributeId) {
        String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
        if (EzBalanceRuntime.DPS_COLUMN_ID.equals(normalized)) {
            return "projected dps";
        }
        int colon = normalized.indexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        return path.replace("generic.", "").replace('_', ' ');
    }

    private Double getDisplayValue(String itemId, String attributeId) {
        String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
        ItemViewData data = getItemData(itemId);
        if (EzBalanceRuntime.DPS_COLUMN_ID.equals(normalized)) {
            return EzBalanceRuntime.calculateProjectedDps(getEffectiveAttributes(data));
        }
        Map<String, Double> resolved = data.resolvedAttributes();
        if (resolved.containsKey(normalized)) {
            return resolved.get(normalized);
        }
        return data.baseAttributes().get(normalized);
    }

    private String formatValue(Double value, boolean overridden) {
        if (value == null) {
            return "-";
        }
        String formatted = formatEditableValue(value);
        return overridden ? formatted + "*" : formatted;
    }

    private String formatEditableValue(Double value) {
        if (value == null) {
            return "-";
        }
        return Math.abs(value - Math.rint(value)) < 0.005
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private int getMetricColor(boolean selected, boolean overridden) {
        if (!overridden) {
            return COLOR_TEXT;
        }
        return selected ? COLOR_BACKGROUND : COLOR_ACCENT;
    }

    private Map<String, Double> getEffectiveAttributes(ItemViewData data) {
        Map<String, Double> effective = new LinkedHashMap<>(data.baseAttributes());
        effective.putAll(data.resolvedAttributes());
        return effective;
    }

    private boolean isEditedValue(String itemId, String attributeId, Map<String, Double> resolved, Map<String, Double> base) {
        String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
        Map<String, Double> savedResolved = EzBalanceRuntime.resolveAttributeOverrides(this.savedConfigSnapshot, itemId);
        if (EzBalanceRuntime.DPS_COLUMN_ID.equals(normalized)) {
            Map<String, Double> currentEffective = new LinkedHashMap<>(base);
            currentEffective.putAll(resolved);
            Map<String, Double> savedEffective = new LinkedHashMap<>(base);
            savedEffective.putAll(savedResolved);
            return Math.abs(EzBalanceRuntime.calculateProjectedDps(currentEffective) - EzBalanceRuntime.calculateProjectedDps(savedEffective)) > 0.00001D;
        }

        Double currentValue = resolved.containsKey(normalized) ? resolved.get(normalized) : base.get(normalized);
        Double savedValue = savedResolved.containsKey(normalized) ? savedResolved.get(normalized) : base.get(normalized);
        if (currentValue == null && savedValue == null) {
            return false;
        }
        if (currentValue == null || savedValue == null) {
            return true;
        }
        return Math.abs(currentValue - savedValue) > 0.00001D;
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

    private boolean isDoubleClick(TableCellHitbox cell, int button) {
        String key = createCellKey(cell, button);
        return button == 0 && key.equals(this.lastTableClickKey) && Util.getMillis() - this.lastTableClickAt <= DOUBLE_CLICK_WINDOW_MS;
    }

    private void rememberTableClick(TableCellHitbox cell, int button) {
        this.lastTableClickKey = createCellKey(cell, button);
        this.lastTableClickAt = Util.getMillis();
    }

    private String createCellKey(TableCellHitbox cell, int button) {
        return cell.itemId() + "|" + cell.kind().name() + "|" + (cell.attributeId() == null ? "" : cell.attributeId()) + "|" + button;
    }

    private void applySort(TableHeaderHitbox header) {
        if (header.mode() == SortMode.NONE) {
            return;
        }

        boolean sameAttribute = header.mode() == SortMode.ATTRIBUTE && this.sortMode == SortMode.ATTRIBUTE && this.sortAttributeId.equals(header.attributeId());
        boolean sameSort = header.mode() == this.sortMode && (header.mode() != SortMode.ATTRIBUTE || sameAttribute);
        if (sameSort) {
            this.sortDescending = !this.sortDescending;
        } else {
            this.sortMode = header.mode();
            this.sortAttributeId = header.attributeId() == null ? "" : header.attributeId();
            this.sortDescending = false;
        }
        invalidateTableData();
        if (this.sortMode == SortMode.ATTRIBUTE) {
            ensureAllVisibleDataLoaded();
        }
        sortVisibleItems();
    }

    private TableHeaderHitbox getTableHeader(double mouseX, double mouseY) {
        int headerY = getTableHeaderY();
        if (mouseY < headerY || mouseY > headerY + TABLE_HEADER_H) {
            return null;
        }
        if (mouseX < getTableX() || mouseX > getTableViewportRight()) {
            return null;
        }

        TableColumns columns = getCurrentTableColumns();
        TableLayout layout = getLayout(columns);
        int contentX = (int) mouseX - getTableX() + this.tableScrollX;
        if (contentX >= layout.itemX() && contentX < layout.itemX() + layout.itemWidth()) {
            return new TableHeaderHitbox(SortMode.ITEM, null);
        }
        if (columns.hasEnchantRules() && contentX >= layout.enchantRulesX() && contentX < layout.enchantRulesX() + layout.enchantRulesWidth()) {
            return new TableHeaderHitbox(SortMode.NONE, EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID);
        }
        if (columns.hasDps() && contentX >= layout.dpsX() && contentX < layout.dpsX() + layout.dpsWidth()) {
            return new TableHeaderHitbox(SortMode.ATTRIBUTE, EzBalanceRuntime.DPS_COLUMN_ID);
        }
        if (contentX >= layout.rarityX() && contentX < layout.rarityX() + layout.rarityWidth()) {
            return new TableHeaderHitbox(SortMode.RARITY, null);
        }
        if (contentX >= layout.itemGroupX() && contentX < layout.itemGroupX() + layout.itemGroupWidth()) {
            return new TableHeaderHitbox(SortMode.ITEM_GROUP, null);
        }
        for (int columnIndex = 0; columnIndex < columns.attributes().size(); columnIndex++) {
            int x1 = layout.metricX(columnIndex);
            int x2 = x1 + layout.metricWidth(columnIndex);
            if (contentX >= x1 && contentX < x2) {
                return new TableHeaderHitbox(SortMode.ATTRIBUTE, columns.attributes().get(columnIndex));
            }
        }
        return null;
    }

    private void renderHoveredItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.rarityPickerOpen || this.inlineEditBox != null && this.inlineEditBox.visible) {
            return;
        }

        TableCellHitbox hoveredCell = getTableCell(mouseX, mouseY);
        if (hoveredCell == null) {
            return;
        }

        if (hoveredCell.kind() == CellKind.ITEM) {
            ItemStack stack = EzBalanceClientCatalog.getItem(hoveredCell.itemId()).getDefaultInstance();
            List<Component> tooltip = new ArrayList<>(stack.getTooltipLines(
                    Item.TooltipContext.EMPTY,
                    this.minecraft.player,
                    this.minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL
            ));
            List<String> tags = EzBalanceClientCatalog.getItemTagIds(hoveredCell.itemId());
            if (!tags.isEmpty()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("Tags"));
                for (String tag : tags) {
                    tooltip.add(Component.literal(tag));
                }
            }
            graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoveredCell.kind() == CellKind.ENCHANT_RULES && hasCustomEnchantmentRules(hoveredCell.itemId())) {
            graphics.renderTooltip(
                    this.font,
                    List.of(Component.literal("Custom enchant rules")),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (hoveredCell.kind() == CellKind.ATTRIBUTE && hoveredCell.attributeId() != null) {
            EzBalanceAttributeValue value = EzBalanceRuntime.getAttributeValue(this.workingConfig, hoveredCell.itemId(), hoveredCell.attributeId());
            graphics.renderTooltip(
                    this.font,
                    List.of(
                            Component.literal(humanize(value.attributeId())),
                            Component.literal("Original: " + formatEditableValue(value.originalValue())),
                            Component.literal("Current: " + formatEditableValue(value.currentValue()))
                    ),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (hoveredCell.kind() == CellKind.ITEM_GROUP) {
            graphics.renderTooltip(
                    this.font,
                    getItemGroupTooltip(hoveredCell.itemId()),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY
            );
        }
    }

    private TableCellHitbox getTableCell(double mouseX, double mouseY) {
        if (!isInsideTable(mouseX, mouseY)) {
            return null;
        }

        int row = (int) ((mouseY - getTableTop()) / TABLE_ROW_H);
        int index = this.itemScroll + row;
        if (index < 0 || index >= this.visibleItems.size()) {
            return null;
        }

        TableColumns columns = getCurrentTableColumns();
        TableLayout layout = getLayout(columns);
        String itemId = this.visibleItems.get(index);
        int y1 = getTableTop() + row * TABLE_ROW_H;
        int y2 = y1 + TABLE_ROW_H - 1;
        int contentX = (int) mouseX - getTableX() + this.tableScrollX;
        if (contentX >= layout.lockX() && contentX < layout.lockX() + layout.lockWidth()) {
            return new TableCellHitbox(
                    itemId,
                    CellKind.LOCK,
                    null,
                    getTableX() + layout.lockX() - this.tableScrollX,
                    y1,
                    getTableX() + layout.lockX() - this.tableScrollX + layout.lockWidth(),
                    y2
            );
        }
        if (contentX >= layout.itemX() && contentX < layout.itemX() + layout.itemWidth()) {
            return new TableCellHitbox(
                    itemId,
                    CellKind.ITEM,
                    null,
                    getTableX() + layout.itemX() - this.tableScrollX,
                    y1,
                    getTableX() + layout.itemX() - this.tableScrollX + layout.itemWidth(),
                    y2
            );
        }
        if (columns.hasEnchantRules() && contentX >= layout.enchantRulesX() && contentX < layout.enchantRulesX() + layout.enchantRulesWidth()) {
            return new TableCellHitbox(
                    itemId,
                    CellKind.ENCHANT_RULES,
                    EzBalanceRuntime.ENCHANT_RULES_COLUMN_ID,
                    getTableX() + layout.enchantRulesX() - this.tableScrollX,
                    y1,
                    getTableX() + layout.enchantRulesX() - this.tableScrollX + layout.enchantRulesWidth(),
                    y2
            );
        }
        if (columns.hasDps() && contentX >= layout.dpsX() && contentX < layout.dpsX() + layout.dpsWidth()) {
            return new TableCellHitbox(
                    itemId,
                    CellKind.DPS,
                    EzBalanceRuntime.DPS_COLUMN_ID,
                    getTableX() + layout.dpsX() - this.tableScrollX,
                    y1,
                    getTableX() + layout.dpsX() - this.tableScrollX + layout.dpsWidth(),
                    y2
            );
        }
        if (contentX >= layout.rarityX() && contentX < layout.rarityX() + layout.rarityWidth()) {
            return new TableCellHitbox(
                itemId,
                    CellKind.RARITY,
                    null,
                    getTableX() + layout.rarityX() - this.tableScrollX,
                    y1,
                    getTableX() + layout.rarityX() - this.tableScrollX + layout.rarityWidth(),
                    y2
            );
        }
        if (contentX >= layout.itemGroupX() && contentX < layout.itemGroupX() + layout.itemGroupWidth()) {
            return new TableCellHitbox(
                    itemId,
                    CellKind.ITEM_GROUP,
                    null,
                    getTableX() + layout.itemGroupX() - this.tableScrollX,
                    y1,
                    getTableX() + layout.itemGroupX() - this.tableScrollX + layout.itemGroupWidth(),
                    y2
            );
        }
        for (int columnIndex = 0; columnIndex < columns.attributes().size(); columnIndex++) {
            int x1 = layout.metricX(columnIndex);
            int x2 = x1 + layout.metricWidth(columnIndex);
            if (contentX >= x1 && contentX < x2) {
                return new TableCellHitbox(
                        itemId,
                        CellKind.ATTRIBUTE,
                        columns.attributes().get(columnIndex),
                        getTableX() + x1 - this.tableScrollX,
                        y1,
                        getTableX() + x2 - this.tableScrollX,
                        y2
                );
            }
        }
        return null;
    }

    private TableCellHitbox getVisibleAttributeCell(String itemId, String attributeId) {
        int rowIndex = this.visibleItems.indexOf(itemId);
        if (rowIndex < this.itemScroll || rowIndex >= this.itemScroll + getVisibleRows()) {
            return null;
        }

        TableColumns columns = getCurrentTableColumns();
        int columnIndex = columns.attributes().indexOf(EzBalanceRuntime.normalizeAttributeId(attributeId));
        if (columnIndex < 0) {
            return null;
        }

        TableLayout layout = getLayout(columns);
        int visibleRow = rowIndex - this.itemScroll;
        int y1 = getTableTop() + visibleRow * TABLE_ROW_H;
        return new TableCellHitbox(
                itemId,
                CellKind.ATTRIBUTE,
                columns.attributes().get(columnIndex),
                getTableX() + layout.metricX(columnIndex) - this.tableScrollX,
                y1,
                getTableX() + layout.metricX(columnIndex) - this.tableScrollX + layout.metricWidth(columnIndex),
                y1 + TABLE_ROW_H - 1
        );
    }

    private record TableLayout(int lockWidth, int itemWidth, int enchantRulesWidth, int dpsWidth, int rarityWidth, int itemGroupWidth, List<Integer> metricWidths) {
        int lockX() {
            return 0;
        }

        int itemX() {
            return this.lockWidth;
        }

        int enchantRulesX() {
            return itemX() + this.itemWidth;
        }

        int dpsX() {
            return enchantRulesX() + this.enchantRulesWidth;
        }

        int rarityX() {
            return dpsX() + this.dpsWidth;
        }

        int itemGroupX() {
            return rarityX() + this.rarityWidth;
        }

        int contentWidth() {
            int width = itemGroupX() + this.itemGroupWidth;
            for (int metricWidth : this.metricWidths) {
                width += metricWidth;
            }
            return width;
        }

        int metricX(int index) {
            int x = itemGroupX() + this.itemGroupWidth;
            for (int currentIndex = 0; currentIndex < index; currentIndex++) {
                x += metricWidth(currentIndex);
            }
            return x;
        }

        int metricWidth(int index) {
            return this.metricWidths.get(index);
        }
    }

    private enum CellKind {
        LOCK,
        ITEM,
        ENCHANT_RULES,
        DPS,
        RARITY,
        ITEM_GROUP,
        ATTRIBUTE
    }

    private enum SortMode {
        NONE,
        ITEM,
        RARITY,
        ITEM_GROUP,
        ATTRIBUTE
    }

    private record TableCellHitbox(String itemId, CellKind kind, String attributeId, int x1, int y1, int x2, int y2) {
    }

    private record TableHeaderHitbox(SortMode mode, String attributeId) {
    }

    private record ColumnResizeHitbox(String columnKey, int currentWidth) {
    }

    private record TableColumns(boolean hasDps, boolean hasEnchantRules, List<String> attributes) {
    }

    private record ItemViewData(
            Item item,
            Map<String, Double> baseAttributes,
            Map<String, Double> resolvedAttributes,
            String rarityLabel,
            String itemGroupLabel,
            boolean locked
    ) {
    }

    private record PopupBounds(int x1, int y1, int x2, int y2, int visibleRows) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x1 && mouseX <= this.x2 && mouseY >= this.y1 && mouseY <= this.y2;
        }
    }

    private record TabHitbox(String tabId, int x1, int y1, int x2, int y2, boolean plus) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x1 && mouseX <= this.x2 && mouseY >= this.y1 && mouseY <= this.y2;
        }
    }

    private record RarityHitbox(String rarityId, int x1, int y1, int x2, int y2) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x1 && mouseX <= this.x2 && mouseY >= this.y1 && mouseY <= this.y2;
        }
    }

    private record ItemGroupHitbox(String groupId, int x1, int y1, int x2, int y2) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x1 && mouseX <= this.x2 && mouseY >= this.y1 && mouseY <= this.y2;
        }
    }
}
