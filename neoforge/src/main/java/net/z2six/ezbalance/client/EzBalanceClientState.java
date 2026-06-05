package net.z2six.ezbalance.client;

import net.z2six.ezbalance.balance.EzBalanceConfig;

public final class EzBalanceClientState {
    private static EzBalanceConfig config = EzBalanceConfig.createDefault();

    private EzBalanceClientState() {}

    public static EzBalanceConfig getConfig() {
        return config;
    }

    public static void setConfig(EzBalanceConfig config) {
        EzBalanceClientState.config = config.normalize();
    }

    public static void reset() {
        config = EzBalanceConfig.createDefault();
    }
}
