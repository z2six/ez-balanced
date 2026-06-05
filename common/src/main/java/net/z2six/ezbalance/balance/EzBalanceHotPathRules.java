package net.z2six.ezbalance.balance;

import java.util.Set;

public final class EzBalanceHotPathRules {
    private EzBalanceHotPathRules() {}

    public static boolean hasAttributeRules(EzBalanceConfig config, String itemId) {
        if (config == null || itemId == null || itemId.isBlank()) {
            return false;
        }

        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule == null) {
            return false;
        }
        if (rule.attributeOverrides != null && !rule.attributeOverrides.isEmpty()) {
            return true;
        }

        for (String groupId : getAssignedItemGroupIds(config, rule)) {
            EzBalanceItemGroupDefinition itemGroup = config.itemGroups.get(groupId);
            if (itemGroup == null || itemGroup.attributeValues == null || itemGroup.attributeValues.isEmpty()) {
                continue;
            }
            Set<String> scopedAttributes = getAppliedItemGroupAttributes(rule, groupId);
            if (scopedAttributes.isEmpty() || scopedAttributes.stream().anyMatch(itemGroup.attributeValues::containsKey)) {
                return true;
            }
        }

        if (!rule.rarityId.isBlank()) {
            EzBalanceRarityDefinition rarity = config.rarities.get(rule.rarityId);
            if (rarity != null && rarity.attributeModifiers != null && !rarity.attributeModifiers.isEmpty()) {
                Set<String> scopedAttributes = rule.appliedRarityAttributes == null ? Set.of() : rule.appliedRarityAttributes;
                return scopedAttributes.isEmpty() || scopedAttributes.stream().anyMatch(rarity.attributeModifiers::containsKey);
            }
        }

        return false;
    }

    public static boolean hasCustomEnchantmentRules(EzBalanceConfig config, String itemId) {
        if (config == null || itemId == null || itemId.isBlank()) {
            return false;
        }

        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule == null) {
            return false;
        }
        if (rule.hasCustomEnchantmentRules()) {
            return true;
        }

        for (String groupId : getAssignedItemGroupIds(config, rule)) {
            if (rule.itemGroupEnchantRuleIds == null || !rule.itemGroupEnchantRuleIds.contains(groupId)) {
                continue;
            }
            EzBalanceItemGroupDefinition itemGroup = config.itemGroups.get(groupId);
            if (itemGroup != null && (itemGroup.forceDisabledEnchants || !itemGroup.allowedEnchantments.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> getAssignedItemGroupIds(EzBalanceConfig config, EzBalanceItemRule rule) {
        if (config == null || rule == null || rule.itemGroupIds == null) {
            return Set.of();
        }
        return rule.itemGroupIds;
    }

    private static Set<String> getAppliedItemGroupAttributes(EzBalanceItemRule rule, String groupId) {
        if (rule == null || groupId == null || groupId.isBlank() || rule.appliedItemGroupAttributesByGroup == null) {
            return Set.of();
        }
        return rule.appliedItemGroupAttributesByGroup.getOrDefault(groupId, Set.of());
    }
}
