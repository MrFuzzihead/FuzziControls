package com.mrfuzzihead.fuzzicontrols;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerAction;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerButton;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerMapping;
import com.mrfuzzihead.fuzzicontrols.controller.KeyboardShortcut;

public class Config {

    // ---- Categories ----
    private static final String CAT_GENERAL = Configuration.CATEGORY_GENERAL;
    private static final String CAT_CONTROLLER = "controller";
    private static final String CAT_BINDINGS = "bindings";
    private static final String CAT_SHORTCUTS = "shortcuts";

    /** Number of controller-button to keyboard-key shortcut slots exposed by the settings GUI. */
    public static final int SHORTCUT_SLOTS = 4;

    // ---- Controller settings ----
    /**
     * Master switch for all controller input. When false, the manager does not create or probe a
     * driver and the game is keyboard/mouse only. The selected {@link #controllerDriver} is kept so
     * turning input back on restores the player's choice.
     */
    public static boolean controllerEnabled = true;
    /**
     * Which driver to use: "auto" (default), "xinput", "dualsense", or "none".
     * "auto" tries DualSense first, then XInput. "none" disables all controller probing.
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
     * When true (default), holding the drop button drops the entire item stack.
     * When false, it always drops a single item regardless of hold duration.
     */
    public static boolean dropEntireStack = true;

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
     * When true (default), the left stick drives analogue movement (gentle push = walk, full push = run).
     * When false, movement is digital (on/off past the dead-zone).
     *
     * <p>
     * The scaling is applied by a mixin that is only applied at class load when this was already
     * true at startup, so <b>enabling it requires a game restart</b>. Disabling it takes effect
     * immediately, because the injected code re-checks this flag on every movement tick.
     */
    public static boolean analogMovement = true;

    /**
     * When true (default), the D-pad navigates GUI elements discretely (console-style) instead of
     * relying solely on the virtual cursor: D-pad directions move focus between buttons, list entries
     * and inventory slots, and holding a direction repeats after a short delay. When false, the D-pad
     * directions are unused and the left-stick virtual cursor is the only GUI navigation method.
     */
    public static boolean dpadNavigation = true;

    /**
     * Step size for D-pad slider adjustment, as a fraction of the slider's full range.
     * A value of 0.05 means each D-pad left/right press moves the slider by 5% of its range, or by
     * one step of the option's own value grid when that grid is coarser (see
     * {@link com.mrfuzzihead.fuzzicontrols.util.SliderStepper}). Valid range is [0.01, 0.5].
     */
    public static float dpadSliderStep = 0.05f;

    /**
     * When true (default), the right stick can be used to scroll in GUI screens
     * (inventories, lists, modded scrolling GUIs, etc.) by pushing up/down.
     * This is independent of D-pad navigation and the virtual cursor.
     */
    public static boolean rightStickScrolling = true;

    /**
     * Scroll speed in "notches per second" at full right-stick deflection.
     * Higher values scroll faster. Range [1.0, 100.0], default 10.0.
     */
    public static float scrollSensitivity = 10.0f;

    /**
     * When true (default), gameplay events such as taking damage or mining produce controller
     * vibration on supported pads.
     */
    public static boolean rumbleEnabled = true;

    /**
     * Global rumble strength multiplier in [0.0, 1.0]. Applied to every pulse.
     */
    public static float rumbleIntensity = 0.6f;

    /**
     * When true (default), show a modern-style toast in the top-right corner when a controller
     * connects or disconnects.
     */
    public static boolean showControllerToasts = true;

    // ---- Action → Button mapping (populated by synchroniseConfiguration) ----
    public static ControllerMapping controllerMapping = new ControllerMapping();

    // ---- Controller-button → keyboard-key shortcuts (modded KeyBinding support) ----
    public static KeyboardShortcut[] keyboardShortcuts = newShortcutArray();

    // ---- Internal config reference ----
    private static Configuration configuration;

    /** The file the current {@link #configuration} was read from, for {@link #reload()}. */
    private static File configurationFile;

    public static void synchronizeConfiguration(File configFile) {
        configurationFile = configFile;
        configuration = new Configuration(configFile);

        // Controller
        controllerEnabled = configuration.getBoolean(
            "enabled",
            CAT_CONTROLLER,
            controllerEnabled,
            "Master switch for controller input. When false, no driver is created or polled.");

        controllerDriver = configuration.getString(
            "driver",
            CAT_CONTROLLER,
            controllerDriver,
            "Controller driver to use: auto | xinput | dualsense | none");
        if ("none".equalsIgnoreCase(controllerDriver == null ? "" : controllerDriver.trim())) {
            // Keep the legacy driver=none spelling working, but expose it as the master switch in
            // the settings GUI. The driver value itself is preserved for a later re-enable.
            controllerEnabled = false;
        }

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
            "When true (default), holding the drop button drops the entire item stack. "
                + "When false, it always drops a single item.");

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

