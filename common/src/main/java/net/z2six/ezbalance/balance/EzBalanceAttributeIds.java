package net.z2six.ezbalance.balance;

public final class EzBalanceAttributeIds {
    public static final String ATTACK_DAMAGE_ATTRIBUTE_ID = "minecraft:generic.attack_damage";
    public static final String ARMOR_ATTRIBUTE_ID = "minecraft:generic.armor";
    public static final String DPS_COLUMN_ID = "ezbalance:projected_dps";
    public static final String ENCHANT_RULES_COLUMN_ID = "ezbalance:enchantment_rules";

    private EzBalanceAttributeIds() {}

    public static String normalize(String attributeId) {
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
}
