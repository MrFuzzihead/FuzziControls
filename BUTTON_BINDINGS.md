# FuzziControls — Controller Button Bindings

Default bindings follow the standard console Minecraft layout documented at
https://minecraft.fandom.com/wiki/Controls (Controller section).

All bindings are **user-configurable** via `config/fuzzicontrols.cfg` under the `[bindings]` category.
Each entry uses the `ControllerButton` enum name as its value (e.g. `MOVE_FORWARD = LEFT_STICK_UP`).

> **Keep this file up to date** whenever `ControllerMapping.applyDefaults()` is changed.

---

## Movement — Left Stick (analogue, held)

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| Left Stick ↑ | Left Stick ↑ | Move forward | Analogue — speed proportional to deflection |
| Left Stick ↓ | Left Stick ↓ | Move backward | Analogue |
| Left Stick ← | Left Stick ← | Strafe left | Analogue |
| Left Stick → | Left Stick → | Strafe right | Analogue |
| Left Stick Click (LS) | L3 | Toggle sprint | Edge-triggered (press once to sprint) |

---

## Camera — Right Stick (analogue, continuous)

Camera rotation is applied **every rendered frame** (not via the action mapping) for smooth input.

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| Right Stick ↑ | Right Stick ↑ | Look up | Smooth — applied per frame, scaled by `lookSensitivity` |
| Right Stick ↓ | Right Stick ↓ | Look down | Smooth — applied per frame |
| Right Stick ← | Right Stick ← | Look left | Smooth — applied per frame |
| Right Stick → | Right Stick → | Look right | Smooth — applied per frame |
| Right Stick Click (RS) | R3 | Sneak / crouch | Toggle by default — press once to sneak, press again to stand. Set `sneakToggle = false` for hold behaviour. |

---

## Actions — Triggers & Bumpers

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| Right Trigger (RT) | R2 | Attack / mine *(in-world)* | Held — fires continuously; works even without a target in crosshair |
| Left Trigger (LT) | L2 | Use item / place block *(in-world)* | Held — fires continuously while held |
| Left Trigger (LT) | L2 | **Toggle shift-click mode** *(in GUI)* | Press once to arm shift mode; next left-click (A/✕) sends a Shift+click, moving the entire stack. Auto-clears after one use or when keyboard Shift is pressed. |
| Right Bumper (RB) | R1 | Next hotbar slot | Edge-triggered |
| Left Bumper (LB) | L1 | Previous hotbar slot | Edge-triggered |

---

## Face Buttons

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| A | Cross (✕) | Jump *(in-world)* / **Left-click** *(in GUI)* | Jump when no screen open; left-clicks at virtual cursor position when a GUI is open |
| B | Circle (○) | **Close GUI** (when a screen is open) | Closes inventory, chat, pause menu, etc. — no item drop on this press |
| B | Circle (○) | **Drop one item** (when no screen is open, tap) | Fires on release — holding does NOT drop first |
| B | Circle (○) | **Drop entire stack** (when no screen is open, hold 0.5 s) | Only when `dropEntireStack = true` in config; tap still drops one item; default is `false` |
| X | Square (□) | Pick block *(in-world)* / **Right-click** *(in GUI)* | Pick block when no screen open; right-clicks at virtual cursor position when a GUI is open |
| Y | Triangle (△) | Open / close inventory | Edge-triggered |

---

## GUI / Inventory Navigation

When any GUI screen is open (inventory, crafting, chest, pause menu, etc.) the controller
switches from game-play mode to cursor mode automatically.

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| Left Stick | Left Stick | Move cursor | Analogue speed (quadratic curve) — gentle tilt = precise, full = fast. Scaled by `inventoryCursorSensitivity`. Speed scales with window size automatically. |
| A | Cross (✕) | Left-click | Selects slots, presses buttons, etc. at cursor position. If shift mode is armed (see LT), sends a Shift+left-click instead. |
| X | Square (□) | Right-click | Half-stack pick-up, etc. at cursor position |
| Left Trigger (LT) | L2 | Toggle shift-click mode | Rising edge arms shift mode. The very next A/✕ left-click will be a Shift+click (move entire stack). Cleared after one use, on GUI close, or if keyboard Shift is pressed. |
| B | Circle (○) | Close screen | Returns to game (same as pressing Escape) |

The left stick drives the real OS cursor via LWJGL `Mouse.setCursorPosition`, so this works
with **every vanilla and mod GUI** automatically — no per-screen code is needed.

