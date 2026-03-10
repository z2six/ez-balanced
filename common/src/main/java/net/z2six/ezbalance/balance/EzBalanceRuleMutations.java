package net.z2six.ezbalance.balance;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class EzBalanceRuleMutations {
    private static final double EPSILON = 0.00001D;

    private EzBalanceRuleMutations() {}

    public static void applyScopedRarity(EzBalanceConfig config, String itemId, String rarityId, Iterable<String> scope) {
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.rarityId = rarityId == null ? "" : rarityId;
        clearManualOverrides(rule, scope);
        rule.appliedRarityAttributes.clear();
        if (!rule.rarityId.isBlank()) {
            for (String attributeId : scope) {
                String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
                if (!normalized.isBlank()) {
                    rule.appliedRarityAttributes.add(normalized);
                }
            }
        }
        cleanup(config, itemId, rule);
    }

    public static void clearRarity(EzBalanceConfig config, String itemId) {
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.rarityId = "";
        rule.appliedRarityAttributes.clear();
        cleanup(config, itemId, rule);
    }

    public static void setAttributeOverride(EzBalanceConfig config, String itemId, String attributeId, Double value) {
        String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
        if (normalized.isBlank()) {
            return;
        }

        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        if (value == null) {
            rule.attributeOverrides.remove(normalized);
            cleanup(config, itemId, rule);
            return;
        }

        Map<String, Double> base = getBaseAttributes(config, itemId);
        Map<String, Double> resolved = EzBalanceRuntime.resolveAttributeOverrides(config, itemId);
        Double baseValue = base.get(normalized);
        Double resolvedValue = resolved.containsKey(normalized) ? resolved.get(normalized) : baseValue;
        boolean matchesBase = equalsNullable(baseValue, value);
        boolean matchesResolved = equalsNullable(resolvedValue, value);
        boolean hasManualOverride = rule.attributeOverrides.containsKey(normalized);

        if (matchesBase || !hasManualOverride && matchesResolved) {
            rule.attributeOverrides.remove(normalized);
        } else {
            rule.attributeOverrides.put(normalized, value);
        }

        cleanup(config, itemId, rule);
    }

    public static void restoreOriginalState(EzBalanceConfig config, String itemId) {
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.rarityId = "";
        rule.appliedRarityAttributes.clear();
        rule.attributeOverrides.clear();

        Map<String, Double> original = EzBalanceRuntime.getOriginalAttributes(config, itemId);
        Map<String, Double> currentBase = EzBalanceRuntime.collectBaseAttributes(itemId);
        if (!original.isEmpty() && !original.equals(currentBase)) {
            rule.attributeOverrides.putAll(new LinkedHashMap<>(original));
        }

        cleanup(config, itemId, rule);
    }

    public static void setLocked(EzBalanceConfig config, String itemId, boolean locked) {
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.locked = locked;
        cleanup(config, itemId, rule);
    }

    public static void setEnchantmentRestriction(EzBalanceConfig config, String itemId, boolean restrict) {
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.restrictEnchantments = restrict;
        cleanup(config, itemId, rule);
    }

    public static void setEnchantmentAllowed(EzBalanceConfig config, String itemId, String enchantmentId, boolean allowed) {
        if (enchantmentId == null || enchantmentId.isBlank()) {
            return;
        }
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.restrictEnchantments = false;
        if (allowed) {
            rule.allowedEnchantments.add(enchantmentId);
            rule.blockedEnchantments.remove(enchantmentId);
        } else {
            rule.allowedEnchantments.remove(enchantmentId);
        }
        cleanup(config, itemId, rule);
    }

    public static void setEnchantmentBlocked(EzBalanceConfig config, String itemId, String enchantmentId, boolean blocked) {
        if (enchantmentId == null || enchantmentId.isBlank()) {
            return;
        }
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.restrictEnchantments = false;
        if (blocked) {
            rule.blockedEnchantments.add(enchantmentId);
            rule.allowedEnchantments.remove(enchantmentId);
        } else {
            rule.blockedEnchantments.remove(enchantmentId);
        }
        cleanup(config, itemId, rule);
    }

    public static void setEnchantmentEnabled(EzBalanceConfig config, String itemId, String enchantmentId, boolean enabled, boolean defaultAllowed) {
        if (enabled == defaultAllowed) {
            setEnchantmentAllowed(config, itemId, enchantmentId, false);
            setEnchantmentBlocked(config, itemId, enchantmentId, false);
            return;
        }
        if (enabled) {
            setEnchantmentAllowed(config, itemId, enchantmentId, true);
        } else {
            setEnchantmentBlocked(config, itemId, enchantmentId, true);
        }
    }

    public static void clearEnchantmentRules(EzBalanceConfig config, String itemId) {
        EzBalanceItemRule rule = getOrCreateRule(config, itemId);
        rule.restrictEnchantments = false;
        rule.allowedEnchantments.clear();
        rule.blockedEnchantments.clear();
        cleanup(config, itemId, rule);
    }

    public static EzBalanceItemRule getOrCreateRule(EzBalanceConfig config, String itemId) {
        return config.items.computeIfAbsent(itemId, key -> new EzBalanceItemRule());
    }

    public static void cleanup(EzBalanceConfig config, String itemId, EzBalanceItemRule rule) {
        if (!rule.hasAnyChanges()) {
            config.items.remove(itemId);
        }
    }

    private static void clearManualOverrides(EzBalanceItemRule rule, Iterable<String> attributes) {
        if (rule.attributeOverrides == null || rule.attributeOverrides.isEmpty()) {
            return;
        }
        for (String attributeId : attributes) {
            String normalized = EzBalanceRuntime.normalizeAttributeId(attributeId);
            if (!normalized.isBlank()) {
                rule.attributeOverrides.remove(normalized);
            }
        }
    }

    private static Map<String, Double> getBaseAttributes(EzBalanceConfig config, String itemId) {
        Map<String, Double> captured = EzBalanceRuntime.getOriginalAttributes(config, itemId);
        if (!captured.isEmpty()) {
            return captured;
        }
        return EzBalanceRuntime.collectBaseAttributes(itemId);
    }

    private static boolean equalsNullable(Double left, Double right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Math.abs(left - right) <= EPSILON;
    }
}
