package net.z2six.ezbalance.config;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.z2six.ezbalance.Constants;
import net.z2six.ezbalance.balance.EzBalanceConfig;
import net.z2six.ezbalance.balance.EzBalanceConfigFiles;
import net.z2six.ezbalance.balance.EzBalanceRuntime;
import net.z2six.ezbalance.balance.EzBalanceSavedData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EzBalanceConfigStore {
    public static final String FILE_NAME = "ezbalance-rules.json";

    private static EzBalanceConfig cachedConfig;

    private EzBalanceConfigStore() {}

    public static synchronized EzBalanceConfig get(MinecraftServer server) {
        if (cachedConfig == null) {
            cachedConfig = load(server);
        }
        return cachedConfig;
    }

    public static synchronized EzBalanceConfig set(MinecraftServer server, EzBalanceConfig config) {
        cachedConfig = prepare(config);
        save(cachedConfig);
        return cachedConfig;
    }

    public static synchronized void clearCache() {
        cachedConfig = null;
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static EzBalanceConfig load(MinecraftServer server) {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                return prepare(EzBalanceConfigFiles.load(path));
            } catch (IOException | RuntimeException exception) {
                Constants.LOG.error("Failed to load EZ Balance rules from {}", path, exception);
            }
        }

        EzBalanceConfig migrated = loadLegacyWorldConfig(server);
        if (!isDefaultConfig(migrated)) {
            save(migrated);
        }
        return migrated;
    }

    private static EzBalanceConfig loadLegacyWorldConfig(MinecraftServer server) {
        if (server == null) {
            return EzBalanceConfig.createDefault();
        }
        try {
            return prepare(EzBalanceSavedData.get(server).getConfig());
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to migrate legacy EZ Balance world data.", exception);
            return EzBalanceConfig.createDefault();
        }
    }

    private static EzBalanceConfig prepare(EzBalanceConfig config) {
        EzBalanceConfig normalized = EzBalanceConfig.fromJson(config == null ? "" : config.toJson());
        EzBalanceRuntime.captureOriginalAttributes(normalized, normalized.items.keySet());
        return normalized;
    }

    private static void save(EzBalanceConfig config) {
        Path path = configPath();
        try {
            EzBalanceConfigFiles.save(path, config);
        } catch (IOException exception) {
            Constants.LOG.error("Failed to save EZ Balance rules to {}", path, exception);
        }
    }

    private static boolean isDefaultConfig(EzBalanceConfig config) {
        return EzBalanceConfig.createDefault().toJson().equals(EzBalanceConfig.fromJson(config == null ? "" : config.toJson()).toJson());
    }
}
