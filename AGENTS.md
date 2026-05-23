# Glyphbound Agent Guide

This repository is the official gameplay addon workspace for Aozai Ink MC.

Your first job is design, not implementation. Do not add Java classes, JSON data, textures, recipes, loot tables, registries, dimensions, or assets unless a human explicitly promotes an approved spec into an implementation task.

## Product Direction

Glyphbound should prove that handwritten glyph marks can reshape vanilla Minecraft systems in ways that feel systemic, combinable, and world-aware.

The addon must avoid shallow one-shot effects. A mark should usually interact with at least one real Minecraft system such as:

```text
attributes
entity goals
block states
block updates
scheduled ticks
random ticks
weather
light
fluids
redstone
items
durability
recipes
loot tables
villagers
structures
chunks
dimensions
world saved data
```

## Hard Rules

- Do not modify Aozai Ink MC.
- Do not redefine the handwriting or OCR system.
- Do not implement effects during the design phase.
- Do not create a generic buff list.
- Do not make a mark mean only one flat action.
- Do not mention concrete glyphs in reusable tutorials or templates.
- Mention concrete glyphs only inside explicit batch assignment files.
- Every design must support counterplay, cost, decay, context, or risk.
- Every design must say which `InkTarget` types it reads.
- Every design must say which NeoForge or Minecraft hooks it probably needs.
- Before implementing or changing any glyph that touches shared systems such as damage, death, attributes, hunger, movement, visibility, inventory, redstone, AI, or persistent state, follow `docs/system-interaction-workflow.md`.
- Do not let shared-system behavior depend on incidental event-handler order. Define the pipeline, accumulation value, final applied value, expiry, stacking, and reset behavior first.
- Any new custom state map, timer, cooldown, stack, delayed damage pool, or transient attribute modifier must be cleared by the relevant reset path, especially death/death clone/respawn/logout/server-stop for player state. Static process memory can survive switching saves in the same client session.

## Expected Design Output

Use `docs/glyph-effect-spec-format.md` exactly. One file or section per assigned glyph is acceptable.

The output should be detailed enough for a programmer to implement later, but it must not include code.

## Review Gate

A design is not approved until it passes `docs/review-rubric.md`.

Rejected designs are normal. Rewrite toward deeper interaction with vanilla systems instead of adding more particles, damage, healing, or potion effects.
