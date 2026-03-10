package net.z2six.ezbalance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.z2six.ezbalance.Constants;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import net.z2six.ezbalance.balance.EzBalanceTabDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class EzBalanceClientCatalog {
    private EzBalanceClientCatalog() {}

    public static List<String> getVisibleItemIds(EzBalanceConfig config, String tabId, String search) {
        EzBalanceTabDefinition tab = config.tabs.get(tabId);
        if (tab == null) {
            Constants.LOG.warn("EZ Balance tab '{}' was requested but does not exist. Known tabs: {}", tabId, config.tabs.keySet());
            return List.of();
        }

        String loweredSearch = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<String> visible = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(item -> EzBalanceRuntime.matchesTab(tab, item))
                .map(EzBalanceRuntime::getItemId)
                .filter(id -> loweredSearch.isBlank() || id.toLowerCase(Locale.ROOT).contains(loweredSearch))
                .sorted()
                .toList();

        if (visible.isEmpty()) {
            Constants.LOG.info(
                    "EZ Balance tab '{}' matched 0 items. requiredAttributes={}, includeItemIds={}, excludeItemIds={}, includeNamespaces={}, excludeNamespaces={}, includeTags={}, excludeTags={}, search='{}'. swordAttrs={}, chestAttrs={}",
                    tabId,
                    tab.requiredAttributes,
                    tab.includeItemIds,
                    tab.excludeItemIds,
                    tab.includeNamespaces,
                    tab.excludeNamespaces,
                    tab.includeTags,
                    tab.excludeTags,
                    loweredSearch,
                    EzBalanceRuntime.collectBaseAttributes(Items.DIAMOND_SWORD.getDefaultInstance()),
                    EzBalanceRuntime.collectBaseAttributes(Items.DIAMOND_CHESTPLATE.getDefaultInstance())
            );
        } else {
            Constants.LOG.info("EZ Balance tab '{}' matched {} items. Sample: {}", tabId, visible.size(), visible.stream().limit(12).toList());
        }

        return visible;
    }

    public static Item getItem(String itemId) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(Items.BARRIER);
    }

    public static List<String> getAllAttributeIds() {
        return BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted(Comparator
                        .comparing((String id) -> !id.startsWith("minecraft:"))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    public static List<String> getAllEnchantmentIds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }

        Registry<?> registry = minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public static Optional<Holder.Reference<Enchantment>> getEnchantment(String enchantmentId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || enchantmentId == null || enchantmentId.isBlank()) {
            return Optional.empty();
        }

        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        if (id == null) {
            return Optional.empty();
        }

        return minecraft.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    public static String nextValue(List<String> values, String current) {
        if (values.isEmpty()) {
            return "";
        }

        int index = values.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        return values.get((index + 1) % values.size());
    }

    public static String previousValue(List<String> values, String current) {
        if (values.isEmpty()) {
            return "";
        }

        int index = values.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        return values.get((index - 1 + values.size()) % values.size());
    }

    public static String firstOrFallback(List<String> values, String fallback) {
        return values.isEmpty() ? fallback : values.getFirst();
    }
}
