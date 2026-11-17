package com.mrfuzzihead.fuzzicontrols.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerAction;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerButton;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerMapping;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerState;
import com.mrfuzzihead.fuzzicontrols.controller.KeyboardShortcut;
import com.mrfuzzihead.fuzzicontrols.gui.widget.GuiFuzziButton;
import com.mrfuzzihead.fuzzicontrols.gui.widget.GuiFuzziRowButton;
import com.mrfuzzihead.fuzzicontrols.gui.widget.GuiFuzziSlider;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Main FuzziControls settings screen.
 *
 * <p>
 * The screen is intentionally built from ordinary {@link GuiButton}s and custom flat buttons rather
 * than a {@code GuiSlot}. That keeps every control visible to {@link
 * com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator}, so the entire menu works with the D-pad
 * and A/B buttons without a mouse.
 */
@SideOnly(Side.CLIENT)
public class GuiFuzziControls extends GuiScreen implements ControllerCaptureScreen {

    private enum Page {

        CONTROLLER("Controller"),
        BINDINGS("Bindings"),
        INPUT("Input"),
        INTERFACE("Interface");

        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private enum BindingCategory {

        MOVEMENT("Move",
            new ControllerAction[] { ControllerAction.MOVE_FORWARD, ControllerAction.MOVE_BACKWARD,
                ControllerAction.STRAFE_LEFT, ControllerAction.STRAFE_RIGHT }),
        ACTIONS("Action",
            new ControllerAction[] { ControllerAction.JUMP, ControllerAction.SNEAK, ControllerAction.SPRINT,
                ControllerAction.ATTACK, ControllerAction.USE_ITEM, ControllerAction.PICK_BLOCK }),
        HOTBAR("Hotbar", new ControllerAction[] { ControllerAction.HOTBAR_NEXT, ControllerAction.HOTBAR_PREV }),
        UI("UI",
            new ControllerAction[] { ControllerAction.INVENTORY, ControllerAction.DROP_ITEM, ControllerAction.CLOSE_GUI,
                ControllerAction.CHAT, ControllerAction.PAUSE, ControllerAction.COMMAND }),
        GUI("GUI",
            new ControllerAction[] { ControllerAction.GUI_LEFT_CLICK, ControllerAction.GUI_RIGHT_CLICK,
                ControllerAction.GUI_SHIFT_CLICK, ControllerAction.GUI_NAV_UP, ControllerAction.GUI_NAV_DOWN,
                ControllerAction.GUI_NAV_LEFT, ControllerAction.GUI_NAV_RIGHT, ControllerAction.GUI_NAV_CONFIRM }),
        KEYS("Keys", null);

        final String label;
        final ControllerAction[] actions;

        BindingCategory(String label, ControllerAction[] actions) {
            this.label = label;
            this.actions = actions;
        }
    }

    private enum CaptureMode {
        NONE,
        BIND_ACTION,
        SHORTCUT_CONTROLLER,
        SHORTCUT_KEY
    }

    private static final int ID_TAB_BASE = 1000;
    private static final int ID_DONE = 1010;
    private static final int ID_CONTROLLER_ENABLED = 1100;
    private static final int ID_CONTROLLER_DRIVER = 1101;
    private static final int ID_CONTROLLER_SLOT = 1102;
    private static final int ID_CONTROLLER_TOASTS = 1103;
    private static final int ID_CONTROLLER_RESCAN = 1104;
    private static final int ID_INPUT_ANALOG = 1110;
    private static final int ID_INPUT_SNEAK = 1111;
    private static final int ID_INPUT_DROP = 1112;
    private static final int ID_INPUT_LOOK_SENSITIVITY = 1113;
    private static final int ID_INPUT_DEAD_ZONE = 1114;
    private static final int ID_INPUT_TRIGGER = 1115;
    private static final int ID_INTERFACE_DPAD = 1120;
    private static final int ID_INTERFACE_CURSOR = 1121;
    private static final int ID_INTERFACE_SCROLL = 1122;
    private static final int ID_INTERFACE_SCROLL_SPEED = 1123;
    private static final int ID_INTERFACE_SLIDER_STEP = 1124;
    private static final int ID_CATEGORY_BASE = 1200;
    private static final int ID_ACTION_BASE = 1300;
    private static final int ID_SHORTCUT_BASE = 1400;
    private static final int ID_REBIND = 1500;
    private static final int ID_CLEAR = 1501;
    private static final int ID_RESET_PAGE = 1502;
    private static final int ID_RESET_ALL = 1503;
    private static final int ID_SHORTCUT_MODE = 1504;

    private static final int TAB_HEIGHT = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 4;

    private final GuiScreen parent;

