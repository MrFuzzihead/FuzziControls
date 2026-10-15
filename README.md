# FuzziControls

> **Full controller support for Minecraft 1.7.10 Forge — plug in and play.**

FuzziControls brings modern, console-quality gamepad support to the classic Minecraft 1.7.10 modding experience. Whether you prefer an Xbox controller, a PlayStation 5 DualSense, or any XInput-compatible gamepad, FuzziControls lets you mine, build, and explore without ever touching a keyboard or mouse.

---

## ✨ Highlights

- 🎮 **XInput support out of the box** — any Xbox or XInput-compatible controller works immediately on Windows with no extra software
- 🎯 **DualSense (PS5) support** — native HID support for PlayStation 5 controllers, detected and prioritized automatically
- 🔌 **Hot-plug** — controllers can be connected or reconnected at any time while the game is running; FuzziControls detects them within seconds
- 🖱️ **Full GUI / inventory navigation** — the left stick drives a virtual cursor across every screen (inventory, crafting, chest, pause menu, main menu, and every mod GUI), with no per-screen code required
- ⚡ **Smooth camera** — right-stick camera rotation runs every rendered frame for butter-smooth looking with configurable sensitivity
- 📝 **Fully configurable** — every button binding, dead zone, trigger threshold, look sensitivity, cursor speed, sneak mode, and drop behavior is exposed in `config/fuzzicontrols.cfg`
- 🧪 **Test-covered** — a JUnit test suite covers dead-zone math, axis normalization, default bindings, and button classification

---

## 🎮 Default Controller Layout

Controls follow the [official Minecraft console controller layout](https://minecraft.fandom.com/wiki/Controls).

| Input | Xbox | PS | Action |
|---|---|---|---|
| Left Stick | ↕↔ | ↕↔ | Move |
| Right Stick | ↕↔ | ↕↔ | Look / camera |
| RT | Right Trigger | R2 | Attack / mine |
| LT | Left Trigger | L2 | Use item / place block |
| RB | Right Bumper | R1 | Next hotbar slot |
| LB | Left Bumper | L1 | Previous hotbar slot |
| A | A | Cross (✕) | Jump / GUI left-click |
| B | B | Circle (○) | Close GUI / drop item |
| X | X | Square (□) | Pick block / GUI right-click |
| Y | Y | Triangle (△) | Open / close inventory |
| LS (click) | LS | L3 | Toggle sprint |
| RS (click) | RS | R3 | Toggle sneak (configurable) |
| Start | Start | Options | Pause menu |
| Back | Back | Share | Open chat |

> See [BUTTON_BINDINGS.md](BUTTON_BINDINGS.md) for the full binding reference including GUI mode, shift-click, and D-pad details.

---

## 🖱️ Inventory & GUI Navigation

When any screen is open — inventory, crafting table, chest, the pause menu, or even the main menu — FuzziControls automatically switches to **cursor mode**:

- **Left stick** → moves the OS cursor with an analogue quadratic curve (gentle tilt = precision, full push = speed), automatically scaled to your window size
- **A / Cross** → left-click (select slots, press buttons, pick items)
- **X / Square** → right-click (split stacks, etc.)
- **LT / L2** → arm shift-click mode — the next left-click moves an entire stack, just like Shift+clicking with a mouse
- **Right stick ↑/↓** → scroll lists, inventories and other scrolling GUIs (quadratic response, speed set by `scrollSensitivity`)
- **B / Circle** → close the screen

Because the real OS cursor is driven, this works with **every vanilla and mod GUI automatically**.

### D-pad discrete navigation (optional)

With `dpadNavigation = true` the D-pad navigates GUI elements discretely instead of relying on the
virtual cursor:

- **D-pad ↑/↓/←/→** → move focus to the nearest element (inventory slot, button or list entry), scrolling lists so the focused entry stays visible
- **D-pad ←/→ on a slider** → adjust the value by `dpadSliderStep` (or one step of the option's own value grid)
- **A / Cross** → activate the focused element; with shift-click armed, moves the whole stack
- **X / Square** → right-click the focused slot (split stacks, place one item)
- The focus is highlighted with a pulsing border and is dropped as soon as you use the left stick or the mouse

---

## ⚙️ Configuration

All options live in `config/fuzzicontrols.cfg`:

| Option | Default | Description |
|---|---|---|
| `driver` | `auto` | Force `xinput`, `dualsense`, or let the mod choose |
| `xInputSlot` | `0` | Controller index (0–3) for XInput |
| `stickDeadZone` | `0.15` | Stick dead-zone radius (0.0–0.99) |
| `triggerThreshold` | `0.2` | Minimum trigger value to register as pressed (0.0–0.99) |
| `lookSensitivity` | `2.0` | Right-stick camera speed multiplier |
| `inventoryCursorSensitivity` | `300` | GUI cursor speed (display px/s at reference resolution, [1–2000]) |
| `sneakToggle` | `true` | `true` = press RS to toggle sneak; `false` = hold RS to sneak |
| `dropEntireStack` | `true` | Hold B/○ to drop entire stack instead of one item |
| `analogMovement` | `true` | Scale movement speed with left-stick deflection instead of on/off |
| `dpadNavigation` | `false` | `true` = D-pad navigates GUI elements discretely (see above) |
| `dpadSliderStep` | `0.05` | Slider step per D-pad press as a fraction of range ([0.01–0.5]) |
| `rightStickScrolling` | `true` | `true` = right stick scrolls while a GUI is open |
| `scrollSensitivity` | `10.0` | Scroll speed in notches/second at full deflection ([1–100]) |

Every button binding can also be remapped via the `[bindings]` section.

Config edits can be applied without restarting the game with `/fuzzicontrols reload`, which re-reads
the config file and re-initialises the controller driver.

---

## 🚀 Installation

1. Install [Minecraft Forge 1.7.10](https://files.minecraftforge.net/).
2. Drop the `fuzzicontrols-*.jar` into your `mods/` folder.
3. Launch the game, plug in your controller, and play.

> FuzziControls is **client-side only**. Installing it on a server has no effect.

---

## 🗺️ Future Plans

- **In-game key binding GUI** — a dedicated screen accessible from the Options or pause menu for remapping every controller action, with proper button icons (Xbox colours for XInput, PlayStation colours for DualSense), all navigable without a mouse
- **Broader controller support** — investigate support for non-XInput controllers (e.g. older DirectInput gamepads) and Linux/macOS environments

---

## 🛠️ Building from Source

```bash
./gradlew build
```

Building requires a Java 25 toolchain (see `.java-version`). The built jar itself is a multi-release
jar that runs on **Java 17+** (native Java 21/25 classes are shipped in `META-INF/versions/`, with a
Java 17 fallback), since [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) requires launching
Minecraft 1.7.10 on Java 17 or newer. Spotless and Checkstyle are applied automatically on build.

---

## 📄 License

[MIT](LICENSE)
