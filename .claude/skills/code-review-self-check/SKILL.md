---
name: code-review-self-check
description: Self-review checklist to run over your own diff before calling it done or opening a pull request — dead code, naming that no longer makes sense, accidental unrelated changes, and unnecessary complexity introduced along the way. This is about whether the change is well-written, complementary to the build-and-validate skill which checks whether it actually runs. Use this after implementing a feature or fix and before declaring the task complete.
metadata:
  category: code-quality
---

# Code Review Self-Check

`build-and-validate` answers "does this work." This skill answers a different question: "if someone else reviewed this diff cold, would they have any notes?" Passing tests doesn't mean a change is well-written — it's easy to leave behind debug leftovers, a name that stopped matching what it names, or scope creep that a real reviewer would flag immediately. Doing this pass yourself, before anyone else sees the diff, catches the easy stuff before it costs someone else review time.

## Look at the diff, not just the final files

Read the actual diff (`git diff`) rather than eyeballing the finished files — the diff shows you exactly what changed, which is what a reviewer will actually look at, and it surfaces things that are invisible when just reading the final state of a file.

## The checklist

- **Dead code.** Any commented-out code, an old implementation left "just in case," a debug `print`/`console.log`, or a function that's no longer called now that the change is in? Remove it — version control already remembers it if it's ever needed again.
- **Naming still makes sense.** If a variable, function, or file was renamed in spirit but not in name during this change (a `list` that's now filtered to one item, a `handleClick` that no longer handles a click), fix the name. A stale name actively misleads the next reader.
- **Scope matches intent.** Does the diff contain only what the task actually required? An unrelated formatting pass across a whole file, a fix for a different bug noticed along the way, or a drive-by refactor bundled into this change all belong in a separate commit (see `feature-based-commits`) — bundling them makes this diff harder to review and harder to revert cleanly if something's wrong.
- **No unnecessary complexity introduced.** Did the implementation reach for an abstraction, a new configuration option, or a generic mechanism that only one caller actually uses? If the simpler, more direct version would have worked, prefer it — see the `clean-code` skill's YAGNI guidance.
- **Error handling matches what can actually happen.** Not "handle every possible exception defensively," but also not silently swallowing a failure that should surface. Check that failure paths do something a caller would expect, and that no exception is caught and ignored just to stop a crash message.
- **Comments still earn their place.** Any comment that just restates what the code does (rather than explaining a non-obvious why) should go — see `clean-code`. Any comment describing behavior that this diff just changed needs to be updated, not left stale.
- **Tests actually match the change.** If new tests were added, do they test the real behavior (see `testing-strategy`), or do they just re-assert whatever the implementation happens to currently do? A test that would still pass after reverting the fix isn't testing the fix.

## How to use this without becoming its own busywork

This is a fast pass, not a second full implementation cycle — most changes will have zero or one item worth fixing. If everything on the list is clean, say so and move on; don't manufacture nitpicks to seem thorough. The goal is catching the two or three things a real reviewer would actually comment on, not producing a checklist-shaped essay for every diff.