    private Page page = Page.CONTROLLER;
    private BindingCategory category = BindingCategory.MOVEMENT;
    private ControllerAction selectedAction = ControllerAction.MOVE_FORWARD;
    private int selectedShortcut = 0;

    private String status = "";
    private boolean resetAllArmed;
    private int resetAllTicks;

    private CaptureMode captureMode = CaptureMode.NONE;
    private ControllerAction captureAction;
    private int captureShortcutIndex = -1;
    private ControllerButton captureCandidate;
    private int captureHoldTicks;
    private boolean captureArmed;
    private ControllerButton pendingShortcutButton;

    /** No-arg constructor for Forge's {@code IModGuiFactory}; returns to the screen that opened it. */
    public GuiFuzziControls() {
        this(Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().currentScreen : null);
    }

    public GuiFuzziControls(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        rebuildWidgets();
    }

    @Override
    public void onGuiClosed() {
        // A slider may still be mid-drag when the screen is closed; make sure its value is saved.
        Config.save();
    }

    private void rebuildWidgets() {
        buttonList.clear();

        // While capturing, the overlay owns all input. Removing the underlying buttons prevents a
        // stray mouse click from changing a setting behind the prompt.
        if (captureMode != CaptureMode.NONE) return;

        int contentX = contentX();
        int contentWidth = contentWidth();
        int tabGap = 2;
        int tabWidth = Math.max(40, (contentWidth - tabGap * (Page.values().length - 1)) / Page.values().length);
        int tabsWidth = tabWidth * Page.values().length + tabGap * (Page.values().length - 1);
        int tabX = contentX + Math.max(0, (contentWidth - tabsWidth) / 2);
        int tabY = 28;
        for (Page candidate : Page.values()) {
            GuiFuzziButton button = new GuiFuzziButton(
                ID_TAB_BASE + candidate.ordinal(),
                tabX,
                tabY,
                tabWidth,
                TAB_HEIGHT,
                candidate.label);
            button.setActive(candidate == page);
            buttonList.add(button);
            tabX += tabWidth + tabGap;
        }

        switch (page) {
            case CONTROLLER -> buildControllerPage(contentX);
            case BINDINGS -> buildBindingsPage(contentX, contentWidth);
            case INPUT -> buildInputPage(contentX, contentWidth);
            case INTERFACE -> buildInterfacePage(contentX, contentWidth);
        }

        GuiFuzziButton done = new GuiFuzziButton(ID_DONE, width / 2 - 50, height - 27, 100, ROW_HEIGHT, "Done");
        done.setActive(false);
        buttonList.add(done);
    }

    private void buildControllerPage(int contentX) {
        int y = 56;
        int width = contentWidth();
        ControllerManager manager = ControllerManager.getInstance();

        GuiFuzziRowButton enabled = row(
            ID_CONTROLLER_ENABLED,
            contentX,
            y,
            width,
            "Controller input",
            Config.controllerEnabled ? "ON" : "OFF");
        enabled.setSelected(true);
        y += ROW_HEIGHT + ROW_GAP;

        GuiFuzziRowButton driver = row(
            ID_CONTROLLER_DRIVER,
            contentX,
            y,
            width,
            "Driver",
            driverLabel(Config.controllerDriver));
        driver.enabled = Config.controllerEnabled;
        y += ROW_HEIGHT + ROW_GAP;

        GuiFuzziRowButton slot = row(
            ID_CONTROLLER_SLOT,
            contentX,
            y,
            width,
            "XInput slot",
            Integer.toString(Config.xInputControllerSlot));
        slot.enabled = Config.controllerEnabled && isXInputSelected();
        y += ROW_HEIGHT + ROW_GAP;

        row(ID_CONTROLLER_TOASTS, contentX, y, width, "Connection toasts", Config.showControllerToasts ? "ON" : "OFF");
        y += ROW_HEIGHT + ROW_GAP;

        GuiFuzziRowButton rescan = row(
            ID_CONTROLLER_RESCAN,
            contentX,
            y,
            width,
            "Rescan controllers",
            manager.isActive() ? "Connected" : "No device");
        rescan.enabled = Config.controllerEnabled;
    }

