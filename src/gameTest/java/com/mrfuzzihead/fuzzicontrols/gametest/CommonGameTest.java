package com.mrfuzzihead.fuzzicontrols.gametest;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerAction;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerButton;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerMapping;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/**
 * Server-side checks: the mod must load, the config must be populated, and the default binding
 * table must be intact. This is the only side that can run on a dedicated server; the interesting
 * controller/GUI behaviour needs the client.
 */
public final class CommonGameTest implements IGameTest {

    @Override
    public void preInit(FMLPreInitializationEvent event) {}

    @Override
    public void onServerStarted(FMLServerStartedEvent event) {
        GameTestReport report = new GameTestReport("server");

        report.check("config mapping loaded", Config.controllerMapping != null);

        String driver = Config.controllerDriver;
        report.check(
            "controller driver is a known value",
            driver != null && ("auto".equalsIgnoreCase(driver) || "xinput".equalsIgnoreCase(driver)
                || "dualsense".equalsIgnoreCase(driver)
                || "none".equalsIgnoreCase(driver)));

        report.check(
            "xInputControllerSlot in range",
            Config.xInputControllerSlot >= 0 && Config.xInputControllerSlot <= 3);
        report.check("stickDeadZone in range", Config.stickDeadZone >= 0f && Config.stickDeadZone < 1f);
        report.check("triggerThreshold in range", Config.triggerThreshold >= 0f && Config.triggerThreshold < 1f);
        report.check("lookSensitivity positive", Config.lookSensitivity > 0f);
        report.check("inventoryCursorSensitivity positive", Config.inventoryCursorSensitivity > 0f);

        ControllerMapping mapping = Config.controllerMapping;
        if (mapping != null) {
            report.check("JUMP is bound to A", mapping.getButton(ControllerAction.JUMP) == ControllerButton.A);
            report.check(
                "ATTACK is bound to RIGHT_TRIGGER",
                mapping.getButton(ControllerAction.ATTACK) == ControllerButton.RIGHT_TRIGGER);
            report.check(
                "binding table is non-empty",
                !mapping.getAllBindings()
                    .isEmpty());
        }

        report.finish();
    }
}
