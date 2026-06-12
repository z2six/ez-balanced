package net.z2six.ezbalance.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EzBalanceRuntimeHotPathTest {
    private static final String ATTACK_DAMAGE_ATTRIBUTE_ID = "minecraft:generic.attack_damage";

    @Test
    void detectsAttributeTouchedItemsOnlyWhenRuntimeAttributeRulesCanApply() {
        EzBalanceConfig config = EzBalanceConfig.createDefault();
        config.items.put("example:plain", new EzBalanceItemRule());

        EzBalanceItemRule attributeRule = new EzBalanceItemRule();
        attributeRule.attributeOverrides.put(ATTACK_DAMAGE_ATTRIBUTE_ID, 8.0D);
        config.items.put("example:attribute", attributeRule);

        EzBalanceItemRule rarityRule = new EzBalanceItemRule();
        rarityRule.rarityId = "rare";
        rarityRule.appliedRarityAttributes.add(ATTACK_DAMAGE_ATTRIBUTE_ID);
        config.items.put("example:rarity", rarityRule);

        EzBalanceRarityDefinition rarity = new EzBalanceRarityDefinition();
        rarity.attributeModifiers.put(ATTACK_DAMAGE_ATTRIBUTE_ID, "20%");
        config.rarities.put("rare", rarity);

        assertFalse(EzBalanceHotPathRules.hasAttributeRules(config, "example:plain"));
        assertTrue(EzBalanceHotPathRules.hasAttributeRules(config, "example:attribute"));
        assertTrue(EzBalanceHotPathRules.hasAttributeRules(config, "example:rarity"));
    }

    @Test
    void detectsEnchantmentTouchedItemsFromDirectAndAppliedGroupRules() {
        EzBalanceConfig config = EzBalanceConfig.createDefault();
        config.items.put("example:plain", new EzBalanceItemRule());

        EzBalanceItemRule directRule = new EzBalanceItemRule();
        directRule.restrictEnchantments = true;
        config.items.put("example:direct", directRule);

        EzBalanceItemGroupDefinition group = new EzBalanceItemGroupDefinition();
        group.forceDisabledEnchants = true;
        config.itemGroups.put("locked_group", group);

        EzBalanceItemRule groupRule = new EzBalanceItemRule();
        groupRule.itemGroupIds.add("locked_group");
        groupRule.itemGroupEnchantRuleIds.add("locked_group");
        config.items.put("example:group", groupRule);

        assertFalse(EzBalanceHotPathRules.hasCustomEnchantmentRules(config, "example:plain"));
        assertTrue(EzBalanceHotPathRules.hasCustomEnchantmentRules(config, "example:direct"));
        assertTrue(EzBalanceHotPathRules.hasCustomEnchantmentRules(config, "example:group"));
    }

    @Test
    void brandedTooltipLinesAreNotShownForManagedItems() {
        EzBalanceConfig config = EzBalanceConfig.createDefault();
        EzBalanceItemRule rule = new EzBalanceItemRule();
        rule.attributeOverrides.put(ATTACK_DAMAGE_ATTRIBUTE_ID, 8.0D);
        config.items.put("example:attribute", rule);

        assertFalse(EzBalanceTooltipPolicy.shouldShowManagedByModLine(config, "example:attribute"));
    }

    @Test
    void exposesOriginalAndCurrentAttributeValuesForClientDisplay() {
        EzBalanceConfig config = EzBalanceConfig.createDefault();
        config.originalAttributes.put(
                "example:sword",
                new java.util.LinkedHashMap<>(java.util.Map.of(ATTACK_DAMAGE_ATTRIBUTE_ID, 7.0D))
        );

        EzBalanceItemRule rule = new EzBalanceItemRule();
        rule.attributeOverrides.put(ATTACK_DAMAGE_ATTRIBUTE_ID, 9.0D);
        config.items.put("example:sword", rule);

        EzBalanceAttributeValue value = EzBalanceAttributeValues.getAttributeValue(
                config,
                "example:sword",
                "minecraft:attack_damage",
                java.util.Map.of(ATTACK_DAMAGE_ATTRIBUTE_ID, 7.0D),
                java.util.Map.of(ATTACK_DAMAGE_ATTRIBUTE_ID, 9.0D)
        );

        assertEquals(ATTACK_DAMAGE_ATTRIBUTE_ID, value.attributeId());
        assertEquals(7.0D, value.originalValue());
        assertEquals(9.0D, value.currentValue());
        assertTrue(value.isChanged());
    }
}
