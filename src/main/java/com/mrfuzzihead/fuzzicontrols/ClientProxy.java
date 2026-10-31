package com.mrfuzzihead.fuzzicontrols;

import net.minecraftforge.client.ClientCommandHandler;

import com.mrfuzzihead.fuzzicontrols.command.CommandReloadControllers;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerTickHandler;
import com.mrfuzzihead.fuzzicontrols.controller.DualSenseDriver;
import com.mrfuzzihead.fuzzicontrols.selftest.ClientSelfTest;
import com.mrfuzzihead.fuzzicontrols.selftest.MixinSelfTest;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusRenderer;

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

        // Register the D-pad focus-highlight renderer (only renders when dpadNavigation is enabled).
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new GuiFocusRenderer());

        // Register /fuzzicontrols reload so players can force controller re-init
        // without restarting the game (needed for DualSense which does not always
        // hot-plug cleanly through hidapi).
        ClientCommandHandler.instance.registerCommand(new CommandReloadControllers());

        FuzziControls.LOG.info(
            "[FuzziControls] Controller tick handler registered. Active driver: {}",
            manager.getActiveDriverName());

        // Verify that every accessor/invoker mixin was actually applied. Unit tests cannot apply
        // mixins, so this is what turns a broken refmap or a failed injection into one explicit log
        // line instead of a silently non-functional GUI. Also grepped by the CI client smoke test.
        MixinSelfTest.logReport();

        // Opt-in integration harness for automated (CI / headless) testing of the live GUI stack.
        if (ClientSelfTest.isEnabled()) {
            FuzziControls.LOG
                .info("[FuzziControls] Client self-test harness enabled ({} set).", "FUZZICONTROLS_SELFTEST");
            FMLCommonHandler.instance()
                .bus()
                .register(new ClientSelfTest());
        }

        Runtime.getRuntime()
            .addShutdownHook(new Thread(() -> {
                manager.shutdown();
                DualSenseDriver.shutdownSharedServices();
            }, "FuzziControls-Shutdown"));
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
