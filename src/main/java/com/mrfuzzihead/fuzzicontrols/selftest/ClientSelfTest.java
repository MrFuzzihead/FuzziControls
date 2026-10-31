package com.mrfuzzihead.fuzzicontrols.selftest;

import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.FuzziControls;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiContainerAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionSliderAccessors;
import com.mrfuzzihead.fuzzicontrols.util.GuiElementActivator;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusRenderer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Opt-in, human-free integration harness for the GUI navigation stack.
 *
 * <p>
 * Unit tests cover the pure logic, but the parts that matter most can only be exercised with live
 * mixins and a real client: mixin application, {@code GuiScreen} button activation, the
 * {@code GuiSlot} list path, the {@code GuiContainer} slot path, slider stepping through real
 * {@link GameSettings.Options}, and the focus-border renderer. This harness drives all of them from
 * the main menu (no world needed) by focusing real buttons and activating them exactly as the D-pad
 * confirm does.
 *
 * <p>
 * It logs one {@code PASS}/{@code FAIL} line per check plus a fixed-shape summary line, then exits the
 * JVM with the failure count as the exit code. Enabled by the {@code FUZZICONTROLS_SELFTEST}
 * environment variable or the {@code -Dfuzzicontrols.selftest=true} system property; never registered
 * otherwise, so it costs nothing in normal play. CI runs it under {@code xvfb} (see
 * {@code .github/workflows/build-and-test.yml}).
 */
@SideOnly(Side.CLIENT)
public final class ClientSelfTest {

    private static final String ENV_FLAG = "FUZZICONTROLS_SELFTEST";

    private static final String PROPERTY_FLAG = "fuzzicontrols.selftest";

    /** Ticks to wait after the main menu appears so resource loading has settled. */
    private static final int WARMUP_TICKS = 20;

    /** Rendered frames to survive with the focus border drawn before the renderer check passes. */
    private static final int RENDERER_FRAMES = 2;

    /**
     * Report file, written in the client's working directory ({@code run/client}). CI asserts on this
     * file rather than scraping Gradle's console output, which console formatting can interleave.
     */
    public static final String REPORT_FILE = "selftest-report.txt";

    /** What the harness is waiting for. */
    private enum Phase {
        WAIT_FOR_MENU,
        WAIT_FOR_RENDERER,
        DONE
    }

    private GuiFocusNavigator navigator;

    private Phase phase = Phase.WAIT_FOR_MENU;

    private int menuTicks;

    private int renderedFrames;

    private boolean exitPending;

    private int checks;

    private int failures;

    /** One line per check, written to {@link #REPORT_FILE} so CI can assert on a file instead of console output. */
    private final List<String> report = new ArrayList<>();

    private boolean originalDpadNavigation;

