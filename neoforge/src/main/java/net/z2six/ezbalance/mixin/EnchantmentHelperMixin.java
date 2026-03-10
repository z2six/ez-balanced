package net.z2six.ezbalance.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import net.z2six.ezbalance.balance.EzBalanceRuntimeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @Inject(method = "getAvailableEnchantmentResults", at = @At("HEAD"), cancellable = true)
    private static void ezbalance$rewriteAvailableEnchantments(
            int level,
            ItemStack stack,
            Stream<Holder<Enchantment>> enchantments,
            CallbackInfoReturnable<List<EnchantmentInstance>> cir
    ) {
        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        String itemId = EzBalanceRuntime.getItemId(stack);
        List<EnchantmentInstance> results = new ArrayList<>();

        enchantments.forEach(holder -> {
            boolean defaultAllowed = stack.isPrimaryItemFor(holder);
            if (!EzBalanceRuntime.isEnchantmentAllowed(config, itemId, holder, defaultAllowed)) {
                return;
            }

            Enchantment enchantment = holder.value();
            for (int currentLevel = enchantment.getMaxLevel(); currentLevel >= enchantment.getMinLevel(); currentLevel--) {
                if (level >= enchantment.getMinCost(currentLevel) && level <= enchantment.getMaxCost(currentLevel)) {
                    results.add(new EnchantmentInstance(holder, currentLevel));
                    break;
                }
            }
        });

        cir.setReturnValue(results);
    }
}
