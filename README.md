# Khanh Client

Fabric client mod for Minecraft **1.21.11** (Fabric Loader 0.19.2, Fabric API 0.141.6, Java 21).

## Build

```sh
./gradlew build
```

The mod jar is written to `build/libs/khanh-client-<version>.jar`. Drop it into `.minecraft/mods`
together with Fabric API.

## Click GUI

Press **Right Shift** to open the menu: flat dark columns across the top of the screen, one per
section, each module with a small switch on the right.

- **Left click** a module to toggle it, **right click** to open its settings, **middle click** to bind a key.
- Drag a column by its title to move it, scroll inside a long column, type anywhere to search.
- The colour follows *Customize → GUI Colors* (blue by default).

## Moving the HUD

Open the menu and click **HUD EDITOR** at the bottom of the **CLIENT** column (or just open chat).

- **Drag** any HUD piece (watermark, radar, module list, coordinates, armor and every widget) to move it.
- **Scroll** over a piece to resize it, **right click** to put it back where it started.
- Positions snap to the screen edges and centre and are saved with your config.
- **Esc** (or *Done*) goes back.
