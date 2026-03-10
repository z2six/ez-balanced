package net.z2six.ezbalance.balance;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class EzBalanceSavedData extends SavedData {
    private static final String DATA_NAME = "ezbalance";
    private static final String CONFIG_JSON = "config_json";
    private static final Factory<EzBalanceSavedData> FACTORY = new Factory<>(
            EzBalanceSavedData::new,
            EzBalanceSavedData::load,
            DataFixTypes.LEVEL
    );

    private EzBalanceConfig config;

    public EzBalanceSavedData() {
        this(EzBalanceConfig.createDefault());
    }

    public EzBalanceSavedData(EzBalanceConfig config) {
        this.config = config.normalize();
        EzBalanceRuntime.captureOriginalAttributes(this.config, this.config.items.keySet());
    }

    public static EzBalanceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static EzBalanceSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return new EzBalanceSavedData(EzBalanceConfig.fromJson(tag.getString(CONFIG_JSON)));
    }

    public EzBalanceConfig getConfig() {
        return this.config.normalize();
    }

    public void setConfig(EzBalanceConfig config) {
        this.config = config.normalize();
        EzBalanceRuntime.captureOriginalAttributes(this.config, this.config.items.keySet());
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString(CONFIG_JSON, this.config.toJson());
        return tag;
    }
}
