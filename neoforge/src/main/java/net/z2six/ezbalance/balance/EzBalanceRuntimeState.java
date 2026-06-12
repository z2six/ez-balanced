package net.z2six.ezbalance.balance;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.z2six.ezbalance.config.EzBalanceConfigStore;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class EzBalanceRuntimeState {
    private static Supplier<EzBalanceConfig> clientConfigSupplier = EzBalanceConfig::createDefault;
    private static BiConsumer<String, Boolean> clientSyncHandler = (json, openEditor) -> {};

    private EzBalanceRuntimeState() {}

    public static EzBalanceConfig getEffectiveConfig() {
        if (FMLEnvironment.dist.isClient()) {
            return clientConfigSupplier.get();
        }

        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return EzBalanceConfig.createDefault();
        }

        return EzBalanceConfigStore.get(ServerLifecycleHooks.getCurrentServer());
    }

    public static void setClientConfigSupplier(Supplier<EzBalanceConfig> supplier) {
        clientConfigSupplier = supplier == null ? EzBalanceConfig::createDefault : supplier;
    }

    public static void setClientSyncHandler(BiConsumer<String, Boolean> handler) {
        clientSyncHandler = handler == null ? (json, openEditor) -> {} : handler;
    }

    public static void handleClientSync(String json, boolean openEditor) {
        clientSyncHandler.accept(json, openEditor);
    }
}