    private void buildInputPage(int contentX, int contentWidth) {
        int y = 56;
        int width = contentWidth;

        row(ID_INPUT_ANALOG, contentX, y, width, "Analogue movement", Config.analogMovement ? "ON" : "OFF");
        y += ROW_HEIGHT + ROW_GAP;

        row(ID_INPUT_SNEAK, contentX, y, width, "Sneak button", Config.sneakToggle ? "Toggle" : "Hold");
        y += ROW_HEIGHT + ROW_GAP;

        row(ID_INPUT_DROP, contentX, y, width, "Drop button", Config.dropEntireStack ? "Stack" : "Single");
        y += ROW_HEIGHT + ROW_GAP;

        addSlider(
            ID_INPUT_LOOK_SENSITIVITY,
            contentX,
            y,
            width,
            "Look sensitivity",
            0.1f,
            10.0f,
            Config.lookSensitivity,
            2,
            1f,
            "");
        y += ROW_HEIGHT + ROW_GAP;

        addSlider(
            ID_INPUT_DEAD_ZONE,
            contentX,
            y,
            width,
            "Stick dead zone",
            0.0f,
            0.99f,
            Config.stickDeadZone,
            2,
            1f,
            "");
        y += ROW_HEIGHT + ROW_GAP;

        addSlider(
            ID_INPUT_TRIGGER,
            contentX,
            y,
            width,
            "Trigger threshold",
            0.0f,
            0.99f,
            Config.triggerThreshold,
            2,
            1f,
            "");
    }

    private void buildInterfacePage(int contentX, int contentWidth) {
        int y = 56;
        int width = contentWidth;

        row(ID_INTERFACE_DPAD, contentX, y, width, "D-pad navigation", Config.dpadNavigation ? "ON" : "OFF");
        y += ROW_HEIGHT + ROW_GAP;

        addSlider(
            ID_INTERFACE_CURSOR,
            contentX,
            y,
            width,
            "GUI cursor speed",
            1f,
            2000f,
            Config.inventoryCursorSensitivity,
            0,
            1f,
            " px/s");
        y += ROW_HEIGHT + ROW_GAP;

        row(
            ID_INTERFACE_SCROLL,
            contentX,
            y,
            width,
            "Right-stick scrolling",
            Config.rightStickScrolling ? "ON" : "OFF");
        y += ROW_HEIGHT + ROW_GAP;

        addSlider(
            ID_INTERFACE_SCROLL_SPEED,
            contentX,
            y,
            width,
            "Scroll speed",
            1f,
            100f,
            Config.scrollSensitivity,
            1,
            1f,
            " /s");
        y += ROW_HEIGHT + ROW_GAP;

        addSlider(
            ID_INTERFACE_SLIDER_STEP,
            contentX,
            y,
            width,
            "D-pad slider step",
            0.01f,
            0.5f,
            Config.dpadSliderStep,
            0,
            100f,
            "%");
    }

    private void buildBindingsPage(int contentX, int contentWidth) {
        int categoryGap = 2;
        int categoryWidth = Math.max(
            32,
            (contentWidth - categoryGap * (BindingCategory.values().length - 1)) / BindingCategory.values().length);
        int categoriesWidth = categoryWidth * BindingCategory.values().length
            + categoryGap * (BindingCategory.values().length - 1);
        int categoryX = contentX + Math.max(0, (contentWidth - categoriesWidth) / 2);
        for (BindingCategory candidate : BindingCategory.values()) {
            GuiFuzziButton button = new GuiFuzziButton(
                ID_CATEGORY_BASE + candidate.ordinal(),
                categoryX,
                50,
                categoryWidth,
                14,
                candidate.label);
            button.setActive(candidate == category);
            buttonList.add(button);
            categoryX += categoryWidth + categoryGap;
        }

        int y = 70;
        if (category == BindingCategory.KEYS) {
            for (int i = 0; i < Config.SHORTCUT_SLOTS; i++) {
                GuiFuzziRowButton shortcutRow = new GuiFuzziRowButton(
                    ID_SHORTCUT_BASE + i,
                    contentX,
                    y,
                    contentWidth,
                    14,
                    "Shortcut " + (i + 1),
                    shortcutValue(Config.keyboardShortcuts[i]));
                shortcutRow.setSelected(i == selectedShortcut);
                buttonList.add(shortcutRow);
                y += 14;
            }
        } else {
            ControllerAction[] actions = category.actions;
            for (int i = 0; i < actions.length; i++) {
                ControllerAction action = actions[i];
                GuiFuzziRowButton actionRow = new GuiFuzziRowButton(
                    ID_ACTION_BASE + i,
                    contentX,
                    y,
                    contentWidth,
                    14,
                    actionLabel(action),
                    buttonLabel(Config.controllerMapping.getButton(action)));
                actionRow.setSelected(action == selectedAction);
                buttonList.add(actionRow);
                y += 14;
            }
        }

        int footerY = height - 50;
        int gap = 3;
        int buttonWidth = Math.max(42, (contentWidth - gap * 4) / 5);
        int buttonsWidth = buttonWidth * 5 + gap * 4;
        int buttonX = contentX + Math.max(0, (contentWidth - buttonsWidth) / 2);

        GuiFuzziButton rebind = new GuiFuzziButton(
            ID_REBIND,
            buttonX,
            footerY,
            buttonWidth,
            16,
            category == BindingCategory.KEYS ? "Bind" : "Rebind");
        rebind.enabled = hasSelection();
        buttonList.add(rebind);
        buttonX += buttonWidth + gap;

        GuiFuzziButton clear = new GuiFuzziButton(ID_CLEAR, buttonX, footerY, buttonWidth, 16, "Clear");
        clear.enabled = hasSelection();
        buttonList.add(clear);
        buttonX += buttonWidth + gap;

        GuiFuzziButton mode = new GuiFuzziButton(
            ID_SHORTCUT_MODE,
            buttonX,
            footerY,
            buttonWidth,
            16,
            shortcutModeLabel());
        mode.enabled = category == BindingCategory.KEYS && selectedShortcutIsBound();
        buttonList.add(mode);
        buttonX += buttonWidth + gap;

        GuiFuzziButton reset = new GuiFuzziButton(ID_RESET_PAGE, buttonX, footerY, buttonWidth, 16, "Reset");
        buttonList.add(reset);
        buttonX += buttonWidth + gap;

        GuiFuzziButton resetAll = new GuiFuzziButton(
            ID_RESET_ALL,
            buttonX,
            footerY,
            buttonWidth,
            16,
            resetAllArmed ? "Confirm" : "Reset All");
        resetAll.setActive(resetAllArmed);
        buttonList.add(resetAll);
    }

