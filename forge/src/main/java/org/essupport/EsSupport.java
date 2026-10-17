package org.essupport;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import org.essupport.network.SupportNetwork;

@Mod(EsSupport.ID)
public final class EsSupport {
    public static final String ID = "essupport";

    public EsSupport() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SupportConfig.SPEC);
        SupportNetwork.register();
    }
}
