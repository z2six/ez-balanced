package net.z2six.ezbalance.balance;

import net.z2six.ezbalance.balance.EzBalanceAttributeNormalizationRule;
import net.z2six.ezbalance.balance.EzBalanceItemGroupDefinition;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EzBalanceRuntime {
    public static final String ATTACK_DAMAGE_ATTRIBUTE_ID = "minecraft:generic.attack_damage";
    public static final String ARMOR_ATTRIBUTE_ID = "minecraft:generic.armor";
    public static final String DPS_COLUMN_ID = "ezbalance:projected_dps";
    public static final String ENCHANT_RULES_COLUMN_ID = "ezbalance:enchantment_rules";
    private static final Method SUPPORTS_ENCHANTMENT_METHOD = findSupportsEnchantmentMethod();

    private EzBalanceRuntime() {}

    public static String getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String getItemId(ItemStack stack) {
        return getItemId(stack.getItem());
    }

    public static String getAttributeId(Holder<Attribute> attribute) {
        return attribute.unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(() -> {
                    ResourceLocation location = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
                    return location == null ? "" : location.toString();
                });
    }

    public static String getEnchantmentId(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
    }

    public static String normalizeAttributeId(String attributeId) {
        return EzBalanceAttributeIds.normalize(attributeId);
    }

    public static Map<String, Double> collectBaseAttributes(Item item) {
        return collectBaseAttributes(item.getDefaultInstance());
    }

    public static Map<String, Double> collectBaseAttributes(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Map.of();
        }

        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
        return item == null ? Map.of() : collectBaseAttributes(item);
    }

    public static Map<String, Double> collectBaseAttributes(ItemStack stack) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stack.forEachModifier(slot, (attribute, modifier) -> {
                String attributeId = getAttributeId(attribute);
                if (!attributeId.isBlank()) {
                    values.merge(attributeId, modifier.amount(), EzBalanceRuntime::preferLargerMagnitude);
                }
            });
        }

        if (values.isEmpty()) {
            stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                    .modifiers()
                    .forEach(entry -> {
                        String attributeId = getAttributeId(entry.attribute());
                        if (!attributeId.isBlank()) {
                            values.merge(attributeId, entry.modifier().amount(), EzBalanceRuntime::preferLargerMagnitude);
                        }
                    });
        }
        return values;
    }

    private static double preferLargerMagnitude(double left, double right) {
        return Math.abs(right) > Math.abs(left) ? right : left;
    }

    public static Map<String, Double> resolveAttributeOverrides(EzBalanceConfig config, String itemId) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule == null) {
            return resolved;
        }

        Map<String, Double> base = new LinkedHashMap<>(getBaseAttributes(config, itemId));

        for (String groupId : getAssignedItemGroupIds(config, itemId)) {
            EzBalanceItemGroupDefinition itemGroup = config.itemGroups.get(groupId);
            if (itemGroup == null || itemGroup.attributeValues == null) {
                continue;
            }
            Set<String> scopedAttributes = getAppliedItemGroupAttributes(rule, groupId);
            if (scopedAttributes.isEmpty()) {
                itemGroup.attributeValues.forEach((attributeId, value) -> {
                    if (value != null && !resolved.containsKey(attributeId)) {
                        resolved.put(attributeId, applyNormalization(config, itemId, attributeId, value));
                    }
                });
            } else {
                for (String attributeId : scopedAttributes) {
                    Double value = itemGroup.attributeValues.get(attributeId);
                    if (value != null && !resolved.containsKey(attributeId)) {
                        resolved.put(attributeId, applyNormalization(config, itemId, attributeId, value));
                    }
                }
            }
        }

        if (!rule.rarityId.isBlank()) {
            EzBalanceRarityDefinition rarity = config.rarities.get(rule.rarityId);
            if (rarity != null && rarity.attributeModifiers != null) {
                Set<String> scopedAttributes = rule.appliedRarityAttributes == null ? Set.of() : rule.appliedRarityAttributes;
                if (scopedAttributes.isEmpty()) {
                    rarity.attributeModifiers.forEach((attributeId, modifierExpression) -> applyRarityModifier(config, itemId, resolved, base, attributeId, modifierExpression));
                } else {
                    for (String attributeId : scopedAttributes) {
                        String modifierExpression = rarity.attributeModifiers.get(attributeId);
                        applyRarityModifier(config, itemId, resolved, base, attributeId, modifierExpression);
                    }
                }
            }
        }

        if (rule.attributeOverrides != null) {
            resolved.putAll(rule.attributeOverrides);
        }

        return resolved;
    }

    public static boolean hasAttributeRules(EzBalanceConfig config, String itemId) {
        return EzBalanceHotPathRules.hasAttributeRules(config, itemId);
    }

    public static double applyNormalization(EzBalanceConfig config, String itemId, String attributeId, double targetValue) {
        String normalized = normalizeAttributeId(attributeId);
        if (config == null || config.normalization == null || !config.normalization.enabled || normalized.isBlank()) {
            return targetValue;
        }

        Map<String, Double> original = getOriginalAttributes(config, itemId);
        Double originalValue = original.get(normalized);
        if (originalValue == null) {
            originalValue = collectBaseAttributes(itemId).get(normalized);
        }
        if (originalValue == null) {
            return targetValue;
        }

        EzBalanceAttributeNormalizationRule rule = getNormalizationRule(config, normalized);
        double preserveFactor = Math.clamp(config.normalization.preserveFactor, 0.0D, 1.0D);
        double offset = (originalValue - targetValue) * preserveFactor;
        double reference = Math.max(1.0D, Math.abs(targetValue));
        double minOffset = rule != null && rule.minRawOffset != null
                ? rule.minRawOffset
                : config.normalization.defaultMinRawOffset != null
                ? config.normalization.defaultMinRawOffset
                : getMinPercent(config, rule) * reference;
        double maxOffset = rule != null && rule.maxRawOffset != null
                ? rule.maxRawOffset
                : config.normalization.defaultMaxRawOffset != null
                ? config.normalization.defaultMaxRawOffset
                : getMaxPercent(config, rule) * reference;

        if (minOffset > maxOffset) {
            double swap = minOffset;
            minOffset = maxOffset;
            maxOffset = swap;
        }

        return targetValue + Math.clamp(offset, minOffset, maxOffset);
    }

    public static Set<String> getAssignedItemGroupIds(EzBalanceConfig config, String itemId) {
        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule == null || rule.itemGroupIds == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(rule.itemGroupIds);
    }

    public static Set<String> getAppliedItemGroupAttributes(EzBalanceItemRule rule, String groupId) {
        if (rule == null || groupId == null || groupId.isBlank() || rule.appliedItemGroupAttributesByGroup == null) {
            return Set.of();
        }
        return rule.appliedItemGroupAttributesByGroup.getOrDefault(groupId, Set.of());
    }

    public static boolean isItemGroupEnchantRulesApplied(EzBalanceConfig config, String itemId, String groupId) {
        EzBalanceItemRule rule = config.items.get(itemId);
        return rule != null && rule.itemGroupEnchantRuleIds != null && rule.itemGroupEnchantRuleIds.contains(groupId);
    }

    public static Map<String, Double> getBaseAttributes(EzBalanceConfig config, String itemId) {
        Map<String, Double> captured = getOriginalAttributes(config, itemId);
        if (!captured.isEmpty()) {
            return captured;
        }
        return collectBaseAttributes(itemId);
    }

    public static Double applyRarityModifierExpression(double currentValue, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String trimmed = expression.trim();
        if (trimmed.endsWith("%")) {
            Double percent = parseDouble(trimmed.substring(0, trimmed.length() - 1));
            if (percent == null) {
                return null;
            }
            return currentValue * (1.0D + percent / 100.0D);
        }
        Double raw = parseDouble(trimmed);
        if (raw == null) {
            return null;
        }
        return currentValue + raw;
    }

    public static EzBalanceAttributeNormalizationRule getNormalizationRule(EzBalanceConfig config, String attributeId) {
        if (config == null || config.normalization == null || config.normalization.attributes == null) {
            return null;
        }
        String normalized = normalizeAttributeId(attributeId);
        return config.normalization.attributes.stream()
                .filter(rule -> normalized.equals(normalizeAttributeId(rule.attributeId)))
                .findFirst()
                .orElse(null);
    }

    private static double getMinPercent(EzBalanceConfig config, EzBalanceAttributeNormalizationRule rule) {
        return rule == null ? config.normalization.defaultMinPercent : rule.minPercent;
    }

    private static double getMaxPercent(EzBalanceConfig config, EzBalanceAttributeNormalizationRule rule) {
        return rule == null ? config.normalization.defaultMaxPercent : rule.maxPercent;
    }

    public static double calculateProjectedDps(Map<String, Double> attributes) {
        double attackDamage = attributes.getOrDefault(ATTACK_DAMAGE_ATTRIBUTE_ID, 0.0D);
        double attackSpeed = attributes.getOrDefault("minecraft:generic.attack_speed", 0.0D);
        return Math.max(0.0D, attackDamage * Math.max(0.0D, attackSpeed + 4.0D));
    }

    public static void captureOriginalAttributes(EzBalanceConfig config, Iterable<String> itemIds) {
        if (config == null || itemIds == null) {
            return;
        }

        for (String itemId : itemIds) {
            if (itemId == null || itemId.isBlank() || config.originalAttributes.containsKey(itemId)) {
                continue;
            }

            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
            if (item == null) {
                continue;
            }

            config.originalAttributes.put(itemId, new LinkedHashMap<>(collectBaseAttributes(item)));
        }
    }

    public static Map<String, Double> getOriginalAttributes(EzBalanceConfig config, String itemId) {
        if (config == null || itemId == null || itemId.isBlank()) {
            return Map.of();
        }

        return config.originalAttributes.getOrDefault(itemId, Map.of());
    }

    public static EzBalanceAttributeValue getAttributeValue(EzBalanceConfig config, String itemId, String attributeId) {
        Map<String, Double> base = getBaseAttributes(config, itemId);
        Map<String, Double> resolved = config == null ? Map.of() : resolveAttributeOverrides(config, itemId);
        return EzBalanceAttributeValues.getAttributeValue(config, itemId, attributeId, base, resolved);
    }

    public static Optional<EzBalanceItemRule> getRule(EzBalanceConfig config, String itemId) {
        return Optional.ofNullable(config.items.get(itemId));
    }

    public static boolean isEnchantmentAllowed(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment) {
        return isEnchantmentAllowed(config, itemId, enchantment, true);
    }

    public static boolean isEnchantmentAllowed(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment, boolean defaultAllowed) {
        EzBalanceItemRule rule = config.items.get(itemId);
        boolean groupAdjustedAllowed = isEnchantmentAllowedByItemGroup(config, itemId, enchantment, defaultAllowed);
        if (rule == null) {
            return groupAdjustedAllowed;
        }

        String enchantmentId = getEnchantmentId(enchantment);
        if (rule.blockedEnchantments.contains(enchantmentId)) {
            return false;
        }
        if (rule.allowedEnchantments.contains(enchantmentId)) {
            return true;
        }
        if (rule.restrictEnchantments) {
            return false;
        }
        return groupAdjustedAllowed;
    }

    public static boolean isEnchantmentExplicitlyAllowed(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment) {
        EzBalanceItemRule rule = config.items.get(itemId);
        return rule != null && rule.allowedEnchantments.contains(getEnchantmentId(enchantment));
    }

    public static boolean isEnchantmentExplicitlyBlocked(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment) {
        EzBalanceItemRule rule = config.items.get(itemId);
        return rule != null && rule.blockedEnchantments.contains(getEnchantmentId(enchantment));
    }

    public static boolean supportsEnchantmentByDefault(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack == null || enchantment == null) {
            return false;
        }

        if (SUPPORTS_ENCHANTMENT_METHOD != null) {
            try {
                Object result = SUPPORTS_ENCHANTMENT_METHOD.invoke(stack, enchantment);
                if (result instanceof Boolean allowed) {
                    return allowed;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return enchantment.value().canEnchant(stack);
    }

    public static boolean hasCustomEnchantmentRules(EzBalanceConfig config, String itemId) {
        return EzBalanceHotPathRules.hasCustomEnchantmentRules(config, itemId);
    }

    public static EzBalanceItemGroupDefinition getAssignedItemGroup(EzBalanceConfig config, String itemId) {
        return getAssignedItemGroups(config, itemId).stream().findFirst().orElse(null);
    }

    public static List<EzBalanceItemGroupDefinition> getAssignedItemGroups(EzBalanceConfig config, String itemId) {
        return getAssignedItemGroupIds(config, itemId).stream()
                .map(config.itemGroups::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<EzBalanceItemGroupDefinition> getAssignedItemGroupsWithEnchantRules(EzBalanceConfig config, String itemId) {
        return getAssignedItemGroupIds(config, itemId).stream()
                .filter(groupId -> isItemGroupEnchantRulesApplied(config, itemId, groupId))
                .map(config.itemGroups::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public static boolean canAssignItemGroup(EzBalanceConfig config, String itemId, String itemGroupId, Iterable<String> scope) {
        return getItemGroupConflictReasons(config, itemId, itemGroupId, scope).isEmpty();
    }

    public static List<String> getItemGroupConflictReasons(EzBalanceConfig config, String itemId, String itemGroupId, Iterable<String> scope) {
        EzBalanceItemGroupDefinition newGroup = config.itemGroups.get(itemGroupId);
        if (newGroup == null) {
            return List.of("Item group does not exist.");
        }
        Set<String> scopedAttributes = new LinkedHashSet<>();
        for (String attributeId : scope) {
            String normalized = normalizeAttributeId(attributeId);
            if (!normalized.isBlank() && newGroup.attributeValues.containsKey(normalized)) {
                scopedAttributes.add(normalized);
            }
        }
        List<String> reasons = new java.util.ArrayList<>();
        for (String existingId : getAssignedItemGroupIds(config, itemId)) {
            if (existingId.equals(itemGroupId)) {
                return List.of();
            }
            EzBalanceItemGroupDefinition existing = config.itemGroups.get(existingId);
            if (existing == null) {
                continue;
            }
            Set<String> existingScoped = getAppliedItemGroupAttributes(config.items.get(itemId), existingId);
            Set<String> existingAttributes = existingScoped.isEmpty() ? existing.attributeValues.keySet() : existingScoped;
            Set<String> overlappingAttributes = existingAttributes.stream()
                    .filter(scopedAttributes::contains)
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!overlappingAttributes.isEmpty()) {
                reasons.add("Attributes overlap with " + getItemGroupName(existing, existingId) + ": " + String.join(", ", overlappingAttributes));
            }
            Set<String> overlappingEnchantments = existing.allowedEnchantments.stream()
                    .filter(newGroup.allowedEnchantments::contains)
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!overlappingEnchantments.isEmpty()) {
                reasons.add("Allowed enchants overlap with " + getItemGroupName(existing, existingId) + ": " + String.join(", ", overlappingEnchantments));
            }
            if (existing.forceDisabledEnchants && newGroup.forceDisabledEnchants) {
                reasons.add("Both " + getItemGroupName(existing, existingId) + " and " + getItemGroupName(newGroup, itemGroupId) + " force-disable default enchants.");
            }
        }
        return reasons;
    }

    public static boolean isEnchantmentAllowedByItemGroup(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment, boolean defaultAllowed) {
        String enchantmentId = getEnchantmentId(enchantment);
        boolean allowed = defaultAllowed;
        for (EzBalanceItemGroupDefinition itemGroup : getAssignedItemGroupsWithEnchantRules(config, itemId)) {
            if (itemGroup.allowedEnchantments.contains(enchantmentId)) {
                allowed = true;
                continue;
            }
            if (itemGroup.forceDisabledEnchants && defaultAllowed) {
                allowed = false;
            }
        }
        return allowed;
    }

    public static boolean isEnchantmentExplicitlyAllowedByItemGroup(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment) {
        String enchantmentId = getEnchantmentId(enchantment);
        return getAssignedItemGroupsWithEnchantRules(config, itemId).stream().anyMatch(itemGroup -> itemGroup.allowedEnchantments.contains(enchantmentId));
    }

    public static boolean isEnchantmentExplicitlyBlockedByItemGroup(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment, boolean defaultAllowed) {
        String enchantmentId = getEnchantmentId(enchantment);
        return getAssignedItemGroupsWithEnchantRules(config, itemId).stream().anyMatch(itemGroup ->
                itemGroup.forceDisabledEnchants
                        && defaultAllowed
                        && !itemGroup.allowedEnchantments.contains(enchantmentId));
    }

    public static boolean matchesTab(EzBalanceTabDefinition tab, Item item) {
        ItemStack stack = item.getDefaultInstance();
        String itemId = getItemId(item);
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        String searchBase = itemId.toLowerCase(Locale.ROOT);
        String hoverName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);

        if (!tab.includeItemIds.isEmpty() && !tab.includeItemIds.contains(itemId)) {
            return false;
        }
        if (tab.excludeItemIds.contains(itemId)) {
            return false;
        }
        if (!tab.includeNamespaces.isEmpty() && !tab.includeNamespaces.contains(namespace)) {
            return false;
        }
        if (tab.excludeNamespaces.contains(namespace)) {
            return false;
        }
        List<String> nameFilters = tab.nameFilters == null || tab.nameFilters.isEmpty()
                ? (tab.searchText == null || tab.searchText.isBlank() ? List.of() : List.of(tab.searchText))
                : tab.nameFilters;
        for (String filter : nameFilters) {
            String lowered = filter.toLowerCase(Locale.ROOT);
            if (!searchBase.contains(lowered) && !hoverName.contains(lowered)) {
                return false;
            }
        }
        for (String filter : tab.excludedNameFilters) {
            String lowered = filter.toLowerCase(Locale.ROOT);
            if (searchBase.contains(lowered) || hoverName.contains(lowered)) {
                return false;
            }
        }
        if (!tab.includeTags.isEmpty()) {
            if (tab.matchAnyTags) {
                boolean matched = false;
                for (String tag : tab.includeTags) {
                    ResourceLocation location = ResourceLocation.tryParse(tag);
                    if (location != null && stack.is(TagKey.create(Registries.ITEM, location))) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            } else {
                for (String tag : tab.includeTags) {
                    ResourceLocation location = ResourceLocation.tryParse(tag);
                    if (location == null || !stack.is(TagKey.create(Registries.ITEM, location))) {
                        return false;
                    }
                }
            }
        }
        if (tab.excludeTags.stream().anyMatch(tag -> stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag))))) {
            return false;
        }

        Map<String, Double> attributes = collectBaseAttributes(stack);
        if (!tab.requiredAttributes.isEmpty()) {
            long matches = tab.requiredAttributes.stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .filter(attributes::containsKey)
                    .count();
            boolean requireAll = tab.requireAllAttributes || tab.requiredAttributes.size() > 1;
            if (requireAll) {
                if (matches != tab.requiredAttributes.size()) {
                    return false;
                }
            } else if (matches == 0) {
                return false;
            }
        }
        if (!tab.excludedAttributes.isEmpty()) {
            long excludedMatches = tab.excludedAttributes.stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .filter(attributes::containsKey)
                    .count();
            if (excludedMatches > 0) {
                return false;
            }
        }

        for (Map.Entry<String, EzBalanceAttributeRange> entry : tab.attributeRanges.entrySet()) {
            Double value = attributes.get(normalizeAttributeId(entry.getKey()));
            if (value == null || !Objects.requireNonNullElseGet(entry.getValue(), EzBalanceAttributeRange::new).matches(value)) {
                return false;
            }
        }

        return true;
    }

    public static Component createRarityLine(EzBalanceConfig config, String itemId) {
        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule == null || rule.rarityId.isBlank()) {
            return Component.empty();
        }

        EzBalanceRarityDefinition rarity = config.rarities.get(rule.rarityId);
        if (rarity == null || rarity.name.isBlank()) {
            return Component.empty();
        }

        return Component.literal(rarity.name).withColor(rarity.color);
    }

    public static List<Component> createTooltipLines(EzBalanceConfig config, String itemId) {
        return EzBalanceTooltipPolicy.shouldShowManagedByModLine(config, itemId) ? List.of(Component.empty()) : List.of();
    }

    private static void applyRarityModifier(EzBalanceConfig config, String itemId, Map<String, Double> resolved, Map<String, Double> base, String attributeId, String modifierExpression) {
        String normalized = normalizeAttributeId(attributeId);
        if (normalized.isBlank() || modifierExpression == null || modifierExpression.isBlank()) {
            return;
        }
        double currentValue = resolved.containsKey(normalized)
                ? resolved.get(normalized)
                : base.getOrDefault(normalized, 0.0D);
        Double targetValue = applyRarityModifierExpression(currentValue, modifierExpression);
        if (targetValue != null) {
            resolved.put(normalized, targetValue);
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean hasEnchantmentConflict(EzBalanceItemGroupDefinition left, EzBalanceItemGroupDefinition right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.allowedEnchantments.stream().anyMatch(right.allowedEnchantments::contains)) {
            return true;
        }
        return left.forceDisabledEnchants && right.forceDisabledEnchants;
    }

    private static String getItemGroupName(EzBalanceItemGroupDefinition itemGroup, String fallbackId) {
        if (itemGroup == null) {
            return fallbackId;
        }
        return itemGroup.name == null || itemGroup.name.isBlank() ? fallbackId : itemGroup.name;
    }

    private static Method findSupportsEnchantmentMethod() {
        try {
            return ItemStack.class.getMethod("supportsEnchantment", Holder.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