    private GuiFuzziRowButton row(int id, int x, int y, int width, String label, String value) {
        GuiFuzziRowButton button = new GuiFuzziRowButton(id, x, y, width, ROW_HEIGHT, label, value);
        buttonList.add(button);
        return button;
    }

    private void addSlider(int id, int x, int y, int width, String label, float min, float max, float value,
        int decimals, float displayFactor, String suffix) {
        GuiFuzziSlider slider = new GuiFuzziSlider(
            id,
            x,
            y,
            width,
            ROW_HEIGHT,
            label,
            min,
            max,
            value,
            decimals,
            displayFactor,
            suffix,
            () -> {
                applySliderValue(id);
                Config.save();
            });
        buttonList.add(slider);
    }

    private void applySliderValue(int id) {
        GuiFuzziSlider slider = findSlider(id);
        if (slider == null) return;
        switch (id) {
            case ID_INPUT_LOOK_SENSITIVITY -> Config.lookSensitivity = slider.getValue();
            case ID_INPUT_DEAD_ZONE -> Config.stickDeadZone = slider.getValue();
            case ID_INPUT_TRIGGER -> Config.triggerThreshold = slider.getValue();
            case ID_INTERFACE_CURSOR -> Config.inventoryCursorSensitivity = slider.getValue();
            case ID_INTERFACE_SCROLL_SPEED -> Config.scrollSensitivity = slider.getValue();
            case ID_INTERFACE_SLIDER_STEP -> Config.dpadSliderStep = slider.getValue();
            default -> {}
        }
    }

    private GuiFuzziSlider findSlider(int id) {
        for (GuiButton button : buttonList) {
            if (button.id == id && button instanceof GuiFuzziSlider slider) {
                return slider;
            }
        }
        return null;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) return;

        if (button.id >= ID_TAB_BASE && button.id < ID_TAB_BASE + Page.values().length) {
            page = Page.values()[button.id - ID_TAB_BASE];
            resetAllArmed = false;
            rebuildWidgets();
            return;
        }

        if (button.id == ID_DONE) {
            Minecraft.getMinecraft()
                .displayGuiScreen(parent);
            return;
        }

        if (button.id == ID_CONTROLLER_ENABLED) {
            Config.controllerEnabled = !Config.controllerEnabled;
            if (Config.controllerEnabled && "none".equalsIgnoreCase(trim(Config.controllerDriver))) {
                Config.controllerDriver = "auto";
            }
            ControllerManager.getInstance()
                .init();
            Config.save();
            rebuildWidgets();
            return;
        }

        if (button.id == ID_CONTROLLER_DRIVER) {
            Config.controllerDriver = nextDriver(Config.controllerDriver);
            ControllerManager.getInstance()
                .init();
            Config.save();
            rebuildWidgets();
            return;
        }

        if (button.id == ID_CONTROLLER_SLOT) {
            Config.xInputControllerSlot = (Config.xInputControllerSlot + 1) % 4;
            ControllerManager.getInstance()
                .init();
            Config.save();
            rebuildWidgets();
            return;
        }

        if (button.id == ID_CONTROLLER_TOASTS) {
            Config.showControllerToasts = !Config.showControllerToasts;
            Config.save();
            rebuildWidgets();
            return;
        }

