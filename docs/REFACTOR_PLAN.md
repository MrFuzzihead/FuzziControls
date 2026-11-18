# FuzziControls — Current Analysis & Refactor Plan

> **Status:** `main` is already on lwjgl3ify + Java 25 (`jvmDowngrader` target 17). The
> LWJGL2 → LWJGL3 migration described in the previous version of this document is complete.
> This document is the current codebase analysis plus the remaining work. `./gradlew test`
> and the client self-test harness are green.
>
> **Scope:** periodic stutter, build/dependency correctness, functional bugs, cleanup,
> optimizations, and features. Items still open from the previous plan are listed in
> section 9 and are not dropped.

---

## 1. Status snapshot

| Area | State |
|---|---|
| Toolchain | GTNHGradle 2.0.34, Gradle 9.7.1, JDK 25 toolchain, `jvmDowngrader` target 17 |
| Mod runtime | Java 17+ (multi-release jar, Java 21 variant present) |
| Input | lwjgl3ify 3.1.0 (`org.lwjglx` input injection), JXInput 1.0.1 (XInput), hid4java 0.8.0 (DualSense) |
| GUI | lwjgl3ify input injection + mixin accessors/invokers; D-pad nav, virtual cursor, scroll |
| Tests | JUnit 4 + client self-test harness (CI workflow exists) |
| Known top issue | **~500 ms HID enumeration on the client thread every 3 s**, plus a 500 ms hid4java scanner thread running continuously — see section 2 |

---

## 2. P0 — the periodic stutter (investigated, fixed)

> **Status: FIXED.** `DualSenseDriver` now opens the controller with
> `HidApi.open(VID, PID, null)` (native VID/PID filter: ~2 ms idle, ~14 ms present), reads with
> `HidApi.read(handle, buffer, 0)` (**zero-timeout — the no-timeout overload blocks on Windows**),
> and closes the raw handle. The hid4java `HidServices` instance is no longer created, so its
> 500 ms scanner thread is gone. Also implemented: `driver = none` and unknown-driver disable, quiet
> DEBUG retries, focus-aware 1 s/30 s hot-plug probing, `read < 0` → disconnect (no stuck buttons),
> `pollFresh` failure closes the handle, and the stale `lastFrameNanos` reset. The measurements
> below are retained as the investigation record.

### 2.1 Measurements

Measured with the mod's own shaded hid4java 0.8.0 / JNA code from
`build/libs/*-dev.jar` on this machine: Windows, 35 attached HID devices, a DualSense
connected.

| Operation | Measured |
|---|---|
| `HidManager.getHidServices(spec)` (first call) | **639 ms** |
| `HidApi.init()` (first call) | **132 ms** |
| `HidServices.getHidDevice(0x054C, 0x0CE6, null)` | **496–801 ms every call** |
| `HidApi.enumerateDevices(0, 0)` (all devices) | **~500 ms** |
| `HidApi.enumerateDevices(0x054C, 0x0CE6)` (filtered) | **~2–9 ms** |
| `HidApi.open(0x054C, 0x0CE6, null)` | **~14 ms present, ~2 ms absent** |
| JXInput `getDeviceFor + poll + isConnected` | **0.019 ms** |

The XInput path is cheap. The cost is the **unfiltered HID enumeration**. JNA 5.19.1
produced identical enumeration times, so this is hidapi/Windows, not a JNA version problem.

### 2.2 Root cause A — unfiltered HID enumeration on the client thread

`ControllerManager` retries every 60 ticks:

```java
// ControllerManager.java:35
private static final int RECONNECT_INTERVAL_TICKS = 60; // 3 seconds
...
// ControllerManager.java:176
tryReconnect();   // -> new DualSenseDriver() -> HidServices.getHidDevice(...)
```

`DualSenseDriver` calls `HidServices.getHidDevice(VID, PID, null)` (DualSenseDriver.java:77).
That helper calls `hidDeviceManager.getAttachedHidDevices()`, which enumerates **all** HID
devices and only then filters in Java. On this machine that is a **~500 ms block** on the
client tick thread, every ~3 s.

This exactly matches the friend's log:

```
[21:26:16] [Client thread/INFO] [fuzzicontrols/]: [FuzziControls] DualSense not found; driver standby.
[21:26:16] [Client thread/INFO] [fuzzicontrols/]: [FuzziControls] XInputDriver initialised for slot 0.
```

The two lines are `DualSenseDriver` (no matching device) and `XInputDriver` constructors.
The cadence is the 60-tick retry. The author does not see the loop because a DualSense is
found at startup, so the retry path never runs.

### 2.3 Root cause B — the hid4java scanner thread runs every 500 ms

`DualSenseDriver.getHidServices()` sets only `setAutoShutdown(false)` (DualSenseDriver.java:41).
The remaining hid4java defaults are:

```java
// hid4java 0.8.0 HidServicesSpecification
scanMode     = SCAN_AT_FIXED_INTERVAL
scanInterval = 500          // ms
autoStart    = true
```

Creating the driver therefore starts a daemon thread that calls `hid_enumerate` every
500 ms for the whole session. On this machine one scan takes ~500 ms, so the scanner is
roughly **50 % of a core continuously**, even when a controller is connected, and even after
`DualSenseDriver.close()` (the shared `HidServices` outlives the driver). This affects the
author too, not only users without a controller.

### 2.4 Root cause C — no off switch and log spam

- `controllerDriver` only supports `auto | xinput | dualsense`. A user without a controller
  cannot stop the 3 s probing; `"none"` falls through `selectDriver()` and retries forever.
- Both constructors log at INFO on every probe (`DualSenseDriver.java:84`,
  `XInputDriver.java:55`), producing the 2-line pair at 20 pairs/minute.
- `ControllerManager`'s Javadoc claims the probe cost is “comparable to a single frame of
  normal polling and is imperceptible in practice” (ControllerManager.java:25–29). That is
  false; it is ~500 ms on this machine.

### 2.5 Fix — use the filtered low-level hidapi call

`HidApi.open(VID, PID, null)` lets hidapi filter by VID/PID natively, so it does **not**
enumerate every device. It is ~2 ms when absent and ~14 ms when present. `HidApi.read` /
`HidApi.close` drive the handle directly, removing the scanner, the stale `HidServices`
instance, and the double-open in one change.

