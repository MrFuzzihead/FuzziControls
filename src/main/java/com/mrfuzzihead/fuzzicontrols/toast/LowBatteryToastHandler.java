package com.mrfuzzihead.fuzzicontrols.toast;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Emits a single low-battery toast when the active controller crosses the configured threshold. */
@SideOnly(Side.CLIENT)
public final class LowBatteryToastHandler {

    private final BatteryToastTracker tracker = new BatteryToastTracker();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (!Config.lowBatteryToast) {
            tracker.update(-1, false, Config.lowBatteryThreshold);
            return;
        }

        ControllerManager manager = ControllerManager.getInstance();
        int percent = manager.getActiveBatteryPercent();
        if (!tracker.update(percent, manager.isActiveBatteryCharging(), Config.lowBatteryThreshold)) return;

        ToastManager.getInstance()
            .add(
                new Toast(
                    Toast.Type.LOW_BATTERY,
                    Toast.Icon.DUALSENSE,
                    manager.getActiveDeviceName(),
                    manager.getActiveTransportName(),
                    percent + "%"));
    }
}
