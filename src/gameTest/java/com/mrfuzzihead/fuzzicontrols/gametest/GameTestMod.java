package com.mrfuzzihead.fuzzicontrols.gametest;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Test-only mod: a separate, never-shipped {@code @Mod} that asserts FuzziControls works against
 * the real game.
 *
 * <p>
 * It lives in the {@code gameTest} source set and is only put on the classpath by
 * {@code ./gradlew gameTestServer} / {@code gameTestClient} (or {@code -PgameTest}), so it never
 * reaches the published jar. The run task is expected to terminate with the assertion result:
 * the mod calls {@link #exit(int)} once the checks have printed their markers.
 * </p>
 *
 * <p>
 * The side split mirrors the production mod: {@link CommonGameTest} only runs on a dedicated
 * server, {@link ClientGameTest} only on a client. This keeps client-only classes (Minecraft GUI,
 * the controller manager) off the dedicated-server classpath.
 * </p>
 */
@Mod(
    modid = GameTestMod.MOD_ID,
    name = "FuzziControls Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:fuzzicontrols")
public class GameTestMod {

    public static final String MOD_ID = "fuzzicontrolsgametest";

    /** Printed only after every assertion passed; CI greps for this. */
    public static final String OK_MARKER = "FUZZICONTROLS_GAMETEST_OK";

    /** Printed when at least one assertion failed; CI greps for this too. */
    public static final String FAIL_MARKER = "FUZZICONTROLS_GAMETEST_FAILED";

    @SidedProxy(
        clientSide = "com.mrfuzzihead.fuzzicontrols.gametest.ClientGameTest",
        serverSide = "com.mrfuzzihead.fuzzicontrols.gametest.CommonGameTest")
    public static IGameTest side;

    private boolean serverStarted;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FuzziControls.LOG.info("[FuzziControls] Game test mod loaded (side: {}).", event.getSide());
        side.preInit(event);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (serverStarted) return;
        serverStarted = true;
        side.onServerStarted(event);
    }

    /**
     * Ends the run with the assertion result. The report file is written before this is called.
     *
     * @param status process exit code (0 = all checks passed)
     */
    static void exit(int status) {
        // A client must halt so the Gradle JavaExec task returns; a dedicated server can shut down
        // gracefully (the reference project does the same), avoiding the "abortive exit" warning.
        final boolean halt = FMLCommonHandler.instance()
            .getSide() == Side.CLIENT;
        FMLCommonHandler.instance()
            .exitJava(status, halt);
    }
}