    /** Returns true when the harness was requested for this JVM. */
    public static boolean isEnabled() {
        return System.getenv(ENV_FLAG) != null || Boolean.getBoolean(PROPERTY_FLAG);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (exitPending) {
            // Exit on the tick after the summary so log4j gets a chance to flush.
            FMLCommonHandler.instance()
                .exitJava(failures == 0 ? 0 : 1, true);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        switch (phase) {
            case WAIT_FOR_MENU -> {
                if (!(mc.currentScreen instanceof GuiMainMenu)) return;
                if (++menuTicks < WARMUP_TICKS) return;
                runChecks(mc);
                phase = Phase.WAIT_FOR_RENDERER;
            }
            case WAIT_FOR_RENDERER -> {
                if (renderedFrames < RENDERER_FRAMES) return;
                record("renderer: focus border drawn for " + renderedFrames + " frames without error", true);
                finish();
            }
            case DONE -> {}
        }
    }

    /**
     * Keeps the focus border enabled across the render phase; the tick handler normally clears it
     * because no D-pad button is physically pressed.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (phase != Phase.WAIT_FOR_RENDERER) return;
        if (Minecraft.getMinecraft().currentScreen == null) return;

        GuiFocusRenderer.setNavActive(true);
        renderedFrames++;
    }

    // -------------------------------------------------------------------------
    // Checks
    // -------------------------------------------------------------------------

    private void runChecks(Minecraft mc) {
        originalDpadNavigation = Config.dpadNavigation;

        for (MixinSelfTest.Check check : MixinSelfTest.run()) {
            record(check.name(), check.applied());
        }

        navigator = new GuiFocusNavigator();
        GuiFocusRenderer.setNavigator(navigator);

        if (!checkScreenActivation(mc)) return;
        checkVideoSettings(mc);
        checkContainerScreen();

        // The renderer check runs over the next rendered frames; the border is drawn for the live
        // video-settings screen focused above.
        Config.dpadNavigation = true;
    }

    /**
     * Focuses and activates real buttons to walk main menu → Options → Video Settings, exercising the
     * same dispatch path as the D-pad confirm (including screen changes caused by a button).
     */
    private boolean checkScreenActivation(Minecraft mc) {
        navigator.update(mc.currentScreen);
        record("main menu: focus list built", navigator.getFocusableCount() > 0);

        int optionsIndex = findButtonIndex(I18n.format("menu.options"));
        record("main menu: 'Options...' button reachable by D-pad focus", optionsIndex >= 0);
        if (optionsIndex < 0) return false;

        navigator.focusIndex(optionsIndex);
        GuiElementActivator.activate(navigator, 0, mc, false);
        boolean openedOptions = mc.currentScreen instanceof GuiOptions;
        record("main menu: activating the focused button opens the Options screen", openedOptions);
        if (!openedOptions) return false;

        navigator.update(mc.currentScreen);
        int videoIndex = findButtonIndex(I18n.format("options.video"));
        record("options screen: 'Video Settings...' button reachable by D-pad focus", videoIndex >= 0);
        if (videoIndex < 0) return false;

        navigator.focusIndex(videoIndex);
        GuiElementActivator.activate(navigator, 0, mc, false);
        boolean openedVideo = mc.currentScreen instanceof GuiVideoSettings;
        record("options screen: activating the focused button opens Video Settings", openedVideo);
        return openedVideo;
    }

    /** GuiSlot list path plus slider stepping, on the live Video Settings screen. */
    private void checkVideoSettings(Minecraft mc) {
        navigator.update(mc.currentScreen);

        record("video settings: focus list built from GuiOptionsRowList rows", navigator.getFocusableCount() > 0);
        record("video settings: focused entry is inside the list viewport", navigator.isFocusedItemVisible());

        Point first = navigator.getFocusedItemCenter();
        navigator.focusDown();
        Point second = navigator.getFocusedItemCenter();
        record("video settings: D-pad moves focus", second != null && !second.equals(first));
        record("video settings: focus stays on screen after moving", navigator.isFocusedItemVisible());

        // Index-based search so both columns of the options list are visited.
        GuiOptionSliderAccessors gamma = null;
        for (int index = 0; index < navigator.getFocusableCount() && gamma == null; index++) {
            navigator.focusIndex(index);
            GuiButton button = navigator.getFocusedButton();
            if (button instanceof GuiOptionSlider) {
                GuiOptionSliderAccessors accessors = (GuiOptionSliderAccessors) button;
                if (accessors.getOptions() == GameSettings.Options.GAMMA) gamma = accessors;
            }
        }
        record("video settings: gamma slider reachable by D-pad focus", gamma != null);
        if (gamma == null) return;

        double original = mc.gameSettings.getOptionFloatValue(GameSettings.Options.GAMMA);
        navigator.sliderRight(0.05f);
        double raised = mc.gameSettings.getOptionFloatValue(GameSettings.Options.GAMMA);
        navigator.sliderLeft(0.05f);
        double restored = mc.gameSettings.getOptionFloatValue(GameSettings.Options.GAMMA);
        mc.gameSettings.setOptionFloatValue(GameSettings.Options.GAMMA, (float) original);

        record("video settings: D-pad right raises the focused slider", raised > original);
        record("video settings: D-pad left lowers it back", Math.abs(restored - original) < 1e-3);

        // Close the screen again so the renderer check draws a normal focused screen below.
        navigator.update(mc.currentScreen);
        navigator.focusIndex(0);
        record("video settings: focus returns to the first element", navigator.getFocusedItemCenter() != null);
    }

    // -------------------------------------------------------------------------
    // GuiContainer slot path (layout accessors, slot filtering, click target)
    // -------------------------------------------------------------------------

    private void checkContainerScreen() {
        InventoryBasic inventory = new InventoryBasic("fuzzicontrols-selftest", false, 8);
        TestContainer container = new TestContainer();
        container.addTestSlot(new Slot(inventory, 0, 8, 8));
        container.addTestSlot(new Slot(inventory, 1, 26, 8));
        // Containers often park unused slots far outside the drawn panel.
        container.addTestSlot(new Slot(inventory, 2, 999, 999));

        TestContainerScreen screen = new TestContainerScreen(container);
        GuiContainerAccessors accessors = (GuiContainerAccessors) screen;
        accessors.setGuiLeft(100);
        accessors.setGuiTop(50);

        GuiFocusNavigator containerNavigator = new GuiFocusNavigator();
        containerNavigator.update(screen);

        // The off-panel slot must be filtered out, leaving exactly the two in-panel slots.
        record("container: only in-panel slots are focusable", containerNavigator.getFocusableCount() == 2);

        // First in-panel slot at (8, 8) with guiLeft/guiTop (100, 50): the click target is the slot
        // centre, 8 px inside the drawn background.
        record(
            "container: focused slot uses the slot centre",
            new Point(116, 66).equals(containerNavigator.getFocusedItemCenter()));

        containerNavigator.focusRight();
        record(
            "container: second in-panel slot focused",
            new Point(134, 66).equals(containerNavigator.getFocusedItemCenter()));

        // Wrapping again must not reach the off-panel slot at (999, 999) -> centre (1107, 1057).
        containerNavigator.focusRight();
        record(
            "container: focus wraps without reaching the off-panel slot",
            !new Point(1107, 1057).equals(containerNavigator.getFocusedItemCenter()));

        // Restore the live-screen navigator for the renderer check.
        GuiFocusRenderer.setNavigator(navigator);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Focuses each element in turn until one's label matches, returning its index or -1. */
    private int findButtonIndex(String label) {
        for (int index = 0; index < navigator.getFocusableCount(); index++) {
            navigator.focusIndex(index);
            GuiButton button = navigator.getFocusedButton();
            if (button != null && label.equals(button.displayString)) return index;
        }
        return -1;
    }

    private void finish() {
        Config.dpadNavigation = originalDpadNavigation;
        GuiFocusRenderer.setNavActive(false);
        FuzziControls.LOG.info("[FuzziControls] Client selftest: {} checks, {} failures", checks, failures);
        writeReport();
        phase = Phase.DONE;
        exitPending = true;
    }

    /**
     * Writes {@link #REPORT_FILE}: one {@code PASS}/{@code FAIL} line per check plus a
     * {@code SUMMARY <checks> <failures>} line. Never throws — a broken report must not hide results.
     */
    private void writeReport() {
        StringBuilder text = new StringBuilder();
        for (String line : report) {
            text.append(line)
                .append('\n');
        }
        text.append("SUMMARY ")
            .append(checks)
            .append(' ')
            .append(failures)
            .append('\n');

        try {
            File file = new File(System.getProperty("user.dir", "."), REPORT_FILE);
            Files.write(
                file.toPath(),
                text.toString()
                    .getBytes(StandardCharsets.UTF_8));
            FuzziControls.LOG.info("[FuzziControls] Client selftest report written to {}", file.getAbsolutePath());
        } catch (Exception e) {
            FuzziControls.LOG.warn("[FuzziControls] Could not write the selftest report: {}", e.toString());
        }
    }

    private void record(String name, boolean passed) {
        checks++;
        report.add((passed ? "PASS " : "FAIL ") + name);
        if (passed) {
            FuzziControls.LOG.info("[FuzziControls] selftest PASS {}", name);
        } else {
            failures++;
            FuzziControls.LOG.error("[FuzziControls] selftest FAIL {}", name);
        }
    }

    /** Minimal container with no behaviour; only its slot list matters here. */
    private static final class TestContainer extends Container {

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        /** {@code Container.addSlotToContainer} is protected, so expose it for the harness. */
        void addTestSlot(Slot slot) {
            addSlotToContainer(slot);
        }
    }

    /**
     * Minimal container screen; the layout is positioned directly instead of via {@code initGui()}
     * (which requires a player). Not final: the accessor mixin interface only exists at runtime, so the
     * cast in {@link #checkContainerScreen()} needs a non-final type.
     */
    private static class TestContainerScreen extends GuiContainer {

        TestContainerScreen(Container container) {
            super(container);
        }

        @Override
        protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
            // Nothing to draw for the harness.
        }
    }
}
