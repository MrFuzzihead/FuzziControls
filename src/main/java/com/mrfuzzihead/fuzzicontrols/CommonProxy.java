package com.mrfuzzihead.fuzzicontrols;

import java.io.File;

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
        // EarlyMixinsLoader already reads the config before the mixin phase; only load it again
        // when Forge points at a different file (for example on a launcher with a custom config
        // directory), so a normal launch parses it once.
        File configFile = event.getSuggestedConfigurationFile();
        if (!Config.isLoadedFrom(configFile)) {
            Config.synchronizeConfiguration(configFile);
        }
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
