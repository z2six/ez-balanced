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
import java.util.Set;

public class EzBalanceEnchantmentScreen extends AbstractEzBalanceScreen {
    private static final int LIST_TOP = 96;
    private static final int ROW_HEIGHT = 24;
    private static final int TRACK_WIDTH = 4;
    private static final int COLOR_DEFAULT_ENABLED = 0xFF2A7CFF;

    private final Screen parent;
    private final EzBalanceConfig config;
    private final Set<String> targetItems;
    private final List<String> visibleEnchantments = new ArrayList<>();

    private EditBox searchBox;
    private int scrollRow;
    private boolean draggingScrollbar;

    public EzBalanceEnchantmentScreen(Screen parent, EzBalanceConfig config, Set<String> targetItems) {
        super(Component.literal("Enchant Rules"));
        this.parent = parent;
        this.config = config;
        this.targetItems = new LinkedHashSet<>(targetItems);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        convertLegacyRestrictionRules();
        this.searchBox = new EditBox(this.font, 24, 54, 320, 20, Component.literal("Search enchantments"));
        this.searchBox.setResponder(value -> refreshEnchantments());
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(customButton("Clear Rules", 356, 54, 118, 20, button -> clearRules()));
        this.addRenderableWidget(customButton("Back", this.width - 90, this.height - 28, 70, 20, button -> this.onClose()));
        refreshEnchantments();
    }

    private void convertLegacyRestrictionRules() {
        List<String> allEnchantments = EzBalanceClientCatalog.getAllEnchantmentIds();
        for (String itemId : getEditableItems()) {
            EzBalanceItemRule rule = this.config.items.get(itemId);
            if (rule == null || !rule.restrictEnchantments) {
                continue;
            }
            for (String enchantmentId : allEnchantments) {
                if (!rule.allowedEnchantments.contains(enchantmentId)) {
                    rule.blockedEnchantments.add(enchantmentId);
                }
            }
            rule.restrictEnchantments = false;
        }
    }

    private void refreshEnchantments() {
        this.visibleEnchantments.clear();
        String search = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase();
        for (String enchantmentId : EzBalanceClientCatalog.getAllEnchantmentIds()) {
            if (search.isBlank() || enchantmentId.toLowerCase().contains(search)) {
                this.visibleEnchantments.add(enchantmentId);
            }
        }
        this.scrollRow = Math.clamp(this.scrollRow, 0, getMaxScrollRow());
    }

    private List<String> getEditableItems() {
        return this.targetItems.stream()
                .filter(itemId -> {
                    EzBalanceItemRule rule = this.config.items.get(itemId);
                    return rule == null || !rule.locked;
                })
                .toList();
    }

    private void clearRules() {
        for (String itemId : getEditableItems()) {
            EzBalanceRuleMutations.clearEnchantmentRules(this.config, itemId);
        }
        refreshEnchantments();
    }

    private boolean isDefaultCompatible(String itemId, String enchantmentId) {
        return EzBalanceClientCatalog.getEnchantment(enchantmentId)
                .map(enchantment -> EzBalanceRuntime.supportsEnchantmentByDefault(EzBalanceClientCatalog.getItem(itemId).getDefaultInstance(), enchantment))
                .orElse(false);
    }

    private boolean isExplicitlyAllowed(String itemId, String enchantmentId) {
        EzBalanceItemRule rule = this.config.items.get(itemId);
        return rule != null && rule.allowedEnchantments.contains(enchantmentId);
    }

    private boolean isExplicitlyBlocked(String itemId, String enchantmentId) {
        EzBalanceItemRule rule = this.config.items.get(itemId);
        return rule != null && rule.blockedEnchantments.contains(enchantmentId);
    }

    private boolean isEnabled(String itemId, String enchantmentId) {
        return EzBalanceClientCatalog.getEnchantment(enchantmentId)
                .map(enchantment -> EzBalanceRuntime.isEnchantmentAllowed(this.config, itemId, enchantment, isDefaultCompatible(itemId, enchantmentId)))
                .orElse(false);
    }

    private void toggleEnchantment(String enchantmentId) {
        List<String> editableItems = getEditableItems();
        if (editableItems.isEmpty()) {
            return;
        }
        boolean nextEnabled = !allEditableItemsEnabled(enchantmentId);
        for (String itemId : editableItems) {
            boolean defaultAllowed = isDefaultCompatible(itemId, enchantmentId);
            EzBalanceRuleMutations.setEnchantmentEnabled(this.config, itemId, enchantmentId, nextEnabled, defaultAllowed);
        }
    }

    private boolean allEditableItemsEnabled(String enchantmentId) {
        List<String> editableItems = getEditableItems();
        return !editableItems.isEmpty() && editableItems.stream().allMatch(itemId -> isEnabled(itemId, enchantmentId));
    }

    private boolean anyEditableItemsEnabled(String enchantmentId) {
        return getEditableItems().stream().anyMatch(itemId -> isEnabled(itemId, enchantmentId));
    }