```java
// DualSenseDriver.java — sketch
private static boolean hidInitialised;
private HidDeviceStructure handle;
private final byte[] data = new byte[REPORT_SIZE];

private static synchronized boolean ensureHid() {
    if (!hidInitialised) {
        try {
            HidApi.init();               // ~132 ms once; move off-thread if desired
            hidInitialised = true;
        } catch (Throwable t) {
            FuzziControls.LOG.warn("[FuzziControls] hidapi unavailable: {}", t.toString());
        }
    }
    return hidInitialised;
}

public DualSenseDriver() {
    if (!ensureHid()) return;
    try {
        handle = HidApi.open(VENDOR_ID, PRODUCT_ID, null); // ~2 ms absent, ~14 ms present
        if (handle != null) {
            FuzziControls.LOG.info("[FuzziControls] DualSense controller detected and opened.");
        } else {
            FuzziControls.LOG.debug("[FuzziControls] DualSense not found; driver standby.");
        }
    } catch (Throwable t) {
        FuzziControls.LOG.warn("[FuzziControls] DualSense init failed: {}", t.toString());
        handle = null;
    }
}

@Override
public boolean isConnected() {
    return handle != null;
}

@Override
public ControllerState poll(float deadZone, float triggerThreshold) {
    if (handle == null) return ControllerState.empty();
    try {
        ControllerState fresh = null;
        int read;
        // The 3-arg overload is hid_read_timeout(..., 0), i.e. non-blocking.
        // The 2-arg overload is hid_read(), which blocks until a report arrives.
        while ((read = HidApi.read(handle, data, 0)) >= REPORT_MIN_BYTES) {
            if ((data[0] & 0xFF) != 0x01) continue; // USB input report only; BT is 0x31/78 bytes
            fresh = parseReport(data, deadZone, triggerThreshold);
        }
        if (read < 0) {                 // unplugged / invalid handle
            close();
            return ControllerState.empty();
        }
        return reportCache.record(fresh);
    } catch (Throwable t) {
        FuzziControls.LOG.debug("[FuzziControls] DualSense read error: {}", t.toString());
        close();
        return ControllerState.empty();
    }
}

@Override
public void close() {
    if (handle != null) {
        try {
            HidApi.close(handle);
        } catch (Throwable ignored) {}
        handle = null;
    }
    reportCache.clear();
}

public static synchronized void shutdownHid() {
    if (hidInitialised) {
        try {
            HidApi.exit();
        } catch (Throwable ignored) {}
        hidInitialised = false;
    }
}
```

Then `ClientProxy` calls `DualSenseDriver.shutdownHid()` instead of
`shutdownSharedServices()`. Remove the `HidServices` / `HidManager` / `HidServicesSpecification`
imports and the scanner.

If you prefer to keep `HidServices`:

```java
spec.setAutoStart(false);
spec.setScanMode(ScanMode.NO_SCAN);
```

and use `HidApi.enumerateDevices(VID, PID)` (2–9 ms) only as a presence check before
`getHidDevice()`. Also construct it with `new HidServices(spec)` rather than
`HidManager.getHidServices(spec)`; `HidManager` returns any existing singleton and
**silently ignores your spec** if another mod created it first.

### 2.6 Carried-over note (old B12)

The previous plan's B12 said the driver “creates a fresh HidServices instance on every
construction” and that this was reverted because HID4Java goes stale after unplug/replug.
The current code is **shared static `HidServices`** (DualSenseDriver.java:30), which is a
third state: the stale-handle problem is back **and** the scanner runs forever. The
`HidApi` approach above avoids both: the handle is created per driver instance, opened with
the native filter, and closed on `close()` or on a `read < 0`.

### 2.7 Additional stutter/polish fixes (implemented)

- [x] `controllerDriver = none` stops all probing; an unknown driver name is treated as disabled
  with one warning instead of probing forever.
- [x] Probe failures log at DEBUG; only "Using …", "disconnected" and "no controller" (once)
  are INFO. The twice-per-3-seconds INFO pair is gone.
- [x] `init()` sets `reconnectCooldown` after a failed probe, so `init()` + the next tick do not
  probe twice.
- [x] Hot-plug probing is focus-aware: 1 s while focused, 30 s while unfocused/minimized,
  immediate on focus gain; see 2.8.
- [ ] Move the probe off the client thread. Not needed at ~2 ms; the one-time `HidApi.init`
  (~132 ms) still happens at startup on the client thread. Revisit only if a slow HID stack is
  reported.
- [x] `pollFresh()` now closes the handle and clears state on a poll error (one WARN) instead of
  logging a WARN every render frame.
- [x] `onRenderTick()` resets `lastFrameNanos` when the manager is inactive, so reconnecting does
  not jump the camera/cursor by the 0.1 s clamp.
- [x] `DualSenseDriver.poll()` treats `read == -1` as a disconnect (instead of "no report") and
  validates the USB report ID, so an unplug no longer leaves buttons stuck.

### 2.8 Hot-plug responsiveness — fixed (focus-aware probing)

The old 3/6/12/24/30 s backoff meant a controller plugged in while the game was focused could take
up to 30 s to appear. The manager is now focus-aware:

- **Focused:** probe every 20 ticks (1 s). With the filtered `HidApi.open` probe (~2 ms idle) this
  costs roughly **0.2% of one core** and never blocks a frame.
- **Unfocused/minimized:** probe every 600 ticks (30 s), so an alt-tabbed game does not keep
  scanning for hardware.
- **Focus gained:** `ControllerManager.setFocused(true)` sets the cooldown to zero, so the next
  client tick probes immediately. Plug in a controller while alt-tabbed, return to the game, and it
  is available on the next tick.
- **Active controller unplugged:** detected on the next client tick; an immediate reconnect probe
  runs, then the normal focused/unfocused interval resumes.
- `/fuzzicontrols reload` still forces an immediate probe.

Options considered and not needed right now:

1. A background probe thread would remove even the ~2 ms probe from the client thread; the measured
   cost is already negligible, and thread hand-off adds lifecycle complexity. Revisit only if a slow
   HID stack is reported.
2. OS device-change notifications (Windows `WM_DEVICECHANGE`, Linux udev/netlink) would be truly
   event-driven but are per-platform and much more code; not worth it yet.

Unit tests cover the 1 s focused cadence, the 30 s unfocused throttle, the immediate focus-gain
probe, and the no-controller/`none` paths.

---

## 3. `gradle.properties`

