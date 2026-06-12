package net.z2six.ezbalance;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.z2six.ezbalance.client.EzBalanceClient;
import net.z2six.ezbalance.config.EzBalanceConfigStore;
import net.z2six.ezbalance.config.EzBalanceServerConfig;
import net.z2six.ezbalance.hook.EzBalanceHooks;
import net.z2six.ezbalance.network.EzBalanceNetwork;

@Mod(Constants.MOD_ID)
public class EZBalance {

    public EZBalance(IEventBus eventBus, ModContainer modContainer) {
        CommonClass.init();
        modContainer.registerConfig(ModConfig.Type.SERVER, EzBalanceServerConfig.SPEC, "ezbalance-server.toml");
        eventBus.addListener(EzBalanceNetwork::register);

        NeoForge.EVENT_BUS.addListener(EzBalanceHooks::onItemAttributes);
        NeoForge.EVENT_BUS.addListener(EzBalanceHooks::onTooltip);
        NeoForge.EVENT_BUS.addListener(EzBalanceHooks::onEnchantmentLevels);
        NeoForge.EVENT_BUS.addListener(EzBalanceHooks::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(EzBalanceHooks::onPlayerEnchant);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        if (FMLEnvironment.dist.isClient()) {
            EzBalanceClient.init(eventBus);
            NeoForge.EVENT_BUS.addListener(EzBalanceClient::onPlayerLogout);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EzBalanceNetwork.sync(player, false);
        }
    }

    private void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        EzBalanceConfigStore.clearCache();
    }
}
