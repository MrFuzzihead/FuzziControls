# Plan: D-Pad Discrete GUI Navigation System

Add a reusable focus-based navigation layer that tracks a "focused element" index across any
open `GuiScreen`, moves it with D-pad up/down (and left/right for sliders), renders a highlight
over the focused element, and confirms with A/Cross. Phase 1 targets the main menu to prove the
system; the same code then generalizes to inventories, settings screens, and mod GUIs with zero
per-screen code in the common case.

This navigation mode is **opt-in via config** (`dpadNavigation = false` by default) so that the
existing virtual-cursor (left-stick) mode remains the default and is unaffected.

---

## Phase 1 Status: ✅ IMPLEMENTED (2026-03-02)

---

## Steps

1. ✅ **Create `GuiFocusNavigator`** — implemented in
   `src/main/java/…/util/GuiFocusNavigator.java`.
   - Inspects `buttonList` (reflected via `getDeclaredField` + `setAccessible`) on each screen.
   - Exposes `focusNext()`, `focusPrev()`, `sliderLeft()`, `sliderRight()`, `isFocusedSlider()`,
     `getFocusedButton()`, `getFocusedCenterX/Y()`.
   - Resets focus to index 0 when screen identity changes via `onScreenChanged()`.
   - All operations are no-ops when `buttonList` is empty or null.
   - Slider value adjustment uses reflected `GuiOptionSlider.sliderValue` field +
     `GuiButton.mouseDragged` method to trigger the slider's onChange callback.

2. ✅ **Add GUI nav `ControllerAction` values** — `GUI_NAV_UP`, `GUI_NAV_DOWN`,
   `GUI_NAV_LEFT`, `GUI_NAV_RIGHT`, `GUI_NAV_CONFIRM` added to `ControllerAction`.
   Wired in `ControllerMapping` with defaults: `DPAD_UP/DOWN/LEFT/RIGHT` for nav, `BUTTON_A`
   for confirm.

3. ✅ **Handle D-pad edges in `ControllerTickHandler`** — inside the GUI path, calls
   `GuiFocusNavigator` on rising edge of each nav action when `dpadNavigation = true`.
   - Up/Down → `focusPrev()` / `focusNext()` + cursor warp to focused button center.
   - Left/Right → `sliderLeft()` / `sliderRight()` when focused slider; otherwise navigate.
   - Confirm → `GuiMouseHelper.injectMouseButton` (press + release) at focused button center.
   - Nav action edges consumed cleanly when `dpadNavigation = false`.

4. ✅ **Render the focus highlight** — `GuiFocusRenderer` registered on `MinecraftForge.EVENT_BUS`
   via `GuiScreenEvent.DrawScreenEvent.Post`.
   - Pulsing blue border rectangle drawn with raw GL calls (GL_LINE_LOOP, GL_BLEND).
   - Only renders when `dpadNavigation = true` and a focused button exists.

5. ✅ **Extend slider support** — `GuiOptionSlider` detected via `instanceof`; value adjusted
   via reflected `sliderValue` field; `mouseDragged` called reflectively to update label.
   Documented in Reflection/Mixins/AT tracker in `plan-controllerSupport.prompt.md`.

6. **Phase 1 validation — main menu** — pending in-game testing. Expected to work since the
   main menu's `buttonList` contains standard `GuiButton` instances for all entries.

7. ✅ **Add `GuiFocusNavigatorTest`** — unit tests in
   `src/test/java/…/util/GuiFocusNavigatorTest.java` covering focus index arithmetic via a
   pure-Java `FocusIndexDriver` inner class (avoids Minecraft classpath dependency in tests).
   Tests cover: initial index=0, `next()` wrap, `prev()` wrap, empty list no-op, screen-change
   reset, `safeIndex()` clamping, round-trip next+prev.

8. ✅ **Update `BUTTON_BINDINGS.md`** — D-pad section updated with navigation mode off/on
   tables, new config options (`dpadNavigation`, `dpadSliderStep`), and changelog entry.

---

## Further Considerations

1. **Mod GUI compatibility** — `buttonList` is a public field on every `GuiScreen` (vanilla and
   Forge), so button-list inspection works for the vast majority of GUIs with zero per-screen
   code. Custom GUIs that use only raw GL drawing with no `GuiButton` objects (common in some
   inventory and JEI-style mods) will not have focusable elements under this system. Two fallback
   strategies should be designed early (even if not shipped in Phase 1):
   - **`IFocusableGui` adapter interface** — mod authors (or FuzziControls itself for known mods)
     implement this on their `GuiScreen` subclass and return a custom ordered list of focusable
     regions.
   - **Automatic fallback to virtual-cursor mode** — if `buttonList` is empty or null and
     `dpadNavigation = true`, silently fall back to the left-stick cursor so the player is never
     stuck without any navigation method.

