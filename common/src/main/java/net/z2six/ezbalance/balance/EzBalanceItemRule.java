package net.z2six.ezbalance.balance;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EzBalanceItemRule {
    public String rarityId = "";
    public Set<String> appliedRarityAttributes = new LinkedHashSet<>();
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
                || !this.attributeOverrides.isEmpty()
                || this.locked
                || this.hasCustomEnchantmentRules();
    }
}
