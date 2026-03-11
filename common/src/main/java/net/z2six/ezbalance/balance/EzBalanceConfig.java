package net.z2six.ezbalance.balance;

import net.z2six.ezbalance.balance.EzBalanceItemGroupDefinition;
import net.z2six.ezbalance.balance.EzBalanceNormalizationConfig;
import net.z2six.ezbalance.balance.EzBalanceRarityDefinition;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class EzBalanceConfig {
    public static final int CURRENT_SCHEMA = 1;

    public int schemaVersion = CURRENT_SCHEMA;
    public Map<String, EzBalanceTabDefinition> tabs = new LinkedHashMap<>();
    public Map<String, EzBalanceRarityDefinition> rarities = new LinkedHashMap<>();
    public Map<String, EzBalanceItemGroupDefinition> itemGroups = new LinkedHashMap<>();
    public Map<String, EzBalanceItemRule> items = new LinkedHashMap<>();
    public Map<String, Map<String, Double>> originalAttributes = new LinkedHashMap<>();
    public EzBalanceNormalizationConfig normalization = new EzBalanceNormalizationConfig();

    public static EzBalanceConfig createDefault() {
        EzBalanceConfig config = new EzBalanceConfig();

        EzBalanceTabDefinition weapons = new EzBalanceTabDefinition("weapons", "Weapons", "minecraft:diamond_sword");
        weapons.requiredAttributes.add(EzBalanceRuntime.ATTACK_DAMAGE_ATTRIBUTE_ID);
        weapons.displayAttributes.add(EzBalanceRuntime.ATTACK_DAMAGE_ATTRIBUTE_ID);
        weapons.displayAttributes.add("minecraft:generic.attack_speed");
        weapons.displayAttributes.add("minecraft:generic.attack_knockback");
        config.tabs.put(weapons.id, weapons);

        EzBalanceTabDefinition armor = new EzBalanceTabDefinition("armor", "Armor", "minecraft:diamond_chestplate");
        armor.requiredAttributes.add(EzBalanceRuntime.ARMOR_ATTRIBUTE_ID);
        armor.displayAttributes.add(EzBalanceRuntime.ARMOR_ATTRIBUTE_ID);
        armor.displayAttributes.add("minecraft:generic.armor_toughness");
        armor.displayAttributes.add("minecraft:generic.knockback_resistance");
        config.tabs.put(armor.id, armor);

        return config;
    }

    public static EzBalanceConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return createDefault();
        }

        EzBalanceConfig config = EzBalanceJson.GSON.fromJson(json, EzBalanceConfig.class);
        if (config == null) {
            return createDefault();
        }

        return config.normalize();
    }

    public String toJson() {
        return EzBalanceJson.GSON.toJson(this.normalize());
    }

    public EzBalanceConfig normalize() {
        if (this.tabs == null) {
            this.tabs = new LinkedHashMap<>();
        }
        if (this.rarities == null) {
            this.rarities = new LinkedHashMap<>();
        }
        if (this.itemGroups == null) {
            this.itemGroups = new LinkedHashMap<>();
        }
        if (this.items == null) {
            this.items = new LinkedHashMap<>();
        }
        if (this.originalAttributes == null) {
            this.originalAttributes = new LinkedHashMap<>();
        }
        if (this.normalization == null) {
            this.normalization = new EzBalanceNormalizationConfig();
        }

        if (this.tabs.isEmpty()) {
            EzBalanceConfig defaults = createDefault();
            this.tabs.putAll(defaults.tabs);
        }

        this.tabs.values().forEach(tab -> {
            tab.requiredAttributes = tab.requiredAttributes.stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (tab.excludedAttributes == null) {
                tab.excludedAttributes = new LinkedHashSet<>();
            }
            tab.excludedAttributes = tab.excludedAttributes.stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            tab.attributeRanges = normalizeAttributeMap(tab.attributeRanges);
            if (tab.nameFilters == null) {
                tab.nameFilters = new ArrayList<>();
            }
            if (tab.excludedNameFilters == null) {
                tab.excludedNameFilters = new ArrayList<>();
            }
            if (tab.nameFilters.isEmpty() && tab.searchText != null && !tab.searchText.isBlank()) {
                tab.nameFilters.add(tab.searchText.trim());
            }
            tab.nameFilters = tab.nameFilters.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(ArrayList::new));
            tab.excludedNameFilters = tab.excludedNameFilters.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(ArrayList::new));
            tab.displayAttributes = normalizeAttributeList(tab.displayAttributes);
            tab.searchText = tab.nameFilters.isEmpty() ? "" : tab.nameFilters.getFirst();
        });

        this.rarities.values().forEach(rarity -> rarity.attributeValues = normalizeAttributeMap(rarity.attributeValues));
        this.rarities.values().forEach(rarity -> {
            if (rarity.attributeModifiers == null) {
                rarity.attributeModifiers = new LinkedHashMap<>();
            }
            if (rarity.attributeModifiers.isEmpty() && rarity.attributeValues != null && !rarity.attributeValues.isEmpty()) {
                rarity.attributeValues.forEach((attributeId, value) -> {
                    if (value != null) {
                        rarity.attributeModifiers.put(EzBalanceRuntime.normalizeAttributeId(attributeId), Double.toString(value));
                    }
                });
            }
            rarity.attributeModifiers = normalizeModifierMap(rarity.attributeModifiers);
        });
        this.itemGroups.values().forEach(group -> {
            group.attributeValues = normalizeAttributeMap(group.attributeValues);
            if (group.allowedEnchantments == null) {
                group.allowedEnchantments = new LinkedHashSet<>();
            }
        });
        this.items.values().forEach(rule -> {
            rule.attributeOverrides = normalizeAttributeMap(rule.attributeOverrides);
            if (rule.appliedRarityAttributes == null) {
                rule.appliedRarityAttributes = new LinkedHashSet<>();
            }
            if (rule.itemGroupIds == null) {
                rule.itemGroupIds = new LinkedHashSet<>();
            }
            if (rule.appliedItemGroupAttributesByGroup == null) {
                rule.appliedItemGroupAttributesByGroup = new LinkedHashMap<>();
            }
            if (rule.itemGroupEnchantRuleIds == null) {
                rule.itemGroupEnchantRuleIds = new LinkedHashSet<>();
            }
            if (rule.appliedItemGroupAttributes == null) {
                rule.appliedItemGroupAttributes = new LinkedHashSet<>();
            }
            if (rule.allowedEnchantments == null) {
                rule.allowedEnchantments = new LinkedHashSet<>();
            }
            if (rule.blockedEnchantments == null) {
                rule.blockedEnchantments = new LinkedHashSet<>();
            }
            if (!rule.itemGroupId.isBlank() && rule.itemGroupIds.isEmpty()) {
                rule.itemGroupIds.add(rule.itemGroupId);
                rule.appliedItemGroupAttributesByGroup.put(
                        rule.itemGroupId,
                        rule.appliedItemGroupAttributes.stream()
                                .map(EzBalanceRuntime::normalizeAttributeId)
                                .filter(value -> !value.isBlank())
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                );
            }
            rule.appliedRarityAttributes = rule.appliedRarityAttributes.stream()
                    .map(EzBalanceRuntime::normalizeAttributeId)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            rule.itemGroupIds = rule.itemGroupIds.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            rule.itemGroupEnchantRuleIds = rule.itemGroupEnchantRuleIds.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            rule.appliedItemGroupAttributesByGroup.replaceAll((groupId, attrs) -> attrs == null
                    ? new LinkedHashSet<>()
                    : attrs.stream()
                            .map(EzBalanceRuntime::normalizeAttributeId)
                            .filter(value -> !value.isBlank())
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
            rule.appliedItemGroupAttributesByGroup.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank());
            if (rule.itemGroupEnchantRuleIds.isEmpty() && !rule.itemGroupIds.isEmpty()) {
                rule.itemGroupIds.stream()
                        .filter(groupId -> {
                            EzBalanceItemGroupDefinition group = this.itemGroups.get(groupId);
                            return group != null && (group.forceDisabledEnchants || !group.allowedEnchantments.isEmpty());
                        })
                        .forEach(rule.itemGroupEnchantRuleIds::add);
            }
            rule.itemGroupId = "";
            rule.appliedItemGroupAttributes = new LinkedHashSet<>();
        });
        this.originalAttributes.replaceAll((itemId, values) -> normalizeAttributeMap(values));
        if (this.normalization.attributes == null) {
            this.normalization.attributes = new ArrayList<>();
        }
        this.normalization.attributes.forEach(rule -> {
            rule.attributeId = EzBalanceRuntime.normalizeAttributeId(rule.attributeId);
        });
        this.normalization.attributes.removeIf(rule -> rule.attributeId == null || rule.attributeId.isBlank());

        this.schemaVersion = CURRENT_SCHEMA;
        return this;
    }

    private static <T> Map<String, T> normalizeAttributeMap(Map<String, T> source) {
        Map<String, T> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }

        source.forEach((key, value) -> normalized.put(EzBalanceRuntime.normalizeAttributeId(key), value));
        return normalized;
    }

    private static List<String> normalizeAttributeList(List<String> source) {
        List<String> normalized = new ArrayList<>();
        if (source == null) {
            return normalized;
        }

        source.stream()
                .map(EzBalanceRuntime::normalizeAttributeId)
                .filter(value -> !value.isBlank())
                .forEach(normalized::add);
        return normalized;
    }

    private static Map<String, String> normalizeModifierMap(Map<String, String> source) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }

        source.forEach((key, value) -> {
            String normalizedKey = EzBalanceRuntime.normalizeAttributeId(key);
            String normalizedValue = value == null ? "" : value.trim();
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return normalized;
    }
}
