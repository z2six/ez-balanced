package net.z2six.ezbalance.balance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class EzBalanceConfigFiles {
    private EzBalanceConfigFiles() {}

    public static EzBalanceConfig load(Path path) throws IOException {
        if (path == null || Files.notExists(path)) {
            return EzBalanceConfig.createDefault();
        }
        return EzBalanceConfig.fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void save(Path path, EzBalanceConfig config) throws IOException {
        if (path == null) {
            throw new IOException("Config path is not set.");
        }

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        EzBalanceConfig normalized = EzBalanceConfig.fromJson(config == null ? "" : config.toJson());
        Path temp = Files.createTempFile(parent == null ? Path.of(".") : parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, normalized.toJson(), StandardCharsets.UTF_8);
            moveIntoPlace(temp, path);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void moveIntoPlace(Path temp, Path path) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
