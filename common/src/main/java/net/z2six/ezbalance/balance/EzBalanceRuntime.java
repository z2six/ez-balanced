package net.z2six.ezbalance.balance;

import net.minecraft.ChatFormatting;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        if (attributeId == null || attributeId.isBlank()) {
            return "";
        }

        return switch (attributeId) {
            case "minecraft:attack_damage" -> ATTACK_DAMAGE_ATTRIBUTE_ID;
            case "minecraft:armor" -> ARMOR_ATTRIBUTE_ID;
            case "minecraft:attack_speed" -> "minecraft:generic.attack_speed";
            case "minecraft:armor_toughness" -> "minecraft:generic.armor_toughness";
            case "minecraft:attack_knockback" -> "minecraft:generic.attack_knockback";
            case "minecraft:luck" -> "minecraft:generic.luck";
            case "minecraft:max_health" -> "minecraft:generic.max_health";
            case "minecraft:movement_speed" -> "minecraft:generic.movement_speed";
            case "minecraft:knockback_resistance" -> "minecraft:generic.knockback_resistance";
            default -> attributeId;
        };
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

        if (!rule.rarityId.isBlank()) {
            EzBalanceRarityDefinition rarity = config.rarities.get(rule.rarityId);
            if (rarity != null && rarity.attributeValues != null) {
                if (rule.appliedRarityAttributes == null || rule.appliedRarityAttributes.isEmpty()) {
                    resolved.putAll(rarity.attributeValues);
                } else {
                    for (String attributeId : rule.appliedRarityAttributes) {
                        Double value = rarity.attributeValues.get(attributeId);
                        if (value != null) {
                            resolved.put(attributeId, value);
                        }
                    }
                }
            }
        }

        if (!rule.itemGroupId.isBlank()) {
            EzBalanceItemGroupDefinition itemGroup = config.itemGroups.get(rule.itemGroupId);
            if (itemGroup != null && itemGroup.attributeValues != null) {
                if (rule.appliedItemGroupAttributes == null || rule.appliedItemGroupAttributes.isEmpty()) {
                    resolved.putAll(itemGroup.attributeValues);
                } else {
                    for (String attributeId : rule.appliedItemGroupAttributes) {
                        Double value = itemGroup.attributeValues.get(attributeId);
                        if (value != null) {
                            resolved.put(attributeId, value);
                        }
                    }
                }
            }
        }

        if (rule.attributeOverrides != null) {
            resolved.putAll(rule.attributeOverrides);
        }

        return resolved;
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
        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule != null && rule.hasCustomEnchantmentRules()) {
            return true;
        }
        EzBalanceItemGroupDefinition itemGroup = getAssignedItemGroup(config, itemId);
        return itemGroup != null && (itemGroup.forceDisabledEnchants || !itemGroup.allowedEnchantments.isEmpty());
    }

    public static EzBalanceItemGroupDefinition getAssignedItemGroup(EzBalanceConfig config, String itemId) {
        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule == null || rule.itemGroupId.isBlank()) {
            return null;
        }
        return config.itemGroups.get(rule.itemGroupId);
    }

    public static boolean isEnchantmentAllowedByItemGroup(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment, boolean defaultAllowed) {
        EzBalanceItemGroupDefinition itemGroup = getAssignedItemGroup(config, itemId);
        if (itemGroup == null) {
            return defaultAllowed;
        }
        String enchantmentId = getEnchantmentId(enchantment);
        if (itemGroup.allowedEnchantments.contains(enchantmentId)) {
            return true;
        }
        if (itemGroup.forceDisabledEnchants && defaultAllowed) {
            return false;
        }
        return defaultAllowed;
    }

    public static boolean isEnchantmentExplicitlyAllowedByItemGroup(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment) {
        EzBalanceItemGroupDefinition itemGroup = getAssignedItemGroup(config, itemId);
        return itemGroup != null && itemGroup.allowedEnchantments.contains(getEnchantmentId(enchantment));
    }

    public static boolean isEnchantmentExplicitlyBlockedByItemGroup(EzBalanceConfig config, String itemId, Holder<Enchantment> enchantment, boolean defaultAllowed) {
        EzBalanceItemGroupDefinition itemGroup = getAssignedItemGroup(config, itemId);
        return itemGroup != null
                && itemGroup.forceDisabledEnchants
                && defaultAllowed
                && !itemGroup.allowedEnchantments.contains(getEnchantmentId(enchantment));
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
        if (!tab.includeTags.isEmpty()) {
            for (String tag : tab.includeTags) {
                ResourceLocation location = ResourceLocation.tryParse(tag);
                if (location == null || !stack.is(TagKey.create(Registries.ITEM, location))) {
                    return false;
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
        EzBalanceItemRule rule = config.items.get(itemId);
        if (rule != null && rule.hasCustomEnchantmentRules()) {
            return List.of(
                    Component.literal("Base stats managed by EZ Balance").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal("Custom enchant rules: " + (rule.allowedEnchantments.size() + rule.blockedEnchantments.size())).withStyle(ChatFormatting.DARK_GRAY)
            );
        }
        EzBalanceItemGroupDefinition itemGroup = getAssignedItemGroup(config, itemId);
        if (itemGroup != null && (itemGroup.forceDisabledEnchants || !itemGroup.allowedEnchantments.isEmpty())) {
            return List.of(
                    Component.literal("Base stats managed by EZ Balance").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal("Custom enchant rules: " + itemGroup.allowedEnchantments.size()).withStyle(ChatFormatting.DARK_GRAY)
            );
        }
        return List.of(Component.literal("Base stats managed by EZ Balance").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Method findSupportsEnchantmentMethod() {
        try {
            return ItemStack.class.getMethod("supportsEnchantment", Holder.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
