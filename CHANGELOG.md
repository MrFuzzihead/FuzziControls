# Changelog

All notable changes to FuzziControls.

Versions are derived from git tags (`git describe`), so a release is published by tagging a commit on
`main` — see [Releasing](#releasing) below.

## [Unreleased]

### Added

- **FuzziControls settings screen.** Open it from **Options → FuzziControls** or the Forge mod
  list. It has Controller, DualSense, Bindings, Input and Interface tabs; the DualSense tab
  appears when a DualSense is active. It is fully D-pad/A/B navigable and edits the existing
  config file immediately. It exposes a master controller on/off switch, the
  Automatic/DualSense/XInput driver choice, XInput slot, connection toasts, live device/transport
  status, every gameplay/input/interface setting, and a **Rescan** button.
- **Bumper tab browsing.** While the FuzziControls screen is open, R1/L1 cycle through the
  top-level tabs. The D-pad/A/B navigation is unchanged.
- **In-game remapping.** The Bindings tab remaps every controller action with a tap-to-capture
  overlay. Capture suspends normal GUI input so B can be bound without closing the screen; a
  one-second hold cancels. Bindings can be cleared, reset per category, or reset all (two-step
  confirmation). Duplicate bindings are allowed and reported.
- **Controller-button → keyboard-key shortcuts.** Four configurable slots in **Bindings → Keys**
  can drive arbitrary keyboard keys in **Hold** or **Tap** mode, giving controller access to most
  modded `KeyBinding`s. Mods that read raw LWJGL keyboard state are outside this mechanism.
- **DualSense Bluetooth support.** The driver now understands the Bluetooth simple (`0x01`, 10-byte)
  and enhanced (`0x31`, 78-byte, CRC32) input reports as well as the existing USB report. Reading
  feature report `0x05` on connect requests the enhanced report; if the controller stays in simple
  mode, sticks, buttons and triggers still work. Bluetooth handles that stop sending reports are
  treated as disconnected after 2 seconds instead of leaving buttons stuck.
- **DualSense Edge support** (`054C:0DF2`) alongside the standard DualSense (`054C:0CE6`).
- **DualSense Edge paddles and Fn buttons.** The Edge's left/right rear paddles and front
  Fn1/Fn2 buttons are decoded from its third button byte and exposed as bindable controller
  buttons in the remap screen. They are unbound by default; standard DualSense controllers
  ignore the reserved bits.
- **Controller vibration.** XInput controllers use `XInputSetState`, and DualSense controllers
  use USB (`0x02`) or Bluetooth (`0x31`) output reports with the required sequence number and
  CRC32. Short pulses fire when the player takes damage, attacks an entity, or mines a block.
  Vibration is configurable with `rumbleEnabled` and `rumbleIntensity`, is de-duplicated so it
  does not spam HID writes, and is stopped on disconnect, driver reset, or shutdown.
- **DualSense feature page.** A DualSense tab, available over USB and Bluetooth, adds
  adaptive-trigger effects for bow drawing and mining, an RGB lightbar with brightness, and a
  low-battery toast toggle. Trigger
  effects use the DualSense 11-byte effect blocks and the existing USB/Bluetooth output report
  path; lightbar and trigger writes are de-duplicated so the settings screen and per-tick apply
  calls do not spam HID.
- **Low-battery toast.** The DualSense battery/charging status is decoded from the input report,
  and a one-shot toast appears when the battery first drops below the configurable threshold.
  The toast shows the current percentage and does not repeat while still low or while charging.
- **Controller connection toasts.** A modern top-right notification slides in when a controller
  connects or disconnects, showing the device name and USB/Bluetooth transport. The title is green
  for a connection and red for a disconnect, and the icon follows the controller family
  (DualSense, Xbox/XInput, or a generic pad). Up to five toasts stack, additional messages queue,
  and each stays for five seconds before sliding out. Disable with `showControllerToasts = false`
  in the `[controller]` section. Implemented as a self-contained 1.7.10 version of the 1.12
  `GuiToast` layout; GTNHLib and Et Futurum Requiem do not expose a reusable toast API.
- Driver metadata (`getDeviceName`, `getTransportName`) for connection toasts and the settings
  screen, plus a `DualSenseTransport` seam and a dedicated report parser with unit tests for all
  three layouts and the Bluetooth CRC.

### Changed

- **Config writes now include settings, not only bindings.** `Config.save()` persists every
  controller/input/interface value and the `[shortcuts]` section, so settings-screen changes survive
  a restart.
- **XInput connection checks no longer re-poll the native device.** `XInputDriver.isConnected()`
  now reads the connection flag cached by the last `poll()`, so the manager performs one native
  XInput poll per tick. Render-frame polls (`pollFresh()`) are unchanged.
- **Bundled JNA updated to 5.19.1.** The shaded jar now carries the current JNA classes and
  native dispatch libraries, and `THIRD_PARTY_NOTICES.md` is shipped inside the jar for the
  bundled LGPL-2.1 and MIT components.
- **Analogue movement now changes actual speed.** Vanilla `Entity.moveFlying()` normalises the
  input vector to length 1, so the per-axis analogue values only ever affected direction. A second
  mixin now scales movement friction by the left stick's radial magnitude, giving true analogue
  speed while preserving the stick's real angle (diagonals no longer bend toward the axes). Half
  tilt is quarter speed, keyboard input keeps per-axis priority, and sneak/use-item factors are
  applied once.
- **Right-stick camera response is smoother near centre.** Look input now uses a 25% linear / 75%
  cubic curve: small corrections are precise, while full deflection still reaches the configured
  turn rate. The previous rotation is advanced with the same per-frame delta as vanilla
  `Entity.setAngles()`, so camera interpolation no longer lags a tick behind.

### Fixed

- **Leaving a singleplayer world no longer shows a disconnect *and* a connect toast.** Quitting a
  world saves it and stops the integrated server on the client thread, which is also the thread that
  polls the controller. A freeze longer than the DualSense liveness watchdog (5 s on USB, 2 s on
  Bluetooth) made a perfectly healthy handle look dead: its reports were still queued and unread.
  The manager released it, reconnected on the same tick, and the player saw both toasts together in
  the main menu. The watchdog is now never trusted until the driver has been drained once, and a
  detected disconnect is held for two seconds so a reconnect of the same device cancels the pair. A
  controller that really was unplugged still toasts (once, and with the correct transport).
- **DualSense USB output reports use the model's declared size.** Standard controllers now get the
  48-byte USB report and the Edge gets the 64-byte form. Sending the oversized Edge form to a
  standard controller could leave its triggers in a bad state even though hidapi accepted the write.
- **Adaptive trigger writes only touch the selected trigger.** R2 effects no longer rewrite L2, and
  an idle trigger is never sent an unnecessary `Off` block. This prevents trigger effects from
  interfering with held block-breaking or sword blocking.
- **A disappearing Bluetooth controller could show three toasts.** The immediate reconnect probe
  could open a stale HID handle and treat it as a successful connection before its first read failed.
  Connection notifications now wait for the first valid input report, so only the real disconnect
  toast is shown.
- **Toast panels and icons were sampled incorrectly.** The 160×32 panel and 16×16 icons were drawn
  with the vanilla 256×256 UV assumption, which stretched the border across the panel and made the
  icons effectively invisible. The textures are now padded to 256×256.
- **Holding LT/L2 in a GUI only shift-clicked the first stack.** The shift-click state was consumed by
  the first left-click, so moving several stacks required releasing and re-pressing the trigger. While
  LT/L2 is held, every A/Cross left-click is now a Shift+click (moving stack after stack); a tap still
  arms a single Shift+click. Keyboard Shift continues to take priority.
- **Keyboard WASD stopped working whenever a controller was connected.** The analogue-movement mixin
  overwrote the movement axes with the left stick's deflection even when the stick was centred, which is
  exactly `0.0` inside the dead zone — so holding W/A/S/D produced no movement at all. A centred stick
  now leaves keyboard movement untouched.
- With a controller connected, walking while sneaking with the keyboard ran at 0.09× speed (the vanilla
  0.3 sneak factor was applied twice to keyboard-driven axes).

## [1.1.0]

### Added

- **D-pad GUI navigation** (`controller.dpadNavigation`, enabled by default). D-pad ↑/↓/←/→ moves focus
  spatially between GUI buttons, option rows, list entries and container slots; the focused element is
  marked with a pulsing border. Holding a direction repeats after 400 ms, then every 100 ms.
  - **A / Cross** activates the focused element. **X / Square** right-clicks the focused inventory slot
    (split stacks, place one item). **LT / L2** arms shift-click, so the next A moves a whole stack.
  - Lists (world select, server list, video settings, …) scroll to follow the focus, and entries that
    are scrolled out of view are never activated.
  - Slots a screen cannot interact with — hidden/off-panel slots in modded containers, horse armour
    before the horse is saddled — are skipped.
  - D-pad ←/→ steps a focused slider, now including options with coarse value grids (Mipmap Levels and
    Anisotropic Filtering could not be moved at all before).
- **Right-stick scrolling** while a GUI is open (`controller.rightStickScrolling`,
  `controller.scrollSensitivity`): scrolled at a constant `scrollSensitivity` notches per second at any
  frame rate, with a quadratic curve for precision.
- `/fuzzicontrols reload` now re-reads `config/fuzzicontrols.cfg` as well as re-initialising the
  controller driver, so config edits no longer need a restart.
- Automated testing: a client self-test harness (`FUZZICONTROLS_SELFTEST=1 ./gradlew runClient25`) that
  verifies mixin application and drives the live GUI navigation stack, and a CI job that runs it
  headless. 330 unit tests.

### Fixed

- **Linux and macOS: the game crashed on startup** when no XInput backend was available. JXInput is
  Windows-only, and its `UnsatisfiedLinkError` escaped the driver and aborted mod initialisation. The
  mod now reports XInput as unavailable and continues with the raw-HID drivers (DualSense works
  cross-platform).
- D-pad slider stepping was a silent no-op on options whose value grid is coarser than the configured
  step, so the Mipmap Levels and Anisotropic Filtering sliders could not be adjusted.
- Right-stick scrolling ran at `sensitivity² / fps` — roughly 1.7 notches/second at 60 fps instead of the
  configured 10, and slower still at high frame rates, which felt laggy and choppy.
- D-pad navigation switched itself off after every slot confirm (the mod's own cursor movement was
  mistaken for the player grabbing the mouse) and swallowed the next D-pad press.
- Cached slot layout was reused after a window resize, fullscreen toggle or GUI-scale change, so the
  focus border drifted off the panel and clicks landed on the wrong slot.
- Focusing a slot outside the container panel and pressing A dropped the carried item stack into the
  world.
- List entries scrolled out of view were still highlighted and could be activated — in the world-select
  list that meant loading a world the player could not see.
- In navigation mode, right-click / shift-click acted at the stale cursor position instead of the
  focused element.
- The controller camera ignored the game's **Invert Mouse** setting.
- Sub-threshold left-stick drift cancelled D-pad navigation, and the first rendered frame after opening
  a screen always counted as mouse movement.
- lwjgl3ify linkage errors escaped into the client tick/render loop on installs without a usable
  lwjgl3ify, instead of falling back to direct dispatch.
- The right-stick scroll, D-pad focus centre and slot click target were computed inconsistently (the
  click landed on the slot's corner rather than its centre).

### Changed

- `controller.dpadNavigation` now defaults to `true`. Set it to `false` for the previous behaviour, where
  only the left-stick virtual cursor navigates.
- To track a moved cursor the mod now ignores the mouse-motion events its own cursor warps generate.
- The focus border is drawn with immediate-mode GL instead of the shared tessellator, so a screen that
  leaves the tessellator open cannot break it.
- `analogMovement` is applied by a mixin at class load: *disabling* it takes effect immediately, but
  *enabling* it needs a restart. Documented in the README.

## [1.0.0]

First tagged release: controller support for Minecraft 1.7.10 Forge on Java 25 + lwjgl3ify — XInput and
DualSense drivers, analogue movement, right-stick camera, hotbar/attack/use/drop bindings, virtual-cursor
GUI navigation, and shift-click.

---

## Releasing

The version is not stored in a file — it comes from `git describe`. To publish a release:

```bash
# after merging the PR into main
git checkout main
git pull
git tag -a 1.1.0 -m "FuzziControls 1.1.0"
git push origin 1.1.0
```

Pushing the tag triggers `.github/workflows/release-tags.yml`, which builds the mod and publishes the
release. `./gradlew build` picks the version up from the tag automatically (the jar is named after it).
