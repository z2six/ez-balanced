package net.z2six.ezbalance.balance;

public final class EzBalanceTooltipPolicy {
    private EzBalanceTooltipPolicy() {}

    public static boolean shouldShowManagedByModLine(EzBalanceConfig config, String itemId) {
        return false;
    }
}
