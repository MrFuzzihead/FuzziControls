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

3. ✅ **Inventory / menu navigation with the controller** — left stick moves a virtual cursor via LWJGL `Mouse.setCursorPosition` every render frame when a `GuiScreen` is open. Speed uses a quadratic curve (precise at low deflection, fast at full), scaled by `inventoryCursorSensitivity` and automatically proportioned to window size (854×480 reference). A (Xbox) / Cross (PS) fires a left-click and X (Xbox) / Square (PS) fires a right-click at the cursor position. LT (L2) toggles shift-click mode — the next left-click sends a Shift+click to move entire stacks. Cursor is centered on screen open. Works with every vanilla and mod GUI with zero per-screen code. Stretch goal: D-pad → arrow key discrete navigation still unimplemented.

4. **Analog movement speed scaling** *(deferred — requires Mixin)* — when enabled, left-stick deflection magnitude should scale the player's walk speed so a gentle push walks slowly and a full push runs at full speed. This cannot be done cleanly via `KeyBinding.setKeyBindState` alone because Minecraft converts the key boolean to a fixed ±1 speed in `EntityPlayerSP.moveEntityWithHeading`. It requires a **SpongePowered Mixin** (or an access transformer on `moveForward`/`moveStrafing`) to intercept the speed computation post-key-read and inject the analogue magnitude before physics runs. Config key `analogMovement` (default `false`) is reserved for this feature.

5. **GUI for key bindings** *(stretch goal — implement after inventory navigation)* — custom `GuiScreen` accessible from Options or the pause menu, listing every [`ControllerAction`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerAction.java) with its currently-bound [`ControllerButton`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerButton.java):
   - **Button icons**: ship sprite sheets (`textures/gui/buttons_xinput.png` and `buttons_dualsense.png`) with a glyph for every button. The active driver (from `ControllerManager.getActiveDriverName()`) determines which sheet renders at runtime — Xbox colours (A/B/X/Y) for XInput, PS colours (Cross/Circle/Square/Triangle) for DualSense.
   - **Remapping flow**: selecting an action row enters a "press any button" capture mode; the next raw controller input is bound to that action and persisted via `Config.save()`.
   - **Prerequisite**: inventory/menu navigation (consideration 3) must be implemented first — the binding GUI is itself a screen that needs to be fully navigable without a physical mouse.

---

## Reflection / Mixins / AT Tracker

This section tracks every place in the codebase that uses reflection, and documents why each one exists, whether it could be replaced by an Access Transformer (AT) or SpongePowered Mixin, and the plan for future migration.

> **Policy:** Reflection currently works and is acceptable. Migration to Mixins is a future goal for anything that can be cleanly replaced. Do not add new reflection without documenting it here first and confirming that AT/Mixin alternatives were evaluated.

---

### `GuiKeyHelper` — `GuiScreen.keyTyped(char, int)`

| | |
|---|---|
| **Used for** | Synthesizing an Escape key press when B/Circle is pressed while a GUI is open |
| **Why not AT?** | AT widens the base class method to `public`. Java forbids subclasses from narrowing an inherited method's access, so every vanilla/Forge screen that overrides `keyTyped` as `protected` (dozens of classes) fails to compile. **Confirmed:** applying the AT produced 53 compile errors. |
| **Why not Mixin (now)?** | A `@Mixin(GuiScreen.class)` with `@Invoker("keyTyped")` would work correctly and avoids the AT access-narrowing problem. Deferred because reflection works and functional work takes priority. |
| **Future plan** | Replace with a Mixin `@Invoker` interface on `GuiScreen` — likely as part of the broader Mixin infrastructure setup needed for analog movement (see below). |

---

### `GuiMouseHelper` — `GuiScreen.mouseClicked(int, int, int)` and `GuiScreen.mouseMovedOrUp(int, int, int)`

| | |
|---|---|
| **Used for** | Synchronously dispatching synthetic left/right mouse clicks to any open GUI screen; used both for normal clicks and for the shift-click direct-dispatch path |
| **Why not AT?** | Same problem as `keyTyped` — 53+ subclasses override both methods as `protected`. AT on the base class causes compile failures across all of them. |
| **Why not Mixin (now)?** | `@Invoker` on both methods would work correctly. Deferred alongside `GuiKeyHelper`. |
| **Future plan** | Replace with Mixin `@Invoker` interfaces, bundled with the `GuiKeyHelper` migration. |

---

### `GuiMouseHelper` — `Mouse.readBuffer` and `Mouse.buttons` (LWJGL)

| | |
|---|---|
| **Used for** | Injecting synthetic mouse events into LWJGL 2's internal event queue and `isButtonDown` buffer so that `GuiScreen.handleMouseInput()` and `GuiSlot` both see controller clicks |
| **Why not AT?** | ATs only apply to Minecraft/Forge classes. `Mouse` is part of LWJGL, a third-party native library shipped separately — ATs have no effect on it. |
| **Why not Mixin?** | Mixins also only target classes in the Minecraft/Forge class-loading domain. LWJGL classes are loaded by the bootstrap classloader and cannot be mixed into. |
| **Future plan** | **Cannot be replaced.** Reflection on LWJGL internals is the only option available to a Forge mod. This reflection stays permanently. |

---

### `KeyboardHelper` — `Keyboard.keyDownBuffer` (LWJGL)

| | |
|---|---|
| **Used for** | Temporarily patching LWJGL's key-down state buffer so that `Keyboard.isKeyDown(KEY_LSHIFT)` returns `true` during a shift-click dispatch, enabling `GuiContainer.handleMouseClick()` to see Shift as physically held |
| **Why not AT?** | Same reason as `Mouse` fields — LWJGL, not Minecraft/Forge. |
| **Why not Mixin?** | Same reason — LWJGL is outside the Mixin class-loading domain. |
| **Future plan** | **Cannot be replaced** via AT or Mixin. However, if we ever implement direct `GuiContainer.slotClick` dispatch (calling `func_146983_a` with `mode=1` for shift-click directly, bypassing the mouse event system entirely), `KeyboardHelper` could be eliminated. That would require a Mixin invoker on `GuiContainer` and only works for `GuiContainer` subclasses, not arbitrary GUIs. Tracked as a potential future improvement. |

---

### Mixin infrastructure (future)

When Mixins are enabled (`usesMixins = true` in `gradle.properties`), the following migrations become possible in a single batch:

1. `GuiKeyHelper` → `@Invoker("keyTyped")` on `GuiScreen`
2. `GuiMouseHelper` (MC methods) → `@Invoker("mouseClicked")` + `@Invoker("mouseMovedOrUp")` on `GuiScreen`
3. Analog movement (`analogMovement` config) → `@Inject` into `EntityPlayerSP.moveEntityWithHeading` to scale `moveForward`/`moveStrafing` by stick magnitude before physics

The LWJGL reflections (`Mouse.readBuffer`, `Mouse.buttons`, `Keyboard.keyDownBuffer`) cannot be replaced by Mixins and will remain regardless.


