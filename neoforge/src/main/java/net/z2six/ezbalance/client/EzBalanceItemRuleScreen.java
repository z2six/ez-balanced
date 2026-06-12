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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EzBalanceItemRuleScreen extends AbstractEzBalanceScreen {
    private final Screen parent;
    private final EzBalanceConfig config;
    private final Set<String> targetItems;
    private final List<String> attributeIds;

    private EditBox valueBox;
    private String selectedAttributeId;
    private String selectedRarityId = "";

    public EzBalanceItemRuleScreen(Screen parent, EzBalanceConfig config, Set<String> targetItems) {
        super(Component.literal("Edit Item Rules"));
        this.parent = parent;
        this.config = config;
        this.targetItems = new LinkedHashSet<>(targetItems);
        EzBalanceRuntime.captureOriginalAttributes(this.config, this.targetItems);
        this.attributeIds = new ArrayList<>(EzBalanceClientCatalog.getAllAttributeIds());
        this.selectedAttributeId = EzBalanceClientCatalog.firstOrFallback(this.attributeIds, EzBalanceRuntime.ATTACK_DAMAGE_ATTRIBUTE_ID);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.valueBox = new EditBox(this.font, 24, 86, 120, 20, Component.literal("Attribute value"));
        this.addRenderableWidget(this.valueBox);

        this.addRenderableWidget(customButton("< Attr", 24, 54, 70, 20, button -> this.selectedAttributeId = EzBalanceClientCatalog.previousValue(this.attributeIds, this.selectedAttributeId)));
        this.addRenderableWidget(customButton("Attr >", 100, 54, 70, 20, button -> this.selectedAttributeId = EzBalanceClientCatalog.nextValue(this.attributeIds, this.selectedAttributeId)));
        this.addRenderableWidget(customButton("Cycle Rarity", 188, 54, 110, 20, button -> cycleRarity()));
        this.addRenderableWidget(customButton("Apply Attribute", 24, 118, 130, 20, button -> applyAttributeValue()));
        this.addRenderableWidget(customButton("Clear Attribute", 160, 118, 130, 20, button -> clearAttributeValue()));
        this.addRenderableWidget(customButton("Apply Rarity", 310, 54, 110, 20, button -> applyRarity()));
        this.addRenderableWidget(customButton("Clear Rarity", 430, 54, 110, 20, button -> clearRarity()));
        this.addRenderableWidget(customButton("Enchantments", 310, 86, 140, 20, button -> this.minecraft.setScreen(new EzBalanceEnchantmentScreen(this, this.config, this.targetItems))));
        this.addRenderableWidget(customButton("Clear All Overrides", 310, 118, 150, 20, button -> clearAllOverrides()));
        this.addRenderableWidget(customButton("Back", this.width - 90, this.height - 28, 70, 20, button -> this.onClose()));
    }

    private void cycleRarity() {
        List<String> rarityIds = new ArrayList<>();
        rarityIds.add("");
        rarityIds.addAll(this.config.rarities.keySet());
        int index = rarityIds.indexOf(this.selectedRarityId);
        this.selectedRarityId = rarityIds.get((Math.max(index, 0) + 1) % rarityIds.size());
    }

    private void applyAttributeValue() {
        Double value = parseNullableDouble(this.valueBox.getValue());
        if (value == null) {
            return;
        }

        for (String itemId : this.targetItems) {
            EzBalanceRuleMutations.setAttributeOverride(this.config, itemId, this.selectedAttributeId, value);
        }
        EzBalanceClientPersistence.persist(this.config);
    }

    private void clearAttributeValue() {
        for (String itemId : this.targetItems) {
            EzBalanceRuleMutations.setAttributeOverride(this.config, itemId, this.selectedAttributeId, null);
        }
        EzBalanceClientPersistence.persist(this.config);
    }

    private void applyRarity() {
        for (String itemId : this.targetItems) {
            EzBalanceRuleMutations.applyScopedRarity(this.config, itemId, this.selectedRarityId, this.attributeIds);
        }
        EzBalanceClientPersistence.persist(this.config);
    }

    private void clearRarity() {
        for (String itemId : this.targetItems) {
            EzBalanceRuleMutations.clearRarity(this.config, itemId);
        }
        EzBalanceClientPersistence.persist(this.config);
    }

    private void clearAllOverrides() {
        for (String itemId : this.targetItems) {
            EzBalanceRuleMutations.restoreOriginalState(this.config, itemId);
        }
        EzBalanceClientPersistence.persist(this.config);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        fillBackground(graphics);
        drawPanel(graphics, 16, 16, this.width - 16, this.height - 36, true);
        drawLabel(graphics, "Item Rules", 24, 24, true);
        drawLabel(graphics, "Selected items: " + this.targetItems.size(), 24, 38, false);
        drawLabel(graphics, "Attribute: " + this.selectedAttributeId, 24, 78, false);
        drawLabel(graphics, "Rarity brush: " + (this.selectedRarityId.isBlank() ? "None" : this.selectedRarityId), 310, 30, false);
        String first = this.targetItems.stream().findFirst().orElse("");
        if (!first.isBlank()) {
            drawLabel(graphics, "Selected value: " + formatAttributeValueSummary(first, this.selectedAttributeId), 24, 144, false);
        }

        int y = 160;
        drawLabel(graphics, "Current overrides on first selected item", 24, y, true);
        EzBalanceItemRule rule = this.config.items.get(first);
        if (rule != null) {
            int line = 0;
            for (var entry : rule.attributeOverrides.entrySet()) {
                if (line >= 12) {
                    break;
                }
                drawLabel(graphics, entry.getKey() + ": " + formatAttributeValueSummary(first, entry.getKey()), 24, y + 18 + line * 14, false);
                line++;
            }
            drawLabel(graphics, "Allowed enchant overrides: " + rule.allowedEnchantments.size(), 24, y + 194, false);
            drawLabel(graphics, "Blocked enchant overrides: " + rule.blockedEnchantments.size(), 24, y + 208, false);
        }

        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private String formatAttributeValueSummary(String itemId, String attributeId) {
        EzBalanceAttributeValue value = EzBalanceRuntime.getAttributeValue(this.config, itemId, attributeId);
        return "Original " + formatValue(value.originalValue()) + " -> Current " + formatValue(value.currentValue());
    }

    private String formatValue(Double value) {
        if (value == null) {
            return "-";
        }
        return Math.abs(value - Math.rint(value)) < 0.005D
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
