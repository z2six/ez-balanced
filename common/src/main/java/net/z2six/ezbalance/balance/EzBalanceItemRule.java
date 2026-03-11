package net.z2six.ezbalance.balance;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EzBalanceItemRule {
    public String rarityId = "";
    public Set<String> appliedRarityAttributes = new LinkedHashSet<>();
    public Set<String> itemGroupIds = new LinkedHashSet<>();
    public Map<String, Set<String>> appliedItemGroupAttributesByGroup = new LinkedHashMap<>();
    public Set<String> itemGroupEnchantRuleIds = new LinkedHashSet<>();
    public String itemGroupId = "";
    public Set<String> appliedItemGroupAttributes = new LinkedHashSet<>();
    public Map<String, Double> attributeOverrides = new LinkedHashMap<>();
    public boolean locked;
    public boolean restrictEnchantments;
    public Set<String> allowedEnchantments = new LinkedHashSet<>();
    public Set<String> blockedEnchantments = new LinkedHashSet<>();

    public boolean hasCustomEnchantmentRules() {
        return this.restrictEnchantments || !this.allowedEnchantments.isEmpty() || !this.blockedEnchantments.isEmpty();
    }

    public boolean hasAnyChanges() {
        return !this.rarityId.isBlank()
                || !this.appliedRarityAttributes.isEmpty()
                || !this.itemGroupIds.isEmpty()
                || !this.appliedItemGroupAttributesByGroup.isEmpty()
                || !this.itemGroupEnchantRuleIds.isEmpty()
                || !this.attributeOverrides.isEmpty()
                || this.locked
                || this.hasCustomEnchantmentRules();
    }
}