### `downgradeTargetVersion = 17` — correct

- The mod uses records/pattern matching and lwjgl3ify 3.x requires Java 17+. The GTNH
  template default of 8 would not help.
- Verified output: base class files are major 61 (Java 17), with `META-INF/versions/21`.
- `forceToolchainVersion = -1` + default `jvmDowngraderMultiReleaseVersions = 21,25` selects
  the JDK 25 toolchain; `.java-version` and CI use 25. Consistent.
- `downgradeTargetVersion = 17` is what makes Java 17 clients work: it produces the Java 17
  base classes. The multi-release list (`21,25`) only controls the optional
  `META-INF/versions/*` variants and does not affect Java 17. GTNHGradle 2.0.34 already
  defaults to `21,25`, so no explicit pin is required.
- `gradle.properties` is regenerated by `./gradlew updateBuildScript`; do not add explanatory
  comments there. The updater preserves property values (including a pin, if ever needed).

### `jvmDowngraderStubsProvider = shade` — correct, but fix the license note

The removed comment said “JvmDowngrader is MIT-licensed”. **JvmDowngrader is LGPL-2.1**
(`LICENSE.md` in `unimined/JvmDowngrader`). Shading the minimized stubs is fine only with
LGPL-2.1 compliance: keep the license text/attribution and allow relinking/decompilation.
Add a `THIRD_PARTY_NOTICES.md` covering:

| Component | License |
|---|---|
| JvmDowngrader stubs | LGPL-2.1 |
| JNA / jna-platform | LGPL-2.1 or Apache-2.0 |
| JXInput | MIT |
| hid4java | MIT |
| FuzziControls | MIT |

`shade` remains the right choice for a standalone mod; `gtnhlib` would add a GTNHLib
≥ 0.11.37 constraint, and lwjgl3ify 3.1.0 does not declare GTNHLib as a runtime dependency
in its module metadata. Keep `shade`.

**Done:** `THIRD_PARTY_NOTICES.md` now records the LGPL-2.1 and MIT components and is copied
into the shaded jar by `processResources`.

### `curseForgeEnvironments = client,server` — change to `client`

The mod is client-only (the README says so); `CommonProxy` only writes a config file.
CurseForge's environment tags describe where the mod can run/is supported, and
`client,server` will make server owners install a controller mod that does nothing.

```properties
curseForgeEnvironments = client
```

This is inert until `curseForgeProjectId` is filled in, but it is the correct tag when you
publish. If you add relations, use `requiredDependency:lwjgl3ify` (or `optionalDependency`
if the `after:` dependency stays soft) and `required-project:lwjgl3ify` on Modrinth.

### Other properties

- `minimizeShadowedDependencies = false` is correct for JNA/hid4java reflection; restore
  the explanation comment.
- `relocateShadowedDependencies = false` is correct for JNA: JNA locates its native
  `libjnidispatch` through its own package path. JXInput/hid4java could be relocated
  separately if duplicate-class conflicts appear.
- `usesShadowedDependencies = true`, `usesMixins = true`,
  `coreModClass = mixins.EarlyMixinsLoader`, `mixinsPackage = mixins` are consistent.
- `mixins.fuzzicontrols.json` must exist even though all current mixins are early mixins
  registered through `mixins.fuzzicontrols.early.json`; deleting it makes the mixin loader
  fail. Keep the file. A future late mixin under `com.mrfuzzihead.fuzzicontrols.mixins` will
  need its `package` and `mixins` entries added then.
- `.github/workflows/build-and-test.yml` — updated: the comment now describes the Java 17
  jvmDowngrader base. `client-only: true` remains correct because the base bytecode is 17, not 8.
- `README.md` installation — updated: it now requires lwjgl3ify and Java 17+.
- `@Mod(dependencies = "required-after:lwjgl3ify")` — done, together with
  `mcmod.info` `requiredMods`. The `Exception | LinkageError` fallbacks remain as defence in
  depth.

---

## 4. `dependencies.gradle`

| Dependency | Current | Latest / verdict | Recommended type |
|---|---|---|---|
| `com.github.GTNewHorizons:lwjgl3ify` | `3.1.0:dev` | **3.1.0 latest** (GTNH Maven, Nov 2026) | `implementation`/`api`; `api` publishes a transitive `:dev` dep even though no lwjgl3ify type appears in the public API. `compileOnly` + `devOnlyNonPublishable` is cleanest for a user-provided mod. |
| `com.xenoamess:JXInput` | `1.0.1` | Latest on Maven Central; 2020 republish of StrikerX3's abandoned **JNI** library (not JNA). | `shadowImplementation` to bundle. Consider replacing with lwjgl3ify's SDL gamepad API (removes a native JNI library; also gives Linux/macOS gamepad support). |
| `org.hid4java:hid4java` | `0.8.0` | **0.8.0 latest** (Apr 2024) | `shadowImplementation` correct; only needs JNA core. |
| `net.java.dev.jna:jna` | `5.19.1` | **Updated** (Jun 2026) | Verified hid4java 0.8.0 works with 5.19.1; the 500 ms enumeration was hidapi, not JNA. |
| `net.java.dev.jna:jna-platform` | removed | **Done** | Nothing imports `com.sun.jna.platform`; hid4java uses only `com.sun.jna` core. |
| `junit:junit` | `4.13.2` | Latest JUnit 4 | `testImplementation` correct. |

Notes:

- `shadowImplementation(...) { transitive = false }` on `hid4java` is safe: its only runtime
  dependency is JNA, which is explicit. Its POM uses `[5.8.0,)`, satisfied by 5.19.1.
- Do not add `jna-platform` back unless Win32 COM/shell helpers are actually used.
- JXInput/hid4java MIT notices are easy to lose when shading; `THIRD_PARTY_NOTICES.md` now
  records them and is shipped inside the jar.
- The jar still ships all JNA natives (all platforms). That is needed for cross-platform
  DualSense support; do not strip them without checking.

---

## 5. Functional bugs

### P1 status (this iteration)

All P1 items are fixed and covered where a headless test can reach them:

- [x] **Pick block** — now edge-triggered with `KeyBinding.onTick`, like the inventory key.
- [x] **`handleBButton`** — only `DROP_ITEM` can drop; `CLOSE_GUI` is no longer a drop trigger.
- [x] **`DropTapHold.blockNextRelease()`** — survives the first `update(true)`; regression test added.
- [x] **Digital `MOVE_*` bindings** — `StickHysteresis.isActive(...)` falls back to
  `ControllerMapping.isActive`; tests added.
