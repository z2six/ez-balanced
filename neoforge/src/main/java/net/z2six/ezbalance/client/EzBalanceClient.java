package net.z2six.ezbalance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.z2six.ezbalance.balance.EzBalanceRuntimeState;
import net.z2six.ezbalance.network.OpenBalancePayload;

public final class EzBalanceClient {
    private static final KeyMapping OPEN_EDITOR_KEY = new KeyMapping(
            "key.ezbalance.open_editor",
            InputConstants.Type.KEYSYM,
            66,
            "key.categories.ezbalance"
    );

    private EzBalanceClient() {}

    public static void init(IEventBus modEventBus) {
        EzBalanceRuntimeState.setClientConfigSupplier(EzBalanceClientState::getConfig);
        EzBalanceRuntimeState.setClientSyncHandler(EzBalanceClient::handleSync);
        modEventBus.addListener(EzBalanceClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(EzBalanceClient::onClientTick);
    }

    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EzBalanceClientState.reset();
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR_KEY);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (OPEN_EDITOR_KEY.consumeClick()) {
            requestOpenEditor();
        }
    }

    public static void requestOpenEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen instanceof EzBalanceScreen) {
            return;
        }

        PacketDistributor.sendToServer(new OpenBalancePayload());
    }

    public static void openEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen instanceof EzBalanceScreen) {
            return;
        }

        minecraft.setScreen(new EzBalanceScreen());
    }

    private static void handleSync(String json, boolean openEditor) {
        EzBalanceClientState.setConfig(net.z2six.ezbalance.balance.EzBalanceConfig.fromJson(json));
        if (openEditor) {
            openEditor();
        }
    }
}
