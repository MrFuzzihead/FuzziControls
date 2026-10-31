package com.mrfuzzihead.fuzzicontrols.selftest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.inventory.GuiContainer;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.FuzziControls;
import com.mrfuzzihead.fuzzicontrols.interfaces.SlotAwareScreen;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiContainerAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionSliderAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiOptionsRowListRowAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiScreenAccessors;
import com.mrfuzzihead.fuzzicontrols.mixins.early.GuiSlotAccessors;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Verifies that every accessor/invoker mixin this mod relies on was actually applied.
 *
 * <p>
 * Unit tests cannot apply mixins, so a broken refmap, a renamed target or a failed injection would
 * otherwise only show up as a non-functional GUI at runtime — or as a {@code ClassCastException} deep
 * inside the tick handler. This check turns that into one explicit log line, which is also what the
 * CI client smoke test greps for.
 *
 * <p>
 * A mixin is applied when its interface (or superclass) has been added to the target, which is what
 * {@link Class#isAssignableFrom} tests for. The check runs on class <em>load</em>, so referencing the
 * targets here is enough — no instances are needed.
 */
@SideOnly(Side.CLIENT)
public final class MixinSelfTest {

    /** One mixin check: the mixin's name and whether it is applied to its target. */
    public record Check(String name, boolean applied) {}

    private MixinSelfTest() {}

    /**
     * Runs every mixin check.
     *
     * @return the checks in a stable order; the count is constant so the summary line is greppable
     */
    public static List<Check> run() {
        List<Check> checks = new ArrayList<>();

        checks.add(new Check("GuiScreenAccessors -> GuiScreen", isApplied(GuiScreenAccessors.class, GuiScreen.class)));
        checks.add(new Check("GuiSlotAccessors -> GuiSlot", isApplied(GuiSlotAccessors.class, GuiSlot.class)));
        checks.add(
            new Check(
                "GuiOptionSliderAccessors -> GuiOptionSlider",
                isApplied(GuiOptionSliderAccessors.class, GuiOptionSlider.class)));
        checks.add(
            new Check(
                "GuiOptionsRowListRowAccessors -> GuiOptionsRowList.Row",
                isApplied(GuiOptionsRowListRowAccessors.class, GuiOptionsRowList.Row.class)));
        checks.add(
            new Check(
                "GuiContainerAccessors -> GuiContainer",
                isApplied(GuiContainerAccessors.class, GuiContainer.class)));
        checks.add(
            new Check(
                "SlotAwareScreen (MixinGuiScreen_SlotTracker) -> GuiScreen",
                isApplied(SlotAwareScreen.class, GuiScreen.class)));

        // The analog-movement mixin is a class mixin, so it cannot be referenced directly at runtime
        // (the mixin subsystem refuses to define it for normal class loading). Check the method it
        // injects into the target instead, which also proves the injection ran.
        boolean analogApplied = declaresInjectedMethod(EntityPlayerSP.class, "fuzzicontrols$");
        checks.add(
            new Check(
                "MixinEntityPlayerSP -> EntityPlayerSP (expected when analogMovement=true)",
                analogApplied || !Config.analogMovement));

        return checks;
    }

    /** Returns true when every mixin is applied (or legitimately not required). */
    public static boolean allApplied() {
        for (Check check : run()) {
            if (!check.applied()) return false;
        }
        return true;
    }

    /**
     * Logs one error per missing mixin plus a single summary line.
     *
     * <p>
     * The summary has a fixed shape ({@code Mixin self-test: 7 checks, N failures}) so it can be
     * asserted by automation.
     */
    public static void logReport() {
        List<Check> checks = run();
        int failures = 0;
        for (Check check : checks) {
            if (check.applied()) continue;
            failures++;
            FuzziControls.LOG
                .error("[FuzziControls] Mixin NOT applied: {} — dependent GUI features will not work", check.name());
        }
        FuzziControls.LOG.info("[FuzziControls] Mixin self-test: {} checks, {} failures", checks.size(), failures);
    }

    private static boolean isApplied(Class<?> mixin, Class<?> target) {
        try {
            return mixin.isAssignableFrom(target);
        } catch (Throwable t) {
            // Linkage problems must not take the client down; report as "not applied".
            return false;
        }
    }

    /**
     * Returns true when the given class declares any method whose name starts with {@code prefix}.
     *
     * <p>
     * Class mixins cannot be referenced directly at runtime (the mixin subsystem refuses to define
     * them for normal class loading), so the check looks for the method the mixin merges into the
     * target instead — which also proves the injection actually ran.
     */
    private static boolean declaresInjectedMethod(Class<?> target, String prefix) {
        try {
            java.lang.reflect.Method[] methods = target.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName()
                    .startsWith(prefix)) return true;
            }
            FuzziControls.LOG.info(
                "[FuzziControls] No '{}' method on {} ({} declared methods) — mixin not applied?",
                prefix,
                target.getSimpleName(),
                methods.length);
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