- [x] **DualSense unplug / double-open** — fixed in P0 (filtered handle, `read < 0` disconnects).
- [x] **`LinkageError` in the keyboard path** — `KeyboardHelper` catches it once and runs without the
  shift patch; `GuiKeyHelper`/`GuiElementActivator` hardened; test added.
- [x] **Keyboard sneak takeover** — no longer clears the key the keyboard is physically holding;
  disconnect cleanup also leaves keyboard-held keys alone.

### P2 status (this iteration)

All P2 items are fixed:

- [x] **`handleKeyboardInput()` duplicate event** — removed; `GuiKeyHelper.injectKey` calls
  `keyTyped` directly.
- [x] **Stale in-world edge state across GUIs** — `ControllerTickHandler.syncInWorldEdges(...)` runs
  every GUI tick; `ControllerTickHandlerTest` pins the behaviour.
- [x] **`GuiMouseHelper` flag restore** — `directMouseClicked` restores the exact previous
  `sdlMouseButtonFlags` in a `finally`; a failed `addButtonEvent` restores the flags before the
  direct-dispatch fallback, so the button cannot be left logically down.
- [x] **`Config.parseButton`** — trims whitespace and uses `Locale.ROOT`; `ConfigReloadTest` covers
  a padded binding name.
- [x] **`ControllerState`** — shared immutable `EMPTY`; redundant accessor/comments removed.
- [x] **`CommandReloadControllers`** — literal `ChatComponentText` messages instead of using English
  strings as translation keys.
- [x] **`MixinSelfTest`** — no longer logs "mixin not applied?" when `analogMovement=false` is
  intentional.
- [x] **Dead fields / stale Javadoc** — `dropHeldTicks`, `dropHoldFired`, `dropBlockedByGui`,
  `BASE_DEGREES_PER_SECOND`, `controllerHeldKeys`, `DROP_HOLD_TICKS` references removed or fixed.

The remaining items are cleanup/optimization in section 6, not P2 correctness bugs.

### P1 — Pick block never fires — FIXED

`ControllerTickHandler.java:489` uses `driveKey(keyBindPickBlock, ...)`, which sets
`KeyBinding.pressed`. Vanilla 1.7.10 uses `keyBindPickBlock.isPressed()`, which reads
`pressTime`; only `KeyBinding.onTick()` increments it. Result: **X/Square never
pick-blocks.** Use the existing edge helper:

```java
handleEdgeTrigger(ControllerAction.PICK_BLOCK, state, mapping, mc.gameSettings.keyBindPickBlock);
```

(Verified in the 1.7.10 bytecode: `Minecraft` calls `KeyBinding.isPressed()` for pick
block, and `setKeyBindState` only writes `pressed`.)

### P1 — `handleBButton` treats `CLOSE_GUI` as a drop trigger — FIXED

`ControllerTickHandler.java:732`:

```java
boolean active = (mapping.isActive(dropAction, ...) ...)
              || (mapping.isActive(closeAction, ...) ...);
```

With the defaults both actions are B, so it is invisible. If a user rebinds `CLOSE_GUI`,
pressing that button in-world drops an item. In the no-GUI path only `DROP_ITEM` should
matter:

```java
boolean active = mapping.isActive(dropAction, state, Config.triggerThreshold)
    && !isActionBlocked(dropAction, mapping);
```

### P1 — `DropTapHold.blockNextRelease()` is cleared by the next press — FIXED

`DropTapHold.update()`:

```java
if (heldTicks == 0) {
    heldTicks = 1;
    holdFired = false;
    blocked = false;   // <-- loses blockNextRelease()
    return NONE;
}
if (blocked) return NONE;
```

The GUI path calls `blockNextRelease()` **without** first calling `update(true)` (the GUI
path returns before the in-world drop handling), so the next in-world tick resets `blocked`
and starts a fresh press. `buttonsHeldOnGuiClose` usually masks this, but the state machine
is wrong and the tests only cover a press that was already in progress. Move
`if (blocked) return NONE;` above `heldTicks == 0` and add this test:

```java
drop.blockNextRelease();
assertEquals(NONE, drop.update(true, true));
for (int i = 0; i < HOLD_TICKS * 2; i++)
    assertEquals(NONE, drop.update(true, true));
assertEquals(NONE, drop.update(false, true));
```

### P1 — Movement does not work if `MOVE_*` is rebound to a digital button — FIXED

`withHysteresis()` returns `false` for any non-axis binding
(ControllerTickHandler.java:117). All actions are freely remappable, so binding
`MOVE_FORWARD` to `DPAD_UP` or `A` silently disables movement:

```java
ControllerButton btn = mapping.getButton(action);
if (btn == null) return false;
if (!btn.isAxis()) return mapping.isActive(action, state, Config.triggerThreshold);
return stickHysteresis.update(action, StickHysteresis.axisValue(btn, state));
```

### P1 — DualSense unplug leaves `isConnected()` true forever — FIXED

`DualSenseDriver.poll()` treats `read == -1` as “no new report”. `isConnected()` still
returns `device.isOpen()` (hid4java only checks the non-null handle), so the manager never
reconnects and returns the last button state forever — **stuck keys after unplug**. Treat
`read < 0` as a disconnect (close the handle, return empty). Also validate the report ID
(`data[0] == 0x01` for USB) before parsing.

### P1 — `HidServices.getHidDevice()` + constructor opens the device twice — FIXED

`HidServices.getHidDevice()` already calls `device.open()` (and ignores the result).
`DualSenseDriver` calls `device.open()` again. The first handle leaks; if the second open
fails (possible on Windows with exclusive access), the controller is reported absent while
the first handle stays open. Remove the constructor's `open()` (or use `HidApi.open`, which
has neither problem).

### P1 — `LinkageError` can escape the DualSense and keyboard paths — FIXED

The changelog says XInput's `UnsatisfiedLinkError` was fixed, but
`DualSenseDriver.getHidServices()`, its constructor, `poll()`, and
`KeyboardHelper.withKeyHeld()/isPhysicallyDown()` catch only `Exception`. JNA/hidapi load
failures and missing `org.lwjglx` classes are `Error`s, so they escape into the client
tick/render loop. Mirror the XInput fix:

```java
catch (Exception | LinkageError e) { ... }
```

At minimum, wrap `HidApi.init/open/read/close` and all `KeyboardHelper` uses.

### P1 — Keyboard sneak takeover cancels the key you are holding — FIXED

`ControllerTickHandler.java:646`:

```java
sneakToggled = false;
heldKeys.release(sneakKeyCode);
KeyBinding.setKeyBindState(sneakKeyCode, false); // keyboard is physically holding it
```

`driveKey(..., false)` then leaves the key alone (claim released), so sneak stops until the
key is released and pressed again. Remove the `setKeyBindState(false)`; the keyboard already
set it true. Similarly, `releaseAllControllerKeys()` (line 584) clears every claimed key even
if the keyboard holds it; check `Keyboard.isKeyDown(code)` before clearing.

### P2 — `handleKeyboardInput()` re-dispatches the last key event — FIXED

The B-button GUI close does:

```java
mc.currentScreen.handleKeyboardInput();
GuiKeyHelper.injectKey(mc.currentScreen, Keyboard.KEY_ESCAPE, '\0');
```

In 1.7.10 `GuiScreen.handleKeyboardInput()` calls
`keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey())` if the current (possibly
stale) LWJGL event is a key-down. It can re-deliver the last typed character (duplicate
input in chat/text fields). `GuiKeyHelper.injectKey()` already calls `keyTyped` with
Escape; remove the `handleKeyboardInput()` call.

### P2 — Stale in-world edge state across GUIs — FIXED

The GUI path returns without advancing `wasActive` for `ATTACK`, `HOTBAR_*`, `INVENTORY`,
etc. If a button was active before a GUI opened, released inside the GUI, and pressed again
after closing, `wasActive` can still be true and the first press (including the attack
`onTick` one-shot) is swallowed. Add `syncInWorldEdges(state, mapping)` (updating only the
in-world actions, without resetting GUI state like `syncEdges` does) before the GUI path
returns.

### P2 — Misc correctness — FIXED

- `ControllerManager.pollFresh()` warns per frame on a persistent poll error; rate-limit or
  close the driver.
- `GuiMouseHelper.directMouseClicked()` sets `sdlMouseButtonFlags` outside a `try/finally`;
  a throwing screen leaves the mouse button logically down. Restore in `finally` (same for
  `injectLwjgl3ify` if `addButtonEvent` throws).
- `ControllerManager.selectDriver`'s `quiet` parameter is unused; remove it.
- `ControllerManager`/`safeClose`/`pollFresh` catch `Exception` only; add `LinkageError`.
- `Config.parseButton` should `trim()` and use `Locale.ROOT`; `controllerDriver` should trim
  and validate. A typo currently means “no driver” plus endless retries.
- `ControllerState` allocates a new empty state per call and the record copies the
  `EnumSet` again; use a static `EMPTY` and avoid the double copy.
- `DualSenseDriver.poll` allocates `new byte[64]` every render frame; make it a field.
- `CommandReloadControllers` uses `ChatComponentTranslation` with literal English strings;
  use `ChatComponentText` + `EnumChatFormatting`.
- `MixinSelfTest` logs “mixin not applied?” even when `analogMovement=false` is intentional.
- `DualSenseDriver.isConnected()` uses the deprecated `isOpen()` (the build's only
  deprecation warning); use `!isClosed()` or the handle-based check.

---

## 6. Dead code and refactor opportunities

### Cleanup status (this iteration)

- [x] Removed `dropHeldTicks`, `dropHoldFired`, `dropBlockedByGui`, `BASE_DEGREES_PER_SECOND`.
- [x] Fixed stale Javadoc (`controllerHeldKeys`, `DROP_HOLD_TICKS`, `dropBlockedByGui`).
- [x] `ControllerState` shared `EMPTY`; redundant accessor/comments removed. The defensive
  `EnumSet` copy is retained intentionally for immutability.
- [x] Removed the never-handled `LOOK_UP/DOWN/LEFT/RIGHT` and `DPAD_UP/DOWN/LEFT/RIGHT` actions.
  Old config files keep those keys, but they are no longer read or written.
- [x] Removed `ControllerButton.GUIDE` and its `XInputReportMapper` line; XInputGetState never
  reports the guide button, so it could never fire.
- [x] Removed the unused `triggerThreshold` parameter from `ControllerMapping.isActive` and
  `StickHysteresis.isActive`.
- [x] Avoided the double config load: `Config.isLoadedFrom(...)` lets `CommonProxy.preInit` skip a
  second parse when the early mixin loader already read the same file.
- [x] Replaced the sound-slider reflection with
  `GuiScreenOptionsSoundsButtonAccessors` (mixin accessor), which works in dev and production.
- [x] Consolidated the repeated one-shot handlers into `ControllerTickHandler.handleEdge(...)`;
  added a rising-edge test.
- [x] Precomputed geometry for buttons and inventory slots in `FocusableItem`, and
  `GuiFocusRenderer` now uses `GuiFocusNavigator.getFocusedItemBounds()` so its border cannot drift
  from the search/click geometry. List entries remain dynamic because the list can scroll.
  The per-tick button-state hash remains intentionally: button `enabled`/`visible` can change
  without `initGui` (e.g. selecting a world), and it is an O(buttons) check at 20 Hz.


- **Dead fields in `ControllerTickHandler` — removed**: `dropHeldTicks`, `dropHoldFired`,
  `dropBlockedByGui`, `BASE_DEGREES_PER_SECOND`.
- **Dead actions — removed**: `LOOK_*` and the `DPAD_*` *actions* are gone; the D-pad
  *buttons* remain for `GUI_NAV_*`. Existing configs keep the stale keys but they are ignored.
- **`ControllerButton.GUIDE` — removed**: XInputGetState never reports the guide button (it needs
  `XInputGetKeystroke`), and the DualSense parser never set it.
- **`ControllerMapping.isActive(..., triggerThreshold)` — removed**: drivers pre-threshold
  triggers/sticks, so the parameter was dead.
- **Stale Javadoc — fixed**: `DROP_HOLD_TICKS`, `controllerHeldKeys`, and `dropBlockedByGui`
  references were corrected.
- **`ControllerManager` Javadoc — fixed in P0**: it now documents the filtered HID probe and the
  measured ~2 ms idle cost instead of the old “imperceptible” claim.
