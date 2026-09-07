---
name: refactoring-safely
description: How to refactor code in small, reversible steps while keeping tests green throughout, instead of attempting a large "big bang" rewrite. Use whenever restructuring existing code, renaming/moving things across files, changing an internal pattern, cleaning up a messy module, or migrating to a new approach without intentionally changing external behavior.
metadata:
  category: workflow
---

# Refactoring Safely

Refactoring means changing the structure of code without changing its behavior. The moment behavior changes too, you're no longer refactoring — you're adding a feature or fixing a bug wearing a refactor's clothes, and you've lost the one thing that made the refactor low-risk: the ability to check "did I break anything" by re-running the same tests before and after.

## Get a safety net before you start

You can't safely refactor what you can't verify. Before restructuring:

- If tests already cover the behavior you're about to touch, run them first and confirm they're green — that's your baseline.
- If they don't, write a small number of characterization tests first: tests that pin down current behavior (including quirks) as it exists today, not as it "should" be. The goal isn't perfect coverage, just enough to notice if the refactor accidentally changes an observable outcome.
- If genuinely nothing can be tested (no test infra, no time), that's a signal to keep steps even smaller and rely on manual verification after each one — not a reason to skip verification altogether.

## Work in small, reversible steps

Each step should be small enough that if it breaks something, the cause is obvious from the diff alone — and you can revert just that step without losing the others.

- One mechanical change at a time: extract this function, rename that variable, move this class — not all three interleaved in one sweep.
- Run the tests (or the relevant subset) after each step, not just once at the end. Catching a break immediately, while you still remember exactly what you just changed, is far cheaper than catching it after ten more steps piled on top.
- Commit after each green step (see the `feature-based-commits` skill). A refactor that's a sequence of small green commits can be bisected and partially reverted if something surfaces later; one giant refactor commit can't be.

## Don't mix refactoring with behavior changes

If, partway through a refactor, you spot a genuine bug or want to improve behavior, resist doing it in the same step. Finish the structural change, confirm it's still green, commit it — then make the behavior change as its own separate, clearly-labeled step. Mixing the two means that if something breaks, you can't tell whether the structure move or the behavior change caused it, and a reviewer can't verify either one cleanly.

## Avoid the big-bang rewrite

A rewrite-from-scratch of a non-trivial module or system is tempting when the existing code feels hopeless, but it carries a specific, well-documented risk: it takes far longer than estimated, blocks all other work on that area in the meantime, and — because nothing runs in production until it's entirely done — throws away the safety net of incremental verification for the entire duration.

Prefer incremental strangulation: keep the old code running, build the new path alongside it, migrate one caller or one code path at a time, verify each migrated piece in isolation, and only remove the old code once nothing depends on it anymore. This means the system is shippable and testable at every point along the way, not just at the (uncertain) end.

## When you're not sure a step preserved behavior

Stop and check rather than pushing forward on the assumption it's probably fine. Re-run the tests, diff the actual output before/after for a few real inputs, or — if this is a genuinely risky step (see the `risk-challenge` skill) — surface the uncertainty rather than guessing. It's far cheaper to catch a behavior change one step after it happened than to discover it five steps later and have to figure out which of five changes caused it.

## If you get stuck mid-refactor

If a step turns out to be bigger or riskier than expected and you're not confident in the current state, the safe move is to revert to the last known-green commit rather than pushing forward from an uncertain state. A small amount of redone work is cheap; debugging from a state where you don't trust what's currently true is not.
