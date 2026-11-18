package com.mrfuzzihead.fuzzicontrols.toast;

/**
 * One controller connection notification displayed by {@link ToastManager}.
 *
 * <p>
 * The class deliberately holds only the toast's data, not its title text. The renderer resolves
 * the localized title and builds the device/transport subtitle at draw time, so queueing a toast
 * from the client tick thread never touches the font or language system.
 */
public final class Toast {

    public enum Type {
        CONNECTED,
        DISCONNECTED,
        LOW_BATTERY
    }

    /** Which controller family sprite to show in the toast. */
    public enum Icon {
        DUALSENSE,
        XBOX,
        GENERIC
    }

    private static final long DEFAULT_DURATION_MILLIS = 5000L;

    private final Type type;
    private final Icon icon;
    private final String deviceName;
    private final String transportName;
    private final String detail;
    private final long durationMillis;

    public Toast(Type type, String deviceName, String transportName) {
        this(type, Icon.GENERIC, deviceName, transportName, "", DEFAULT_DURATION_MILLIS);
    }

    public Toast(Type type, Icon icon, String deviceName, String transportName) {
        this(type, icon, deviceName, transportName, "", DEFAULT_DURATION_MILLIS);
    }

    public Toast(Type type, Icon icon, String deviceName, String transportName, String detail) {
        this(type, icon, deviceName, transportName, detail, DEFAULT_DURATION_MILLIS);
    }

    Toast(Type type, Icon icon, String deviceName, String transportName, long durationMillis) {
        this(type, icon, deviceName, transportName, "", durationMillis);
    }

    Toast(Type type, Icon icon, String deviceName, String transportName, String detail, long durationMillis) {
        this.type = type != null ? type : Type.DISCONNECTED;
        this.icon = icon != null ? icon : Icon.GENERIC;
        this.deviceName = deviceName != null ? deviceName : "";
        this.transportName = transportName != null ? transportName : "";
        this.detail = detail != null ? detail : "";
        this.durationMillis = Math.max(1L, durationMillis);
    }

    public Type getType() {
        return type;
    }

    public Icon getIcon() {
        return icon;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getTransportName() {
        return transportName;
    }

    /** Optional short detail, e.g. {@code "15%"} for a low-battery toast. */
    public String getDetail() {
        return detail;
    }

    public long getDurationMillis() {
        return durationMillis;
    }
}
