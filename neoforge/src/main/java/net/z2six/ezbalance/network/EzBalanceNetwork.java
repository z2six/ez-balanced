package net.z2six.ezbalance.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceRuntimeState;
import net.z2six.ezbalance.config.EzBalanceConfigStore;
import net.z2six.ezbalance.config.EzBalanceServerConfig;

public final class EzBalanceNetwork {
    private static final String NETWORK_VERSION = "1";

    private EzBalanceNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(SyncBalancePayload.TYPE, SyncBalancePayload.STREAM_CODEC, EzBalanceNetwork::handleSync);
        registrar.playToServer(OpenBalancePayload.TYPE, OpenBalancePayload.STREAM_CODEC, EzBalanceNetwork::handleOpenRequest);
        registrar.playToServer(SaveBalancePayload.TYPE, SaveBalancePayload.STREAM_CODEC, EzBalanceNetwork::handleSave);
    }

    public static void sync(ServerPlayer player, boolean openEditor) {
        EzBalanceConfig config = EzBalanceConfigStore.get(player.server);
        PacketDistributor.sendToPlayer(player, new SyncBalancePayload(config.toJson(), openEditor));
    }

    public static void syncAll(ServerPlayer triggerPlayer) {
        EzBalanceConfig config = EzBalanceConfigStore.get(triggerPlayer.server);
        PacketDistributor.sendToAllPlayers(new SyncBalancePayload(config.toJson(), false));
    }

    private static void handleSync(SyncBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EzBalanceRuntimeState.handleClientSync(payload.json(), payload.openEditor()));
    }

    private static void handleSave(SaveBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !canUseEditor(player)) {
                return;
            }

            EzBalanceConfigStore.set(player.server, EzBalanceConfig.fromJson(payload.json()));
            syncAll(player);
        });
    }

    private static void handleOpenRequest(OpenBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!EzBalanceServerConfig.isGuiEnabled()) {
                player.displayClientMessage(Component.literal("EZ Balance GUI is disabled on this server."), true);
                return;
            }

            if (!player.hasPermissions(2)) {
                player.displayClientMessage(Component.literal("You must be an operator to use EZ Balance."), true);
                return;
            }

            sync(player, true);
        });
    }

    private static boolean canUseEditor(ServerPlayer player) {
        return EzBalanceServerConfig.isGuiEnabled() && player.hasPermissions(2);
    }
}
