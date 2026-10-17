package org.essupport;

import net.minecraftforge.common.ForgeConfigSpec;

import org.essupport.core.SupportTypes.Rules;

public final class SupportConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue MAX =
            B.comment("Maximum retained requests per team, including history")
                    .defineInRange("maximumPerTeam", 48, 1, 96);
    public static final ForgeConfigSpec.IntValue QUANTITY =
            B.defineInRange("maximumQuantity", 5000, 1, 100000);
    public static final ForgeConfigSpec.IntValue INTERVAL =
            B.defineInRange("createIntervalTicks", 200, 0, 72000);
    public static final ForgeConfigSpec.IntValue SUPPLY_TTL =
            B.defineInRange("supplyLifetimeTicks", 36000, 20, 1728000);
    public static final ForgeConfigSpec SPEC = B.build();

    public static Rules rules() {
        return new Rules(MAX.get(), QUANTITY.get(), INTERVAL.get(), SUPPLY_TTL.get());
    }
}
