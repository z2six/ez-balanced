package net.z2six.ezbalance.client;

import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.network.SaveBalancePayload;

public final class EzBalanceClientPersistence {
    private EzBalanceClientPersistence() {}

    public static EzBalanceConfig persist(EzBalanceConfig config) {
        EzBalanceConfig normalized = EzBalanceConfig.fromJson(config.toJson());
        copyInto(config, normalized);
        EzBalanceClientState.setConfig(EzBalanceConfig.fromJson(normalized.toJson()));
        PacketDistributor.sendToServer(new SaveBalancePayload(normalized.toJson()));
        return normalized;
    }

    public static void copyInto(EzBalanceConfig target, EzBalanceConfig source) {
        target.schemaVersion = source.schemaVersion;
        target.tabs.clear();
        target.tabs.putAll(source.tabs);
        target.rarities.clear();
        target.rarities.putAll(source.rarities);
        target.itemGroups.clear();
        target.itemGroups.putAll(source.itemGroups);
        target.items.clear();
        target.items.putAll(source.items);
        target.originalAttributes.clear();
        target.originalAttributes.putAll(source.originalAttributes);
        target.normalization = source.normalization;
    }
}
