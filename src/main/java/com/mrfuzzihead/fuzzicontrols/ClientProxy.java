package com.mrfuzzihead.fuzzicontrols;

import net.minecraftforge.common.MinecraftForge;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusRenderer;

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

        // Shared focus navigator instance — used by both the tick handler (input) and the
        // renderer (drawing the highlight). Sharing ensures they see the same focus state.
        GuiFocusNavigator focusNavigator = new GuiFocusNavigator();

        // Register the client tick handler on both event buses so it receives tick events
        ControllerTickHandler tickHandler = new ControllerTickHandler(focusNavigator);
        FMLCommonHandler.instance()
            .bus()
            .register(tickHandler);
        MinecraftForge.EVENT_BUS.register(tickHandler);

        // Register the focus highlight renderer on the Forge event bus
        MinecraftForge.EVENT_BUS.register(new GuiFocusRenderer(focusNavigator));

        FuzziControls.LOG.info(
            "[FuzziControls] Controller tick handler registered. Active driver: {}",
            manager.getActiveDriverName());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
