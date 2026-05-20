# Glyph Effect Spec Format

Use this format for each assigned glyph. This file is generic by design; do not add concrete glyph examples here.

## Glyph

```text
Character:
Design Name:
One-Sentence Fantasy:
Primary Vanilla Systems:
Target Types:
```

## Core Behavior

Describe what the mark changes in the world. Focus on state, rules, and interactions rather than immediate visual effects.

## Activation

Define when the design becomes active:

```text
on mark attached
while mark exists
on scheduled tick
on random tick
on entity event
on block update
on item use
on weather change
on redstone change
on dimension travel
```

Choose only the triggers needed by the design.

## Context Modifiers

List environmental factors that can strengthen, weaken, redirect, or cancel the design:

```text
biome
dimension
weather
time of day
moon phase
nearby blocks
light level
fluid contact
entity type
held item
armor
redstone power
chunk mark density
nearby compatible marks
nearby conflicting marks
```

## Costs And Limits

State the required cost, limit, cooldown, decay, instability, or risk. Avoid free permanent power unless the effect is tiny and mostly systemic.

## Combinations

Describe how this design might combine with other marks without naming specific concrete glyphs. Use roles such as:

```text
growth mark
boundary mark
heat mark
movement mark
binding mark
memory mark
light mark
resource mark
```

## Data Model

List what state the implementation probably needs to store:

```text
per mark
per entity
per item stack
per block position
per chunk
per dimension
```

Keep the data small and serializable.

## Implementation Hooks

List likely implementation surfaces without code:

```text
InkMarkAttachedEvent
AozaiInkApi.marks()
entity tick
level tick
block event
living damage event
living heal event
attribute modifier
AI goal injection
item use event
right-click block event
random tick
scheduled tick
loot modifier
recipe condition
saved data
packet for client visuals
```

## Visual And Audio Feedback

Describe minimal feedback that helps players understand the rule. Avoid making VFX the main content.

## Failure Cases

List ways the design can fail, backfire, expire, or be removed.

## Test Plan

Give concrete gameplay test scenarios for the future implementer. No code.

## MVP Slice

Define the smallest version worth implementing first.

## Expansion Path

Define what can be added later after the MVP proves fun.
