# Uyuni Salt Flat

For Minecraft 1.20.1 / Forge 47.x.
Adds a dimension of endless mirror-like salt flats inspired by Salar de Uyuni.
The thin layer of water on the salt reflects the sky, clouds, stars, players and mobs,
and every footstep leaves a ripple.
Built to look its best with shader packs (Oculus + Embeddium), and also works without them.

---

## Installing

**Players:** put `uyuni-1.0.0.jar` into your `mods` folder (Forge 1.20.1).
For shaders, also install [Oculus](https://modrinth.com/mod/oculus) and [Embeddium](https://modrinth.com/mod/embeddium).

**Building from source:**
- Windows: unzip and double-click `install.bat`. It finds or downloads Java 17, builds the mod and copies it into `mods`. The first build takes about 10 minutes.
- Mac / Linux: double-click `install.command`.
- Manually: install JDK 17 and run `./gradlew build`. The jar is written to `build/libs/`.

---

## How to play

| Item | Recipe / how to get | Description |
|---|---|---|
| **Salt Mirror** | Glass Pane ×4 + Nether Quartz ×4 + Ender Pearl (center) | Right-click to travel to the Salt Flat. Use it there to return to where you were |
| **Mirror Salt Flat** | Salt Block ×4 + Water Bucket | A thin salt slab covered in brine. Wherever you place it becomes a small mirror |
| **Salt Block** | Dig below the surface of the Salt Flat | A white salt building block |
| **Mirror Brine Bucket** | Scoop the Salt Flat's water with an empty bucket | Build your own little salt lake anywhere |

All items are in the dedicated **Uyuni Salt Flat** creative tab.

Via command: `/execute in uyuni:salt_flat run tp @s ~ 65 ~`

### Mirror Brine

The water on the Salt Flat is not vanilla water but the mod's own fluid, **Mirror Brine**.

- Pale, glass-clear water rather than blue, with tiny salt crystals that slowly sparkle
- Dense and syrupy: it flows slowly and only spreads 2–3 blocks
- It cannot form infinite sources; scooping it removes it
- Flowing lava that touches it hardens into a Salt Block (lava sources become obsidian)
- It evaporates in the Nether
- Scooping it from a Mirror Salt Flat leaves a dry slab; pouring it back makes it a mirror again

---

## The water mirror

### Without shaders
A true planar reflection: the sky, clouds, the sun, players, mobs and placed blocks are reflected in real time.

### With shaders (Oculus)
- **Players, mobs and blocks** are drawn as part of the world while the shader pack renders, so they receive the pack's lighting, colour grading and fog.
  Distant mobs are reflected as long as they are within your entity render distance.
- **Sky, sun, clouds and stars** are taken from what the shader pack actually drew.
  - Directions that are on screen are read directly from the finished image, so they are always live (meteors included).
  - Off-screen directions use a "sky map" that remembers the sky you have looked at.
    Looking up refreshes it, including fine cloud and star detail.
  - When the time of day or weather changes sharply (`/time set`, rain starting, sunset colours),
    the remembered sky is marked as stale and repainted from the horizon inward over a few seconds.
- **Wobble:** ripples tilt the water surface, so reflections distort naturally as you walk.

Tested with: AstraLex V93.0, BSL v10.0, Complementary Reimagined r5.6.1 + Euphoria Patches 1.7.8,
Complementary Unbound r5.6.1 (with and without Euphoria Patches 1.7.8), iterationT 3.2.0, SEUS Renewed v1.0.1 and Sildur's Vibrant Shaders v1.541 Extreme
(Forge 47.4.28, Oculus 1.8.0, Embeddium 0.3.31).

### Known limitations with shaders
- Sky directions you have not looked at for a while may look slightly blurred or show a slightly older sky. Looking up refreshes them.
- Fast-moving mobs' reflections can smear a little because of the shader pack's temporal anti-aliasing (TAA).
- The Salt Flat's water is not "water" to the shader pack, so the pack's water waves/reflection settings don't affect it (they still affect normal oceans and rivers).

### If the Salt Flat looks dark with a shader pack
The pack may not treat this dimension as an overworld. Add this line to the pack's `shaders/dimension.properties` (create it if missing):
```
dimension.world0 = minecraft:overworld uyuni:salt_flat
```

---

## Ripples

- One ripple per footstep, placed under the foot that just landed; two when you land from a jump.
- Other players and mobs make ripples too.
- The wave motion follows real shallow-water physics: rings start fine and spread apart as they travel, tiny capillary ripples race ahead, and each ripple is slightly irregular.

---

## In-game commands (client-side, work on any server)

| Command | What it does |
|---|---|
| `/uyuni debug` | Shows how the water mirror is running, and why it stopped if it did |
| `/uyuni retry` | Restarts a feature that stopped because of an error |
| `/uyuni sky reset` | Clears the remembered sky (shader mode) and records it again |

When reporting a bug, a screenshot of `/uyuni debug`, your shader pack name and `logs/latest.log` help a lot.

---

## Config: `config/uyuni-client.toml`

Created automatically the first time you launch the game with the mod.
You can also change the language, toggle ripples and the mirror, and rename items in-game.
Open the settings with the **Uyuni Settings** button in the top-left of the pause menu, the `/uyuni config` command, or **Mods → Uyuni Salt Flat → Config**.
You can also bind a key under Controls → Key Binds → Uyuni Salt Flat → Open settings.

**[general]**

| Key | Default | Description |
|---|---|---|
| `language` | auto | Mod language: `auto` = same as the game, `ja` = Japanese, `en` = English |

**[names]** (leave empty for the default name)

| Key | Renames |
|---|---|
| `tab` | The creative tab |
| `saltMirror` | Salt Mirror |
| `mirrorSalt` | Mirror Salt Flat |
| `saltBlock` | Salt Block |
| `mirrorBrine` | Mirror Brine |
| `mirrorBrineBucket` | Mirror Brine Bucket |

Custom names only change what you see; other players on a server still see the default names.

**[ripples]**

| Key | Default | Description |
|---|---|---|
| `enabled` | true | Ripples on/off |
| `inAllShallowWater` | false | Also show ripples in ordinary shallow water (1 block deep or less) |
| `scale` | 1.0 | Ripple size/strength |
| `range` | 96 | Distance (blocks) within which mobs/players make ripples |
| `splashDroplets` | false | Small splash droplets when landing from a jump |

**[mirror]**

| Key | Default | Description |
|---|---|---|
| `enabled` | true | Water mirror on/off |
| `withShaders` | true | Draw the mirror on top of the shader pack when using Oculus |
| `reflectivity` | 0.82 | Reflection strength looking straight down (lower shows more salt) |
| `grazingReflectivity` | 0.97 | Reflection strength toward the horizon |
| `shaderReflectivity` | 0.93 | Reflection strength looking down with shaders (raise it if salt or shadows show through at night) |
| `wobble` | 1.0 | Mirror wobble when walking (0 = none) |
| `blockRadius` | 24 | Radius for reflecting placed blocks (0 = off, lighter) |

---

## License

MIT. Made by BINGO.
