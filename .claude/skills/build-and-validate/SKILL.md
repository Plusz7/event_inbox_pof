---
name: build-and-validate
description: Verify a code change actually works before declaring it done — detect and run the project's linter, type-checker, build, and test suite rather than assuming success from reading the diff. Use this before saying a task is "done," "fixed," "implemented," or "ready" for any non-trivial code change, and whenever the user asks "does this work" or "did that pass." This applies regardless of language, build tool, or framework.
metadata:
  category: workflow
---

# Build and Validate

Reading code and knowing it's correct are two different skills, and only one of them is reliable. A change that looks right can still fail to compile, fail a type check, or break a test that exercises a path you didn't think about. Claiming success without running verification transfers the risk of being wrong onto whoever trusts that claim next — often the user, sometimes in production. Verify first, report second.

## Why this is a separate step from writing the code

It's tempting to treat "I wrote the fix" and "the fix works" as the same moment, especially for a change that seems small. They aren't. The gap between them is exactly where regressions live — an unrelated test that now fails, a type error in a file you didn't touch but that depends on the one you did, a lint rule that catches something real. Closing that gap is the entire point of this checklist.

## The checklist, in order

Run these in order — each one is cheaper than the next, so failing fast at a cheap step saves time:

1. **Lint / static analysis** — catches obvious mistakes (unused variables, unreachable code, style violations that hide real bugs) before spending time on anything slower.
2. **Type-check** — if the language has one (TypeScript, mypy, a Rust/Go compiler pass). Catches whole classes of runtime errors before any code executes.
3. **Build / compile** — does the project actually produce a working artifact. For interpreted languages without a separate build step, this may collapse into the next step.
4. **Test suite** — run the tests that cover the area you changed, and — for anything but a trivial or clearly isolated change — the full suite. A change can pass its own new test and still break something else.

Skip a step only if the project genuinely has no equivalent (e.g., no type checker configured) — don't skip a step because it's inconvenient or slow.

## Detecting how to run each step

Don't guess a command from habit (`npm test` in a Python project will just fail confusingly) — find out what this specific project actually uses:

- **Node/JS/TS**: check `package.json` `scripts` for `lint`, `typecheck`/`tsc`, `build`, `test`.
- **Python**: check for `pyproject.toml` / `tox.ini` / `Makefile` for configured tools (`ruff`/`flake8`, `mypy`/`pyright`, `pytest`).
- **Rust**: `cargo check`, `cargo build`, `cargo test`, `cargo clippy`.
- **Go**: `go vet`, `go build ./...`, `go test ./...`.
- **Any language**: a `Makefile`, `justfile`, or CI config (`.github/workflows/*.yml`, `.gitlab-ci.yml`) is often the most reliable source of truth — it's literally the commands the project already trusts to gate merges. Prefer replicating those over inventing your own.

If none of this is discoverable, ask rather than silently assume — running the wrong command and reporting its (irrelevant) success is worse than admitting you couldn't verify.

## When something fails

- Read the actual error, not just the fact that it failed. Fix the root cause.
- Don't reach for a shortcut that makes the failure go away without fixing the underlying problem: skipping the failing test, loosening a type to `any`, adding `--no-verify`, or silencing a lint rule instead of addressing what it flagged. These make the next verification pass lie by construction.
- If a failure is clearly pre-existing and unrelated to your change (confirm by checking it also fails on a clean checkout / before your change), it's fine to note that explicitly rather than block on fixing something out of scope — but say so, don't stay silent about it.

## Reporting the result

State what you actually ran and what happened — "ran the test suite, all 142 passing" is verifiable and honest; "should work now" is a guess dressed up as a fact. If you could not run verification (no test suite exists, no access to execute code, a UI change that needs a browser), say that explicitly instead of implying you checked when you didn't.
