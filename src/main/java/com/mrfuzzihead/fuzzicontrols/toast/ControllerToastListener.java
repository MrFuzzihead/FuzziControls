package com.mrfuzzihead.fuzzicontrols.toast;

import java.util.Locale;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerConnection;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;

/**
 * Translates controller manager connection events into top-right toasts.
 *
 * <p>
 * Registered before the first {@link ControllerManager#init()} so a controller that is already
 * connected at game start still produces a toast once its first poll reports the transport.
 */
public final class ControllerToastListener implements ControllerManager.ConnectionListener {

    @Override
    public void onControllerConnected(ControllerConnection connection) {
        if (!Config.showControllerToasts) return;

        ToastManager.getInstance()
            .add(
                new Toast(
                    Toast.Type.CONNECTED,
                    iconFor(connection),
                    connection.getDeviceName(),
                    connection.getTransportName()));
    }

    @Override
    public void onControllerDisconnected(ControllerConnection connection) {
        if (!Config.showControllerToasts) return;

        ToastManager.getInstance()
            .add(
                new Toast(
                    Toast.Type.DISCONNECTED,
                    iconFor(connection),
                    connection.getDeviceName(),
                    connection.getTransportName()));
    }

    private static Toast.Icon iconFor(ControllerConnection connection) {
        String name = (connection.getDriverName() + " " + connection.getDeviceName()).toLowerCase(Locale.ROOT);
        if (name.contains("dualsense")) return Toast.Icon.DUALSENSE;
        if (name.contains("xinput") || name.contains("xbox")) return Toast.Icon.XBOX;
        return Toast.Icon.GENERIC;
    }
}
