package com.mrfuzzihead.fuzzicontrols.toast;

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
            .add(new Toast(Toast.Type.CONNECTED, connection.getDeviceName(), connection.getTransportName()));
    }

    @Override
    public void onControllerDisconnected(ControllerConnection connection) {
        if (!Config.showControllerToasts) return;

        ToastManager.getInstance()
            .add(new Toast(Toast.Type.DISCONNECTED, connection.getDeviceName(), connection.getTransportName()));
    }
}
