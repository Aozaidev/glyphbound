# Glyphbound: Hanzi Magic

Glyphbound is a NeoForge 1.21.1 gameplay mod built on Aozai Ink MC. It lets you draw handwritten Chinese characters to cast survival spells, enter ink arenas, place area seals, and reshape nearby terrain.

The mod is designed first for singleplayer survival. It uses Aozai Ink MC for handwriting recognition and Patchouli for the in-game guide book.

## Requirements

```text
Minecraft 1.21.1
NeoForge 21.1.230 or newer in the 21.1 line
Aozai Ink MC 0.1.0 or newer
Patchouli 1.21.1-93 NeoForge or newer
Java 21
```

## Current Features

Implemented glyphs:

```text
心 命 救 息 忍 净 坚 稳 隐 明 脉 力 怒 魄
入 出 劫
印 泉 山 裂 墨
```

Core systems:

```text
14 body glyphs for survival, recovery, scouting, and counterattack
Ink arenas through 入 and 出
Tribulation and staff upgrade progression through 劫
Area seals through 印
Instant and area healing through 泉
Temporary terrain through 山 and 裂
Toggleable ink fields through 墨
Ink core drops and anvil staff repair
Patchouli guide book: 执笔引
```

## How To Start

1. Craft a wooden ink staff with one plank over one stick in the short diagonal shape.
2. Hold the staff and press `G` to open the casting circle.
3. Draw `心` to gain temporary max health.
4. Draw `命` before dangerous trips for a cheap fatal-hit safety net.
5. Open `执笔引` in game for the full glyph list, staff tiers, arena rules, and seal combinations.

`G` writes onto yourself. `V` writes onto the world as an inscription circle. Most body glyphs use `G`; world and seal glyphs such as `印`, `泉`, `山`, and `裂` use `V`.

## Build

Build Aozai Ink MC first so the local API jar exists, then build Glyphbound:

```powershell
gradle -p ../aozainkmc build
gradle build
```

If using a normal Gradle install:

```powershell
gradle build
```

The release jar is written to:

```text
build/libs/glyphbound-0.1.0.jar
```

## License

MIT. See `LICENSE`.
