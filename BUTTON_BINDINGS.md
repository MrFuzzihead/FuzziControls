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
| Right Trigger (RT) | R2 | Attack / mine | Held — fires continuously; works even without a target in crosshair |
| Left Trigger (LT) | L2 | Use item / place block | Held — fires continuously while held |
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
| Left Stick | Left Stick | Move cursor | Analogue speed (quadratic curve) — gentle tilt = precise, full = fast. Scaled by `inventoryCursorSensitivity`. |
| A | Cross (✕) | Left-click | Selects slots, presses buttons, etc. at cursor position |
| X | Square (□) | Right-click | Half-stack pick-up, etc. at cursor position |
| B | Circle (○) | Close screen | Returns to game (same as pressing Escape) |

The left stick drives the real OS cursor via LWJGL `Mouse.setCursorPosition`, so this works
with **every vanilla and mod GUI** automatically — no per-screen code is needed.

---

## D-Pad

| Xbox Button | PS Button | Action | Notes |
|---|---|---|---|
| D-pad ↑ | D-pad ↑ | *(unbound)* | Available for custom binding |
| D-pad ↓ | D-pad ↓ | *(unbound)* | Available for custom binding (was COMMAND — bind `COMMAND=DPAD_DOWN` in config to restore) |
| D-pad ← | D-pad ← | *(unbound)* | Available for custom binding |
| D-pad → | D-pad → | *(unbound)* | Available for custom binding |

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
| `inventoryCursorSensitivity` | `300` | GUI cursor speed in **display pixels/s** at full stick deflection [1–2000] — consistent across all screens |
| `dropEntireStack` | `false` | When `true`, holding B for 0.5 s drops the entire held stack |
| `sneakToggle` | `true` | When `true` (default), RS-click toggles sneak; when `false`, sneak is held |
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
