package com.mrfuzzihead.fuzzicontrols.selftest;

import java.awt.Point;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiContainerAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionSliderAccessors;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator;

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
 * mixins and a real client: mixin application, the {@code GuiSlot} list path, the {@code GuiContainer}
 * slot path, and slider stepping through real {@link GameSettings.Options}. This harness drives those
 * with real screens at the main menu (no world needed), logs one {@code PASS}/{@code FAIL} line per
 * check plus a fixed-shape summary, and then exits the JVM with the failure count as the exit code.
 *
 * <p>
 * Enabled by the {@code FUZZICONTROLS_SELFTEST} environment variable or the
 * {@code -Dfuzzicontrols.selftest=true} system property; it is never registered otherwise, so it costs
 * nothing in normal play. CI runs it under {@code xvfb} (see {@code .github/workflows/build-and-test.yml}).
 */
@SideOnly(Side.CLIENT)
public final class ClientSelfTest {

    private static final String ENV_FLAG = "FUZZICONTROLS_SELFTEST";

    private static final String PROPERTY_FLAG = "fuzzicontrols.selftest";

    /** Ticks to wait after the main menu appears so resource loading has settled. */
    private static final int WARMUP_TICKS = 20;

    private GuiFocusNavigator navigator;

    private int menuTicks;

    private boolean finished;

    private boolean exitPending;

    private int checks;

    private int failures;

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
        if (finished) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiMainMenu)) return;
        if (++menuTicks < WARMUP_TICKS) return;

        finished = true;
        runChecks(mc);
        FuzziControls.LOG.info("[FuzziControls] Client selftest: {} checks, {} failures", checks, failures);
        exitPending = true;
    }

    private void runChecks(Minecraft mc) {
        for (MixinSelfTest.Check check : MixinSelfTest.run()) {
            record(check.name(), check.applied());
        }
        checkVideoSettings(mc);
        checkContainerScreen();
    }

    // -------------------------------------------------------------------------
    // GuiSlot list path (real GuiOptionsRowList: rows, sliders and enum buttons)
    // -------------------------------------------------------------------------

    private void checkVideoSettings(Minecraft mc) {
        GuiVideoSettings screen = new GuiVideoSettings(null, mc.gameSettings);
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        screen.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());

        navigator = new GuiFocusNavigator();
        navigator.update(screen);

        Point first = navigator.getFocusedItemCenter();
        record("video settings: focus list built from GuiOptionsRowList rows", first != null);
        record("video settings: focused entry is inside the list viewport", navigator.isFocusedItemVisible());

        navigator.focusDown();
        Point second = navigator.getFocusedItemCenter();
        record("video settings: D-pad moves focus", second != null && !second.equals(first));
        record("video settings: focus stays on screen after moving", navigator.isFocusedItemVisible());

        GuiOptionSliderAccessors gamma = findSlider(GameSettings.Options.GAMMA);
        record("video settings: gamma slider reachable by D-pad", gamma != null);
        if (gamma == null) return;

        double original = mc.gameSettings.getOptionFloatValue(GameSettings.Options.GAMMA);
        navigator.sliderRight(0.05f);
        double raised = mc.gameSettings.getOptionFloatValue(GameSettings.Options.GAMMA);
        navigator.sliderLeft(0.05f);
        double restored = mc.gameSettings.getOptionFloatValue(GameSettings.Options.GAMMA);
        mc.gameSettings.setOptionFloatValue(GameSettings.Options.GAMMA, (float) original);

        record("video settings: D-pad right raises the focused slider", raised > original);
        record("video settings: D-pad left lowers it back", Math.abs(restored - original) < 1e-3);
    }

    /** Walks the focus list until the slider for {@code option} is focused. */
    private GuiOptionSliderAccessors findSlider(GameSettings.Options option) {
        // Index-based so both columns of the options list are visited deterministically; spatial
        // navigation only walks one column at a time.
        for (int index = 0; index < navigator.getFocusableCount(); index++) {
            navigator.focusIndex(index);
            GuiButton button = navigator.getFocusedButton();
            if (button instanceof GuiOptionSlider) {
                GuiOptionSliderAccessors accessors = (GuiOptionSliderAccessors) button;
                if (accessors.getOptions() == option) return accessors;
            }
        }
        return null;
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

        navigator = new GuiFocusNavigator();
        navigator.update(screen);

        // The off-panel slot must be filtered out, leaving exactly the two in-panel slots.
        record("container: only in-panel slots are focusable", navigator.getFocusableCount() == 2);

        // First in-panel slot at (8, 8) with guiLeft/guiTop (100, 50): the click target is the slot
        // centre, 8 px inside the drawn background.
        record(
            "container: focused slot uses the slot centre",
            new Point(116, 66).equals(navigator.getFocusedItemCenter()));

        // The two slots sit side by side, so the next one is reached with a horizontal move.
        navigator.focusRight();
        record("container: second in-panel slot focused", new Point(134, 66).equals(navigator.getFocusedItemCenter()));

        // Wrapping again must not reach the off-panel slot at (999, 999) -> centre (1107, 1057).
        navigator.focusRight();
        record(
            "container: focus wraps without reaching the off-panel slot",
            !new Point(1107, 1057).equals(navigator.getFocusedItemCenter()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void record(String name, boolean passed) {
        checks++;
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
     * (which requires a player). Not final: the accessor mixin interface is only present at runtime,
     * so the cast in {@link #checkContainerScreen()} needs a non-final type.
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
