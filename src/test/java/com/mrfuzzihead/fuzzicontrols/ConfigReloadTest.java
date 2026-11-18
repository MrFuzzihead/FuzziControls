package com.mrfuzzihead.fuzzicontrols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mrfuzzihead.fuzzicontrols.controller.ControllerAction;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerButton;
import com.mrfuzzihead.fuzzicontrols.controller.KeyboardShortcut;

/**
 * Tests that {@link Config#reload()} actually re-reads the configuration file.
 *
 * <p>
 * {@code /fuzzicontrols reload} only re-initialised the controller driver, so edits to the config
 * file (including {@code dpadNavigation} and {@code rightStickScrolling}) needed a full game
 * restart. These tests write a real config file and drive the real Forge {@code Configuration}
 * parser, so they cover the parsing and the mapping re-read rather than a stub.
 */
public class ConfigReloadTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private File configFile;

    /**
     * Forge's {@code Configuration} constructor resolves the config path against FML's injection
     * data ({@code FMLInjectionData.data()[6]}), which is only populated when the game launches.
     * Point it at a temporary directory so the real parser can run in the unit test JVM instead of
     * having to stub the config layer out.
     */
    @BeforeClass
    public static void provideFmlMinecraftHome() throws Exception {
        File home = Files.createTempDirectory("fuzzicontrols-test-home")
            .toFile();
        java.lang.reflect.Field field = cpw.mods.fml.relauncher.FMLInjectionData.class
            .getDeclaredField("minecraftHome");
        field.setAccessible(true);
        field.set(null, home);
    }

    /** Restores defaults so the global config statics do not leak into other tests. */
    @After
    public void restoreDefaults() throws Exception {
        Config.controllerEnabled = true;
        Config.controllerDriver = "auto";
        Config.rumbleEnabled = true;
        Config.rumbleIntensity = 0.6f;
        Config.adaptiveTriggersEnabled = false;
        Config.miningTriggerEffect = "vibration";
        Config.miningTriggerStrength = 5;
        Config.bowTriggerEffect = "bow";
        Config.bowTriggerStrength = 6;
        Config.lightbarEnabled = true;
        Config.lightbarRed = 0;
        Config.lightbarGreen = 128;
        Config.lightbarBlue = 255;
        Config.lightbarBrightness = 1;
        Config.lowBatteryToast = true;
        Config.lowBatteryThreshold = 15;
        Config.synchronizeConfiguration(folder.newFile("defaults.cfg"));
    }

    private void writeConfig(String... lines) throws Exception {
        if (configFile == null) {
            configFile = folder.newFile("fuzzicontrols.cfg");
        }
        Files.write(
            configFile.toPath(),
            String.join("\n", lines)
                .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void reloadPicksUpChangedControllerSettings() throws Exception {
        writeConfig("controller {", "    B:dpadNavigation=false", "    B:rightStickScrolling=true", "}");

        Config.synchronizeConfiguration(configFile);
        assertFalse(Config.dpadNavigation);
        assertTrue(Config.rightStickScrolling);

        // The user edits the file while the game is running.
        Files.write(
            configFile.toPath(),
            String
                .join(
                    "\n",
                    "controller {",
                    "    B:dpadNavigation=true",
                    "    B:rightStickScrolling=false",
                    "    I:scrollSensitivity=42",
                    "}")
                .getBytes(StandardCharsets.UTF_8));

        assertTrue("A loaded config must be re-readable", Config.reload());
        assertTrue(Config.dpadNavigation);
        assertFalse(Config.rightStickScrolling);
        assertEquals(42f, Config.scrollSensitivity, 0f);
    }

    @Test
    public void reloadPicksUpChangedBindings() throws Exception {
        writeConfig("bindings {", "    S:JUMP=A", "}");

        Config.synchronizeConfiguration(configFile);
        assertEquals(ControllerButton.A, Config.controllerMapping.getButton(ControllerAction.JUMP));

        Files.write(
            configFile.toPath(),
            String.join("\n", "bindings {", "    S:JUMP=X", "}")
                .getBytes(StandardCharsets.UTF_8));

        Config.reload();
        assertEquals(ControllerButton.X, Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void reloadIsIdempotent() throws Exception {
        writeConfig("controller {", "    F:lookSensitivity=3.5", "}", "bindings {", "    S:JUMP=X", "}");

        Config.synchronizeConfiguration(configFile);
        float first = Config.lookSensitivity;
        ControllerButton firstBinding = Config.controllerMapping.getButton(ControllerAction.JUMP);

        Config.reload();
        Config.reload();

        assertEquals(first, Config.lookSensitivity, 0f);
        assertEquals(firstBinding, Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void savedBindingsSurviveAReload() throws Exception {
        // Config.save() writes the in-memory mapping; it had no coverage at all.
        writeConfig("bindings {", "    S:JUMP=A", "}");
        Config.synchronizeConfiguration(configFile);

        Config.controllerMapping.bind(ControllerAction.JUMP, ControllerButton.Y);
        Config.save();

        // Simulate a restart: reading the file again must produce the saved binding.
        Config.synchronizeConfiguration(configFile);
        assertEquals(ControllerButton.Y, Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void settingsSaveAndReloadRoundTrip() throws Exception {
        writeConfig("controller {", "    B:enabled=true", "}", "bindings {", "    S:JUMP=A", "}");
        Config.synchronizeConfiguration(configFile);

        Config.lookSensitivity = 3.25f;
        Config.stickDeadZone = 0.22f;
        Config.triggerThreshold = 0.35f;
        Config.analogMovement = false;
        Config.controllerEnabled = false;
        Config.rumbleEnabled = false;
        Config.rumbleIntensity = 0.35f;
        Config.adaptiveTriggersEnabled = true;
        Config.miningTriggerEffect = "machine";
        Config.miningTriggerStrength = 7;
        Config.bowTriggerEffect = "weapon";
        Config.bowTriggerStrength = 8;
        Config.lightbarEnabled = false;
        Config.lightbarRed = 12;
        Config.lightbarGreen = 34;
        Config.lightbarBlue = 56;
        Config.lightbarBrightness = 2;
        Config.lowBatteryToast = false;
        Config.lowBatteryThreshold = 20;
        Config.save();

        Config.synchronizeConfiguration(configFile);

        assertEquals(3.25f, Config.lookSensitivity, 0f);
        assertEquals(0.22f, Config.stickDeadZone, 0f);
        assertEquals(0.35f, Config.triggerThreshold, 0f);
        assertFalse(Config.analogMovement);
        assertFalse(Config.controllerEnabled);
        assertFalse(Config.rumbleEnabled);
        assertEquals(0.35f, Config.rumbleIntensity, 0f);
        assertTrue(Config.adaptiveTriggersEnabled);
        assertEquals("machine", Config.miningTriggerEffect);
        assertEquals(7, Config.miningTriggerStrength);
        assertEquals("weapon", Config.bowTriggerEffect);
        assertEquals(8, Config.bowTriggerStrength);
        assertFalse(Config.lightbarEnabled);
        assertEquals(12, Config.lightbarRed);
        assertEquals(34, Config.lightbarGreen);
        assertEquals(56, Config.lightbarBlue);
        assertEquals(2, Config.lightbarBrightness);
        assertFalse(Config.lowBatteryToast);
        assertEquals(20, Config.lowBatteryThreshold);
    }

    @Test
    public void controllerMasterSwitchRoundTrips() throws Exception {
        writeConfig("controller {", "    B:enabled=false", "    S:driver=dualsense", "}");

        Config.synchronizeConfiguration(configFile);
        assertFalse(Config.controllerEnabled);
        assertEquals("dualsense", Config.controllerDriver);

        Config.controllerEnabled = true;
        Config.save();
        Config.synchronizeConfiguration(configFile);

        assertTrue(Config.controllerEnabled);
        assertEquals("The selected driver must survive an off/on cycle", "dualsense", Config.controllerDriver);
    }

    @Test
    public void legacyDriverNoneReadsAsDisabled() throws Exception {
        writeConfig("controller {", "    S:driver=none", "}");

        Config.synchronizeConfiguration(configFile);

        assertFalse("driver=none must keep working for existing configs", Config.controllerEnabled);
    }

    @Test
    public void keyboardShortcutsSurviveSaveAndReload() throws Exception {
        writeConfig("shortcuts {", "    S:slot1=A:33:HOLD", "    S:slot2=B:42:TAP", "}");
        Config.synchronizeConfiguration(configFile);

        assertTrue(Config.keyboardShortcuts[0].isBound());
        assertEquals(ControllerButton.A, Config.keyboardShortcuts[0].getButton());
        assertEquals(KeyboardShortcut.Mode.TAP, Config.keyboardShortcuts[1].getMode());

        Config.keyboardShortcuts[2] = new KeyboardShortcut(ControllerButton.X, 19, KeyboardShortcut.Mode.HOLD);
        Config.save();
        Config.synchronizeConfiguration(configFile);

        assertTrue(Config.keyboardShortcuts[2].isBound());
        assertEquals(ControllerButton.X, Config.keyboardShortcuts[2].getButton());
        assertEquals(19, Config.keyboardShortcuts[2].getKeyCode());
    }

    @Test
    public void malformedKeyboardShortcutFallsBackToUnbound() throws Exception {
        writeConfig("shortcuts {", "    S:slot1=garbage", "}");

        Config.synchronizeConfiguration(configFile);

        assertFalse(Config.keyboardShortcuts[0].isBound());
    }

    @Test
    public void isLoadedFromDetectsTheLoadedFile() throws Exception {
        writeConfig("bindings {", "    S:JUMP=A", "}");
        Config.synchronizeConfiguration(configFile);

        assertTrue(Config.isLoadedFrom(configFile));
        assertFalse(
            "A different config file must not count as loaded",
            Config.isLoadedFrom(new File(configFile.getParentFile(), "other.cfg")));
    }

    @Test
    public void edgeButtonsCanBeLoadedFromTheConfig() throws Exception {
        writeConfig("bindings {", "    S:JUMP=LEFT_PADDLE", "}");

        Config.synchronizeConfiguration(configFile);

        assertEquals(ControllerButton.LEFT_PADDLE, Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void bindingNamesAreTrimmedBeforeParsing() throws Exception {
        writeConfig("bindings {", "    S:JUMP=  X  ", "}");

        Config.synchronizeConfiguration(configFile);

        assertEquals(
            "Whitespace around a button name must not fall back to the default",
            ControllerButton.X,
            Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void paddedNoneStillUnbindsTheAction() throws Exception {
        writeConfig("bindings {", "    S:JUMP=  NONE  ", "}");

        Config.synchronizeConfiguration(configFile);

        assertNull(
            "Whitespace around NONE must still mean an explicit unbind",
            Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void unboundActionsSurviveASaveAndReload() throws Exception {
        writeConfig("bindings {", "    S:JUMP=A", "}");
        Config.synchronizeConfiguration(configFile);

        Config.controllerMapping.bind(ControllerAction.JUMP, null);
        Config.save();

        Config.synchronizeConfiguration(configFile);
        assertNull(
            "An explicitly unbound action must stay unbound",
            Config.controllerMapping.getButton(ControllerAction.JUMP));
    }

    @Test
    public void missingKeysKeepTheirCurrentValue() throws Exception {
        // Pins the documented reload semantics: keys present in the file win, keys absent from it
        // keep their current value (Forge's Configuration getters use the in-memory value as the
        // default for a missing key, then write it back).
        writeConfig("controller {", "    B:dpadNavigation=true", "}");

        Config.synchronizeConfiguration(configFile);
        assertTrue(Config.dpadNavigation);

        writeConfig("controller {", "    B:sneakToggle=false", "}");
        Config.reload();

        assertTrue("An absent key keeps its current value", Config.dpadNavigation);
        assertFalse("A key present in the file wins", Config.sneakToggle);
    }
}
