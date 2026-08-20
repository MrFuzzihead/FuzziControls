package com.mrfuzzihead.fuzzicontrols;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-side proxy stub. On a dedicated server the mod only needs to satisfy the
 * {@link cpw.mods.fml.common.SidedProxy} contract — there is no client, so no controller input
 * is handled (and the {@link ClientProxy} is never instantiated). Config is still synchronised
 * here so a server install writes its config file harmlessly.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