- **`GuiFocusNavigator.adjustSlider` — done**: `GuiScreenOptionsSoundsButtonAccessors` is an
  accessor mixin for the private sound-slider class, so the fields are remapped in dev and
  production and no reflection runs per press.
- **`GuiFocusNavigator`/`GuiFocusRenderer` — geometry done**: button/inventory-slot geometry is
  cached at rebuild time and the renderer uses the navigator's bounds. The button-state hash is
  retained because enable/visible changes can happen without `initGui`; it is cheap at 20 Hz.
- **`ControllerTickHandler` — done**: the repeated one-shot handlers now delegate to
  `handleEdge(action, state, mapping, Runnable)`; `handleEdgeTrigger` uses the same helper.
  A rising-edge unit test covers it.
- **`Config` double load — fixed**: `Config.isLoadedFrom(...)` lets `CommonProxy.preInit` skip the
  second parse when the early mixin loader already read the same file.
- **`HidManager` vs `new HidServices` — moot**: P0 removed `HidServices`; the driver uses a raw
  `HidApi.open/read/close` handle.
- [x] **B11 — XInput was polled multiple times per frame — fixed.** `XInputDriver.isConnected()`
  now returns the connection flag cached by `poll()` instead of calling `device.poll()` again.
  The manager's tick therefore performs one native XInput poll, and `pollFresh()` remains the
  intentional per-frame refresh. `XInputDriverTest` pins that a connection check never polls.

---

## 7. Features worth adding

- **`driver = none`** — done (see P0): users without a controller pay zero cost. The settings
  screen also exposes a master `enabled` switch, while `driver = none` remains supported for
  existing configs.
- **Bluetooth DualSense support** — done (this iteration). The driver now parses the USB
  `0x01` (64-byte) report, the Bluetooth simple `0x01` (10-byte) report and the Bluetooth
  enhanced `0x31` (78-byte, CRC32 seed `0xA1`) report. Reading feature report `0x05` on
  connect requests the enhanced report; if the controller stays in simple mode, sticks,
  buttons and triggers still work. DualSense Edge PID `0x0DF2` is supported alongside
  `0x0CE6`. Bluetooth handles that stop sending reports are treated as disconnected after
  2 seconds. The report layouts live in `DualSenseReportParser` and the HID lifecycle in
  `DualSenseTransport`/`HidApiDualSenseTransport`, both covered by unit tests. Remaining:
  hardware validation on Windows/Linux/macOS Bluetooth and a USB-vs-Bluetooth preference when
  both are connected.
- **DualSense Edge paddles and Fn buttons** — done (this iteration). The Edge's left/right rear
  paddles and front Fn1/Fn2 buttons are decoded from bits 4-7 of the third button byte in all
  three report layouts. They are exposed as `LEFT_PADDLE`, `RIGHT_PADDLE`, `FUNCTION_1` and
  `FUNCTION_2`, appear in the Bindings capture UI, and are unbound by default. Standard
  DualSense controllers ignore those reserved bits.
- **SDL gamepad input via lwjgl3ify** instead of JXInput + hid4java: removes one JNI
  library (JXInput) and gives Linux/macOS gamepad support. lwjgl3ify 3.x uses SDL3, not GLFW;
  `SDL_INIT_GAMEPAD` is initialised, but there is no gamepad wrapper in lwjgl3ify's public API,
  so this needs a compile-only `lwjgl-sdl` dependency and an SDL gamepad driver. hid4java
  remains for DualSense-specific features. (Also carried over from the previous plan, section 9.)
- **Rumble/vibration** — done (this iteration). XInput uses `XInputDevice.setVibration`;
  DualSense uses a 48-byte standard / 64-byte Edge USB `0x02` or 78-byte Bluetooth `0x31` output report, with the
  Bluetooth sequence/tag and output CRC32 seed `0xA2`. `DualSenseOutputReport` builds and signs
  the reports, and `ControllerManager` schedules short pulses for damage, entity attacks and
  mining. Values are de-duplicated, refreshed periodically, scaled by `rumbleIntensity`, and
  stopped on disable, disconnect and shutdown. The settings screen exposes the on/off switch and
  strength slider. Remaining: hardware validation on XInput and DualSense over USB/Bluetooth.
- **DualSense feature page** — done (this iteration). A DualSense settings tab appears when
  controller input is enabled and a DualSense is active over USB or Bluetooth. It exposes
  adaptive-trigger on/off, mining and bow effect presets and strengths, RGB lightbar on/off and
  color sliders, brightness, and the low-battery toast toggle. Adaptive triggers use 11-byte
  effect blocks at payload offsets 10/21 gated by valid-flag bits `0x04`/`0x08`; the lightbar uses
  payload bytes 44–46 and the brightness byte 42. Bow drawing and block mining drive the effects
  from the client tick, and trigger effects are cleared in GUIs, on disconnect and on shutdown.
  Battery status is decoded from the input report and a one-shot toast fires below the threshold.
- **Controller-type-aware button glyphs** (Xbox vs PlayStation) for the remap screen. The screen
  currently uses text labels; this is the next visual-polish step.
- **Per-device DualSense calibration** (Sony ships calibration data; raw 0–255 is
  approximate).
- **Controller connected/disconnected toast** — done (this iteration). A self-contained modern
  toast queue renders a 160×32 panel in the top-right corner, with a 600 ms slide-in, five-second
  hold and 600 ms slide-out; up to five toasts stack and the rest queue. Toasts show the device name
  and `USB`/`Bluetooth` transport, use a green/red state title and a DualSense/Xbox/generic sprite,
  are emitted from `ControllerManager.ConnectionListener` after the first valid input report (so
  the transport is known and a stale opened handle cannot produce a phantom connect/disconnect
  pair), and are suppressed for a same-device `/fuzzicontrols reload`. Disable with
  `showControllerToasts = false` in the `[controller]` section. GTNHLib and Et Futurum Requiem do
  not provide a reusable toast API, so the layout is implemented locally from the 1.12 `GuiToast`
  behaviour.
- **Analogue movement/camera feel** — done (this iteration). Vanilla `moveFlying()` normalises
  the input vector, so analogue values previously affected direction only. `MixinEntity_MoveFlying`
  now scales movement friction by the stick's radial magnitude, preserving the true stick angle
  (half tilt = quarter speed) while `MixinEntityPlayerSP` keeps keyboard per-axis priority and
  applies sneak/use-item factors once. The camera uses a 25% linear / 75% cubic response curve and
  advances `prevRotation*` with the same per-frame delta as vanilla `setAngles()` for smooth
  interpolation. The look-sensitivity, dead-zone and trigger-threshold sliders are now exposed in the settings screen.
