package net.z2six.ezbalance.balance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EzBalanceConfigFilesTest {
    private static final String ATTACK_DAMAGE_ATTRIBUTE_ID = "minecraft:generic.attack_damage";

    @TempDir
    Path tempDir;

    @Test
    void savesItemRulesToStandaloneConfigFile() throws Exception {
        Path configPath = this.tempDir.resolve("ezbalance-rules.json");
        EzBalanceConfig config = EzBalanceConfig.createDefault();
        EzBalanceItemRule rule = new EzBalanceItemRule();
        rule.attributeOverrides.put(ATTACK_DAMAGE_ATTRIBUTE_ID, 11.0D);
        config.items.put("minecraft:diamond_sword", rule);

        EzBalanceConfigFiles.save(configPath, config);
        EzBalanceConfig loaded = EzBalanceConfigFiles.load(configPath);

        assertTrue(java.nio.file.Files.exists(configPath));
        assertEquals(11.0D, loaded.items.get("minecraft:diamond_sword").attributeOverrides.get(ATTACK_DAMAGE_ATTRIBUTE_ID));
    }
}
