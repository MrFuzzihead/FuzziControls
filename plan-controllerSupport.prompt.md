# Plan: Add XInput / DualSense Controller Support

Add full gamepad support to FuzziControls by reading controller input each tick (client-side only), translating axes and buttons into Minecraft actions via a configurable mapping, and shipping JUnit tests for the core logic. XInput (Xbox) controllers are the primary target; DualSense (PlayStation 5) via HID is the stretch goal.

## Steps

1. ✅ **Add controller-input library dependencies** in [`dependencies.gradle`](dependencies.gradle) — shadow-embedded [JXInput](https://github.com/StrikerX3/JXInput) for XInput (Windows native) and `hid4java` for DualSense. `usesShadowedDependencies = true` enabled in [`gradle.properties`](gradle.properties).

2. ✅ **Create the controller driver layer** — [`IControllerDriver`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/IControllerDriver.java) interface, [`XInputDriver`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/XInputDriver.java) (JXInput-backed), [`DualSenseDriver`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/DualSenseDriver.java) (raw HID, VID:PID `054C:0CE6`), and [`ControllerManager`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerManager.java) singleton. Manager polls the active driver every client tick; hot-plug support retries connection every 3 seconds without a game restart. [`ControllerState`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerState.java) is a normalised value object with dead-zone and clamping applied.

3. ✅ **Define the action mapping** in [`ControllerMapping`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerMapping.java) covering the [Minecraft wiki controller layout](https://minecraft.fandom.com/wiki/Controls): movement (left stick), camera (right stick, per-frame smooth), jump (A/Cross), sneak toggle (RS/R3, configurable hold/toggle), sprint (LS/L3), attack (RT/R2), use/place (LT/L2), hotbar cycle (RB/LB), inventory (Y/Triangle), drop item (B/Circle), close GUI (B/Circle), pause (Start), chat (Back/Select). D-pad and COMMAND unbound by default.

4. ✅ **Extend [`Config`](src/main/java/com/mrfuzzihead/fuzzicontrols/Config.java)** — persists every action→button binding, dead-zone, trigger threshold, look sensitivity, `dropEntireStack`, and `sneakToggle` via Forge's `Configuration` API in `config/fuzzicontrols.cfg`.

5. ✅ **Wire up the tick handler** — [`ControllerTickHandler`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerTickHandler.java) registered on both `FMLCommonHandler` (20 Hz game tick) and `MinecraftForge` event bus (per-frame render tick for smooth camera). Uses `driveKey()` (non-interfering — only releases keys the controller itself held, so keyboard input is never overridden). RT attack calls `KeyBinding.onTick` every held tick so `attackEntity()` fires on mobs, blocks, and air-swing alike. B button uses a `dropBlockedByGui` guard to prevent item drops when closing screens. Sneak supports toggle (default) and hold modes via `sneakToggle` config. Controller-held keys are released cleanly on disconnect.

6. ✅ **Add JUnit tests** in [`src/test/java/`](src/test/java/com/mrfuzzihead/fuzzicontrols/controller/) — `ControllerStateTest` (dead-zone, clamping, axis normalisation), `ControllerMappingTest` (all 55 default bindings, intentionally-unbound set, rebinding), `ControllerButtonTest` (axis vs digital classification). All 55 tests pass.

## Further Considerations

1. ✅ **XInput controller index** — auto-detects by default; user may set `xInputSlot = 0–3` in config. Hot-plug reconnects within ~3 seconds of plugging in with no lag (reconnect probe runs once per 60 ticks and costs ~one poll frame).

2. ✅ **DualSense priority** — auto mode tries DualSense first (HID), falls back to XInput. User may force a specific driver via `driver = xinput | dualsense | auto` in config.

3. ✅ **Inventory / menu navigation with the controller** — left stick moves a virtual cursor via LWJGL `Mouse.setCursorPosition` every render frame when a `GuiScreen` is open. Speed uses a quadratic curve (precise at low deflection, fast at full), scaled by `inventoryCursorSensitivity`. A (Xbox) / Cross (PS) fires a left-click and X (Xbox) / Square (PS) fires a right-click at the cursor position via access-transformed `GuiScreen.mouseClicked` / `mouseMovedOrUp`. Cursor is centered on screen open. Works with every vanilla and mod GUI with zero per-screen code. Stretch goal: D-pad → arrow key discrete navigation still unimplemented.

4. **GUI for key bindings** *(stretch goal — implement after inventory navigation)* — custom `GuiScreen` accessible from Options or the pause menu, listing every [`ControllerAction`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerAction.java) with its currently-bound [`ControllerButton`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerButton.java):
   - **Button icons**: ship sprite sheets (`textures/gui/buttons_xinput.png` and `buttons_dualsense.png`) with a glyph for every button. The active driver (from `ControllerManager.getActiveDriverName()`) determines which sheet renders at runtime — Xbox colours (A/B/X/Y) for XInput, PS colours (Cross/Circle/Square/Triangle) for DualSense.
   - **Remapping flow**: selecting an action row enters a "press any button" capture mode; the next raw controller input is bound to that action and persisted via `Config.save()`.
   - **Prerequisite**: inventory/menu navigation (consideration 3) must be implemented first — the binding GUI is itself a screen that needs to be fully navigable without a physical mouse.