        analogMovement = configuration.getBoolean(
            "analogMovement",
            CAT_CONTROLLER,
            analogMovement,
            "When true (default), the left stick drives analogue movement speed (gentle push = slow walk, "
                + "full push = full run). When false, movement is digital (on/off past the dead-zone).");

        dpadNavigation = configuration.getBoolean(
            "dpadNavigation",
            CAT_CONTROLLER,
            dpadNavigation,
            "When true (default), D-pad navigates GUI elements discretely (console-style). "
                + "When false, the D-pad is unused and the left-stick virtual cursor is the only navigation method.");

        dpadSliderStep = configuration.getFloat(
            "dpadSliderStep",
            CAT_CONTROLLER,
            dpadSliderStep,
            0.01f,
            0.5f,
            "Step size for D-pad slider adjustment as a fraction of the full range [0.0, 1.0]. "
                + "Each D-pad left/right press moves the slider by this fraction.");

        rightStickScrolling = configuration.getBoolean(
            "rightStickScrolling",
            CAT_CONTROLLER,
            rightStickScrolling,
            "When true (default), the right stick scrolls in GUI screens (inventories, lists, etc.).");

        scrollSensitivity = configuration.getFloat(
            "scrollSensitivity",
            CAT_CONTROLLER,
            scrollSensitivity,
            1.0f,
            100.0f,
            "Scroll speed in notches per second at full right-stick deflection [1.0 – 100.0].");

        rumbleEnabled = configuration.getBoolean(
            "rumbleEnabled",
            CAT_CONTROLLER,
            rumbleEnabled,
            "Enable controller vibration for damage, mining and attacks.");

        rumbleIntensity = configuration.getFloat(
            "rumbleIntensity",
            CAT_CONTROLLER,
            rumbleIntensity,
            0.0f,
            1.0f,
            "Controller vibration strength multiplier (0.0 - 1.0).");

        showControllerToasts = configuration.getBoolean(
            "showControllerToasts",
            CAT_CONTROLLER,
            showControllerToasts,
            "Show a modern-style toast in the top-right corner when a controller connects or disconnects.");

        // Bindings: persist each action → button pair
        ControllerMapping defaults = new ControllerMapping();
        // Reset to a fresh default mapping so repeated loads are idempotent.
        controllerMapping = new ControllerMapping();
        for (ControllerAction action : ControllerAction.values()) {
            ControllerButton defaultBtn = defaults.getButton(action);
            String defaultName = defaultBtn != null ? defaultBtn.name() : "NONE";
            String stored = configuration.getString(
                action.name(),
                CAT_BINDINGS,
                defaultName,
                "Button bound to " + action.name() + ". Valid values: " + buttonNames());
            stored = stored == null ? "" : stored.trim();
            if ("NONE".equalsIgnoreCase(stored)) {
                // An explicit NONE value (including surrounding whitespace) means the action is unbound.
                controllerMapping.bind(action, null);
            } else {
                ControllerButton bound = parseButton(stored, defaultBtn);
                if (bound != null) {
                    controllerMapping.bind(action, bound);
                }
            }
        }