2. **Coexistence with virtual-cursor mode** — D-pad navigation and the left-stick virtual cursor
   should cooperate within the same screen session. Proposed behavior: moving the left stick while
   D-pad nav is active moves the OS cursor and temporarily suppresses the focus highlight (until
   the next D-pad press). This gives players a smooth mouse-cursor fallback without needing to
   disable D-pad nav in config.

3. **Slider interaction strategy (Reflection/AT/Mixin note)** — direct reflective access to
   `GuiOptionSlider.sliderValue` and `GuiButton.mouseDragged` is used. Both are documented in
   the Reflection/Mixins/AT tracker in `plan-controllerSupport.prompt.md`. A future Mixin
   `@Accessor` on `GuiOptionSlider.sliderValue` could replace the field reflection.

4. **Stretch goal — `GuiSlot` row navigation** — inventory screens (`GuiContainer` subclasses)
   use `GuiSlot` for item grids, which are not `GuiButton` instances. A future Phase 2 could
   extend `GuiFocusNavigator` with a slot-index tracking layer that understands `GuiContainer`
   slot layouts, enabling D-pad navigation of item grids in the style of console Minecraft.
   This is explicitly a stretch goal and out of scope for Phase 1.

5. **Stretch goal — D-pad tab navigation** — some screens (the Options screen, mod config
   screens) have horizontal tabs. D-pad Left/Right could cycle tabs when the focused element is
   not a slider. This is reserved for a future phase once vertical navigation is stable.

6. **GUI for key bindings dependency** — the planned in-game controller binding GUI (see
   `plan-controllerSupport.prompt.md` consideration 5) will itself be a `GuiScreen` with
   `GuiButton` rows. D-pad navigation implemented here will make that GUI fully navigable
   without a mouse, which is a prerequisite for shipping it.


Add a reusable focus-based navigation layer that tracks a "focused element" index across any
open `GuiScreen`, moves it with D-pad up/down (and left/right for sliders), renders a highlight
over the focused element, and confirms with A/Cross. Phase 1 targets the main menu to prove the
system; the same code then generalizes to inventories, settings screens, and mod GUIs with zero
per-screen code in the common case.

This navigation mode is **opt-in via config** (`dpadNavigation = false` by default) so that the
existing virtual-cursor (left-stick) mode remains the default and is unaffected.

---

## Steps

1. **Create `GuiFocusNavigator`** — a new utility class in
   [`src/main/java/…/util/`](src/main/java/com/mrfuzzihead/fuzzicontrols/util/) that owns all
   reusable focus-navigation logic:
   - On each `GuiScreen` open (detected by screen identity change), inspects the public
     `buttonList` field (`List<GuiButton>`) to build an ordered list of focusable elements.
   - Exposes `focusNext()`, `focusPrev()`, `confirmFocused()`, `sliderLeft()`, and
     `sliderRight()` methods that `ControllerTickHandler` calls on D-pad rising edges.
   - Resets focus to index 0 automatically whenever a new screen opens.
   - Provides `getFocusedButton()` for the highlight renderer.
   - Handles an empty `buttonList` gracefully (all operations are no-ops).

2. **Add GUI nav `ControllerAction` values** — add `GUI_NAV_UP`, `GUI_NAV_DOWN`,
   `GUI_NAV_LEFT`, `GUI_NAV_RIGHT`, `GUI_NAV_CONFIRM` to
   [`ControllerAction`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerAction.java).
   Wire them in
   [`ControllerMapping`](src/main/java/com/mrfuzzihead/fuzzicontrols/controller/ControllerMapping.java)
   with defaults: `DPAD_UP/DOWN/LEFT/RIGHT` for nav and `BUTTON_A` for confirm. These actions
   are only consumed when a GUI is open so there is no in-world conflict.

3. **Handle D-pad edges in `ControllerTickHandler`** — inside the existing
   `mc.currentScreen != null` GUI block, call `GuiFocusNavigator` on the rising edge of each
   nav action when `dpadNavigation = true`.
   - Up/Down → `focusPrev()` / `focusNext()`.
   - Left/Right → `sliderLeft()` / `sliderRight()` when focused element is a slider; otherwise
     skip (reserved for future horizontal tab navigation).
   - Confirm → synthesize a left mouse click at the focused button's center using
     `GuiMouseHelper.injectMouseButton`, matching what a real click would do.
   - When `dpadNavigation = false` the D-pad continues to fall through to its existing
     (currently unbound) handling, so there is no behavior change for existing users.

4. **Render the focus highlight** — hook into `GuiScreenEvent.DrawScreenEvent.Post` (Forge event
   fired after `drawScreen`) via a new `GuiFocusRenderer` class registered on `MinecraftForge.EVENT_BUS`.
   - Draws a colored border rectangle around the focused `GuiButton`'s bounding box using
     vanilla `drawRect` / `drawHorizontalLine` / `drawVerticalLine` GL calls — no texture assets
     required.
   - Alpha pulses sinusoidally driven by `System.currentTimeMillis()` for a subtle animated glow
     that makes the selection unmistakable without being distracting.
   - Only renders when `dpadNavigation = true` and `mc.currentScreen != null` and
     `GuiFocusNavigator.getFocusedButton()` is non-null.

