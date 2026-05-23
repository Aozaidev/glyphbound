# Archived: Implementation Phase 001

This phase resets the first implementation pass to **身命卷** only.

The goal is to make handwritten player marks useful in ordinary survival play before adding terrain, dungeon, parkour, redstone, summon, or dimension-specific systems.

## Scope

Implemented glyphs:

```text
心 救 息 忍 坚 稳 隐 明 净 怒 脉
```

Do not implement extra glyphs in this phase. Do not modify Aozai Ink MC. Do not add new blocks, entities, dimensions, recipes, loot tables, textures, models, or sounds.

## Design Rule

These are not generic potion buffs. Each glyph should rewrite a temporary body rule:

- `心`: increases max health without filling it.
- `救`: prevents one lethal hit, then leaves the player weak and slow.
- `息`: rewards stillness outside combat with slow recovery.
- `忍`: converts most incoming damage into delayed ink wound damage; repeated hits keep the wound open before settlement begins.
- `坚`: creates a heavy defensive stance with movement cost.
- `稳`: resists knockback, explosion push, slippery movement, and water push.
- `隐`: reduces detection but the current mark fails on attacks, block interaction, item use, and block breaking.
- `明`: reveals dark-environment threats and useful clues without granting night vision.
- `净`: cleans harmful effects and dissolves part of delayed ink wound damage with escalating exhaustion pressure.
- `怒`: accumulates from final received damage, converts stored pain into damage reduction, attack damage, and periodic shockwaves. Recasting while active keeps the pools; recasting after expiry resets reduction and attack bonus.
- `脉`: reports vague nearby life direction and strength, not precise wallhack vision.

## Implementation Notes

- Read marks through `AozaiInkApi.marks()` and `InkMarkAttachedEvent`.
- Support `InkTargetType.PLAYER` first.
- Keep effects temporary and tied to active marks.
- Use vanilla/NeoForge hooks, attributes, ticks, damage events, and small state maps.
- Avoid client-only code in this phase except minimal vanilla feedback such as actionbar text or particles.
- Expired marks must stop affecting the player.
- Repeated marks must not stack attributes infinitely.
- Shared damage/death/state behavior must follow `docs/system-interaction-workflow.md`.

## Acceptance Criteria

- `gradle compileJava` passes.
- The addon still has required dependency metadata for `aozainkmc`.
- No changes are made to `../aozainkmc`.
- The old mixed `心 山 火 水 门 缚` implementation is removed.
- All ten phase glyphs have at least one ordinary survival behavior.
- Debug logs make important activations visible.
