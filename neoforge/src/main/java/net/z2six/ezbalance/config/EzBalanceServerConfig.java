package net.z2six.ezbalance.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EzBalanceServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue GUI_ENABLED = BUILDER
            .comment("If true, server operators can open the EZ Balance editor GUI.")
            .define("gui_enabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EzBalanceServerConfig() {}

    public static boolean isGuiEnabled() {
        return GUI_ENABLED.get();
    }
}
