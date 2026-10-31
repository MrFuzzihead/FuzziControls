# Changelog

All notable changes to FuzziControls.

Versions are derived from git tags (`git describe`), so a release is published by tagging a commit on
`main` — see [Releasing](#releasing) below.

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
