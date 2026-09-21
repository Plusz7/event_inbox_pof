---
name: exception-handling
description: How to handle errors and exceptions properly — catch only what you can meaningfully act on, never swallow a failure silently, preserve the original cause when wrapping, and choose deliberately between failing fast and degrading gracefully. Make sure to apply this whenever writing or reviewing code that can fail (I/O, network calls, parsing, external APIs, database access), whenever you're about to write a try/catch, and whenever the user asks about error handling, retries, or why a failure was silent.
metadata:
  category: code-quality
---

# Exception Handling

A swallowed exception is a landmine: the code that hit the failure looks fine, everything downstream that depended on it silently gets wrong or missing data, and the person who eventually finds the bug has no stack trace, no log line, and no idea where to start. The goal of this skill is to make failures loud enough to be debuggable and precise enough to be actionable, without drowning real signal in noise from failures nobody can do anything about.

## Catch narrowly, only what you can act on

Catch a specific exception type when you have something meaningful to do about it — retry, fall back to a default, translate it into a domain-specific error the caller understands. Catching a broad `Exception`/`Throwable` and treating every possible failure the same way (a network timeout, a null pointer, an out-of-memory error) usually means none of them are actually being handled correctly — it's handling "something went wrong," not the specific thing that did.

**Smell:** `catch (Exception e) { return null; }` — this treats a transient network blip identically to a programming bug, and returns a value indistinguishable from a legitimate "not found."

**Better:** catch the specific exception type(s) you expect from this call, and let anything else propagate — an exception you didn't anticipate is exactly the one you want visible, not silently absorbed into generic handling.

## Never swallow a failure silently

An empty catch block, or a catch that just logs at `debug` and moves on as if nothing happened, hides the failure from everyone — including future you. At minimum:

1. Log it with enough context to act on later (see the `observability-and-logging` skill) — at a level that matches its real severity, not buried at debug just to make it quiet.
2. Decide explicitly what happens next: rethrow, return a well-defined fallback, or propagate a translated error. "Silently continue" should never be the unexamined default — it should be a deliberate choice you could explain if asked.

If the answer really is "this specific failure is safe to ignore," say so in a comment explaining why — that's exactly the kind of non-obvious reasoning worth a comment (see `clean-code`), because otherwise the next reader can't tell "ignored on purpose" from "someone forgot to handle this."

## Preserve the original cause

When you catch an exception and throw a new one (to translate a low-level error into a domain-specific one, for example), chain the original as the cause rather than discarding it. Most languages support this directly (`throw new DomainError("...", cause: e)`, exception chaining, `%w` wrapping in Go, etc.). Losing the original stack trace turns a debuggable failure into a guessing game — the new exception tells you *that* something failed, the original tells you *why*.

## Fail fast vs. degrade gracefully — choose deliberately

Not every failure should be handled the same way, and the choice should be intentional, not accidental:

- **Fail fast** when continuing would produce a worse outcome than stopping — invalid configuration at startup, a required dependency that's unreachable, data that's already known to be corrupt. Let it crash loudly rather than limping forward on bad assumptions.
- **Degrade gracefully** when the failing part is genuinely optional — a recommendation service being down shouldn't take down checkout; falling back to a cached value or a sensible default is often better than failing the whole request.

The mistake to avoid in both directions: don't wrap something in a broad try/catch "just in case" without deciding which of these two you actually want — that produces the worst of both, a failure that's neither loud nor genuinely handled.

## Don't use exceptions for expected control flow

Reserve exceptions for genuinely exceptional, unexpected conditions — not for outcomes that are a normal, anticipated part of the function's contract. If "not found" or "validation failed" is a routine, expected result of calling something, prefer a return value (an optional/nullable type, a `Result`/`Either` type, a status field) over throwing. Throwing for expected outcomes makes normal control flow harder to follow and is typically slower, since exception handling machinery isn't designed for the hot path.

## Custom exception types, when the caller needs to react differently

If different failure causes should lead callers to different behavior (retry this one, don't retry that one, show this message vs. that one), give them distinct exception types rather than one generic exception with a string message the caller would have to pattern-match on. If callers only ever do the same thing regardless of cause, a generic exception is fine — don't create a type hierarchy nobody branches on.

## Catch where you have enough context to decide

Catch close to a boundary (an API request handler, a job runner, a CLI entrypoint) where you actually know what the right user-facing or system-facing response is — not deep inside business logic that doesn't yet know how its caller wants to react to failure. Business logic should generally let exceptions propagate upward to whoever does know.

## Clean up resources regardless of the failure path

Use the language's structured mechanism for guaranteed cleanup (`try/finally`, `with`/context managers, `using`, `defer`) rather than relying on cleanup code that only runs on the success path. A connection, file handle, or lock that isn't released on the exception path leaks — often invisibly, until the pool runs out under load.
