---
name: clean-code
description: Guidelines for writing clean, readable, maintainable code — naming, function size, complexity, comments discipline, and core design principles (SOLID, DRY, KISS, YAGNI). Use this whenever writing new code, reviewing a diff, naming variables/functions/classes/files, deciding whether to add an abstraction or a new layer, or when code is getting hard to follow — regardless of language or framework. Make sure to apply this any time you're about to write non-trivial code, not only when the user explicitly asks for "clean code" or "best practices."
metadata:
  category: code-quality
---

# Clean Code

Code is read far more often than it is written — by teammates, by you in six months, by whoever debugs it at 2am during an incident. Optimizing for the writer's convenience over the reader's is almost always the wrong trade. Everything below follows from that one idea.

## Naming

A name is documentation that never goes stale — if it's honest.

- Reveal intent: `daysSinceLastLogin` beats `d`. A reader should understand what a thing is for without opening its definition.
- Don't lie: a name that no longer matches what the code does (a `list` that's now a `Set`, a `validate` that also mutates) is worse than no name at all — fix the name or fix the code.
- Use the vocabulary of the domain, not of the implementation. Prefer `Invoice.isOverdue()` to `Invoice.checkStatusFlag()`.
- Pick one word per concept and stick to it across the codebase (`fetch` vs `get` vs `retrieve` — choose one meaning per verb).
- Length should match scope: a loop index in a 3-line block can be `i`; a class field or exported function needs a name that stands on its own.

## Functions

- A function should do one thing, and its name should say what that thing is. If you need "and" to describe it, it's probably two functions.
- Small enough to read without scrolling is a good default — not a hard line count, but a signal. If you can't summarize a function in one sentence, it's doing too much.
- Prefer few parameters. More than three or four is a hint that the arguments want to be a single object, or that the function should be split.
- Avoid hidden side effects. A function called `getUser` that also writes to a database will betray whoever calls it expecting a pure read. If it mutates state, the name should say so.

## Reducing complexity

- Prefer early returns / guard clauses over deeply nested `if`s. Handle the exceptional or invalid case first and return, so the main logic reads at the top level instead of three indents deep.
- Flatten before you abstract. A long but flat function is usually easier to follow than a short one that jumps through four layers of indirection to do the same thing.
- Boolean flags as parameters are a smell — `render(user, true)` forces the reader to go check what `true` means. Prefer separate functions or a named option.
- If a comment is needed to explain *what* a block of code does, that's usually a sign the block should be extracted into a well-named function instead — the function name becomes the comment.

## Comments

Write comments to explain **why**, never **what**. Well-named code already says what it does; a comment that restates it just adds a second thing that can go out of sync with the first.

Worth a comment:
- A non-obvious constraint ("must run before X because the API rate-limits at Y")
- A workaround for a specific external bug, with a reference if possible
- An invariant that isn't visible from the code itself
- A decision that looks wrong at first glance but isn't (and why)

Not worth a comment: restating the function name, narrating control flow (`// loop through users`), or leaving a changelog of what used to be here.

## Duplication vs. premature abstraction

Both extremes cost you. Two near-identical blocks that will evolve independently are fine as duplication — a shared abstraction between them would force unrelated changes to touch the same code. But three or more copies of genuinely the same logic (the "rule of three") usually means it's time to extract it.

Don't build an abstraction for a need you're imagining might arrive later. A generic plugin system for a feature that has exactly one implementation is speculative complexity — it costs real maintenance now for a flexibility that may never be used. Add the abstraction when the second real use case actually shows up, not before.

## Going deeper

For the classic design principles that clean code rests on:

- [references/solid.md](references/solid.md) — Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion, with concrete before/after examples.
- [references/other-principles.md](references/other-principles.md) — DRY, KISS, YAGNI, Law of Demeter, composition over inheritance.

Read these when a specific design decision needs justification (e.g., "should this be one interface or two?", "is this violating SRP?") — you don't need to hold all of SOLID in your head for every small edit.