    private boolean anyEditableItemsDefaultEnabled(String enchantmentId) {
        return getEditableItems().stream().anyMatch(itemId -> isDefaultCompatible(itemId, enchantmentId));
    }

    private boolean anyEditableItemsExplicitAllowed(String enchantmentId) {
        return getEditableItems().stream().anyMatch(itemId -> isExplicitlyAllowed(itemId, enchantmentId));
    }

    private boolean anyEditableItemsExplicitBlocked(String enchantmentId) {
        return getEditableItems().stream().anyMatch(itemId -> isExplicitlyBlocked(itemId, enchantmentId));
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
            if (index >= 0 && index < this.visibleEnchantments.size()) {
                toggleEnchantment(this.visibleEnchantments.get(index));
                return true;
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
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int getVisibleRows() {
        return Math.max(1, (this.height - LIST_TOP - 62) / ROW_HEIGHT);
    }

    private int getMaxScrollRow() {
        return Math.max(0, this.visibleEnchantments.size() - getVisibleRows());
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= this.width - 32 && mouseY >= LIST_TOP && mouseY <= this.height - 44;
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int trackX = this.width - 24;
        return mouseX >= trackX && mouseX <= trackX + TRACK_WIDTH && mouseY >= LIST_TOP && mouseY <= this.height - 44;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, "Enchant Rules", 24, 24, true);
        drawLabel(graphics, "Selected items: " + this.targetItems.size(), 24, 36, false);
        drawLabel(graphics, "Editable items: " + getEditableItems().size(), 24, 46, false);
        drawLabel(graphics, "Blue = vanilla/default compatibility. Red = EZ Balance override.", 24, 78, false);

        renderRows(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleRows = getVisibleRows();
        for (int row = 0; row < visibleRows; row++) {
            int index = this.scrollRow + row;
            if (index >= this.visibleEnchantments.size()) {
                break;
            }

            String enchantmentId = this.visibleEnchantments.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseX >= 24 && mouseX <= this.width - 32 && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2;
            boolean checked = allEditableItemsEnabled(enchantmentId);
            boolean partial = !checked && anyEditableItemsEnabled(enchantmentId);
            boolean explicitAllowed = anyEditableItemsExplicitAllowed(enchantmentId);
            boolean explicitBlocked = anyEditableItemsExplicitBlocked(enchantmentId);
            boolean defaultEnabled = anyEditableItemsDefaultEnabled(enchantmentId);
            int background = hovered ? (0x22111111 | COLOR_ACCENT) : (row % 2 == 0 ? COLOR_SURFACE : COLOR_SURFACE_ALT);
            graphics.fill(24, y, this.width - 32, y + ROW_HEIGHT - 2, background);
            renderCheckbox(graphics, 32, y + 6, checked, partial, explicitAllowed, explicitBlocked, defaultEnabled);
            graphics.drawString(this.font, trimToWidth(enchantmentId, this.width - 112), 50, y + 8, COLOR_TEXT, false);
        }
    }

    private void renderCheckbox(GuiGraphics graphics, int x, int y, boolean checked, boolean partial, boolean explicitAllowed, boolean explicitBlocked, boolean defaultEnabled) {
        int borderColor = explicitAllowed || explicitBlocked ? COLOR_ACCENT : (defaultEnabled ? COLOR_DEFAULT_ENABLED : COLOR_BORDER);
        int fillColor = explicitAllowed ? COLOR_ACCENT : COLOR_DEFAULT_ENABLED;
        graphics.fill(x, y, x + 10, y + 10, COLOR_BACKGROUND);
        graphics.fill(x, y, x + 10, y + 1, borderColor);
        graphics.fill(x, y + 9, x + 10, y + 10, borderColor);
        graphics.fill(x, y, x + 1, y + 10, borderColor);
        graphics.fill(x + 9, y, x + 10, y + 10, borderColor);
        if (checked) {
            graphics.fill(x + 2, y + 2, x + 8, y + 8, fillColor);
        } else if (partial) {
            graphics.fill(x + 2, y + 4, x + 8, y + 6, fillColor);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int trackX = this.width - 24;
        int trackHeight = this.height - LIST_TOP - 44;
        graphics.fill(trackX, LIST_TOP, trackX + TRACK_WIDTH, LIST_TOP + trackHeight, COLOR_BORDER);
        int rowCount = this.visibleEnchantments.isEmpty() ? getVisibleRows() : this.visibleEnchantments.size();
        int thumbHeight = Math.max(18, trackHeight * getVisibleRows() / Math.max(getVisibleRows(), rowCount));
        int maxTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = LIST_TOP + (getMaxScrollRow() == 0 ? 0 : maxTravel * this.scrollRow / getMaxScrollRow());
        graphics.fill(trackX, thumbY, trackX + TRACK_WIDTH, thumbY + thumbHeight, COLOR_ACCENT);
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
            screen.applyEditedConfig(this.config, "");
        }
        this.minecraft.setScreen(this.parent);
    }
}
