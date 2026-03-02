package com.mrfuzzihead.fuzzicontrols;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-side proxy stub. With {@code clientSideOnly = true} on the {@code @Mod} annotation,
 * this proxy is never instantiated on a dedicated server — Forge will refuse to load the mod
 * there entirely. It exists only to satisfy the {@link cpw.mods.fml.common.SidedProxy} contract.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