        if (button.id == ID_CONTROLLER_RESCAN) {
            ControllerManager.getInstance()
                .init();
            status = ControllerManager.getInstance()
                .isActive()
                    ? "Rescanned: " + ControllerManager.getInstance()
                        .getActiveDeviceName()
                    : "Rescanned: no controller detected.";
            rebuildWidgets();
            return;
        }

        if (button.id >= ID_CATEGORY_BASE && button.id < ID_CATEGORY_BASE + BindingCategory.values().length) {
            category = BindingCategory.values()[button.id - ID_CATEGORY_BASE];
            selectedAction = category.actions != null ? category.actions[0] : null;
            selectedShortcut = 0;
            status = "";
            resetAllArmed = false;
            rebuildWidgets();
            return;
        }

        if (button.id >= ID_ACTION_BASE && button.id < ID_ACTION_BASE + 16) {
            int index = button.id - ID_ACTION_BASE;
            if (category.actions != null && index < category.actions.length) {
                selectedAction = category.actions[index];
                status = "";
                resetAllArmed = false;
                rebuildWidgets();
            }
            return;
        }

        if (button.id >= ID_SHORTCUT_BASE && button.id < ID_SHORTCUT_BASE + Config.SHORTCUT_SLOTS) {
            selectedShortcut = button.id - ID_SHORTCUT_BASE;
            status = "";
            resetAllArmed = false;
            rebuildWidgets();
            return;
        }

        if (button.id == ID_REBIND) {
            if (category == BindingCategory.KEYS) {
                beginShortcutCapture(selectedShortcut);
            } else if (selectedAction != null) {
                beginActionCapture(selectedAction);
            }
            return;
        }

        if (button.id == ID_CLEAR) {
            if (category == BindingCategory.KEYS) {
                if (Config.keyboardShortcuts[selectedShortcut] != null) {
                    Config.keyboardShortcuts[selectedShortcut].clear();
                }
            } else if (selectedAction != null) {
                Config.controllerMapping.bind(selectedAction, null);
            }
            Config.save();
            status = "Binding cleared.";
            rebuildWidgets();
            return;
        }

        if (button.id == ID_SHORTCUT_MODE) {
            KeyboardShortcut shortcut = Config.keyboardShortcuts[selectedShortcut];
            if (shortcut != null && shortcut.isBound()) {
                shortcut.setMode(
                    shortcut.getMode() == KeyboardShortcut.Mode.HOLD ? KeyboardShortcut.Mode.TAP
                        : KeyboardShortcut.Mode.HOLD);
                Config.save();
                status = "Shortcut " + (selectedShortcut + 1)
                    + " mode: "
                    + shortcut.getMode()
                        .name();
                rebuildWidgets();
            }
            return;
        }

        if (button.id == ID_RESET_PAGE) {
            resetPage();
            return;
        }

        if (button.id == ID_RESET_ALL) {
            if (!resetAllArmed) {
                resetAllArmed = true;
                resetAllTicks = 0;
                status = "Click Confirm to reset every binding and shortcut.";
                rebuildWidgets();
            } else {
                Config.controllerMapping = new ControllerMapping();
                Config.keyboardShortcuts = newShortcuts();
                selectedAction = category.actions != null ? category.actions[0] : null;
                selectedShortcut = 0;
                resetAllArmed = false;
                Config.save();
                status = "All bindings reset.";
                rebuildWidgets();
            }
            return;
        }

        switch (button.id) {
            case ID_INPUT_ANALOG -> {
                Config.analogMovement = !Config.analogMovement;
                Config.save();
                rebuildWidgets();
            }
            case ID_INPUT_SNEAK -> {
                Config.sneakToggle = !Config.sneakToggle;
                Config.save();
                rebuildWidgets();
            }
            case ID_INPUT_DROP -> {
                Config.dropEntireStack = !Config.dropEntireStack;
                Config.save();
                rebuildWidgets();
            }
            case ID_INTERFACE_DPAD -> {
                Config.dpadNavigation = !Config.dpadNavigation;
                Config.save();
                rebuildWidgets();
            }
            case ID_INTERFACE_SCROLL -> {
                Config.rightStickScrolling = !Config.rightStickScrolling;
                Config.save();
                rebuildWidgets();
            }
            default -> {}
        }
    }

    private void resetPage() {
        if (category == BindingCategory.KEYS) {
            Config.keyboardShortcuts = newShortcuts();
            selectedShortcut = 0;
        } else if (category.actions != null) {
            ControllerMapping defaults = new ControllerMapping();
            for (ControllerAction action : category.actions) {
                Config.controllerMapping.bind(action, defaults.getButton(action));
            }
            selectedAction = category.actions[0];
        }
        Config.save();
        status = "Page reset to defaults.";
        resetAllArmed = false;
        rebuildWidgets();
    }

