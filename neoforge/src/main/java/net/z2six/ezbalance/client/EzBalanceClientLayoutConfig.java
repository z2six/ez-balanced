package net.z2six.ezbalance.client;

import net.neoforged.fml.loading.FMLPaths;
import net.z2six.ezbalance.Constants;
import net.z2six.ezbalance.balance.EzBalanceJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EzBalanceClientLayoutConfig {
    private static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve("EZ Balance");
    private static final Path FILE = DIRECTORY.resolve("client-layout.json");
    private static EzBalanceClientLayoutConfig instance;

    public Map<String, Integer> columnWidths = new LinkedHashMap<>();
    public String savedSearchText = "";

    private EzBalanceClientLayoutConfig() {}

    public static EzBalanceClientLayoutConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public int getColumnWidth(String key, int fallback) {
        return Math.max(24, this.columnWidths.getOrDefault(key, fallback));
    }

    public boolean hasColumnWidth(String key) {
        return this.columnWidths.containsKey(key);
    }

    public void setColumnWidth(String key, int width) {
        this.columnWidths.put(key, Math.max(24, width));
        save();
    }

    public String getSavedSearchText() {
        return this.savedSearchText == null ? "" : this.savedSearchText;
    }

    public void setSavedSearchText(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.equals(getSavedSearchText())) {
            return;
        }
        this.savedSearchText = normalized;
        save();
    }

    private static EzBalanceClientLayoutConfig load() {
        try {
            Files.createDirectories(DIRECTORY);
            if (Files.exists(FILE)) {
                return EzBalanceJson.GSON.fromJson(Files.readString(FILE), EzBalanceClientLayoutConfig.class);
            }
        } catch (Exception exception) {
            Constants.LOG.warn("Failed to load EZ Balance client layout config from {}", FILE, exception);
        }
        return new EzBalanceClientLayoutConfig();
    }

    private void save() {
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(FILE, EzBalanceJson.GSON.toJson(this));
        } catch (IOException exception) {
            Constants.LOG.warn("Failed to save EZ Balance client layout config to {}", FILE, exception);
        }
    }
}