---

## D-Pad

### D-Pad Navigation Mode OFF (default — `dpadNavigation = false`)

All D-pad directions are unbound in-world and in GUIs. The left-stick virtual cursor remains
the only GUI navigation method.

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| D-pad ↑ | D-pad ↑ | *(unbound)* | Available for custom binding |
| D-pad ↓ | D-pad ↓ | *(unbound)* | Available for custom binding |
| D-pad ← | D-pad ← | *(unbound)* | Available for custom binding |
| D-pad → | D-pad → | *(unbound)* | Available for custom binding |

### D-Pad Navigation Mode ON (`dpadNavigation = true`)

When `dpadNavigation = true` and a GUI screen is open, the D-pad navigates between the
screen's buttons. When no GUI is open, the D-pad remains unbound.

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| D-pad ↑ | D-pad ↑ | **Previous button** | Moves focus up/backward through the button list; wraps from first to last |
| D-pad ↓ | D-pad ↓ | **Next button** | Moves focus down/forward through the button list; wraps from last to first |
| D-pad ← | D-pad ← | **Previous button** *or* **Decrease slider** | If the focused element is a slider, decreases its value by `dpadSliderStep`; otherwise moves focus backward |
| D-pad → | D-pad → | **Next button** *or* **Increase slider** | If the focused element is a slider, increases its value by `dpadSliderStep`; otherwise moves focus forward |
| A | Cross (✕) | **Activate focused button** | Fires a left-click at the focused button's center, identical to moving the cursor there and pressing A/✕ normally |

The focused button is highlighted with a **pulsing blue border** drawn over the GUI.

After any D-pad Up/Down navigation the OS cursor is also warped to the focused button's center
so the virtual-cursor and D-pad focus states remain in sync. A player who switches back to
left-stick cursor navigation will see the cursor already on the focused element.

---

## Menu Buttons

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| Start | Options | Pause / open game menu | Edge-triggered (only when no GUI is already open) |
| Back | Share / Create | Open chat | Edge-triggered (only when no GUI is already open) |
| Guide | PS | *(system)* | Not handled by mod |

---

## Sensitivity & Dead-zone Config (`config/fuzzicontrols.cfg`)

| Key | Default | Description |
|---|---|---|
| `stickDeadZone` | `0.15` | Analogue stick dead-zone radius [0.0–0.99] |
| `triggerThreshold` | `0.20` | Minimum trigger value to register as pressed [0.0–0.99] |
| `lookSensitivity` | `2.0` | Right-stick camera speed multiplier [0.1–10.0] |
| `inventoryCursorSensitivity` | `300` | GUI cursor speed in **display pixels/s** at full stick deflection at the 854×480 reference resolution [1–2000]. Scales automatically with window size. |
| `dropEntireStack` | `false` | When `true`, holding B for 0.5 s drops the entire held stack |
| `sneakToggle` | `true` | When `true` (default), RS-click toggles sneak; when `false`, sneak is held. Pressing the keyboard sneak key while the toggle is on automatically clears the toggle so keyboard control resumes normally. |
| `dpadNavigation` | `false` | When `true`, D-pad Up/Down navigates between GUI buttons; D-pad Left/Right adjusts sliders or also navigates; A/✕ activates the focused button. A pulsing highlight shows the current focus. |
| `dpadSliderStep` | `0.05` | Fraction of slider range adjusted per D-pad Left/Right press when `dpadNavigation = true` [0.01–1.0]. Default 0.05 = 5% per press. |
| `driver` | `auto` | Controller driver: `auto`, `xinput`, or `dualsense` |
| `xInputSlot` | `0` | XInput controller slot to use [0–3] |

---

## Changelog

