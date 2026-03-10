package net.z2six.ezbalance.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import net.z2six.ezbalance.balance.EzBalanceRuntimeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnchantCommand.class)
public abstract class EnchantCommandMixin {

    @Redirect(
            method = "enchant",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;supportsEnchantment(Lnet/minecraft/core/Holder;)Z"
            )
    )
    private static boolean ezbalance$allowConfiguredEnchantments(ItemStack stack, Holder<Enchantment> enchantment) {
        EzBalanceConfig config = EzBalanceRuntimeState.getEffectiveConfig();
        boolean defaultAllowed = stack.supportsEnchantment(enchantment);
        return EzBalanceRuntime.isEnchantmentAllowed(config, EzBalanceRuntime.getItemId(stack), enchantment, defaultAllowed);
    }
}
