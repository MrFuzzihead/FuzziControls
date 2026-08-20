package com.mrfuzzihead.fuzzicontrols;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Initialize the controller manager (selects XInput or DualSense driver)
        ControllerManager manager = ControllerManager.getInstance();
        manager.init();

        // Register the client tick handler. TickEvent.* is posted on the FML common handler bus.
        ControllerTickHandler tickHandler = new ControllerTickHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(tickHandler);

        FuzziControls.LOG.info(
            "[FuzziControls] Controller tick handler registered. Active driver: {}",
            manager.getActiveDriverName());

        // Release native controller resources when the JVM exits (e.g. closing the game).
        Runtime.getRuntime()
            .addShutdownHook(new Thread(manager::shutdown, "FuzziControls-Shutdown"));
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
