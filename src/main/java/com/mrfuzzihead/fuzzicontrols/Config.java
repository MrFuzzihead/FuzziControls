package com.mrfuzzihead.fuzzicontrols;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerAction;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerButton;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerMapping;

public class Config {

    // ---- Categories ----
    private static final String CAT_GENERAL = Configuration.CATEGORY_GENERAL;
    private static final String CAT_CONTROLLER = "controller";
    private static final String CAT_BINDINGS = "bindings";

    // ---- Controller settings ----
    /**
     * Which driver to use: "auto" (default), "xinput", or "dualsense".
     * "auto" tries DualSense first, then XInput.
     */
    public static String controllerDriver = "auto";

    /**
     * XInput controller slot (0–3). Only used when driver is "xinput" or "auto".
     */
    public static int xInputControllerSlot = 0;

    /**
     * Dead-zone radius for analogue sticks in [0.0, 1.0].
     * Axis values with magnitude below this are treated as zero.
     */
    public static float stickDeadZone = 0.15f;

    /**
     * Minimum trigger value [0.0, 1.0] before the trigger registers as pressed.
     */
    public static float triggerThreshold = 0.2f;

    /**
     * Look-sensitivity multiplier for the right stick camera.
     */
    public static float lookSensitivity = 2.0f;

    /**
     * When true, holding the drop button drops the entire item stack.
     * When false (default), it always drops a single item regardless of hold duration.
     */
    public static boolean dropEntireStack = false;

    /**
     * When true (default), the sneak button acts as a toggle (press once to sneak, press again
     * to stand). When false, sneak is held — the player sneaks only while the button is held.
     */
    public static boolean sneakToggle = true;

    /**
     * Speed of the virtual GUI cursor driven by the left stick, in <em>display pixels per second</em>
     * at maximum stick deflection. Actual speed scales with deflection squared for fine-grained
     * control at low deflections. Because this is in display pixels, the speed is consistent
     * across all GUI screens (main menu, inventory, pause menu) regardless of GUI scale.
     */
    public static float inventoryCursorSensitivity = 300f;

    /**
     * When true, D-pad Up/Down navigates between focusable buttons on the current GUI screen
     * and A / Cross activates the focused button. D-pad Left/Right adjusts slider values.
     * When false (default), the D-pad is unbound in GUI screens and the left-stick virtual
     * cursor remains the only navigation method.
     */
    public static boolean dpadNavigation = false;

    /**
     * How much to adjust a slider value per D-pad left/right press when
     * {@link #dpadNavigation} is true. Value is in [0.0, 1.0] and represents a fraction of
     * the slider's full range. Default 0.05 = 5% per press.
     */
    public static float dpadSliderStep = 0.05f;

    // ---- Action → Button mapping (populated by synchroniseConfiguration) ----
    public static ControllerMapping controllerMapping = new ControllerMapping();

    // ---- Internal config reference ----
    private static Configuration configuration;

    public static void synchronizeConfiguration(File configFile) {
        configuration = new Configuration(configFile);

        // Controller
        controllerDriver = configuration.getString(
            "driver",
            CAT_CONTROLLER,
            controllerDriver,
            "Controller driver to use: auto | xinput | dualsense");

        xInputControllerSlot = configuration.getInt(
            "xInputSlot",
            CAT_CONTROLLER,
            xInputControllerSlot,
            0,
            3,
            "XInput controller slot to use (0-3). Only relevant when driver=xinput or auto.");

        stickDeadZone = configuration.getFloat(
            "stickDeadZone",
            CAT_CONTROLLER,
            stickDeadZone,
            0.0f,
            0.99f,
            "Dead-zone radius for analogue sticks (0.0 – 0.99).");

        triggerThreshold = configuration.getFloat(
            "triggerThreshold",
            CAT_CONTROLLER,
            triggerThreshold,
            0.0f,
            0.99f,
            "Minimum trigger value to register as pressed (0.0 – 0.99).");

        lookSensitivity = configuration.getFloat(
            "lookSensitivity",
            CAT_CONTROLLER,
            lookSensitivity,
            0.1f,
            10.0f,
            "Right-stick camera sensitivity multiplier.");

        dropEntireStack = configuration.getBoolean(
            "dropEntireStack",
            CAT_CONTROLLER,
            dropEntireStack,
            "When true, holding the drop button drops the entire item stack. "
                + "When false (default), it always drops a single item.");

        sneakToggle = configuration.getBoolean(
            "sneakToggle",
            CAT_CONTROLLER,
            sneakToggle,
            "When true (default), the sneak button toggles sneak on/off. "
                + "When false, sneak is held — player sneaks only while the button is held.");

        inventoryCursorSensitivity = configuration.getFloat(
            "inventoryCursorSensitivity",
            CAT_CONTROLLER,
            inventoryCursorSensitivity,
            1f,
            2000f,
            "Virtual GUI cursor speed in display pixels per second at full left-stick deflection. "
                + "Consistent across all screens regardless of GUI scale.");

        dpadNavigation = configuration.getBoolean(
            "dpadNavigation",
            CAT_CONTROLLER,
            dpadNavigation,
            "When true, D-pad Up/Down navigates between buttons in any open GUI screen, "
                + "D-pad Left/Right adjusts sliders, and A/Cross activates the focused element. "
                + "When false (default), the left-stick virtual cursor is the only GUI navigation method.");

        dpadSliderStep = configuration.getFloat(
            "dpadSliderStep",
            CAT_CONTROLLER,
            dpadSliderStep,
            0.01f,
            1.0f,
            "Fraction of slider range adjusted per D-pad left/right press when dpadNavigation = true. "
                + "Default 0.05 = 5%% per press.");

        // Bindings: persist each action → button pair
        ControllerMapping defaults = new ControllerMapping();
        for (ControllerAction action : ControllerAction.values()) {
            ControllerButton defaultBtn = defaults.getButton(action);
            String defaultName = defaultBtn != null ? defaultBtn.name() : "NONE";
            String stored = configuration.getString(
                action.name(),
                CAT_BINDINGS,
                defaultName,
                "Button bound to " + action.name() + ". Valid values: " + buttonNames());
            ControllerButton bound = parseButton(stored, defaultBtn);
            if (bound != null) {
                controllerMapping.bind(action, bound);
            }
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /** Saves the current mapping back to disk (e.g. after GUI remapping). */
    public static void save() {
        if (configuration == null) return;
        for (ControllerAction action : ControllerAction.values()) {
            ControllerButton btn = controllerMapping.getButton(action);
            configuration.get(CAT_BINDINGS, action.name(), btn != null ? btn.name() : "NONE")
                .set(btn != null ? btn.name() : "NONE");
        }
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    // ---- Helpers ----

    private static ControllerButton parseButton(String name, ControllerButton fallback) {
        if (name == null || name.isEmpty() || "NONE".equalsIgnoreCase(name)) return fallback;
        try {
            return ControllerButton.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            FuzziControls.LOG.warn("[FuzziControls] Unknown button '{}' in config; using default.", name);
            return fallback;
        }
    }

    private static String buttonNames() {
        StringBuilder sb = new StringBuilder();
        ControllerButton[] values = ControllerButton.values();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i].name());
            if (i < values.length - 1) sb.append(", ");
        }
        return sb.toString();
    }
}
