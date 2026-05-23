# Glyphbound 60-Glyph Roadmap

This is the first full gameplay target: six directions, ten glyphs each. The goal is not to make sixty isolated skills. Each direction should share a few systems so new glyphs deepen the same rules instead of multiplying one-off code paths.

## Player And Body

```text
心  temporary max health and protective body aura
疾  movement speed, sprint momentum, traversal windows
力  attack knockback and heavy-tool interactions
息  breathing, drowning delay, calm recovery
骨  fall resistance, armor weight, impact handling
血  injury memory, risk/reward under low health
目  detection, glowing reveal, line-of-sight rules
耳  sound awareness, sculk/vibration interaction
手  reach, block interaction, tool handling
命  death prevention with strict cost and cooldown
```

## Terrain And Blocks

```text
山  fall protection and blast-stable marker zone
土  farmland, trampling, dirt-like block stability
石  mining resistance, reinforcement, tool wear
木  log/leaf growth, chopping, flammability tradeoffs
沙  falling blocks, suffocation, desert instability
田  crops, moisture, harvest timing
井  water source search, cauldron/farm support
桥  temporary path support and crossing rules
墙  entity blocking, explosion shielding, line control
穴  cave/underground sensing and local mining risk
```

## Heat And Light

```text
火  ignition and furnace acceleration
明  low-light reveal and hostile spawn pressure
炎  stronger heat with self-risk
灯  stable marker light utility without free torches
灰  fire aftermath, ash-like decay, fuel loss
炉  smelting/fuel specialization
日  sunlight, undead pressure, solar timing
烬  delayed burn memory and cleanup
烛  small ritual light, candles, detection radius
焦  overheat penalty, food/fuel transformation
```

## Water And Weather

```text
水  fire mitigation and fire spread cancellation
雨  rain-only crop and wetness systems
冰  freezing, slowing, powder snow, water cooling
潮  tidal radius, fluid adjacency, shoreline behavior
雾  visibility, mob targeting, ranged accuracy
露  morning moisture, crop support, light decay
泉  small water utility with recharge limits
霜  cold damage control, frost walker-like restraint
雷  storm/redstone/charged entity interaction
云  weather buffering, altitude, slow fall context
```

## Space And Boundary

```text
门  paired marker gates and temporary player access keys
界  boundary pushback and portal safety
路  pathfinding, road speed, village paths
阵  multi-marker formation detection
封  lockout, container/door restraint, anti-access
返  return point with strict cost
引  pull/attract entities or items in a narrow rule
隔  line-of-sight, projectile, sound boundary
域  chunk aura aggregation and local rule weight
隙  short blink/windowed crossing, not free teleport
```

## Entity Control

```text
缚  next-hit movement disruption and hostile marker slow
驯  taming/panic reduction, not instant control
怒  aggression redirection and risk
眠  sleep/calm windows, village/night rules
惧  fear, flee goals, light/context dependence
群  herd behavior and animal clustering
守  guard radius and target preference
猎  tracking, prey marking, loot risk
迷  pathfinding confusion and navigation cost
友  temporary trust, trading/social modifiers
```

## Implementation Policy

Start with one representative per direction, then expand within that direction's shared system. Avoid adding a new event path for every glyph unless there is no clean alternative.

Phase 001 has been reset to the most generally useful 身命卷 player-body glyphs:

```text
心 救 息 忍 坚 稳 隐 明 净 怒 脉
```

Terrain, redstone, summon, dungeon, parkour, and dimension-specific glyphs should wait until this ordinary-survival body layer feels good in game.