- **In-game settings/remap screen** — done (this iteration). `GuiFuzziControls` is reachable from
  Options and Forge's mod list, has Controller/Bindings/Input/Interface tabs, and is fully
  D-pad/A/B navigable. It exposes the master `enabled` switch, driver/slot/toast/status/rescan
  controls, every gameplay/input/interface setting, live tap-to-capture remapping with duplicate
  warnings, and four controller-button → keyboard-key shortcut slots for modded `KeyBinding`s.
  Capture suspends normal GUI input, so B can be bound without closing the screen; a one-second hold
  cancels. `Config.save()` now persists settings as well as bindings.

---

## 8. Tests, CI, docs

### Implemented test infrastructure (this iteration)

- **JUnit 4.13.2 + Mockito 5.24.0** (`mockito-core`, with Byte Buddy 1.18.17 forced so the inline
  mock maker understands Java 25 class files). `ControllerManagerMockitoTest` pins the
  select/poll/close interaction contract; the existing hand-written fakes remain the detailed
  behaviour spec.
- **JaCoCo 0.8.16** in `addon.gradle.kts`: `jacocoTestReport` (HTML/XML) and
  `jacocoTestCoverageVerification` (52% line / 52% branch floor). `check` depends on the
  verification. The report analyses the **downgraded** main classes the test JVM actually loads
  (`build/tmp/downgradeMainClasses/main`), not `build/classes/java/main`, and excludes
  `META-INF/versions/**` so JaCoCo does not see duplicate class names. The `gui/**` package is
  excluded from the headless coverage number because it is a live `GuiScreen`; `ClientSelfTest`
  covers open/tab/rebind-capture/slider focus in a real client. Current baseline:
  **61.4% line / 58.0% branch**.
- **`gameTest` source set** with a never-shipped `@Mod` (`src/gameTest/java`), a common/server
  checker and a client checker. `./gradlew gameTestServer` runs
  `runServer25` with the test mod on the classpath; `./gradlew gameTestClient` does the same for
  `runClient25`. Both exit the JVM with the assertion result and write
  `run/<side>/gametest-report.txt` (`ok`/`FAIL`, `SUMMARY <checks> <failures>`,
  `FUZZICONTROLS_GAMETEST_OK`/`_FAILED`).
  - Server: config load, config ranges, default bindings (10 checks).
  - Client: all mixins applied, config/default bindings, a no-controller `ControllerManager.tick()`
    (elapsed time logged for the stutter fix), and live `GuiFocusNavigator` enumeration/focus on the
    real main menu (24 checks).
- **CI**: a dedicated `game-test-server-java25` job asserts the server report; the existing
  `client-smoke-test-java25` job runs `gameTestClient` headless after the client self-test and
  asserts the client report. Both upload logs/reports.
- **Additional coverage in the cleanup batch**: focus-aware probe cadence, shared
  `ControllerState.EMPTY`, `Config.isLoadedFrom`, padded binding names/`NONE`, the no-lwjgl3ify
  fallback paths for mouse/key injection, cached GUI bounds, and the XInput polled-connection
  cache (`isConnected()` must not call `device.poll()`).

- Missing tests — now covered:
  - [x] `DropTapHold.blockNextRelease()` **before** any `update(true)`:
    `DropTapHoldTest.blockBeforeTheFirstPressStillBlocksTheWholeHold`.
  - [x] `withHysteresis` with a digital binding:
    `StickHysteresisTest.digitalBindingFallsBackToTheNormalMapping`.
  - [x] `handleBButton` with `DROP_ITEM` and `CLOSE_GUI` on different buttons; the active-state
    calculation is now `ControllerTickHandler.isDropActive(...)` and covers both the split and
    swapped bindings.
  - [x] `releaseAllControllerKeys` does not clear a key the keyboard holds:
    `releaseUnheldControllerKeys(...)` is unit tested with a keyboard-held key.
  - [x] Extract a `DualSenseTransport` (`open/read/close`) from `DualSenseDriver` and test
    `read == -1` → disconnect, report-ID filtering, and the no-report cache path.
    `DualSenseDriverTest` covers the transport lifecycle, read errors, stale handles and the
    no-report cache; `DualSenseReportParserTest` covers USB, Bluetooth simple, Bluetooth
    enhanced, layout detection and the Bluetooth CRC.
- The earlier idea of a `ProbeExecutor`/worker test is obsolete: the filtered `HidApi.open`
  probe costs ~2 ms and is intentionally run on the client tick at the configured cadence.
  Existing tests pin the 1 s focused and 30 s unfocused intervals, and that `pollFresh()` never
  creates a driver.
- [x] `client-smoke-test-java25` now asserts that no `hid4java device scanner` thread appears
  and that `DualSense not found` is not logged repeatedly. The client game-test job asserts the
  same for its log.
- `README.md` installation — done: requires lwjgl3ify and Java 17+.
- `BUTTON_BINDINGS.md` — aligned with the implementation (`HOTBAR_NEXT` is RB, sprint is held,
  Y opens the inventory in-world, and B closes an open screen).
- `.github/workflows/build-and-test.yml` — stale `modern` comment updated. Pinning the shared
  workflow to a tag/commit remains a CI-maintenance choice, not a blocker.
- `jitpack.yml` — works as-is; no JDK override is needed.

---

## 9. Open items carried over from the previous plan

These were not completed by the migration and remain valid:

