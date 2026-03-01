package com.mrfuzzihead.fuzzicontrols;

import net.minecraftforge.common.MinecraftForge;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Initialise the controller manager (selects XInput or DualSense driver)
        ControllerManager manager = ControllerManager.getInstance();
        manager.init();

        // Register the client tick handler on both event buses so it receives tick events
        ControllerTickHandler tickHandler = new ControllerTickHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(tickHandler);
        MinecraftForge.EVENT_BUS.register(tickHandler);

        FuzziControls.LOG.info(
            "[FuzziControls] Controller tick handler registered. Active driver: {}",
            manager.getActiveDriverName());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