    private static KeyboardShortcut[] newShortcuts() {
        KeyboardShortcut[] shortcuts = new KeyboardShortcut[Config.SHORTCUT_SLOTS];
        for (int i = 0; i < shortcuts.length; i++) {
            shortcuts[i] = new KeyboardShortcut();
        }
        return shortcuts;
    }

    private boolean hasSelection() {
        return category == BindingCategory.KEYS || selectedAction != null;
    }

    private boolean selectedShortcutIsBound() {
        return category == BindingCategory.KEYS && selectedShortcut >= 0
            && selectedShortcut < Config.keyboardShortcuts.length
            && Config.keyboardShortcuts[selectedShortcut] != null
            && Config.keyboardShortcuts[selectedShortcut].isBound();
    }

    private String shortcutModeLabel() {
        if (category != BindingCategory.KEYS || selectedShortcut < 0
            || selectedShortcut >= Config.keyboardShortcuts.length) {
            return "Mode";
        }
        KeyboardShortcut shortcut = Config.keyboardShortcuts[selectedShortcut];
        return shortcut == null || !shortcut.isBound() ? "Mode"
            : shortcut.getMode() == KeyboardShortcut.Mode.TAP ? "Tap" : "Hold";
    }

    private void beginActionCapture(ControllerAction action) {
        captureMode = CaptureMode.BIND_ACTION;
        captureAction = action;
        captureShortcutIndex = -1;
        resetCaptureState();
        status = "Press a controller button for " + actionLabel(action) + ".";
        rebuildWidgets();
    }

    private void beginShortcutCapture(int index) {
        if (index < 0 || index >= Config.SHORTCUT_SLOTS) return;
        captureMode = CaptureMode.SHORTCUT_CONTROLLER;
        captureAction = null;
        captureShortcutIndex = index;
        pendingShortcutButton = null;
        resetCaptureState();
        status = "Press a controller button for Shortcut " + (index + 1) + ".";
        rebuildWidgets();
    }

    private void resetCaptureState() {
        captureCandidate = null;
        captureHoldTicks = 0;
        captureArmed = false;
    }

    private void cancelCapture() {
        if (captureMode == CaptureMode.NONE) return;
        captureMode = CaptureMode.NONE;
        captureAction = null;
        captureShortcutIndex = -1;
        pendingShortcutButton = null;
        resetCaptureState();
        status = "Capture cancelled.";
        rebuildWidgets();
    }

    @Override
    public boolean isCapturingControllerInput() {
        return captureMode != CaptureMode.NONE;
    }

    @Override
    public void captureControllerState(ControllerState state) {
        if (captureMode == CaptureMode.NONE || captureMode == CaptureMode.SHORTCUT_KEY) return;

        Set<ControllerButton> active = activeButtons(state);
        if (!captureArmed) {
            if (active.isEmpty()) captureArmed = true;
            return;
        }

        if (active.isEmpty()) {
            if (captureCandidate != null) {
                completeControllerCapture(captureCandidate);
            }
            return;
        }

        if (captureCandidate == null || !active.contains(captureCandidate)) {
            captureCandidate = active.iterator()
                .next();
            captureHoldTicks = 0;
        } else {
            captureHoldTicks++;
            if (captureHoldTicks >= 20) {
                // A quick tap binds; holding for one second cancels. This makes it possible to
                // bind B (which normally closes a screen) without trapping the player in capture.
                cancelCapture();
            }
        }
    }

    private void completeControllerCapture(ControllerButton button) {
        if (captureMode == CaptureMode.BIND_ACTION) {
            Config.controllerMapping.bind(captureAction, button);
            Config.save();
            status = "Bound " + actionLabel(captureAction) + " to " + buttonLabel(button) + conflictSuffix(button);
            captureMode = CaptureMode.NONE;
            captureAction = null;
            resetCaptureState();
            rebuildWidgets();
        } else if (captureMode == CaptureMode.SHORTCUT_CONTROLLER) {
            pendingShortcutButton = button;
            captureCandidate = null;
            captureHoldTicks = 0;
            captureArmed = false;
            captureMode = CaptureMode.SHORTCUT_KEY;
            status = "Press a keyboard key for Shortcut " + (captureShortcutIndex + 1) + ".";
            rebuildWidgets();
        }
    }

    private String conflictSuffix(ControllerButton button) {
        List<String> conflicts = new ArrayList<>();
        for (ControllerAction action : ControllerAction.values()) {
            if (action != captureAction && Config.controllerMapping.getButton(action) == button) {
                conflicts.add(actionLabel(action));
            }
        }
        if (conflicts.isEmpty()) return "";
        return " (also: " + String.join(", ", conflicts) + ")";
    }

