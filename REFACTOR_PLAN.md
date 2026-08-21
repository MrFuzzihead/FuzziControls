# FuzziControls — Java 25 + lwjgl3ify Refactor Plan

> **Status:** Phase 0 (bug fixes) + Step 5 (input plumbing) done on `main`; build green on Java 25 + lwjgl3ify 3.0.10; **runtime smoke test passed** (JXInput/hid4java/JNA natives load on Java 25).
> **Decision:** Migrate `main` directly to lwjgl3ify + Java 25. No long-lived migration branch.
> **Goal:** Keep the existing "seamless console Minecraft" feel while putting the mod on a modern, more maintainable input/rendering stack and removing JNA/LWJGL2 reflection hacks.

---

## 1. Goals and Non-Goals

### Goals

1. Move `main` to lwjgl3ify and target Java 25 (or the newest JDK officially supported by lwjgl3ify/RetroFuturaGradle). Use lwjgl3ify version 3.0.10:dev
2. Replace LWJGL 2 input/mouse/keyboard/window-focus code with LWJGL 3/GLFW (directly or through lwjgl3ify's supported APIs).
3. Remove or drastically reduce the reflection hacks in `GuiKeyHelper`, `GuiMouseHelper`, and `KeyboardHelper`.
4. Reduce or eliminate JNA-based dependencies (JXInput) by using GLFW gamepad input where it provides better or equal behavior.
5. Fix the input logic bugs found during codebase analysis.
6. Keep DualSense support intact (HID report parsing), with a path to modernize/verify HID4Java on Java 25.
7. Preserve or improve test coverage and CI.

### Non-Goals

- Do not redesign the default button mapping.
- Do not implement the planned in-game remap GUI or D-pad navigation in this refactor (those remain separate plans).
- Do not implement analog movement scaling unless it falls out of the platform-abstraction work cheaply.

---

## 2. Current Architecture Summary

| Area                   | File(s)                                                                                                                  | Notes                                        |
|------------------------|--------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| Mod entry              | `FuzziControls.java`                                                                                                     | `@Mod`, `@SidedProxy`                        |
| Config                 | `Config.java`                                                                                                            | Forge `Configuration`                        |
| Controller abstraction | `IControllerDriver`, `ControllerManager`, `ControllerState`, `ControllerMapping`, `ControllerAction`, `ControllerButton` | Good seam already                            |
| Backends               | `XInputDriver.java`, `DualSenseDriver.java`                                                                              | JXInput/hid4java                             |
| Gameplay/tick          | `ControllerTickHandler.java`                                                                                             | Polls 20 Hz, renders camera/cursor per frame |
| GUI input hacks        | `GuiKeyHelper.java`, `GuiMouseHelper.java`, `KeyboardHelper.java`                                                        | Reflectively poke LWJGL 2 internals          |
| Build                  | `build.gradle.kts`, `gradle.properties`, `dependencies.gradle`, `settings.gradle.kts`                                    | GTNH convention, Jabel, shade deps           |

---

## 3. Bugs to Fix During the Refactor

These were found during analysis and should be fixed as part of the migration so they are covered by the new tests.

### 3.1 High severity

#### B1 — Trigger inputs are double-thresholded
- `XInputDriver` and `DualSenseDriver` call `ControllerState.normaliseTrigger(...)`, then
  `ControllerMapping.isActive` compares the already-normalized trigger value to
  `triggerThreshold` again.
- `XInputDriver`/`DualSenseDriver` also add digital `LEFT_TRIGGER`/`RIGHT_TRIGGER` based on the
  already-normalized value, not the raw value.
- **Effect:** with default `triggerThreshold = 0.2`, triggers do not activate until raw pressure is roughly `0.36`.
- **Fix direction:**
  - Keep `ControllerState` analog fields as normalized analog values.
  - Change `ControllerMapping.isActive` for triggers to `state.leftTrigger() > 0f` /
    `state.rightTrigger() > 0f`, or use `state.isPressed(LEFT_TRIGGER/RIGHT_TRIGGER)` if the
    digital set is the source of truth.
  - Emit digital trigger buttons from raw values in the drivers (`raw >= threshold`).
  - Update `ControllerMappingTest` so it tests the real normalize→isActive flow.

#### B2 — Stick axes are compared against `triggerThreshold`
- `ControllerMapping.isActive` uses `state.leftStickY() < -triggerThreshold`, etc.
- The driver already applies `stickDeadZone`, so movement activates at around
  `deadZone + (1 - deadZone) * triggerThreshold` instead of just past the deadzone.
- **Fix direction:**
  - Introduce a dedicated stick-action threshold if needed, otherwise compare normalized stick
    directions against `> 0f` / `< 0f`.
  - Do not reuse `triggerThreshold` for sticks.
  - Update config docs and `BUTTON_BINDINGS.md`.

#### B3 — `DualSenseDriver.poll()` can crash the client tick on unplug
- `DualSenseDriver.poll()` is not wrapped in try/catch, and `ControllerManager.tick()` also
  does not guard its `activeDriver.poll()` call.
- An unplug between `isConnected()` and `device.read(...)` can throw.
- **Fix direction:**
  - Wrap driver reads and `ControllerManager.tick()` polling in defensive try/catch.
  - On transient failure return the cached state (`lastState`) or `ControllerState.empty()`.
  - Ensure `DualSenseDriver.close()` is also exception-safe.

#### B4 — Focus-loss stale edge state fires actions on refocus
- `onClientTick` returns early when `!Display.isActive()` without advancing `wasActive[]`,
  `wasGuiLeftClick`, `wasGuiRightClick`, `wasGuiShiftClick`, or the drop state.
- Pressing an edge-triggered button while unfocused and then refocusing can fire a false edge.
- **Fix direction:**
  - Resync all edge/previous-state fields without firing actions when focus is lost, when focus
    returns, and after controller disconnect/reconnect.
  - Clear/rebuild `buttonsHeldOnGuiClose` and `dropBlockedByGui` in the same resync path.

### 3.2 Medium severity

#### B5 — `Config` cannot persist "unbound"
- `parseButton("NONE", defaultBtn)` returns the default button instead of `null`.
- **Fix:** honor `NONE` as explicit unbind, and reset/replace `controllerMapping` before loading.

#### B6 — `ControllerMapping.applyDefaults()` does not clear the map first
- Custom bindings for actions without defaults survive a "reset".
- **Fix:** call `actionToButton.clear()` before re-applying defaults.

#### B7 — Dead code and stale docs
- Remove unused `ControllerTickHandler.consumeAllInWorldEdges()`.
- Remove unused `lastPartialTick`.
- Remove dead `handleBButton` GUI branch (the top-level GUI path already returns earlier).
- Remove unused `leftClickCounter` access transformer from `fuzzicontrols_at.cfg` (or the whole
  AT file/`accessTransformersFile` if no longer needed).
- Fix `CommonProxy` Javadoc (`clientSideOnly = true` claim).
- Fix `ControllerAction` comments (PICK_BLOCK/COMMAND).
- Align README/BUTTON_BINDINGS defaults with `Config.java`.

#### B8 — No shutdown hook for `ControllerManager`
- `shutdown()` is never called.
- **Fix:** call it from an appropriate FML/Forge stop event.

#### B9 — Tick handler registered on two buses
- Only the FML bus fires `TickEvent.*` in 1.7.10, so `MinecraftForge.EVENT_BUS.register(...)`
  is dead registration.
- **Fix:** register only on `FMLCommonHandler.instance().bus()`.

#### B10 — `ControllerManager.getInstance()` is not thread-safe
- **Fix:** use an eager `static final` singleton.

### 3.3 Low severity / optimization

#### B11 — XInput is polled multiple times per frame
- `XInputDriver.isConnected()` calls `device.poll()`, and `ControllerManager.isActive()` invokes
  that from `onRenderTick` every frame in addition to the 20 Hz game-tick poll.
- **Fix:** cache connected/polled status per tick, or reuse the last poll result.

#### B12 — Hot-plug reconnect rebuilds HID services every 3 seconds
- Auto mode without a DualSense repeatedly creates and shuts down HID4Java services.
- **Fix:** cache the `HidServices`/driver instance across reconnect probes.

#### B13 — Shade/minimize dependency risk
- `minimizeShadowedDependencies = true` plus `transitive = false` may strip HID4Java/JXInput/JNA
  classes or required slf4j classes used reflectively.
- **Action:** inspect the produced shaded jar to verify HID4Java/JXInput/JNA runtime resources.

#### B14 — `normaliseAxis(0, 0)` returns `-0.0f`
- Cosmetic; tidy if touched.

---

## 4. Build and Toolchain Migration (on `main`)

1. **Pin the JDK/toolchain.**
   - Set `forceToolchainVersion` (or the GTNH convention equivalent) to `25`; verify the exact
     lwjgl3ify/RetroFuturaGradle-supported JDK version.
   - Disable Jabel (`enableModernJavaSyntax`) if targeting modern bytecode natively; remove the
     `@Desugar` record workaround after records are native.

2. **Add lwjgl3ify.**
   - Add the lwjgl3ify dependency (latest 3.x; cached locally at `3.0.31`) via the build config.
   - Confirm the recommended GTNH lwjgl3ify integration:
     - dependency configuration (`runtimeOnlyNonPublishable`, `api`, or plugin),
     - mixin flags (`forceEnableMixins`/`usesMixins`),
     - any required `--add-opens`/JVM args,
     - run-client and run-server config changes.
   - Reference the lwjgl3ify README/example buildscript before committing the exact coordinates.

3. **Update dependencies.**
   - Prefer GLFW gamepad input to replace JXInput/JNA for Xbox.
   - Keep HID4Java only for DualSense HID parsing, updated to a Java-25-compatible version.
   - If HID4Java/JNA must remain, test against Java 25 and add required native-access/JVM flags.

4. **CI matrix.**
   - Update `.github/workflows` to build/test with Java 25 + lwjgl3ify.
   - Keep a compatibility job only if the code intentionally still supports Java 8/LWJGL2; this
     plan assumes main moves fully, so Java 8 may be dropped.

---

## 5. Code Migration Map (LWJGL2 → LWJGL3/GLFW)

### 5.1 Introduce a platform abstraction first
Create a small internal boundary so gameplay code no longer depends on `org.lwjgl.input.*`
directly:

- `PlatformWindow` / `PlatformCursor` / `PlatformKeyboard` wrappers for:
  - window focus (`Display.isActive()`),
  - cursor position/movement (`Mouse.setCursorPosition`, display size),
  - mouse button event synthesis,
  - key-down state (`Keyboard.isKeyDown`, `KEY_LSHIFT`, `KEY_ESCAPE`).
- Backends:
  - current LWJGL2 implementation for the transition period,
  - lwjgl3ify/GLFW implementation for the final main state.

This removes the direct LWJGL calls from `ControllerTickHandler` and isolates the brittle
reflection in one place before it is deleted.

### 5.2 `ControllerTickHandler.java`
- Replace `org.lwjgl.opengl.Display.isActive()` with the platform focus check.
- Replace `org.lwjgl.input.Keyboard.KEY_LSHIFT/KEY_RSHIFT/KEY_ESCAPE` with platform key codes or
  `GLFW` constants.
- Replace `Mouse.setCursorPosition(...)` with the platform cursor call.
- Keep the game logic (movement, sneak, drop, hotbar, chat, pause) otherwise unchanged.

### 5.3 `GuiKeyHelper.java`
- On lwjgl3ify, `GuiScreen.keyTyped` dispatch should be re-evaluated.
- Prefer an access-transformer-free/mixin-free approach only if lwjgl3ify exposes a supported
  event/input API; otherwise continue reflective `keyTyped` temporarily, isolated behind the
  platform abstraction.

### 5.4 `GuiMouseHelper.java`
- Replace LWJGL 2 `Mouse.readBuffer`/`Mouse.buttons` reflection with:
  - GLFW cursor/button APIs, and/or
  - direct `GuiScreen` dispatch via access transformer/mixin invokers where safe.
- Confirm the GUI coordinate-space conversion (bottom-left display pixels → top-left scaled GUI
  coords) still matches lwjgl3ify's `handleMouseInput`.

### 5.5 `KeyboardHelper.java`
- Replace `Keyboard.keyDownBuffer` reflection with GLFW key state APIs or lwjgl3ify's keyboard
  abstraction.
- Preserve the shift-click behavior exactly: patch shift state only for the duration of the click.

### 5.6 Controller backends
- **Xbox / XInput:** prototype a `GLFWGamepadDriver` using `glfwGetGamepadState`, GLFW gamepad
  buttons/axes, and joystick connect/disconnect callbacks.
  - Remove `com.xenoamess:JXInput` and the JNA dependency if GLFW coverage is acceptable.
- **DualSense:** keep raw HID parsing in `DualSenseDriver`. Update HID4Java/JNA versions for
  Java 25, or move to a Java FFM-based HID reader if HID4Java proves unstable.
- **Hot-plug:** prefer GLFW joystick callbacks over the current 3-second polling loop.

---

## 6. Modern Java Cleanups

Once native modern bytecode is used, apply cleanups only where they reduce risk:

- Remove `@Desugar` and Jabel record workaround from `ControllerState`.
- Use pattern matching for `ControllerButton.isAxis()` / `ControllerMapping.isActive` if clearer.
- Use sealed/enum improvements only where they improve readability.
- Consider `VarHandle`/FFM instead of reflection if any third-party native access remains.

---

## 7. GTNHLib Evaluation

**Result (inspected `GTNHLib-0.11.41` sources):**
- No gamepad/controller API — not a replacement for `IControllerDriver`.
- `com.gtnewhorizon.gtnhlib.reflect.Fields` — type-safe static/instance field reflection helper.
  Could replace the small fallback reflection, but our usage is already minimal and isolated, so
  adding a hard dependency for ~30 lines is not worth it.
- `com.gtnewhorizon.gtnhlib.config.*` (`Config`, `ConfigurationManager`, `SimpleGuiConfig`, `SimpleGuiFactory`)
  — provides a config-**GUI** out of the box. This is the strongest reason to adopt GTNHLib, but
  only when the in-game options/remap GUI is actually built.
- `keybind.*` — network-synced keybinds; not controller-related.

**Decision:** do not add GTNHLib now. Re-evaluate if/when the config-GUI/remap-GUI feature is built.

---

## 8. Testing and Validation Plan

### Unit tests
- Add normalize→`isActive` regression tests for trigger thresholds.
- Add stick-axis threshold tests.
- Add `applyDefaults` reset test.
- Add `Config.parseButton("NONE")` unbind test.
- Add `DualSenseDriver` cache/error-path tests (already partially stubbed).

### Manual validation on lwjgl3ify + Java 25
- Xbox controller: buttons, sticks, triggers, hot-plug.
- DualSense controller: face buttons, sticks, triggers, D-pad, share/options.
- GUI navigation: main menu, inventory, pause menu, chest, `GuiSlot` world/server lists.
- Shift-click in inventories (LT + A/X).
- Window focus loss/refocus; controller disconnect/reconnect.
- Cursor speed at multiple window sizes (854×480 reference scaling still works).

### CI
- Java 25 build + test job.
- Optional: keep a Java 8/LWJGL2 compatibility job only if the migration is staged, otherwise
  remove it.

---

## 9. Risks and Mitigations

| Risk                                                                | Mitigation                                                                                        |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| lwjgl3ify/RetroFuturaGradle does not yet officially support Java 25 | Verify supported JDK in lwjgl3ify README; target the newest supported JDK if 25 is not stable     |
| JNA/JXInput/HID4Java break on Java 25                               | Replace JXInput with GLFW gamepad; update HID4Java/JNA or move DualSense to FFM                   |
| Reflection-based GUI event injection differs under lwjgl3ify        | Isolate all input behind platform abstraction; test `GuiSlot` and `handleMouseInput` early        |
| GLFW gamepad mapping differs from XInput defaults                   | Keep `ControllerState`/`ControllerMapping` semantics; add normalization parity tests              |
| Shadow minimization strips native/reflection classes                | Inspect shaded jar contents at build time; bump `minimizeShadowedDependencies` settings if needed |
| DualSense HID support regresses                                     | Keep HID4Java path and gate GLFW gamepad to Xbox/generic controllers only                         |

---

## 10. Phase 0 Status

> Applied to `main`; `./gradlew build` is green (compile + tests + checkstyle + spotless).

**Fixed in code:**
- **B1** — Trigger double-threshold eliminated: `ControllerMapping.isActive` treats normalized triggers as active when `> 0f`; drivers emit digital trigger buttons from raw values.
- **B2** — Stick digital bindings no longer reuse `triggerThreshold`; they use normalized `> 0f` / `< 0f` (dead-zone is the single gate).
- **B3** — `DualSenseDriver.poll()`/`close()` and `ControllerManager.tick()` wrapped in try/catch; `safeClose` added.
- **B4** — Focus-loss & (re)connect edge resync via `syncEdges(...)` in `ControllerTickHandler`.
- **B5** — `Config` honors explicit `NONE` (true unbind) and resets the mapping on each load.
- **B6** — `ControllerMapping.applyDefaults()` clears the map first.
- **B7** — Removed dead code (`consumeAllInWorldEdges`, `lastPartialTick`, unused `handleBButton` GUI branch) and stale docs (AT file, `CommonProxy` Javadoc, `ControllerAction` comments, README defaults/JDK note).
- **B8** — JVM shutdown hook releases controller resources on exit (in `ClientProxy`).
- **B9** — Tick handler registered only on the FML bus (Forge-bus duplicate removed).
- **B10** — `ControllerManager` uses an eager singleton.
- **B11** — Cached `lastConnected` flag avoids re-polling XInput on every `onRenderTick`.
- **B14** — `normaliseAxis(0, 0)` returns `+0.0f`.

**Tests added/updated:** trigger threshold regression tests, stick dead-zone flow tests, `applyDefaults` clear test, `normaliseAxis(0,0)` sign test.

**Deferred (handled in the driver-rework / lwjgl3ify phase):**
- **B12** — Cache/reuse DualSense `HidServices` across reconnect probes. **Reverted after testing:**
  HID4Java's {@code HidServices} goes stale after an unplug/replug even with {@code scan()},
  breaking DualSense hot-plug. The driver now creates a fresh instance on every construction
  (reconnect probes happen at most every 3 seconds, so the cost is negligible) and shut it
  down in {@code close()}, which guarantees full USB re-enumeration and correct hot-plug.
- **Reload command (added):** `/fuzzicontrols reload` forces the controller manager to
  re-initialize both drivers from scratch. Useful for DualSense which doesn't always hot-plug
  cleanly through hidapi. Registered as a client-side command in `ClientProxy`.
  Command: `com.mrfuzzihead.fuzzicontrols.command.CommandReloadControllers`.
- **B13** — Inspect the shaded jar and revisit `relocateShadowedDependencies` (currently `true`) so JNA/hid4java/JXInput native loading is verified/locked down.

---

## 11. Tuning & Known Issues (notes)

- **DualSense input-lag fix (done):** the driver now drains the hidapi report FIFO each poll and
  keeps the *newest* report. Previously it read exactly one report per game tick from the ~1 kHz
  streaming device, which parsed the *oldest* queued report and fell progressively behind — this
  was the source of the “>=1 second” lag vs snappy XInput.
- **DualSense residual lag (still to verify):** latency now feels much better but may need tuning.
  If it still feels laggy, the next suspect is the report-rate handshake: the DualSense only
  streams at its full report rate once the host *writes* an output report (e.g. the 0x02
  lightbar/motor report). We currently never write to the device. Add a best-effort, non-fatal
  output-report write on open to force high-rate input if needed.
- **Right-stick Y inversion (checked, no change):** reported as inverted, but verified camera
  behaves correctly on the current build — no sign flip was required. If a controller later shows
  real inversion, check whether movement is also inverted (fix at driver: negate Y) or only the
  camera (fix in `applyLook`).
- **Left-stick cursor inversion in inventory (fixed):** lwjgl3ify's compat `Mouse.setCursorPosition`
  passes its Y coordinate directly to SDL, which uses top-left origin. LWJGL convention is
  bottom-left. Added `mc.displayHeight - 1 - guiCursorY` conversion at every call to
  `setCursorPosition` so SDL receives the inverted Y.

## Smoothness improvements (done)

- **Camera micro-stutter (fixed):** `onRenderTick` now calls `manager.pollFresh()` instead of
  `manager.getState()`, polling the controller hardware every render frame (60+ Hz) instead of
  relying on the 20 Hz game-tick state. This removes the ~50 ms dead-band where axis data was
  stale between game ticks, making right-stick camera rotation continuously responsive.
- **Movement hysteresis (fixed):** added `withHysteresis(...)` helper for the four stick-direction
  movement actions. It tracks per-action state and uses two thresholds (HYST_ON = 0.065 to
  activate, HYST_OFF = 0.015 to deactivate), preventing micro-oscillation when the stick is near
  the dead-zone boundary. Activated in `syncEdges` reset.
- **Camera Movement with right stick choppy:**

---

## 12. Definition of Done

- [x] `main` builds and runs with lwjgl3ify on the target modern JDK.
- [x] Java 8/Jabel record workarounds are removed (native Java 25; `@Desugar` dropped).
- [x] `ControllerTickHandler` calls only lwjgl3ify-provided `org.lwjgl.input.*` compat shims (API-identical).
- [x] `GuiKeyHelper`, `GuiMouseHelper`, and `KeyboardHelper` no longer rely on LWJGL 2 internals: the lwjgl3ify path uses public `org.lwjglx` APIs (LWJGL2 reflection kept only as fallback).
- [x] JXInput/JNA (XInput path): **kept and justified** — JNA/JXInput/hid4java work on Java 25 (runtime smoke test passed); GLFW gamepad deferred.
- [x] All high-severity bugs B1–B4 are fixed and covered by tests.
- [x] Medium/low cleanups B5–B11 and B14 are fixed; B12/B13 explicitly deferred to the driver-rework phase.
- [x] **B13** — shaded jar verified: `com.sun.jna` + `org.hid4java` are *not* relocated (native paths intact), JXInput is relocated into `shadow/`, all natives present, and the runtime smoke test passed.
- [ ] Hot-plug, focus loss/refocus, GUI clicks, and cursor movement pass manual validation.
- [ ] CI runs on Java 25 + lwjgl3ify (pending reusable-workflow support / matrix setup).
