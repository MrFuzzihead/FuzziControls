package com.mrfuzzihead.fuzzicontrols.gametest;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

/** One side of the game-test mod. {@link GameTestMod}'s {@code @SidedProxy} selects it. */
public interface IGameTest {

    /** Called from the game-test mod's pre-init after the production mod is available. */
    void preInit(FMLPreInitializationEvent event);

    /** Called once when a dedicated server has started; no-op on the client. */
    void onServerStarted(FMLServerStartedEvent event);
}