5. **Extend slider support** — when the focused element is a `GuiSlider` (Forge) or
   `GuiOptionSlider` (vanilla), `sliderLeft()` and `sliderRight()` adjust the slider value by a
   configurable step (config key `dpadSliderStep`, default `0.05` = 5% of range). The primary
   approach is a **direct reflective call** to the slider's value-change method (resolved once at
   class load) rather than simulating a mouse drag, which is fragile across a press-hold-move-release
   sequence in a single tick. This reflection will be documented in the Reflection/Mixins/AT
   tracker section of [`plan-controllerSupport.prompt.md`](plan-controllerSupport.prompt.md).

6. **Phase 1 validation — main menu** — confirm that all main menu buttons (`Singleplayer`,
   `Multiplayer`, `Options`, `Mods`, `Quit Game`, etc.) are focusable, that Up/Down cycles
   through them with wrap-around, that A/Cross activates the focused button, and that B/Circle
   still closes any submenu that opens.

7. **Add `GuiFocusNavigatorTest`** unit tests in
   [`src/test/java/…/controller/`](src/test/java/com/mrfuzzihead/fuzzicontrols/controller/)
   covering:
   - Initial focus lands on index 0.
   - `focusNext()` wraps from last element back to 0.
   - `focusPrev()` wraps from 0 back to last element.
   - Empty `buttonList` — all operations are no-ops, no exception thrown.
   - `confirmFocused()` reports the correct center coordinates of the focused button.
   - Slider detection correctly identifies `GuiSlider` vs plain `GuiButton`.

8. **Update [`BUTTON_BINDINGS.md`](BUTTON_BINDINGS.md)** with the new D-pad navigation bindings
   and a note that D-pad nav is opt-in via `dpadNavigation = true` in config.

---

## Further Considerations

1. **Mod GUI compatibility** — `buttonList` is a public field on every `GuiScreen` (vanilla and
   Forge), so button-list inspection works for the vast majority of GUIs with zero per-screen
   code. Custom GUIs that use only raw GL drawing with no `GuiButton` objects (common in some
   inventory and JEI-style mods) will not have focusable elements under this system. Two fallback
   strategies should be designed early (even if not shipped in Phase 1):
   - **`IFocusableGui` adapter interface** — mod authors (or FuzziControls itself for known mods)
     implement this on their `GuiScreen` subclass and return a custom ordered list of focusable
     regions.
   - **Automatic fallback to virtual-cursor mode** — if `buttonList` is empty or null and
     `dpadNavigation = true`, silently fall back to the left-stick cursor so the player is never
     stuck without any navigation method.

2. **Coexistence with virtual-cursor mode** — D-pad navigation and the left-stick virtual cursor
   should cooperate within the same screen session. Proposed behavior: moving the left stick while
   D-pad nav is active moves the OS cursor and temporarily suppresses the focus highlight (until
   the next D-pad press). This gives players a smooth mouse-cursor fallback without needing to
   disable D-pad nav in config.

3. **Slider interaction strategy (Reflection/AT/Mixin note)** — simulating a mouse drag on
   `GuiOptionSlider` is fragile. Direct reflective access to the slider's internal value field
   or change method is more reliable. This new reflection point must be documented in the
   Reflection/Mixins/AT tracker in
   [`plan-controllerSupport.prompt.md`](plan-controllerSupport.prompt.md) before implementation
   begins. A future Mixin `@Accessor` on `GuiOptionSlider.sliderValue` could replace it.

4. **Stretch goal — `GuiSlot` row navigation** — inventory screens (`GuiContainer` subclasses)
   use `GuiSlot` for item grids, which are not `GuiButton` instances. A future Phase 2 could
   extend `GuiFocusNavigator` with a slot-index tracking layer that understands `GuiContainer`
   slot layouts, enabling D-pad navigation of item grids in the style of console Minecraft.
   This is explicitly a stretch goal and out of scope for Phase 1.

5. **Stretch goal — D-pad tab navigation** — some screens (the Options screen, mod config
   screens) have horizontal tabs. D-pad Left/Right could cycle tabs when the focused element is
   not a slider. This is reserved for a future phase once vertical navigation is stable.

6. **GUI for key bindings dependency** — the planned in-game controller binding GUI (see
   `plan-controllerSupport.prompt.md` consideration 5) will itself be a `GuiScreen` with
   `GuiButton` rows. D-pad navigation implemented here will make that GUI fully navigable
   without a mouse, which is a prerequisite for shipping it.

