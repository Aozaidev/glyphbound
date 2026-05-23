# System Interaction Workflow

Use this workflow before implementing or changing any glyph that touches shared gameplay systems.

Glyphbound effects are not isolated skills. They can modify the same vanilla event path, resource pool, player state, death lifecycle, or world rule. Before editing code, define the interaction order and reset behavior explicitly.

## Required Before Code

For any glyph that touches damage, healing, death, attributes, hunger, movement, visibility, inventory, item drops, experience, redstone, block replacement, entity AI, or persistent state, write down:

```text
Which vanilla hook starts this?
Which vanilla reductions or rules have already happened?
Which Glyphbound effects run before it?
Which Glyphbound effects run after it?
What exact value is counted for progression or accumulation?
What exact value is finally applied to the player/world?
What state is stored?
When does that state expire?
What clears it on death, dimension change, logout, server stop, client logout, or recast?
Can the same effect stack, refresh, pause, or fail?
```

Do not rely on incidental Java method order as design. If multiple glyphs use the same hook, create a named local pipeline in code and keep the comments/docs aligned with that pipeline.

## Damage Pipeline

Current body glyph damage order:

```text
1. Vanilla damage enters Minecraft.
2. Vanilla armor, enchantment, potion, shield, and similar reductions run.
3. NeoForge LivingDamageEvent.Pre fires with post-vanilla, pre-health damage.
4. Glyphbound golden body from 救 checks first. If active, final damage is 0.
5. 忍 moves part of the post-vanilla damage into the ink wound pool.
6. 怒 reduces the remaining damage using already accumulated rage reduction.
7. 命/救 checks whether the remaining damage would be lethal. If yes, lock to 1 health, clear the configured portion of ink wounds, and start golden body.
8. 怒 records the final damage that will actually be applied.
9. Minecraft applies the final health loss and fires post-damage behavior.
```

Rules:

- Accumulation uses final damage after vanilla, 忍, and 怒 reduction, not original incoming damage.
- 救 golden body blocks all damage while active, including magic and delayed damage.
- 救 triggering clears part or all of current ink wounds according to staff tier so old delayed damage cannot immediately kill the saved player.
- 忍 delayed damage must not recursively create more ink wounds.
- Death, death clone, respawn, logout, and server stop must clear all custom Glyphbound body state and active player marks.

## State Reset Checklist

When adding a new stored state map, timer, stack, cooldown, or counter, update the shared reset path.

The reset path must clear:

```text
death
death clone
respawn
player logout
server stop / world close
client logout from a server
expired effect
explicit recast failure if the glyph design says the mark is spent
```

Also remove any transient attribute modifiers owned by the effect. Vanilla potion effects normally clear on death, but Glyphbound maps and Aozai player marks are process memory and must be cleared explicitly. Do not assume saving, quitting, or switching worlds will reset static maps unless a lifecycle event does it.

Exception: `魄` deliberately creates one short-lived death recovery record after death drops are generated. It expires after its recovery window or is consumed by one successful cast, and server stop clears it with the rest of process-local state.

## Stacking Checklist

For every stackable or refreshable effect, define:

```text
stack increment
maximum stacks
refresh behavior
whether expiry is per-stack or shared
what happens when recast after expiry
what happens on death
```

If a visible stat is modified through attributes, prefer one stable modifier id with a recomputed amount instead of adding one modifier per mark.

## Current Body Glyph Stacking Notes

```text
心
- Recast while active adds one max-health stack and refreshes shared expiry.
- Recast after expiry starts from zero stacks.

魄
- Death tracks item entity UUIDs from the real death drops and reserves part of dropped XP into a soul imprint.
- Casting 魄 after respawn groups existing tracked item entities by item plus components, chooses a percentage of categories, then removes a percentage of each chosen category from the world item stacks.
- One death imprint can be consumed once; expired, destroyed, picked-up, or despawned drops cannot be recovered.

怒
- Recast while active extends/refreshes the active window and keeps accumulated reduction, attack bonus, and shockwave hit count.
- Recast after expiry resets reduction, attack bonus, and shockwave hit count.
- Death, death clone, respawn, logout, and server stop clear all rage state.
```

## Verification Checklist

After implementation, test at least one interaction case, not only the isolated glyph.

Examples:

```text
damage glyph + death
damage glyph + delayed damage
defense glyph + lethal hit protection
visibility glyph + attack/interact/recast
stackable glyph + expiry + death
cleanse glyph + vanilla negative effect + custom delayed state
```

Run `gradle build` after the change.
