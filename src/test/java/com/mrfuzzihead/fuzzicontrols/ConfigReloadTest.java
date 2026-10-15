package com.mrfuzzihead.fuzzicontrols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
