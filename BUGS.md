# Branch Analysis — `feature-InventoryNavigation`

Analysis of `feature-InventoryNavigation` vs `main` (read-only review, no code changes made).

## Fix status

| # | Bug | Status |
|---|---|---|
| 1 | Slider stepping no-op on coarse-step options | **FIXED** — `SliderStepper` + 17 unit tests |
| 2 | Right-stick scroll rate wrong / FPS-dependent | **FIXED** — `ScrollStepper` + 12 unit tests |
| 3 | `guiCursorY` origin inversion | **FIXED** (side effect of #6) |
| 4 | Slot "center" off by (8,8) | **FIXED** (cleanup batch) |
| 5 | Stale geometry after resize | **FIXED** — `ScreenLayoutTracker` + 9 tests (awaiting in-game check) |
| 6 | Nav self-deactivates after slot confirm | **FIXED** — `MouseMotionTracker` + 12 tests (awaiting in-game check) |
| 7 | Off-panel slots → held-stack drop hazard | **FIXED** — `isSlotFocusable` + 5 tests (awaiting in-game check) |
| 8 | List entries activated/drawn outside the viewport | **FIXED** — visibility + scroll-into-view + 8 tests (awaiting in-game check) |
| 9 | Right-click/shift-click ignored in nav mode | **FIXED** — `activateFocused` (awaiting in-game check) |
| 10 | lwjgl3ify `Error` vs `Exception` fallbacks | **FIXED** — `Exception \| LinkageError` + 1 test |
| 12 | `/fuzzicontrols reload` does not re-read the config | **FIXED** — `Config.reload()` + 4 tests |
| 11 | Deactivation thresholds disagree | **FIXED** (side effect of #6) |
| 13 | Activation race | **FIXED** (side effect of #6) |

### Fix log

**#1 — Slider stepping (fixed)**

* New `util/SliderStepper.java`: keeps `dpadSliderStep` (fraction of range) as the normal path,
  but when the requested step snaps back to the current value it walks forward on the option's own
  value grid, so every press moves by at least one grid step.
* `GuiFocusNavigator.adjustSlider` now delegates to it.
* New `test/util/SliderStepperTest.java` — 17 tests, including two regression tests
  (`mipmapLevels_eachPressMovesTheSlider`, `anisotropicFiltering_eachPressMovesTheSlider`)
  and a generic invariant over **every** float option
  (`everyFloatOption_movesInBothDirectionsFromMidRange`).
* Coverage proven: temporarily restoring the old formula fails **9** of the 17 tests, including the
  generic invariant — so this class of bug is now caught by unit tests rather than in-game play.
* Also fixed: `spotlessCheck` was failing on this branch (CI blocker) for `GuiContainerAccessors`,
  `GuiFocusNavigator`, `GuiFocusNavigatorTest` — formatting applied.

**#2 — Right-stick scroll rate (fixed)**

* New `util/ScrollStepper.java`: the quadratic response is applied to the *deflection* (dimensionless
  gain) and then multiplied by the frame delta, so the delivered rate is `scrollSensitivity`
  notches/second at any frame rate. Previously the per-frame delta was squared, giving
  `sensitivity² / fps` (~1.67 notches/s at 60 fps = one notch every 0.6 s — the reported jank).
* `ControllerTickHandler.onRenderTick` now delegates to it and skips injection entirely when the
  computed delta is zero.
* New `test/util/ScrollStepperTest.java` — 12 tests, including `rateIsFrameRateIndependent`
  (20/30/60/75/144/240 fps must all deliver exactly `scrollSensitivity` notches/s),
  `halfDeflectionGivesAQuarterOfTheRate`, `smallDeflectionIsPrecise` and
  `deltaIsProportionalToElapsedTime` (a 100 ms hitch equals six 16.6 ms frames).
* Coverage proven: temporarily restoring the old formula fails **7** of the 12 tests.

**#5 — Stale geometry after resize (fixed, awaiting in-game check)**

* New `util/ScreenLayoutTracker.java`: a monotonic layout generation, bumped by
  `MixinGuiScreen_SlotTracker` at the tail of every `setWorldAndResolution`. That method is what
  `Minecraft.resize()` calls (`Minecraft.java:1633-1639`), and both resize paths go through it —
  F11 fullscreen (`toggleFullscreen` → `resize`, `Minecraft.java:1608`) and window drag-resize
  (`Display.wasResized()` → `resize`, `Minecraft.java:1146-1165`).
* `GuiFocusNavigator.update()` now rebuilds when the layout generation changed, or when the
  container slot count / `GuiSlot` entry count changed, in addition to the existing button hash.
  Previously a re-init left the cached `guiLeft`/`guiTop`, the cached `GuiSlot` instance and the
  cached `Slot` objects pointing at the pre-resize layout, because the button list (and its hash)
  is rebuilt identically — so the highlight drifted off the panel and A clicked whatever slot sat
  at the stale offset.
* Focus is now clamped rather than reset on a layout rebuild, so a resize no longer throws the
  player back to the first slot.
* Tests: 9 new (3 in `ScreenLayoutTrackerTest`, 6 in `GuiFocusNavigatorTest`) covering the pure
  change-detection logic (`layoutChanged`), the rebuild wiring (`update`), index clamping, and
  idempotence. `getScreenButtonList(null)`/`buttonStateHash(null)` are now null-safe so the
  change-detection path can be tested without instantiating a `GuiScreen` (its static initialiser
  needs a live `Minecraft`, which the unit test JVM does not have).
* Coverage proven: ignoring the generation in `layoutChanged(GuiScreen)` (the old behaviour) fails
  `update_rebuildsWhenScreenIsReinitialised`. Note the pure `layoutChanged(...)` test alone would
  *not* catch that — the wiring test is what pins the behaviour.

**#6 — D-pad navigation deactivating itself (fixed, awaiting in-game check)**

* Root cause: the platform emits a real mouse-motion event when this mod warps the cursor
  (`Mouse.setCursorPosition`), and the deactivation check was a plain absolute-position comparison,
  so the mod's own warp looked like the player grabbing the mouse. lwjgl3ify has
  `ignoreNextMove`/`ignoreNextDelta` for exactly this, but `setCursorPosition` never sets them
  (verified dead code), so the mod has to track its own warps.
* New `util/MouseMotionTracker.java`: is told which positions the mod commanded, ignores the frames
  in which that warp is still in flight, tolerates the framebuffer↔screen rounding (±2 px), and
  does not report the first observation as movement. Warps to the position the cursor already
  occupies do not re-arm the settle window, so a held-still virtual cursor cannot permanently
  suppress real mouse input.
* `ControllerTickHandler` now routes the check through the tracker, and `applyGuiCursor` reports
  every warp via `warpCursor(...)`.
* The D-pad slot confirm no longer warps the OS cursor at all: the synthetic click already passes
  explicit coordinates to `GuiContainer.getSlotAtPosition()`, and the focus border marks the slot.
  That also removes the `guiCursorX`/`guiCursorY` desync (#3) and the off-centre cursor placement
  (#4's cursor aspect).
* Side effects: #11 (sub-threshold stick drift used to warp the cursor and deactivate nav despite
  the 0.15 threshold) and #13 (the first render frame always counted as mouse movement) are fixed
  by the same mechanism.
* Tests: 12 new in `MouseMotionTrackerTest`. Coverage proven: restoring the raw position comparison
  fails 9 of them.

**#7 — Off-panel slots could drop the held stack (fixed, awaiting in-game check)**

* `GuiContainer.mouseClicked` treats any click outside the panel as "outside the container"
  (slot id -999, click type 4) which makes the container drop the carried stack into the world.
  The navigator enumerated *every* `Container.inventorySlots` entry, including slots that modded
  containers park outside the drawn panel or mark non-hoverable.
* New `GuiFocusNavigator.isSlotFocusable(slot, xSize, ySize)`: requires `Slot.func_111238_b()`
  (the screen's own "can this slot be interacted with" flag, e.g. horse armour before saddling)
  and requires the slot's click point `(guiLeft + x, guiTop + y)` to lie inside the panel, which is
  the exact condition vanilla uses. Uses the previously dead `getXSize()`/`getYSize()` accessors.
* Tests: 5 new in `GuiFocusNavigatorTest` (inside/outside edges, hidden modded coordinates,
  non-hoverable slot, null slot). Coverage proven: disabling the filter fails all but one.

**#8 — list entries outside the viewport (fixed, awaiting in-game check)**

* `GuiSlot.drawScreen` clips entries with a scissor rectangle, but the focus list contained every
  entry. So the border was painted outside the list (over unrelated UI) and A activated an entry the
  player could not see — in the world-select list that means loading an unseen world.
* New pure geometry in `GuiFocusNavigator`: `isEntryVisible(entryTop, entryHeight, viewportHeight,
  scroll)` and `scrollToShow(...)`, plus adapters that read the `GuiSlot` layout.
* Focus movement now goes through `setFocus(...)`, which scrolls the newly focused entry into view,
  so D-pad navigation through a long list follows the highlight instead of walking off the bottom.
  The scroll value is clamped by `GuiSlot.bindAmountScrolled()` during the next draw, so an
  out-of-range target is safe.
* `confirmSlotEntry()` refuses to activate an entry that is outside the viewport (e.g. after the
  player scrolled it away with the right stick); it scrolls it back into view instead.
* `GuiFocusRenderer` skips drawing the border for entries outside the viewport.
* Tests: 8 new, including a round-trip property test over 40 entries × 4 scroll offsets asserting
  that `scrollToShow` always leaves the entry visible.

**#9 — right-click and shift-click in nav mode (fixed, awaiting in-game check)**

* Right-click (X) was not handled by the navigation path at all, so while D-pad navigation was active
  it injected a right click wherever the virtual cursor happened to be — a different slot, or
  outside the panel. Shift-click (LT toggle) was also ignored by the confirm path, so whole-stack
  moves were unavailable.
* The confirm path is now a button-agnostic `activateFocused(int mouseButton)` used by both A (left)
  and X (right): it dispatches a coherent press + release on the focused element, honours the
  shift toggle (holding the key for the whole pair, with the release dispatched directly rather than
  through the event queue, which would use the OS cursor position), and refuses to right-click a
  focused button or slider (vanilla's `GuiScreen.mouseClicked` ignores the button index when
  scanning `buttonList`, so a right click would press the button).
* While an element is focused, both mouse buttons are suppressed for **both** edges, so no stray
  mouse-up is injected at the virtual cursor position while a stack may be on the cursor.
* No unit test: this is tick-handler wiring over Minecraft's mouse dispatch. The pure part is
  unchanged; the dispatch itself needs in-game validation.

**#10 — lwjgl3ify linkage failures escaped into the game loop (fixed)**

* The mod declares only a soft ordering dependency on lwjgl3ify (`after:lwjgl3ify`), but
  `org.lwjglx.input.Mouse` and its `sdlMouseButtonFlags` / `addButtonEvent` / `addWheelEvent`
  members are lwjgl3ify extensions. On an install without a usable lwjgl3ify they throw
  `LinkageError` (`NoClassDefFoundError`, `NoSuchMethodError`, …), which is an `Error` and was
  therefore **not** caught by the previous `catch (Exception)` — so the documented fallback could
  never engage and the error escaped into the client tick / render loop.
* `GuiMouseHelper` now catches `Exception | LinkageError`, remembers the failure so the failing path
  is not retried on every click, and logs one actionable warning. Wheel scrolling has no fallback
  (there is no other way to synthesise a scroll event) and is reported as disabled.
* Test: `GuiMouseHelperLinkageTest` runs in a JVM with no usable lwjgl3ify, which is exactly the
  failing environment. Coverage proven: with the old `catch (Exception)` the test fails with
  `NoClassDefFoundError`.

---

## Cleanup batch

**#4 — click target geometry (fixed)**

The click point for a focused slot was the slot's top-left hotspot corner, 8 px away from the centre
of the box that `GuiFocusRenderer` highlights, and `getFocusedItemCenter()` disagreed with
`getItemBox()`. It only landed in the right slot because vanilla's `func_146978_c()` has a ±1 px
tolerance. The centre is now computed consistently (slot background spans `[x-1, x+17]`, hotspot
`[x, x+16]`, both centred at `x+8`).

This also removed a redundant conversion: `getFocusedCenter()` converted GUI coordinates to display
pixels and the caller converted them straight back, including a `ScaledResolution` allocation per
click. The confirm path now uses `getFocusedItemCenter()` (already in scaled GUI coordinates, the
space `mouseClicked`/`getSlotAtPosition` expect) and `getFocusedCenter()` is gone.

Tests: 4 new assertions on the click target (slot with/without container offset, button, button with
default height) — previously **no** test asserted a single coordinate. Coverage proven: restoring the
corner-point computation fails two of them.

**#12 — config reload (fixed)**

`/fuzzicontrols reload` only re-initialised the driver, so config edits (including `dpadNavigation`
and `rightStickScrolling`) needed a restart. `Config` now remembers the file it loaded and exposes
`reload()`; the command re-reads the config, re-initialises the driver and reports when no config has
been loaded yet.

Documented semantics: keys present in the file win; a key absent from the file keeps its current
value, because Forge's `Configuration` getters use the in-memory value as the default for a missing
key (then write it back). That is now pinned by a test.

Tests: 4 in `ConfigReloadTest`, driving the real Forge parser over a real temp config file. Forge's
`Configuration` resolves paths against FML injection data, which only exists at game launch, so the
test populates `FMLInjectionData.minecraftHome` reflectively rather than stubbing the config layer.

**Test quality — `DualSenseDriverCacheTest` no longer tests a stub**

It previously tested a hand-written `StubDriver` that duplicated `poll()`'s cache logic, so it could
never fail when the real driver broke. Now:

* The cache contract lives in `LastReportCache` (used by `DualSenseDriver.poll`/`close`) and is tested
  directly: first poll without a report, cached-state fallback across 10 polls, replacement by a
  newer report, and clearing on close.
* `DualSenseDriver.parseReport` is package-private and tested against real HID byte layouts: axis
  centring, full-deflection scaling/clamping, dead-zone handling, trigger threshold, face-button
  nibble, shoulder/stick/menu bits, and all eight D-pad hat directions plus neutral. 14 tests
  (was 6) over production code.

**Dead code removed**

* `GuiContainerAccessors.callGetSlotAtPosition` and `GuiSlotAccessors.getHeaderPadding` (never used).
* The reflective `buttonList` fallback in `GuiFocusNavigator.getScreenButtonList`: it looked up the
  MCP field name, which does not exist in production where fields carry SRG names, so it could never
  work. A missing `GuiScreenAccessors` mixin now logs an explicit error instead of silently
  returning no buttons.

**Renderer hardening**

`GuiFocusRenderer.drawRect` used the shared `Tessellator` from `DrawScreenEvent.Post`, so it depended
on (and could disturb) tessellator state a screen or another mod may have left behind. It now uses
immediate-mode `GL_QUADS`, which is immune to that and drops the `startDrawingQuads()`/`draw()`
bookkeeping.

**Docs**

* `README.md`: added the four missing config keys (`analogMovement`, `dpadNavigation`,
  `dpadSliderStep`, `rightStickScrolling`, `scrollSensitivity`), corrected `dropEntireStack`
  (documented `false`, actual default `true`), documented D-pad discrete navigation and list
  scrolling, mentioned `/fuzzicontrols reload`, and removed two entries from "Future Plans" that are
  already implemented (D-pad navigation, Mixin `@Invoker` migration).
* `BUTTON_BINDINGS.md`: documented X/right-click and LT/shift-click on the focused element, list
  scroll-into-view, the off-panel slot filter, the slider grid stepping, and a changelog entry for
  the whole bug-fix pass.
* `Config.dpadSliderStep` javadoc said the valid range was `[0.0, 1.0]`; it is `[0.01, 0.5]`.
* `ControllerManager.shutdown()` still claimed `HidServices` were per-driver and closed in `close()`;
  they are shared and released by the shutdown hook.
* `gradle.properties`: documented why `minimizeShadowedDependencies` stays `false` (hid4java/JNA
  resolve their own classes reflectively).
* `build.gradle.kts`: restored the missing trailing newline.

---

## Remaining nits (accepted, not fixed)

* **Test seams in production code** — `GuiFocusNavigator.rebuildForTest*`, `getRebuildCountForTest`
  and `getFocusableCount` exist only for tests. They are package-private and documented, but a
  cleaner design would move the focus-list source behind an interface. Not worth the churn now.
* **`MixinGuiScreen_SlotTracker` reflects per screen open** — the `GuiSlot` field scan walks the class
  hierarchy with `getDeclaredFields()` + `setAccessible` on every `setWorldAndResolution`. Caching per
  screen class would remove it, but it happens once per screen open.
* **`DualSenseDriver.byteToAxis` is slightly asymmetric** — `(unsigned - 128) / 127.5` maps 0..255 to
  -1.004..0.996, so full right/down deflection reaches 0.9954 instead of 1.0 (left/up clamps to -1).
  A 0.4 % asymmetry in movement speed; left as-is to avoid changing in-world movement feel. Pinned by
  a test.
* **`ControllerManager.pollFresh()` reads the device every rendered frame** — a HID read per frame on
  DualSense. Deliberate (the camera/cursor must not run on 20 Hz data); polling at tick rate and
  interpolating would be the alternative if it ever shows up in profiles.

---

## Investigated — not a bug

### Resource reload / sound engine restart when changing Mipmap Levels or Anisotropic Filtering

**Expected vanilla behaviour**, and unavoidable for these two options. Mechanism:

1. `GameSettings.setOptionFloatValue` (`GameSettings.java:390-408`) — for
   `ANISOTROPIC_FILTERING` and `MIPMAP_LEVELS` only, when the integer value actually changes:
   `mc.getTextureMapBlocks().setAnisotropicFiltering/setMipmapLevels(...)` then
   `mc.scheduleResourcesRefresh()`.
2. `Minecraft.scheduleResourcesRefresh()` just sets `refreshTexturePacksScheduled = true`
   (`Minecraft.java:2579-2582`).
3. The flag is consumed in the main loop (`Minecraft.java:1028-1032`):
   `if ((this.theWorld == null || this.currentScreen == null) && refreshTexturePacksScheduled)`
   → `refreshResources()`.
4. `refreshResources()` reloads every resource pack, and `SoundHandler implements
   IResourceManagerReloadListener`, whose `onResourceManagerReload` calls
   `sndManager.reloadSoundSystem()` (`SoundHandler.java:31, 63-65`) — that is the sound engine
   restart seen in the log.

Notes:

* The reload is **deferred while a GUI is open in a world** (condition at step 3), so in-game a
  series of presses costs a single reload when the screen closes. In the **main menu**
  (`theWorld == null`) each change reloads immediately — which is what makes it look per-press.
* Clicking/dragging these same sliders with the **mouse** triggers exactly the same reloads in
  vanilla; nothing in this mod adds extra `setOptionFloatValue` calls (`adjustSlider` calls it once
  per press).
* The only thing fix #1 changed is that the value now actually changes on each press — before the
  fix the slider was stuck, so no reload was scheduled.
* Optional future mitigation (deviates from vanilla, needs a mixin on the private
  `refreshTexturePacksScheduled`): coalesce the refresh until the D-pad is released, so a run of
  presses costs one reload instead of N. Not done — risk of leaving the texture atlas stale if the
  release is missed outweighs the benefit.

---
## Scope

The branch contains:

| Part                            | Source                               |
|---------------------------------|--------------------------------------|
| Entire GUI-navigation feature   | commit `a128765`                     |
| Music/sound fix                 | commit `b916483`                     |
| Inventory-slot D-pad navigation | **uncommitted** working-tree changes |
| Right-stick scrolling           | **uncommitted** working-tree changes |

Build state: compiles, 143 tests pass, mixin refmap resolves correctly (verified
`GuiContainerAccessors` → `field_147003_i`, `func_146975_c`, etc. are present in
`build/resources/main/mixins.fuzzicontrols.refmap.json`).

**Config note:** the live config `config/fuzzicontrols.cfg` predates this work — it has no
`dpadNavigation`, `rightStickScrolling`, `scrollSensitivity`, or `GUI_NAV_*` keys. So D-pad nav
is currently `false` (dormant), while **`rightStickScrolling` will default to `true` on the next
launch**, making bug #2 live immediately.

---

## Critical bugs

### 1. D-pad slider stepping is a no-op on coarse-step options (matches `BUGS.txt`) — FIXED

Fixed by `SliderStepper` (see Fix log above). Original analysis follows.

`GuiFocusNavigator.adjustSlider` (`GuiFocusNavigator.java:639-657`) adds `dpadSliderStep` in
**normalized** space, then pushes it through `denormalizeValue()` → `snapToStepClamp()`, which
snaps back to the option's own grid. If the option's normalized grid is coarser than
`dpadSliderStep` (0.05), the value round-trips unchanged:

| Option                  | range/step              | normalized grid        | result at step 0.05                      |
|-------------------------|-------------------------|------------------------|------------------------------------------|
| `MIPMAP_LEVELS`         | 0–4, step 1             | 0, .25, .5, .75, 1     | **stuck everywhere**                     |
| `ANISOTROPIC_FILTERING` | 1–16, power-of-two snap | 0, .0667, .2, .4667, 1 | **stuck everywhere** (needs ≥0.067–0.13) |
| `RENDER_DISTANCE`       | 2–16, step 1            | .0714 apart            | moves 1 step                             |
| `FRAMERATE_LIMIT`       | 10–260, step 10         | .04 apart              | moves 2 steps                            |
| `GAMMA`, sound sliders  | no snapping             | continuous             | fine                                     |

Confirmed against the decompiled `GameSettings.Options`
(`build/rfg/minecraft-src/java/net/minecraft/client/settings/GameSettings.java:1253-1259, 1357-1366`):
`ANISOTROPIC_FILTERING` overrides `snapToStep` to `roundUpToPowerOfTwo((int)v)`, so e.g.
2 → 2.75 → `(int)`2 → 2 → no change.

**Fix direction:** step on the option's own grid (advance/retreat one `valueStep`, or use
`setOptionValue(option, ±1)` semantics) instead of adding a fraction of the normalized range.

### 2. Right-stick scroll is ~6–14× too slow and frame-rate dependent

`ControllerTickHandler.java:646-651`:

```java
float scrollDelta = state.rightStickY() * Config.scrollSensitivity * deltaSeconds; // rate per frame
scrollDelta = Math.signum(scrollDelta) * scrollDelta * scrollDelta;               // squares the per-frame value
```

Squaring an already time-scaled value makes the delivered rate `S²/fps` instead of the
documented `S` notches/second:

| FPS | effective notches/s (S=10, full deflection) |
|-----|---------------------------------------------|
| 30  | 3.33                                        |
| 60  | 1.67                                        |
| 144 | 0.69                                        |

At 30 % deflection and 60 fps it is ~0.004 notches/s — effectively dead. The quadratic must be
applied to the *deflection* (dimensionless gain), then multiplied by `deltaSeconds`.

lwjgl3ify accumulates fractional deltas (`org/lwjglx/input/Mouse.java:159-194`), so fractional
input itself is fine; only the magnitude is wrong. Verified lwjgl3ify defaults:
`INPUT_SCROLL_SPEED=1.0`, `FORCE_DISCRETE_SCROLLING=false` — it is not rescaling this.

### 3. `guiCursorY` is assigned a bottom-left-origin value (coordinate-space inversion)

`ControllerTickHandler.java:382-384`:

```java
Mouse.setCursorPosition(center.x, center.y); // center from getFocusedCenter() = displayHeight - displayY (bottom-left origin)
guiCursorX = center.x;
guiCursorY = center.y;                       // WRONG: guiCursorY is top-left-origin everywhere else
```

Everywhere else `guiCursorY` is treated as top-left origin:

* `applyGuiCursor`: `setCursorPosition(guiCursorX, displayHeight - 1 - guiCursorY)`
* `handleGuiClick`: `guiY = scaledHeight - guiCursorY * … - 1`

So after a D-pad slot confirm the internal cursor is mirrored: the OS cursor sits on the slot,
but the code believes it is at the mirror image. The next stick nudge (or the next non-nav A
click) teleports the cursor/click to the mirrored Y.

**Fix direction:** store `mc.displayHeight - 1 - center.y`, or keep one internal convention and
convert only at the syscall boundary.

### 4. Inventory-slot "center" is not the center of the slot box

`getItemCenter` returns `(guiLeft + xDisplayPosition, guiTop + yDisplayPosition)` and claims that
is the center of the 18×18 background (`GuiFocusNavigator.java:497-503`). It is not: the
background spans `[x-1, x+17]`, so its center is `x+8`. Meanwhile `getItemBox`
(`GuiFocusNavigator.java:529-534`) correctly uses `x-1 … x+17`, i.e. centre `x+8`.

So **navigation uses one center and clicking warps to another, offset by (8,8)**. It currently
works only because `GuiContainer.func_146978_c` has a ±1 tolerance around a 16×16 box, so
`(x, y)` is still inside the right slot — the click lands on the slot's top-left hotspot corner.
Fragile, and visibly wrong (cursor sits in the corner of the highlighted slot).

`getFocusedCenter()` also omits the `-1` that `applyGuiCursor` applies, adding a 1 px error.

### 5. No cache invalidation on `setWorldAndResolution` → stale geometry after a resize

`GuiFocusNavigator.update()` rebuilds only when the *screen object* changes or the **button**
hash changes (`GuiFocusNavigator.java:270-286`). `buttonStateHash` hashes only `buttonList`
(size/visible/enabled/id), so:

* `guiLeft`/`guiTop` captured per `INVENTORY_SLOT` item stay at **old** values on a window resize
  or F11 fullscreen toggle, because `initGui()` rebuilds the button list identically.
* `SLOT_ENTRY` items keep a reference to the **previous** `GuiSlot`. `GuiSelectWorld.initGui()`
  does `this.field_146638_t = new GuiSelectWorld.List()` on every init
  (`build/rfg/minecraft-src/java/net/minecraft/client/gui/GuiSelectWorld.java:72`), so the
  navigator calls `elementClicked`/`setSelectedElement` on a discarded list.

Impact: the highlight is drawn detached from the (moved) panel, and pressing A dispatches the
click at the stale coordinates → **it hits whatever slot is now at that offset** (wrong-slot item
movement), or lands outside the panel (see #7). `MixinGuiScreen_SlotTracker` already re-scans on
`setWorldAndResolution`; the navigator needs the same signal (generation counter, or hook
`GuiScreenEvent.InitGuiEvent.Post`).

### 6. D-pad nav turns itself off after every inventory-slot confirm (swallows the next press)

The confirm path warps the OS cursor (`ControllerTickHandler.java:382`). SDL's
`SDL_WarpMouseInWindow` generates a mouse-motion event, and lwjgl3ify surfaces it as a changed
`Mouse.getX()/getY()` — the `ignoreNextMove`/`ignoreNextDelta` guards exist for exactly this but
are **never set anywhere** in lwjgl3ify (verified: only decremented, see
`org/lwjglx/input/Mouse.java:56-57, 85-86, 104-105`).

So `onRenderTick`'s comparison (`ControllerTickHandler.java:633-639`) sets
`mouseMovedSinceLastTick = true`, and the next client tick runs:

```java
if (anyDpadPressed && INACTIVE) { ACTIVE; setNavActive(true); }            // :305-308
...
if (mouseMovedSinceLastTick && ACTIVE) { INACTIVE; setNavActive(false); }  // :324-328
mouseMovedSinceLastTick = false;
```

Activation and deactivation happen in the same tick, the highlight flickers off, and the `else`
branch consumes the nav edges (`:418-420`) so **that D-pad press does nothing**. Net effect:
after confirming a slot, one D-pad press is eaten and the highlight disappears. This is very
likely why the feature feels broken in play.

---

## High / medium

### 7. Off-panel slots are focusable → A can drop your held stack

The navigator adds *every* `Container.inventorySlots` entry (`GuiFocusNavigator.java:578-592`)
with no panel-bounds or `Slot.func_111238_b()` filter. Clicking at a point outside
`[guiLeft, guiLeft+xSize) × [guiTop, guiTop+ySize)` makes `GuiContainer.mouseClicked` set
`k1 = -999` → `handleMouseClick(slot, -999, 0, 4)` → clickType 4 = **drop the carried stack into
the world**. Modded containers with hidden/off-panel slots turn this into a foot-gun.

### 8. No visibility culling for `GuiSlot` entries, no scroll-into-view

Entry geometry is computed from `amountScrolled` (`GuiFocusNavigator.java:505-513`), so
scrolled-away entries get off-screen Y values. The renderer is not clipped, so the border is
drawn over other UI, and A can activate a list entry that is not visible (e.g. load a
scrolled-away world).

### 9. `suppressGuiLeftClick` can swallow a legitimate click

It is set whenever the confirm edge fires *and* an item is focused (`ControllerTickHandler.java:412`),
even if nothing was dispatched (`center == null`, a slider, or an exception).

Also, the D-pad confirm ignores `guiShiftToggled` and `GUI_RIGHT_CLICK`, so LT-shift and
right-click have no effect in nav mode.

### 10. The documented lwjgl3ify fallbacks can never engage

`GuiMouseHelper.injectWheelEvent` / `injectLwjgl3ify` catch `Exception`, but
`NoSuchMethodError` / `NoSuchFieldError` / `NoClassDefFoundError` are `Error`s.
`FuzziControls.dependencies = "after:lwjgl3ify"` is a *soft* ordering dep, so on a non-lwjgl3ify
install the "fallback to direct dispatch" path is unreachable and the mod throws.
`injectWheelEvent` has no fallback at all (only a debug log).

### 11. Deactivation thresholds disagree

Nav deactivates when `|leftStick| > 0.15` (`ControllerTickHandler.java:316-319`) but
`applyGuiCursor` moves the cursor for *any* non-zero (post-dead-zone) value → a few counts of
drift past the dead zone warps the cursor, which then deactivates nav one tick later. Same class
of self-cancellation as #6.

### 12. `/fuzzicontrols reload` does not re-read the config

`CommandReloadControllers` only calls `manager.init()`, so new keys like `dpadNavigation` /
`scrollSensitivity` need a full restart. The docs imply otherwise.

### 13. Activation race

Because activation is evaluated *before* the mouse-moved check in the same tick, a real mouse
nudge in the preceding frame swallows the first D-pad press (same mechanism as #6).

---

## Minor / cleanup

* Dead accessors never used: `GuiContainerAccessors.callGetSlotAtPosition/getXSize/getYSize`,
  `GuiSlotAccessors.getHeaderPadding/setAmountScrolled`. `callGetSlotAtPosition` in particular
  suggests the click path was intended to use it.
* `GuiFocusNavigator.getScreenButtonList`'s reflection fallback uses the MCP name `"buttonList"`,
  which does not exist in production (SRG) — it silently returns `null` rather than working.
* Test-only API in production code: `rebuildForTest` / `rebuildForTestInventory` construct
  `new Slot(null, …)` inside `GuiFocusNavigator`.
* `DualSenseDriverCacheTest` tests a hand-written `StubDriver` that duplicates `poll()` — it can
  never fail when the real driver breaks.
* `GuiFocusNavigatorTest` has a stray trailing blank line.
* `build.gradle.kts` lost its trailing newline.
* `minimizeShadowedDependencies = false` (committed on this branch) ships all unused
  JNA/JXInput/hid4java classes.
* Doc drift:
  * `Config.dpadSliderStep` comment says range `[0.0, 1.0]` while the actual clamp is `[0.01, 0.5]`.
  * `getItemCenter` "centre" comment is wrong (#4).
  * Changelog "notches per second" claim does not match behavior (#2).
* `GuiFocusRenderer.drawRect` uses `Tessellator.instance.startDrawingQuads()` without checking
  `isDrawing`; an exception there escapes into Forge's `DrawScreenEvent` post, which sits inside
  EntityRenderer's try/catch and produces a crash report. Low probability, cheap to harden.

---

## What is solid

* Mixin/refmap wiring is correct and verified.
* `DrawScreenEvent.Post` is posted by `EntityRenderer` *after* the full screen draw
  (`build/rfg`-equivalent Forge source: `EntityRenderer.java:1060-1062`), so the highlight draws
  on top with the correct ortho matrix.
* The wheel path does reach `GuiSlot.drawScreen`'s event drain.
* The shared-`HidServices` refactor (avoiding create/shutdown cycling) is the right fix and its
  shutdown ordering is correct.
* `ControllerManager` blocking of held buttons across GUI close is intact.

---

## Optimizations

1. **Precompute geometry at rebuild time.** Store `left/top/right/bottom/centerX/centerY` in
   `FocusableItem` instead of recomputing per search — removes the per-press `ArrayList` /
   `AxisAligned` churn and makes staleness a single explicit invalidation point (fixes #5 cleanly).
2. **Replace the button-hash heuristic with `GuiScreenEvent.InitGuiEvent.Post`** — fires on every
   `initGui`, including resize; one event instead of hashing every tick while nav is active.
3. **Cache `ScaledResolution` per frame** — allocated in the confirm path, in `handleGuiClick`, and
   in `applyGuiCursor` per frame.
4. **`MixinGuiScreen_SlotTracker`**: cache the reflected `Field` per screen class instead of
   walking the hierarchy with `getDeclaredFields()` + `setAccessible` on every screen init.
5. **`manager.pollFresh()` every render frame** does a HID read per frame on DualSense; consider
   polling at tick rate and interpolating the camera/cursor.
6. **Avoid warping the OS cursor in the nav path at all.** The synthetic click already passes
   explicit coordinates; the warp only makes the vanilla hover highlight follow. Dropping it
   removes bugs #6 and #11 and the per-confirm motion events.

---

## Suggested fix order

1. #1 — known-broken feature (slider stepping)
2. #2 — default-on and visibly wrong (right-stick scroll rate)
3. #5 — wrong-slot item movement (stale geometry after resize)
4. #3 / #4 / #6 — nav correctness and UX
5. #7 / #10 — robustness and safety

---

## In-game validation checklist

### #1 — slider stepping (awaiting validation)

1. Enable the feature in `config/fuzzicontrols.cfg`:
   `controller { B:dpadNavigation=true }` (requires a game restart; `/fuzzicontrols reload` does not
   re-read the config — see #12).
2. Options → Video Settings. D-pad ↑/↓ to focus a slider, then D-pad ←/→ to step it.
3. Check each of these moves **one step per press** and clamps at both ends:

   | Slider | Expected |
   |---|---|
   | Mipmap Levels | Off → 1 → 2 → 3 → 4 → 4 (stops) |
   | Anisotropic Filtering | Off → 2 → 4 → 8 → 16 → 16 (stops) |
   | Render Distance | +1 chunk per press |
   | Framerate Limit | advances (2 steps per press is expected: grid 10 fps vs step 0.05 × 250) |
   | Brightness (Gamma) | smooth 5 % steps (unchanged behaviour) |

4. Music & Sounds sliders (continuous, separate code path) should still step smoothly — they were
   not affected by this fix, but confirm no regression.

### #2 — right-stick scrolling (awaiting validation)

`rightStickScrolling` defaults to `true`, so no config change is needed.

1. Options → Video Settings, push the right stick fully up/down: the list should scroll
   continuously at roughly 10 rows' worth per second (one notch per ~100 ms), in both directions.
2. Tilt the stick gently (~half): speed should drop to about a quarter (quadratic curve) and stay
   smooth — this is the case that used to be near-motionless.
3. Confirm the rate is the same with the frame-rate cap at 30, 60, and unlimited
   (Options → Video Settings → Max Framerate) — it must not change with FPS.
4. Confirm no scrolling happens when the stick is centred, and that in-world right-stick camera
   look still works (scrolling must only apply while a GUI is open).

### #5 — stale geometry after resize (awaiting validation)

The repro needs a screen whose layout depends on the window size, and it must stay open across the
resize:

1. `dpadNavigation=true`, enter a world, open a **chest** (or the player inventory).
2. Press D-pad ↑ once so the focus highlight appears, and note which slot it is on.
3. Press **F11** (or drag-resize the window) while the screen stays open.
4. Expected: the highlight is still drawn on the *same* slot, now at its new position, and the
   focused slot does not jump back to the first slot.
5. Press A on a highlighted slot: the click must land on the highlighted slot (items move
   correctly). Before the fix the highlight drifted away from the panel and the click hit whatever
   slot sat at the pre-resize offset.
6. Repeat with a **world-select list** (Options-free path: Singleplayer screen) — highlight should
   follow the rows, and A should activate the row it is highlighting.

Note: only the *same screen object* re-initialised matters here; closing and reopening the screen
always rebuilt the list and already worked.

### #6 — navigation must survive a slot confirm (awaiting validation)

1. `dpadNavigation=true`, in a world, open the inventory or a chest.
2. Press D-pad ↑/↓ so the focus border appears, then press **A** on a slot (pick an item up).
3. Expected: the focus border **stays visible**, and the very next D-pad press moves focus.
   Before the fix the border vanished after the confirm and that next press did nothing.
4. Press A a few more times to move the item between slots — each press must act on the highlighted
   slot and the border must persist throughout.
5. Confirm the intended change: the OS cursor no longer jumps to the slot on A (the focus border is
   the indicator). The cursor stays wherever the left stick left it.
6. Confirm the mouse still works: move the physical mouse and the focus border must disappear
   (navigation hands control back), and the left-stick virtual cursor must still be usable.

### #7 — no accidental drops (awaiting validation)

1. In a chest: press A on a focused slot to pick a stack up onto the cursor, D-pad to another slot,
   press A to place it. The item must land in the panel — never be thrown into the world.
2. Right-click a **horse that is not saddled** (GuiScreenHorseInventory). The armour slots are
   marked non-hoverable by vanilla, so the D-pad must skip them; they must not be focusable and A
   must never act on them.
3. If you have a modded container that parks slots off-panel (NEI/JEI-era mods do this), confirm the
   focus never lands on those slots.
4. Regression: normal slot navigation and item movement in chest/inventory/crafting must be
   unchanged.

### #8 — list navigation (awaiting validation)

1. `dpadNavigation=true`. Singleplayer → the world list (create several worlds first if needed).
2. Press D-pad ↓ repeatedly. The focus border must stay **inside the list** and the list must
   **scroll to follow** it, instead of the border walking off the bottom of the list.
3. D-pad ↑ back to the top: the list must scroll back so the first entry is visible.
4. Press A on a highlighted entry — it must activate exactly the entry that is highlighted.
5. Scroll the list with the right stick so the focused entry leaves the view; the border should
   disappear, and pressing A should scroll the entry back into view rather than activating
   something unseen.
6. Same check on a long list in Options → Video Settings (the options list scrolls too).

### #9 — right-click and shift-click in nav mode (awaiting validation)

1. In a chest, D-pad to a slot holding a stack, press **X** (right-click) → half the stack should be
   picked up onto the cursor (or one item placed if the cursor already holds a stack). Before the
   fix, X acted at the virtual cursor position instead.
2. Press **A** on a slot with a stack, then D-pad to another slot and press **A** → the stack moves
   there. Nothing should ever be dropped into the world.
3. Press **LT** (shift toggle), D-pad to a slot with a stack, press **A** → the **whole stack** must
   move (shift-click), not one item.
4. In a button menu (main menu / pause menu), press X while a button is focused → nothing should
   happen (a right click must not press the focused button).
5. Regression: A on a focused button/slot and D-pad left/right on sliders must behave exactly as
   before.

### Cleanup batch — what to check in game

Most of this batch is internal (dead code, docs, tests), but three changes are visible:

1. **#4 click target** — in a chest, D-pad to a slot and press A. The click must still land on the
   highlighted slot (the target moved from the slot's top-left corner to its centre). Repeat with the
   D-pad on a slot near the panel edge.
2. **Focus border rendering** — the border is now drawn with immediate-mode GL instead of the shared
   tessellator. Check it still looks identical (pulsing 2 px border, correct position) on a
   container screen, on a button screen, and on a list screen.
3. **#12 config reload** — with the game running, change something in `config/fuzzicontrols.cfg`
   (e.g. `lookSensitivity`) and run `/fuzzicontrols reload`; the new value must take effect without a
   restart. Also confirm the driver still re-initialises as before.
