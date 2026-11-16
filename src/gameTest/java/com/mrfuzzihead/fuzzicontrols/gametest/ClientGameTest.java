package com.mrfuzzihead.fuzzicontrols.gametest;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerAction;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerButton;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerMapping;
import com.mrfuzzihead.fuzzicontrols.selftest.MixinSelfTest;
import com.mrfuzzihead.fuzzicontrols.toast.Toast;
import com.mrfuzzihead.fuzzicontrols.toast.ToastManager;
import com.mrfuzzihead.fuzzicontrols.util.GuiFocusNavigator;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side game test: waits for the main menu, then asserts the live integration points that
 * unit tests cannot cover:
 *
 * <ul>
 * <li>every accessor/invoker mixin was actually applied ({@link MixinSelfTest});</li>
 * <li>the config and default binding table loaded;</li>
 * <li>{@link ControllerManager#tick()} survives a no-controller probe (the stutter fix will make
 * this fast; the elapsed time is logged for comparison);</li>
 * <li>{@link GuiFocusNavigator} can enumerate and move focus over the real main-menu buttons.</li>
 * </ul>
 *
 * <p>
 * Exits the JVM with the assertion result, so {@code ./gradlew gameTestClient} terminates by
 * itself. Only loaded by the game-test mod's {@code @SidedProxy}, never by the production mod.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ClientGameTest implements IGameTest {

    /** Ticks to wait after the main menu appears so resource loading has settled. */
    private static final int WARMUP_TICKS = 20;

    /** Give up if no main menu appears after this many client ticks (~30 s at 20 TPS). */
    private static final int MENU_TIMEOUT_TICKS = 600;

    private int menuTicks;

    private int totalTicks;

    private boolean ran;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // TickEvent lives on FML's bus in 1.7.10, not on the Forge bus.
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Override
    public void onServerStarted(FMLServerStartedEvent event) {
        // The integrated server also fires this on a client; the client checks run from the tick.
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ran) return;

        if (++totalTicks > MENU_TIMEOUT_TICKS) {
            ran = true;
            GameTestReport report = new GameTestReport("client");
            report.check("main menu appeared within " + MENU_TIMEOUT_TICKS + " ticks", false);
            report.finish();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !(mc.currentScreen instanceof GuiMainMenu)) {
            menuTicks = 0;
            return;
        }
        if (++menuTicks < WARMUP_TICKS) return;

        ran = true;
        runChecks(mc);
    }

    private void runChecks(Minecraft mc) {
        GameTestReport report = new GameTestReport("client");

        // ---- Mixins: the accessors/invokers every GUI feature depends on ----
        List<MixinSelfTest.Check> mixins = MixinSelfTest.run();
        for (MixinSelfTest.Check check : mixins) {
            report.check("mixin: " + check.name(), check.applied());
        }

        // ---- Config / default bindings ----
        ControllerMapping mapping = Config.controllerMapping;
        report.check("config mapping loaded", mapping != null);
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

        // ---- Controller manager: a no-controller tick must not throw or hang ----
        ControllerManager manager = ControllerManager.getInstance();
        report.check("controller manager instance exists", manager != null);
        report.check("active driver name is non-null", manager.getActiveDriverName() != null);
        boolean tickOk = true;
        String tickError = null;
        long start = System.nanoTime();
        try {
            manager.tick();
        } catch (Throwable t) {
            tickOk = false;
            tickError = t.toString();
            t.printStackTrace();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        report.check(
            "controller manager tick completed without throwing" + (tickError == null ? "" : ": " + tickError),
            tickOk);
        // Logged, not asserted: before the HID-probe fix this is ~500 ms on machines with many
        // HID devices, afterwards it should be single-digit milliseconds. Having the number in the
        // CI log makes the improvement (and any regression) visible without a flaky timing gate.
        System.out.println(
            "[FC-GAMETEST] info controller manager tick took " + elapsedMs + " ms (active=" + manager.isActive() + ")");
        System.out.println(
            "[FC-GAMETEST] info active controller: driver='" + manager.getActiveDriverName()
                + "' device='"
                + manager.getActiveDeviceName()
                + "' transport='"
                + manager.getActiveTransportName()
                + "'");

        // ---- Connection toast: assets exist, the queue accepts a message, and it renders ----
        report.check(
            "toast background texture is present",
            resourceExists(mc, new ResourceLocation("fuzzicontrols:textures/gui/toast.png")));
        report.check(
            "connected controller icon is present",
            resourceExists(mc, new ResourceLocation("fuzzicontrols:textures/gui/controller_connected.png")));
        report.check(
            "disconnected controller icon is present",
            resourceExists(mc, new ResourceLocation("fuzzicontrols:textures/gui/controller_disconnected.png")));

        ToastManager.getInstance()
            .clear();
        ToastManager.getInstance()
            .add(new Toast(Toast.Type.CONNECTED, "GameTest Controller", "USB"));
        report.check(
            "controller toast can be queued",
            ToastManager.getInstance()
                .getQueuedToastCount() > 0);
        try {
            ToastManager.getInstance()
                .render(mc, new ScaledResolution(mc, mc.displayWidth, mc.displayHeight));
            report.check("controller toast renders without throwing", true);
        } catch (Throwable t) {
            report.check("controller toast renders without throwing: " + t, false);
        }

        // ---- Live GUI navigation stack on the real main menu ----
        GuiFocusNavigator navigator = new GuiFocusNavigator();
        navigator.update(mc.currentScreen);
        int focusable = navigator.getFocusableCount();
        report.check("main menu has focusable elements", focusable > 0);
        if (focusable > 0) {
            navigator.focusDown();
            report.check("focus moved to an element", navigator.getFocusedItem() != null);
        }

        report.finish();
    }

    private static boolean resourceExists(Minecraft mc, ResourceLocation location) {
        try {
            mc.getResourceManager()
                .getResource(location);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