    private static Set<ControllerButton> activeButtons(ControllerState state) {
        Set<ControllerButton> active = EnumSet.noneOf(ControllerButton.class);
        for (ControllerButton button : ControllerButton.values()) {
            boolean on = switch (button) {
                case LEFT_TRIGGER -> state.leftTrigger() >= 0.6f;
                case RIGHT_TRIGGER -> state.rightTrigger() >= 0.6f;
                case LEFT_STICK_UP -> state.leftStickY() <= -0.65f;
                case LEFT_STICK_DOWN -> state.leftStickY() >= 0.65f;
                case LEFT_STICK_LEFT -> state.leftStickX() <= -0.65f;
                case LEFT_STICK_RIGHT -> state.leftStickX() >= 0.65f;
                case RIGHT_STICK_UP -> state.rightStickY() <= -0.65f;
                case RIGHT_STICK_DOWN -> state.rightStickY() >= 0.65f;
                case RIGHT_STICK_LEFT -> state.rightStickX() <= -0.65f;
                case RIGHT_STICK_RIGHT -> state.rightStickX() >= 0.65f;
                default -> state.isPressed(button);
            };
            if (on) active.add(button);
        }
        return active;
    }

    @Override
    public void updateScreen() {
        if (resetAllArmed) {
            resetAllTicks++;
            if (resetAllTicks > 100) {
                resetAllArmed = false;
                rebuildWidgets();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int panelX = (width - panelWidth()) / 2;
        int panelY = 4;
        int panelW = panelWidth();
        int panelH = height - 8;
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xB0101010);
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + 1, 0x50FFFFFF);
        Gui.drawRect(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0x50000000);

        drawCenteredString(fontRendererObj, "FuzziControls", width / 2, 9, 0xFFFFFF);
        if (!status.isEmpty() && captureMode == CaptureMode.NONE) {
            drawCenteredString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(status, panelW - 20),
                width / 2,
                19,
                0xFFD37A);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (page == Page.CONTROLLER && captureMode == CaptureMode.NONE) {
            drawControllerStatus();
        } else if (page == Page.INPUT && captureMode == CaptureMode.NONE) {
            drawCenteredString(
                fontRendererObj,
                "* Enabling analogue movement requires a game restart.",
                width / 2,
                height - 42,
                0xA0A0A0);
        }

        if (captureMode != CaptureMode.NONE) {
            drawCaptureOverlay();
        }
    }

    private void drawControllerStatus() {
        ControllerManager manager = ControllerManager.getInstance();
        String line1;
        String line2;
        if (!Config.controllerEnabled) {
            line1 = "Controller input is off — keyboard and mouse only.";
            line2 = "";
        } else if (manager.isActive()) {
            line1 = "Connected: " + manager.getActiveDeviceName();
            String transport = manager.getActiveTransportName();
            line2 = transport.isEmpty() ? manager.getActiveDriverName()
                : manager.getActiveDriverName() + " · " + transport;
        } else {
            line1 = "No controller detected.";
            line2 = "Hot-plug probing is active.";
        }
        drawCenteredString(fontRendererObj, line1, width / 2, height - 48, 0xB8D8B8);
        if (!line2.isEmpty()) {
            drawCenteredString(fontRendererObj, line2, width / 2, height - 38, 0x909090);
        }
    }