- **Manual validation checklist** (still unchecked):
  - Xbox controller: buttons, sticks, triggers, hot-plug.
  - DualSense: face buttons, sticks, triggers, D-pad, share/options; USB unplug/replug.
  - DualSense Bluetooth: pair in the OS, connect while the game is running, verify sticks,
    buttons and triggers in both simple and enhanced modes, then turn the controller off and
    verify it reconnects without stuck keys (2 s stale-handle timeout).
  - DualSense Edge (PID `0x0DF2`): standard buttons/sticks/triggers over USB and Bluetooth,
    plus left/right paddles and Fn1/Fn2; verify each can be bound and released without sticking.
  - GUI navigation: main menu, inventory, pause, chest, `GuiSlot` world/server lists.
  - Shift-click (LT + A/X), scroll, D-pad focus, sliders.
  - Focus loss/refocus; controller disconnect/reconnect.
  - Cursor speed at multiple window sizes (854×480 reference scaling).
  - **New:** no periodic hitch with no controller connected; no recurring
    `DualSense not found` lines; no `hid4java device scanner` thread.
  - Rumble: damage, entity attack and block mining on XInput and DualSense, USB and Bluetooth;
    verify the strength slider, the on/off switch, and that vibration stops on disconnect.
  - DualSense tab: confirm it appears over USB and Bluetooth, adaptive triggers fire for bow/mining,
    each effect preset feels correct, the lightbar color/brightness updates, and the low-battery
    toast appears once below the threshold.
- **SDL gamepad backend** (section 7): still the preferred long-term replacement for
  JXInput; keep `ControllerState`/`ControllerMapping` semantics and add normalization parity
  tests. lwjgl3ify 3.x uses SDL3, so target `org.lwjgl.sdl.SDLGamepad`, not GLFW.
- **GTNHLib evaluation** — settled. The settings GUI is implemented locally because
  `SimpleGuiConfig` is only a thin `GuiConfig` wrapper and does not provide remap capture, controller
  status, driver switching or keyboard shortcuts. Adding GTNHLib would also add a hard runtime
  dependency and a version floor that lwjgl3ify does not pull in. Do not add it.
- **DualSense output-report handshake**: the device may need a best-effort output report
  (e.g. `0x02` lightbar/motor) on open to stream at full rate. Still worth testing if
  latency feels high.
- **Right-stick Y inversion**: previously checked as correct; re-check on the final build if
  a controller reports inversion. Fix at the driver (negate Y) if movement is also
  inverted, or in `applyLook` if only the camera is.
- **Shaded-jar verification (old B13)**: current values are
  `minimizeShadowedDependencies=false`, `relocateShadowedDependencies=false` (the old plan
  text said `true`). Verified in the final jar: JNA 5.19.1 classes and all native bundles are
  present and unrelocated, hid4java/JXInput are unrelocated, `jna-platform` is absent, and
  `THIRD_PARTY_NOTICES.md` plus the JNA license are inside the jar. `BUGS.md` now records that
  `minimizeShadowedDependencies = false` is intentional because JNA/hid4java resolve classes
  reflectively.

---

## 10. Suggested order of work

1. **P0 stutter — done**: `DualSenseDriver` uses `HidApi.open/read(..., 0)/close`, the
   `HidServices` scanner is gone, and `ClientProxy` calls `shutdownHid()`.
2. **Retry hygiene — done**: `driver = none`, unknown-driver disable, DEBUG retries, and
   focus-aware 1 s/30 s hot-plug probing with an immediate focus-gain probe.
3. **Small high-value bugs — done**: pick block, drop-button remapping,
   `DropTapHold.blockNextRelease`, digital `MOVE_*` bindings.
4. **DualSense lifecycle — done in P0**: report-ID check, `read < 0` → disconnect, no double open,
   `LinkageError` guards.
5. **Keyboard interaction — done**: keyboard-held keys preserved on takeover/disconnect,
   `LinkageError` guarded, and the P2 `handleKeyboardInput` duplicate removed.
6. **P2 correctness — done**: GUI-open edge sync, mouse-flag restore, config parsing, literal chat
   messages, `MixinSelfTest` noise, dead fields/Javadoc.
7. **Hot-plug responsiveness — done**: focus-aware 1 s/30 s probing and an immediate probe on focus gain; see 2.8.
8. **Dependencies**: upgrade JNA to 5.19.1, fix the JXInput comment, add
   `THIRD_PARTY_NOTICES.md` (`jna-platform` is already removed).
9. **Config/docs/metadata — partly done**: `required-after:lwjgl3ify`, README install, and
   `mcmod.info` are done; restoring the accurate `gradle.properties` comments (including the
   LGPL-2.1 correction) remains.
10. **Cleanup/refactor — done**: dead actions/`GUIDE`, the unused `isActive` threshold, the double
    config load, the sound-slider accessor, the edge-handler consolidation, and cached button/slot
    geometry. The per-tick button-state hash is retained deliberately (dynamic enabled/visible
    changes).
11. **Rumble — done**: XInput and DualSense output reports, client-side damage/attack/mining
    feedback, config/UI controls, and manager-level pulse scheduling with stop-on-disconnect.
    Manual hardware validation remains.

---

## 11. Definition of done

- [x] Idle (no controller) probing costs < ~20 ms per probe and never blocks the client
      thread for a frame; no hid4java scanner thread.
- [x] `HidApi` lifecycle has no leaked handles; unplug is detected within one tick.
- [x] `driver = none` is supported and documented.
- [x] Pick block, drop:one/drop:stack, movement rebinding, sneak takeover, and GUI close via
      B all behave correctly in-game (unit tests cover the parts that do not need a live client).
- [x] Focus-aware hot-plug detection: 1 s focused, 30 s unfocused, immediate on focus gain (see 2.8).
- [x] `curseForgeEnvironments = client`; `jna-platform` removed.
- [x] DualSense Edge paddles and Fn1/Fn2 are decoded on USB and both Bluetooth layouts and can
      be remapped; standard DualSense reserved bits are ignored.
- [x] JNA upgraded to 5.19.1 and third-party notices added and shipped in the jar.
- [x] P2 correctness fixes (keyboard input, GUI edge sync, mouse flags, config parsing, chat
      messages, self-test noise) with tests.
- [x] Removed dead actions/buttons, unused `isActive` threshold, and the double config load;
      `./gradlew build` (tests + checkstyle + spotless) green.
- [x] Section 6 cleanup complete: cached button/slot geometry plus the deliberate per-tick
      button-state hash remainder documented in section 6.
- [x] New unit tests from section 8 pass; the client self-test still reports
      `Mixin self-test: 9 checks, 0 failures`.
- [x] Rumble output reports are unit tested for USB/Bluetooth layout, motor offsets, sequence
      numbers and CRC, and the manager stops pulses on expiry, disable and disconnect.
- [x] Adaptive-trigger blocks, trigger/lightbar output reports, battery decoding and the
      one-shot low-battery policy are unit tested; the client self-test remains green.
- [ ] Manual validation checklist in section 9 completed.