        // Shortcuts: fixed slots keep the config format simple and editable by hand.
        keyboardShortcuts = newShortcutArray();
        for (int i = 0; i < SHORTCUT_SLOTS; i++) {
            String stored = configuration.getString(
                "slot" + (i + 1),
                CAT_SHORTCUTS,
                "NONE",
                "Controller shortcut " + (i + 1) + " in BUTTON:KEYCODE:HOLD|TAP format, or NONE.");
            keyboardShortcuts[i] = KeyboardShortcut.parse(stored);
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /**
     * Returns true when the configuration was already loaded from the given file (same absolute
     * path). The early mixin loader needs the config before Forge's pre-init; the proxy can use
     * this to avoid parsing the same file twice.
     *
     * @param configFile candidate config file
     * @return true when {@link #synchronizeConfiguration(File)} has already read that file
     */
    public static boolean isLoadedFrom(File configFile) {
        if (configurationFile == null || configFile == null) return false;
        return configurationFile.getAbsoluteFile()
            .equals(configFile.getAbsoluteFile());
    }

    /**
     * Re-reads the configuration file, so config changes apply without restarting the game.
     * Called by {@code /fuzzicontrols reload}.
     *
     * <p>
     * Keys present in the file override the current values. A key <em>absent</em> from the file
     * keeps its current value rather than reverting to the documented default, because Forge's
     * {@code Configuration} getters use the in-memory value as the default for a missing key (and
     * that value is then written back to the file).
     *
     * @return true when a config file has been loaded at least once and was re-read
     */
    public static boolean reload() {
        if (configurationFile == null) return false;
        synchronizeConfiguration(configurationFile);
        return true;
    }

    /**
     * Saves the current settings, action bindings and keyboard shortcuts back to disk (e.g.
     * after a change in the settings GUI).
     */
    public static void save() {
        if (configuration == null) return;

        configuration.get(CAT_CONTROLLER, "enabled", true)
            .set(controllerEnabled);
        String savedDriver = controllerDriver == null ? "auto" : controllerDriver;
        configuration.get(CAT_CONTROLLER, "driver", savedDriver)
            .set(savedDriver);
        configuration.get(CAT_CONTROLLER, "xInputSlot", xInputControllerSlot)
            .set(xInputControllerSlot);
        configuration.get(CAT_CONTROLLER, "stickDeadZone", stickDeadZone)
            .set(stickDeadZone);
        configuration.get(CAT_CONTROLLER, "triggerThreshold", triggerThreshold)
            .set(triggerThreshold);
        configuration.get(CAT_CONTROLLER, "lookSensitivity", lookSensitivity)
            .set(lookSensitivity);
        configuration.get(CAT_CONTROLLER, "dropEntireStack", dropEntireStack)
            .set(dropEntireStack);
        configuration.get(CAT_CONTROLLER, "sneakToggle", sneakToggle)
            .set(sneakToggle);
        configuration.get(CAT_CONTROLLER, "inventoryCursorSensitivity", inventoryCursorSensitivity)
            .set(inventoryCursorSensitivity);
        configuration.get(CAT_CONTROLLER, "analogMovement", analogMovement)
            .set(analogMovement);
        configuration.get(CAT_CONTROLLER, "dpadNavigation", dpadNavigation)
            .set(dpadNavigation);
        configuration.get(CAT_CONTROLLER, "dpadSliderStep", dpadSliderStep)
            .set(dpadSliderStep);
        configuration.get(CAT_CONTROLLER, "rightStickScrolling", rightStickScrolling)
            .set(rightStickScrolling);
        configuration.get(CAT_CONTROLLER, "scrollSensitivity", scrollSensitivity)
            .set(scrollSensitivity);
        configuration.get(CAT_CONTROLLER, "rumbleEnabled", rumbleEnabled)
            .set(rumbleEnabled);
        configuration.get(CAT_CONTROLLER, "rumbleIntensity", rumbleIntensity)
            .set(rumbleIntensity);
        configuration.get(CAT_CONTROLLER, "showControllerToasts", showControllerToasts)
            .set(showControllerToasts);

        for (ControllerAction action : ControllerAction.values()) {
            ControllerButton btn = controllerMapping.getButton(action);
            configuration.get(CAT_BINDINGS, action.name(), btn != null ? btn.name() : "NONE")
                .set(btn != null ? btn.name() : "NONE");
        }

        for (int i = 0; i < SHORTCUT_SLOTS; i++) {
            KeyboardShortcut shortcut = keyboardShortcuts != null && i < keyboardShortcuts.length ? keyboardShortcuts[i]
                : null;
            configuration.get(CAT_SHORTCUTS, "slot" + (i + 1), "NONE")
                .set(shortcut != null ? shortcut.serialise() : "NONE");
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static KeyboardShortcut[] newShortcutArray() {
        KeyboardShortcut[] shortcuts = new KeyboardShortcut[SHORTCUT_SLOTS];
        for (int i = 0; i < shortcuts.length; i++) {
            shortcuts[i] = new KeyboardShortcut();
        }
        return shortcuts;
    }

    // ---- Helpers ----

    private static ControllerButton parseButton(String name, ControllerButton fallback) {
        if (name == null) return fallback;
        String trimmed = name.trim();
        // An explicit NONE is handled by synchronizeConfiguration(); empty/whitespace means the
        // user cleared the value, so keep the documented default rather than throwing.
        if (trimmed.isEmpty() || "NONE".equalsIgnoreCase(trimmed)) return fallback;
        try {
            return ControllerButton.valueOf(trimmed.toUpperCase(Locale.ROOT));
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