    private void drawCaptureOverlay() {
        Gui.drawRect(0, 0, width, height, 0xA0000000);
        int boxW = Math.min(340, width - 20);
        int boxH = 66;
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, 0xF0141414);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + 1, 0x80FFFFFF);
        Gui.drawRect(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0x80000000);

        String title;
        String hint;
        if (captureMode == CaptureMode.SHORTCUT_KEY) {
            title = "Press a keyboard key";
            hint = "Escape cancels";
        } else {
            title = "Press a controller button";
            hint = "Tap to bind · hold 1 second to cancel";
        }
        drawCenteredString(fontRendererObj, title, width / 2, boxY + 18, 0xFFFFFF);
        drawCenteredString(fontRendererObj, hint, width / 2, boxY + 42, 0xA8A8A8);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (captureMode == CaptureMode.SHORTCUT_KEY) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                cancelCapture();
            } else if (keyCode > 0) {
                KeyboardShortcut shortcut = Config.keyboardShortcuts[captureShortcutIndex];
                if (shortcut == null) {
                    shortcut = new KeyboardShortcut();
                    Config.keyboardShortcuts[captureShortcutIndex] = shortcut;
                }
                shortcut.setButton(pendingShortcutButton);
                shortcut.setKeyCode(keyCode);
                if (!shortcut.isBound()) {
                    shortcut.setMode(KeyboardShortcut.Mode.HOLD);
                }
                Config.save();
                status = "Shortcut " + (captureShortcutIndex + 1)
                    + ": "
                    + buttonLabel(pendingShortcutButton)
                    + " → "
                    + Keyboard.getKeyName(keyCode);
                captureMode = CaptureMode.NONE;
                pendingShortcutButton = null;
                captureShortcutIndex = -1;
                rebuildWidgets();
            }
            return;
        }

        if (captureMode != CaptureMode.NONE) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                cancelCapture();
            }
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (captureMode != CaptureMode.NONE) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (captureMode != CaptureMode.NONE) return;
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    private int contentWidth() {
        return panelWidth() - 20;
    }

    private int contentX() {
        return (width - panelWidth()) / 2 + 10;
    }

    private int panelWidth() {
        return Math.min(430, width - 16);
    }

    private boolean isXInputSelected() {
        String driver = trim(Config.controllerDriver).toLowerCase(Locale.ROOT);
        return "xinput".equals(driver) || "auto".equals(driver);
    }

    private static String nextDriver(String current) {
        String driver = trim(current).toLowerCase(Locale.ROOT);
        return switch (driver) {
            case "auto" -> "dualsense";
            case "dualsense" -> "xinput";
            default -> "auto";
        };
    }

    private static String driverLabel(String driver) {
        return switch (trim(driver).toLowerCase(Locale.ROOT)) {
            case "dualsense" -> "DualSense";
            case "xinput" -> "XInput";
            case "none" -> "None";
            default -> "Automatic";
        };
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String shortcutValue(KeyboardShortcut shortcut) {
        if (shortcut == null || !shortcut.isBound()) return "NONE";
        String keyName;
        try {
            keyName = Keyboard.getKeyName(shortcut.getKeyCode());
        } catch (RuntimeException e) {
            keyName = Integer.toString(shortcut.getKeyCode());
        }
        return buttonLabel(shortcut.getButton()) + " → "
            + keyName
            + (shortcut.getMode() == KeyboardShortcut.Mode.TAP ? " (Tap)" : "");
    }

    private static String buttonLabel(ControllerButton button) {
        if (button == null) return "NONE";
        return switch (button) {
            case A -> "A";
            case B -> "B";
            case X -> "X";
            case Y -> "Y";
            case LEFT_BUMPER -> "LB";
            case RIGHT_BUMPER -> "RB";
            case LEFT_TRIGGER -> "LT";
            case RIGHT_TRIGGER -> "RT";
            case LEFT_STICK_CLICK -> "L3";
            case RIGHT_STICK_CLICK -> "R3";
            case DPAD_UP -> "D-Pad Up";
            case DPAD_DOWN -> "D-Pad Down";
            case DPAD_LEFT -> "D-Pad Left";
            case DPAD_RIGHT -> "D-Pad Right";
            case START -> "Start";
            case BACK -> "Back";
            case LEFT_STICK_UP -> "LS Up";
            case LEFT_STICK_DOWN -> "LS Down";
            case LEFT_STICK_LEFT -> "LS Left";
            case LEFT_STICK_RIGHT -> "LS Right";
            case RIGHT_STICK_UP -> "RS Up";
            case RIGHT_STICK_DOWN -> "RS Down";
            case RIGHT_STICK_LEFT -> "RS Left";
            case RIGHT_STICK_RIGHT -> "RS Right";
        };
    }

    private static String actionLabel(ControllerAction action) {
        if (action == null) return "None";
        return switch (action) {
            case MOVE_FORWARD -> "Move Forward";
            case MOVE_BACKWARD -> "Move Backward";
            case STRAFE_LEFT -> "Strafe Left";
            case STRAFE_RIGHT -> "Strafe Right";
            case JUMP -> "Jump";
            case SNEAK -> "Sneak";
            case SPRINT -> "Sprint";
            case ATTACK -> "Attack / Mine";
            case USE_ITEM -> "Use Item";
            case PICK_BLOCK -> "Pick Block";
            case HOTBAR_NEXT -> "Hotbar Next";
            case HOTBAR_PREV -> "Hotbar Previous";
            case INVENTORY -> "Inventory";
            case DROP_ITEM -> "Drop Item";
            case CLOSE_GUI -> "Close GUI";
            case CHAT -> "Open Chat";
            case PAUSE -> "Pause";
            case COMMAND -> "Open Command";
            case GUI_LEFT_CLICK -> "GUI Left Click";
            case GUI_RIGHT_CLICK -> "GUI Right Click";
            case GUI_SHIFT_CLICK -> "GUI Shift Click";
            case GUI_NAV_UP -> "GUI Nav Up";
            case GUI_NAV_DOWN -> "GUI Nav Down";
            case GUI_NAV_LEFT -> "GUI Nav Left";
            case GUI_NAV_RIGHT -> "GUI Nav Right";
            case GUI_NAV_CONFIRM -> "GUI Confirm";
        };
    }
}
