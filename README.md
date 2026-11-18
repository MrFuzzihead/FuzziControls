# FuzziControls

> **Full controller support for Minecraft 1.7.10 Forge — plug in and play.**

FuzziControls brings modern, console-quality gamepad support to the classic Minecraft 1.7.10 modding experience. Whether you prefer an Xbox controller, a PlayStation 5 DualSense, or any XInput-compatible gamepad, FuzziControls lets you mine, build, and explore without ever touching a keyboard or mouse.

---

## ✨ Highlights

- 🎮 **XInput support out of the box** — any Xbox or XInput-compatible controller works immediately on Windows with no extra software
- 🎯 **DualSense (PS5) support** — native HID support over **USB and Bluetooth**, including the DualSense Edge, detected and prioritized automatically. Bluetooth uses the enhanced 0x31 report when the controller supports it and falls back to the simple report otherwise. The Edge's rear paddles and Fn1/Fn2 buttons are bindable in the remap screen.
- 🔌 **Hot-plug** — connect or reconnect a controller at any time. An active controller dropping is noticed within one tick; with no controller, probing runs every second while the game window is focused, backs off to 30 s when unfocused, and probes immediately when focus returns.
- 📳 **Controller vibration** — taking damage, attacking entities and mining blocks produce short rumble pulses on XInput and DualSense controllers. Strength is configurable, and vibration is stopped on disconnect or when disabled.
- 🎛️ **DualSense features** — a DualSense settings page adds adaptive-trigger effects for bow drawing and mining, an RGB lightbar with brightness control, and a low-battery toast over USB or Bluetooth.
- 🔔 **Connection toasts** — a modern top-right toast (160×32, slide-in/out) shows the controller name and USB/Bluetooth transport when it connects or disconnects, with a green/red state title and a DualSense, Xbox or generic controller sprite. It can be disabled with `showControllerToasts = false` in the `[controller]` section.
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
| Y | Y | Triangle (△) | Open inventory (in-world) |
| LS (click) | LS | L3 | Sprint (hold) |
| RS (click) | RS | R3 | Toggle sneak (configurable) |
| Start | Start | Options | Pause menu |
| Back | Back | Share | Open chat |
| DualSense Edge | — | Left/Right paddle, Fn1/Fn2 | Unbound by default; remap in **Options → FuzziControls → Bindings** |

> See [BUTTON_BINDINGS.md](BUTTON_BINDINGS.md) for the full binding reference including GUI mode, shift-click, and D-pad details.

---

## 🖱️ Inventory & GUI Navigation

When any screen is open — inventory, crafting table, chest, the pause menu, or even the main menu — FuzziControls automatically switches to **cursor mode**:

- **Left stick** → moves the OS cursor with an analogue quadratic curve (gentle tilt = precision, full push = speed), automatically scaled to your window size
- **A / Cross** → left-click (select slots, press buttons, pick items)
- **X / Square** → right-click (split stacks, etc.)
- **LT / L2** → hold for shift-click mode — every left-click while held moves an entire stack; tap it to arm a single Shift+click
- **Right stick ↑/↓** → scroll lists, inventories and other scrolling GUIs (quadratic response, speed set by `scrollSensitivity`)
- **B / Circle** → close the screen

Because the real OS cursor is driven, this works with **every vanilla and mod GUI automatically**.

### D-pad discrete navigation

By default (`dpadNavigation = true`) the D-pad navigates GUI elements discretely; set it to `false` to rely
only on the virtual cursor:

- **D-pad ↑/↓/←/→** → move focus to the nearest element (inventory slot, button or list entry), scrolling lists so the focused entry stays visible; hold a direction to repeat after 400 ms
- **D-pad ←/→ on a slider** → adjust the value by `dpadSliderStep` (or one step of the option's own value grid)
- **A / Cross** → activate the focused element; with shift-click armed, moves the whole stack
- **X / Square** → right-click the focused slot (split stacks, place one item)
- The focus is highlighted with a pulsing border and is dropped as soon as you use the left stick or the mouse

---

## 🎛️ Settings screen

Open **Options → FuzziControls**, or use the Forge mod list's Config button. The screen is fully
navigable with a controller (D-pad focus, A to activate, B/Escape to go back); a mouse works too.

| Tab | What it controls |
|---|---|
| **Controller** | Master on/off switch, Automatic/DualSense/XInput driver, XInput slot, connection toasts, vibration on/off and strength, live device/transport status, and **Rescan** |
| **DualSense** | Appears when a DualSense is active. Adaptive-trigger on/off, mining and bow effect types/strengths, RGB lightbar and brightness, and the low-battery toast. Works over USB and Bluetooth. |
| **Bindings** | Remap every controller action by pressing a button. Category tabs keep each list short enough to navigate without scrolling. **Rebind**, **Clear**, **Reset**, and two-step **Reset All** are available. Duplicate bindings are allowed and reported. |
| **Input** | Analogue movement, sneak toggle/hold, stack-vs-single drop, look sensitivity, stick dead zone, and trigger threshold |
| **Interface** | D-pad navigation, GUI cursor speed, right-stick scrolling, scroll speed, and D-pad slider step |

The **Bindings → Keys** category adds four controller-button → keyboard-key shortcut slots for
modded `KeyBinding`s. Each slot can be **Hold** or **Tap**, and can drive any registered or arbitrary
keyboard key. Mods that read raw LWJGL keyboard state without using `KeyBinding` are outside this
mechanism.

Capture is deliberate: tap a button to bind it, or hold it for one second to cancel. While capture is
active, normal GUI clicks/navigation are suspended, so binding **B** does not close the screen and
binding **A** does not activate whatever is behind the overlay. Escape also cancels keyboard capture.

---

## ⚙️ Configuration

All options live in `config/fuzzicontrols.cfg`; the settings screen edits the same file:

| Option | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch for controller input. `false` stops all probing and leaves keyboard/mouse only |
| `driver` | `auto` | Force `xinput`, `dualsense`, `none` (legacy disable), or let the mod choose |
| `showControllerToasts` | `true` | Show a modern top-right toast when a controller connects or disconnects |
| `rumbleEnabled` | `true` | Enable vibration feedback for damage, attacks and mining |
| `rumbleIntensity` | `0.6` | Vibration strength multiplier (0.0–1.0) |
| `adaptiveTriggersEnabled` | `false` | Enable DualSense adaptive-trigger effects |
| `miningTriggerEffect` | `vibration` | Mining effect: `off`, `feedback`, `vibration`, `machine` |
| `miningTriggerStrength` | `5` | Mining trigger strength (1–8) |
| `bowTriggerEffect` | `bow` | Bow-draw effect: `off`, `bow`, `feedback`, `weapon` |
| `bowTriggerStrength` | `6` | Bow-draw trigger strength (1–8) |
| `lightbarEnabled` | `true` | Drive the DualSense RGB lightbar |
| `lightbarRed` / `lightbarGreen` / `lightbarBlue` | `0` / `128` / `255` | Lightbar RGB channels (0–255) |
| `lightbarBrightness` | `1` | `0` high, `1` medium, `2` low |
| `lowBatteryToast` | `true` | Show a toast when battery first drops below the threshold |
| `lowBatteryThreshold` | `15` | Low-battery toast threshold in percent |
| `xInputSlot` | `0` | Controller index (0–3) for XInput |
| `stickDeadZone` | `0.15` | Stick dead-zone radius (0.0–0.99) |
| `triggerThreshold` | `0.2` | Minimum trigger value to register as pressed (0.0–0.99) |
| `lookSensitivity` | `2.0` | Right-stick camera speed multiplier (25% linear / 75% cubic aim curve; full deflection = 360°/s at 2.0) |
| `inventoryCursorSensitivity` | `300` | GUI cursor speed (display px/s at reference resolution, [1–2000]) |
| `sneakToggle` | `true` | `true` = press RS to toggle sneak; `false` = hold RS to sneak |
| `dropEntireStack` | `true` | Hold B/○ to drop entire stack instead of one item |
| `analogMovement` | `true` | True radial movement speed: half tilt = quarter speed, full stick = full speed; set `false` for digital on/off |
| `dpadNavigation` | `true` | `false` = use only the left-stick virtual cursor |
| `dpadSliderStep` | `0.05` | Slider step per D-pad press as a fraction of range ([0.01–0.5]) |
| `rightStickScrolling` | `true` | `true` = right stick scrolls while a GUI is open |
| `scrollSensitivity` | `10.0` | Scroll speed in notches/second at full deflection ([1–100]) |

Every button binding can also be remapped via the `[bindings]` section. The `[shortcuts]` section
stores the four keyboard shortcuts in `BUTTON:KEYCODE:HOLD|TAP` form.

Config edits can be applied without restarting the game with `/fuzzicontrols reload`, which re-reads
the config file and re-initialises the controller driver. The settings screen saves immediately.
One exception: `analogMovement` is applied by a mixin at class load, so **enabling** it needs a
restart (disabling it is immediate).

---

## 🚀 Installation

1. Install [Minecraft Forge 1.7.10](https://files.minecraftforge.net/).
2. Install [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) — **required**, and the reason the mod
   runs on Java 17+ rather than Java 8.
3. Drop the `fuzzicontrols-*.jar` into your `mods/` folder.
4. Launch the game, plug in your controller, and play. For a DualSense over Bluetooth, pair it in
   your operating system first; USB and Bluetooth are both supported.

> FuzziControls is **client-side only**. Installing it on a server has no effect.

---

## 🗺️ Future Plans

- **Controller-type-aware button glyphs** — Xbox colours for XInput and PlayStation shapes for DualSense in the remap screen
- **Per-controller binding profiles** — separate maps for different pads
- **Broader controller support** — investigate support for non-XInput controllers (e.g. older DirectInput gamepads) and Linux/macOS environments

---

## 🧪 Testing

```bash
./gradlew test                            # unit tests (JUnit 4 + Mockito 5)
./gradlew jacocoTestReport                # HTML/XML coverage report (build/reports/jacoco/test/html)
./gradlew jacocoTestCoverageVerification  # enforces the configured coverage floor
./gradlew check                           # tests + coverage floor + spotless + checkstyle
```

The unit suite uses JUnit 4.13.2 and Mockito 5 (inline mock maker; Byte Buddy 1.18 for Java 25
class files). `addon.gradle.kts` wires JaCoCo and a line/branch coverage floor; the report analyses
the same downgraded classes the test JVM runs, so JaCoCo does not emit "classes do not match" warnings.

Two layers cover what a plain JVM cannot:

**Client self-test** (`FUZZICONTROLS_SELFTEST=1`). It boots at the main menu (no world needed),
verifies that every mixin was applied, then drives the real navigation stack the same way the D-pad
confirm does: focus the **Options…** button and activate it, open **FuzziControls**, switch tabs,
select a binding, enter and cancel capture, focus a settings slider, focus **Video Settings…** and
activate it, step a real slider, navigate the options list, check the `GuiContainer` slot path
(off-panel slots filtered, click target at the slot centre), and render the focus border for a couple
of frames. It
logs one `PASS`/`FAIL` line per check, prints a summary and exits with the failure count as its exit
code:

```bash
# Linux/macOS
FUZZICONTROLS_SELFTEST=1 ./gradlew runClient25
# Windows (PowerShell)
$env:FUZZICONTROLS_SELFTEST=1; ./gradlew runClient25
```

**Game-test mod** (`src/gameTest/java`, a separate `@Mod` that is never shipped). It asserts the live
integration points and exits the JVM with the assertion result:

```bash
./gradlew gameTestServer   # dedicated server: config load + default binding table (no display needed)
./gradlew gameTestClient   # client: mixins, config, manager tick, real main-menu focus
```

Both write `run/<side>/gametest-report.txt` with `ok`/`FAIL` lines, a `SUMMARY <checks> <failures>`
line, and `FUZZICONTROLS_GAMETEST_OK` / `FUZZICONTROLS_GAMETEST_FAILED`. The server run needs
`run/server/eula.txt`; the client run needs a display (use `xvfb-run` on Linux). CI runs the server
game test as its own job and the client game test headless in `client-smoke-test-java25`, so a broken
mixin refmap, a config regression, or a controller-manager crash fails the build instead of only
showing up in game.

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

## 📄 Changelog

See [CHANGELOG.md](CHANGELOG.md) for user-facing changes per release.

---

## 📄 License

[MIT](LICENSE)