| Date | Change |
|---|---|
| 2026-02-28 | Initial bindings established from Minecraft wiki controller layout |
| 2026-02-28 | Hotbar cycling moved from D-pad ←/→ to LB/RB; D-pad cleared; PICK_BLOCK → X; COMMAND → D-pad ↓ |
| 2026-02-28 | B / Circle now closes open GUIs (inventory, chat, pause) on press; drops item otherwise |
| 2026-02-28 | RT attack fires unconditionally (no longer requires a crosshair target) |
| 2026-02-28 | Added `dropEntireStack` config option (hold B 0.5 s to drop stack, default off) |
| 2026-03-01 | RT attack: replaced reflection with access transformer (fuzzicontrols_at.cfg) on `leftClickCounter` |
| 2026-03-01 | Drop item: when `dropEntireStack=true`, nothing drops on initial press — tap drops one, hold drops stack |
| 2026-03-01 | Fixed stale config: HOTBAR_NEXT/PREV reset to RB/LB; PICK_BLOCK reset to X; COMMAND reset to D-pad ↓ |
| 2026-03-01 | RT attack now fires on entities and air via KeyBinding.onTick (not leftClickCounter) |
| 2026-03-01 | B button no longer drops an item after closing a GUI (dropBlockedByGui guard) |
| 2026-03-01 | D-pad ↓ / COMMAND unbound by default — all D-pad directions now unbound |
| 2026-03-01 | Hot-plug support: controller plugged in after game launch is detected within ~3 seconds |
| 2026-03-01 | Keyboard no longer stutters when controller is connected — controller only releases keys it claimed |
| 2026-03-01 | Sneak is now a toggle by default (RS / R3); set `sneakToggle = false` in config for hold behaviour |
| 2026-03-01 | Controller-held keys are released cleanly on disconnect to prevent stuck movement |
| 2026-03-01 | GUI navigation: left stick moves virtual cursor; A = left-click; X = right-click in any open screen |
| 2026-03-01 | Cursor centers on screen open; quadratic speed curve for precision at low deflections |
| 2026-03-01 | Fixed: in-world controls (movement, attack, etc.) no longer fire while a GUI screen is open |
| 2026-03-01 | Fixed: GUI cursor speed now in display pixels/s — consistent across main menu, inventory, and pause menu |
| 2026-03-01 | GUI cursor and click navigation now works on the main menu (no world/player required) |
| 2026-03-01 | Fixed: cursor speed now uses System.nanoTime() wall-clock delta — consistent at all frame rates including uncapped pause menu |
| 2026-03-01 | Fixed: A/X click now injects real LWJGL mouse events, enabling world/server list selection (GuiSlot-based screens) |
| 2026-03-01 | Fixed: B/Circle now injects Escape key press, backing out of all screens universally including main-menu submenus |
| 2026-03-01 | Fixed: GuiMouseHelper rewritten with correct LWJGL 2 Mouse internals — 22-byte event records (byte button, byte state, int absX, int absY, int dwheel, long nanos); buttons ByteBuffer also patched for isButtonDown() — enables world/server list selection |
| 2026-03-01 | Mod marked client-side only via acceptableRemoteVersions="*" — installing on a server is safe and has no effect |
| 2026-03-01 | Fixed: character no longer jumps after closing a GUI with a button also bound to an in-world action (e.g. A = GUI left-click AND jump) — buttonsHeldOnGuiClose set tracks every button held at GUI close time; each button is blocked from triggering any in-world action until it is fully released, regardless of how long it is held |
| 2026-03-01 | Fixed: virtual cursor and camera no longer move when the game window is not focused — both onClientTick and onRenderTick guard with Display.isActive(); delta timer resets on focus loss to prevent a position jump on return |
| 2026-03-02 | Fixed: DualSense input lag — switched to non-blocking HID reads (timeout=0); last valid state is cached and returned when no new report is available, eliminating the 5 ms forced wait per poll |
| 2026-03-02 | Added: Shift-left-click in inventories — LT (L2) toggles shift mode while a GUI is open; the next A/✕ left-click delivers a Shift+click via KeyboardHelper patching LWJGL Keyboard.isKeyDown(), moving the entire stack. Toggle auto-clears after one use, on GUI close, or if keyboard Shift is pressed |
| 2026-03-02 | Fixed: GUI cursor speed now scales with window size relative to an 854×480 reference — cursor traversal time is now consistent at any resolution from small windowed to full-screen |
| 2026-03-02 | Fixed: pressing the keyboard sneak key while the controller sneak toggle is on now clears the toggle, so keyboard sneak works normally without fighting the controller |
| 2026-03-02 | Deferred: analog movement speed scaling (requires Mixin into EntityPlayerSP) — tracked in plan for future implementation |
| 2026-03-02 | Added: D-pad GUI navigation system (`dpadNavigation` config, default `false`) — D-pad Up/Down moves focus between buttons, D-pad Left/Right adjusts sliders or also navigates, A/✕ activates the focused element. Focused button highlighted with a pulsing blue border. OS cursor warps to focused button center to keep virtual-cursor and D-pad focus in sync. |
