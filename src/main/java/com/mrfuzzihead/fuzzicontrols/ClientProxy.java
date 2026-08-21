package com.mrfuzzihead.fuzzicontrols;

import net.minecraftforge.client.ClientCommandHandler;

import com.mrfuzzihead.fuzzicontrols.command.CommandReloadControllers;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        ControllerManager manager = ControllerManager.getInstance();
        manager.init();

        ControllerTickHandler tickHandler = new ControllerTickHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(tickHandler);

        // Register /fuzzicontrols reload so players can force controller re-init
        // without restarting the game (needed for DualSense which does not always
        // hot-plug cleanly through hidapi).
        ClientCommandHandler.instance.registerCommand(new CommandReloadControllers());

        FuzziControls.LOG.info(
            "[FuzziControls] Controller tick handler registered. Active driver: {}",
            manager.getActiveDriverName());

        Runtime.getRuntime()
            .addShutdownHook(new Thread(manager::shutdown, "FuzziControls-Shutdown"));
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
