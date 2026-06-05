package net.z2six.ezbalance.hook;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import net.z2six.ezbalance.balance.EzBalanceRuntimeState;
import net.z2six.ezbalance.Constants;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EzBalanceHooks {
    private EzBalanceHooks() {}

    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        String itemId = EzBalanceRuntime.getItemId(event.getItemStack());
        if (!EzBalanceRuntime.hasAttributeRules(config, itemId)) {
            return;
        }

        Map<String, Double> overrides = EzBalanceRuntime.resolveAttributeOverrides(config, itemId);
        if (overrides.isEmpty()) {
            return;
        }

        Set<String> replacedAttributes = new HashSet<>();
        event.getDefaultModifiers().modifiers().forEach(entry -> {
            Double override = overrides.get(EzBalanceRuntime.getAttributeId(entry.attribute()));
            if (override != null) {
                replacedAttributes.add(EzBalanceRuntime.getAttributeId(entry.attribute()));
                event.replaceModifier(entry.attribute(), new AttributeModifier(entry.modifier().id(), override, entry.modifier().operation()), entry.slot());
            }
        });

        EquipmentSlotGroup fallbackSlot = event.getDefaultModifiers().modifiers().stream()
                .findFirst()
                .map(net.minecraft.world.item.component.ItemAttributeModifiers.Entry::slot)
                .orElse(EquipmentSlotGroup.MAINHAND);
        overrides.forEach((attributeId, value) -> {
            if (replacedAttributes.contains(attributeId)) {
                return;
            }
            BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.parse(attributeId)).ifPresent(attribute ->
                    event.addModifier(
                            BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute),
                            new AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "generated/" + attributeId.replace(':', '_').replace('.', '_')),
                                    value,
                                    AttributeModifier.Operation.ADD_VALUE
                            ),
                            fallbackSlot
                    )
            );
        });
    }

    public static void onTooltip(ItemTooltipEvent event) {
        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        String itemId = EzBalanceRuntime.getItemId(event.getItemStack());
        if (EzBalanceRuntime.getRule(config, itemId).isEmpty()) {
            return;
        }

        Component rarityLine = EzBalanceRuntime.createRarityLine(config, itemId);
        if (!rarityLine.getString().isBlank()) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), Component.literal("Rarity: ").append(rarityLine));
        }

        for (Component line : EzBalanceRuntime.createTooltipLines(config, itemId)) {
            if (!line.getString().isBlank()) {
                event.getToolTip().add(line);
            }
        }
    }

    public static void onEnchantmentLevels(GetEnchantmentLevelEvent event) {
        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        String itemId = EzBalanceRuntime.getItemId(event.getStack());
        if (!EzBalanceRuntime.hasCustomEnchantmentRules(config, itemId)) {
            return;
        }

        event.getEnchantments().removeIf(holder -> !EzBalanceRuntime.isEnchantmentAllowed(config, itemId, holder));
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }

        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        String itemId = EzBalanceRuntime.getItemId(left);
        if (!EzBalanceRuntime.hasCustomEnchantmentRules(config, itemId)) {
            return;
        }

        var incomingEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
        if (incomingEnchantments.isEmpty()) {
            return;
        }

        ItemStack output = left.copy();
        boolean[] changed = {false};
        long[] cost = {Math.max(1L, event.getCost())};
        EnchantmentHelper.updateEnchantments(output, mutable -> {
            for (var entry : incomingEnchantments.entrySet()) {
                var enchantment = entry.getKey();
                int currentLevel = mutable.getLevel(enchantment);
                int incomingLevel = entry.getIntValue();
                int targetLevel = currentLevel == incomingLevel ? incomingLevel + 1 : Math.max(currentLevel, incomingLevel);
                targetLevel = Math.min(targetLevel, enchantment.value().getMaxLevel());

                boolean compatible = EnchantmentHelper.isEnchantmentCompatible(mutable.keySet(), enchantment);
                boolean defaultAllowed = output.supportsEnchantment(enchantment);
                boolean allowed = EzBalanceRuntime.isEnchantmentAllowed(config, itemId, enchantment, defaultAllowed);
                if (!allowed || !compatible) {
                    continue;
                }

                if (currentLevel != targetLevel) {
                    mutable.set(enchantment, targetLevel);
                    changed[0] = true;
                    cost[0] += Math.max(1, enchantment.value().getAnvilCost()) * targetLevel;
                }
            }
        });

        if (!changed[0]) {
            if (config.items.get(itemId) != null && config.items.get(itemId).restrictEnchantments) {
                event.setCanceled(true);
            }
            return;
        }

        event.setOutput(output);
        event.setMaterialCost(1);
        event.setCost(cost[0]);
    }

    public static void onPlayerEnchant(PlayerEnchantItemEvent event) {
        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        String itemId = EzBalanceRuntime.getItemId(event.getEnchantedItem());
        if (!EzBalanceRuntime.hasCustomEnchantmentRules(config, itemId)) {
            return;
        }

        EnchantmentHelper.updateEnchantments(event.getEnchantedItem(), mutable -> mutable.removeIf(holder -> !EzBalanceRuntime.isEnchantmentAllowed(config, itemId, holder)));
    }
}
