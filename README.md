# Glyphbound

Glyphbound, display name `墨蕴·万象`, is the official gameplay addon workspace for Aozai Ink MC.

This repository hosts the addon shell, design workflow, and first gameplay implementation pass for handwritten glyph effects.

## Relationship To Aozai Ink MC

Aozai Ink MC is the foundation:

```text
handwriting -> OCR -> InkMark -> API/events
```

Glyphbound is the gameplay layer:

```text
InkMark -> vanilla system interaction -> long-term world consequences
```

The addon should not change the foundation mod. It should read marks through the public API and respond through ordinary NeoForge/Minecraft systems.

## Current Status

Implemented:

```text
NeoForge 1.21.1 addon shell
Required dependency metadata for aozainkmc
Agent design workflow
身命卷 phase 001 body glyphs: 心 救 息 忍 坚 稳 隐 明 净 怒 脉
```

Not implemented yet:

```text
Specific glyph mechanics
Blocks
Items
Entities
Dimensions
Recipes
Loot tables
Progression systems
Terrain/redstone/summon/dimension glyph modules
```

## Build

Build Aozai Ink MC first so its local API jar exists:

```powershell
cd D:\projectmc\aozainkmc
..\.gradle-dist\gradle-8.14.3\bin\gradle.bat build
```

Then build this addon:

```powershell
cd D:\projectmc\glyphbound
..\.gradle-dist\gradle-8.14.3\bin\gradle.bat build
```

If using a normal Gradle install:

```powershell
gradle build
```

## Design Workflow

1. Give a low-cost agent one batch assignment from `docs/batches/`.
2. Require the agent to output Markdown design specs only, using `docs/glyph-effect-spec-format.md`.
3. Do not let the agent write Java code, data packs, assets, or recipes in the first pass.
4. Review the output with `docs/review-rubric.md`.
5. Only approved specs become implementation tasks.

## License

MIT. See `LICENSE`.
