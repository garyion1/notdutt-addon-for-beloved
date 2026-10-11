# TPA Tools

Meteor Client addon for Minecraft **1.21.11** (Fabric, Java 21). Rebuilt from the
`tpatools.jar` 1.6.1 release into a buildable Gradle project, with a redesigned GUI
and movable HUD elements.

## Modules (category: TPA Tools)

| Module | What it does |
| --- | --- |
| `tpa-burst` | Sends `/tpahere` to random non-bot players until disabled. |
| `auto-strength` | Drinks a Strength potion from the hotbar when Strength runs out. |
| `auto-loot-sell` | Opens `/sell` when drops are nearby and shift-clicks the center slot. Pauses TPA Burst while selling. |
| `kill-history` | Opens the history window on the Kills tab. |
| `loot-history` | Opens the history window on the Loot tab. |
| `spawner-noti` | Spawner pillars, ESP, labels, and toast, HUD, chat or sound alerts. |

## GUI

- **History window**: a dark panel with **Kills / Loot tabs**, live **search**,
  item icons for loot, hover highlight, a draggable scrollbar and a **Clear** button.
  **Drag the header to move the window.** Its position is remembered for the session.
- **Spawner toast**: restyled to match, with a mob/coords layout and a progress bar
  in your accent colour.
- **In-world labels**: the same dark/red style.

## Movable HUD

Two HUD elements, in the HUD editor under the **TPA Tools** group:

- `tpa-tools-status`: shows which modules are on, their live info
  (TPAs sent, sell state, spawner count), and session kills, loot and spawners.
- `spawner-alerts`: a movable version of the spawner toast. To use it, set
  **Spawner Noti → Alerts → alert-style** to `Hud` or `Both`.

To move them: open the Meteor GUI (Right Shift), go to the **HUD** tab, add the
elements, and drag them where you want. Each element has **scale**, **accent**,
**background** and **border** settings.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. Each push also builds it on GitHub Actions; the jar
is attached to the run as the `tpa-tools` artifact.
