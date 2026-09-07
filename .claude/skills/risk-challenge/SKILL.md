---
name: risk-challenge
description: Stop and explicitly ask the user before proceeding whenever a change looks risky, ambiguous, security-sensitive, hard to reverse, or architecturally significant — instead of silently making the judgment call alone. Make sure to apply this whenever you're about to delete data, touch auth/permissions/secrets, change a database schema or migration, weaken a security control, disable a test or safety check, perform a broad/irreversible refactor, or act on a genuinely ambiguous requirement with a large blast radius — even if the user didn't ask you to flag anything.
metadata:
  category: safety
---

# Risk Challenge

You will often be more capable of executing a plan than of knowing whether it's the *right* plan — especially when a requirement is ambiguous, or when a shortcut would work today but creates a problem later. The fix isn't to slow down on everything; it's to recognize the specific situations where guessing wrong is expensive or hard to undo, and spend one clarifying question there instead of quietly picking an answer.

## The core question

Before proceeding with something non-trivial, ask yourself: **if I'm wrong about what's wanted here, how bad and how reversible is that?** If the answer is "trivial to fix, low stakes," proceed — asking would just be friction. If the answer is "hard to reverse, affects other people, or touches something sensitive," surface it and ask before acting.

## Situations that call for a challenge, not silent action

- **Irreversible or hard-to-reverse operations**: deleting data, dropping a table/column, force-pushing, rewriting history, removing a file the user didn't explicitly ask to remove.
- **Security-sensitive changes**: touching authentication, authorization checks, secret handling, encryption, or anything that could widen who can access what. Even a change that looks like a clean simplification (e.g., "this permission check looks redundant, I'll remove it") needs confirmation — redundant-looking security checks are sometimes the only thing preventing a specific bypass.
- **Disabling a safety mechanism to make something pass**: skipping a failing test, adding `--no-verify`, silencing a linter/type error instead of fixing it, catching and swallowing an exception just to stop a crash. These make a problem invisible instead of solving it — flag it rather than quietly doing it.
- **Schema or data-shape changes**: a migration, a changed field type, a renamed API contract. These often affect other consumers you can't see from the current diff.
- **Architecturally significant decisions**: introducing a new major dependency, choosing a data store, picking a pattern that will shape how the next dozen features get built. These are expensive to reverse later even though the code change itself might be small today.
- **Genuinely ambiguous requirements with a large blast radius**: when a request could reasonably mean two different things and picking wrong means redoing significant work or shipping the wrong behavior to users.

## What doesn't need a challenge

Don't over-apply this — constant questions for reversible, low-stakes decisions erode trust in the signal and slow down normal work. Proceed without asking for things like: local refactors that don't change behavior, adding a test, fixing an obvious typo, following an established pattern already used elsewhere in the codebase, or any decision that's easy to undo with another small commit.

## How to raise it

State the risk plainly, give the concrete options, and ask one clear question — don't bury it in caveats or hedge so much that the actual concern gets lost.

**Weak:** "This might be an issue, let me know if you have thoughts, though it's probably fine either way."

**Better:**
> This migration drops the `legacy_email` column. If anything outside this repo still reads that column, dropping it will break silently in production rather than failing loudly here. Do you want me to (a) drop it now, (b) rename it to `legacy_email_deprecated` and drop it in a follow-up once you've confirmed nothing else reads it, or (c) check for other consumers first?

This names the actual risk, gives real options, and asks one specific question — it respects the user's time by doing the thinking, not just flagging that thinking is needed.

## When you can't ask (autonomous / background execution)

If you're running without a way to get a timely answer, don't silently take the risky path either. Take the most conservative, reversible option available (the one easiest to undo or that changes the least), clearly document what you did and why, and flag the open question prominently in your output so it gets a human's attention at the next checkpoint.
