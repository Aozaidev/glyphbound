# Review Rubric

Use this rubric before any glyph spec becomes an implementation task.

## Reject Immediately

Reject the design if it:

- Is only a potion effect with handwriting attached.
- Is only damage, healing, knockback, or particles.
- Ignores `InkTarget`.
- Ignores world context.
- Cannot combine with other systems.
- Requires modifying Aozai Ink MC.
- Needs a new framework when vanilla or NeoForge hooks are enough.
- Has no cost, limit, decay, or counterplay.
- Is impossible to test in a small MVP.
- Touches a shared gameplay pipeline but does not define interaction order, accumulation values, expiry, and reset behavior.

## Strong Design Signals

Prefer designs that:

- Change a vanilla rule rather than only spawning a visible effect.
- Use block states, entity state, item state, chunk state, or dimension state.
- Give different results in different environments.
- Create readable player choices.
- Can be implemented in a narrow first slice.
- Can later scale into deeper progression.
- Makes the foundation mod look useful to other developers.

## Implementation Readiness

Before implementation, the spec must answer:

```text
What event or tick path starts it?
What state does it read?
What state does it write?
How does it expire?
How does a player notice it?
How can a player misuse it?
What is the smallest test case?
If this touches damage/death/resources/state, what is the exact pipeline order?
What clears all custom state on death, expiry, recast, or failure?
```

## Final Verdict Format

```text
Verdict: approve / revise / reject
Reason:
Required changes:
Implementation risk:
MVP priority:
```
