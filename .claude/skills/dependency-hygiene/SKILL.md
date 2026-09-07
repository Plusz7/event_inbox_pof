---
name: dependency-hygiene
description: What to check before adding a new dependency (npm/pip/cargo/gem/etc.) instead of installing the first package that seems to fit — maintenance status, license, known vulnerabilities, and footprint. Make sure to apply this whenever about to run an install command, add an entry to package.json/requirements.txt/Cargo.toml/etc., or when the user asks to "add a library" for something.
metadata:
  category: workflow
---

# Dependency Hygiene

Every dependency you add becomes something the project now trusts with code execution, and something someone has to keep updated, patched, and compatible for as long as the project lives. That cost is invisible at `npm install` time and very visible eighteen months later when it's unmaintained, has a known vulnerability, or conflicts with an upgrade you actually need. A minute of vetting now is cheaper than that later bill.

## First question: do you need a dependency at all?

Check whether the standard library, a framework feature already in use, or a dozen lines of your own code covers the actual need. A dependency is justified when it saves meaningfully more effort or risk than writing it yourself (date/timezone handling, cryptography, parsing a complex format) — not for something trivial like "left-pad a string" or a one-line utility.

## Before adding one, check

- **Is it actively maintained?** Look at the last release date and recent commit activity. A package with no updates in years, or with open issues piling up unanswered, is a package you'll eventually have to fork, patch yourself, or replace under time pressure.
- **License compatibility.** Confirm the license (MIT, Apache-2.0, BSD are typically safe defaults; GPL/AGPL and anything unusual need a deliberate decision, not an assumption) is actually compatible with how this project is distributed/used. This matters more for anything shipped to customers or open-sourced than for purely internal tooling, but check either way rather than assuming.
- **Known vulnerabilities.** Run the ecosystem's audit tool before or right after adding it (`npm audit`, `pip-audit`, `cargo audit`, `bundler-audit`, etc.) and check it doesn't pull in something with an open advisory.
- **Transitive dependencies.** A package with one purpose but fifty transitive dependencies is a much larger attack surface and update burden than its own size suggests. Check what it actually pulls in, not just its own footprint.
- **Popularity as a signal, not a guarantee.** Download counts and star counts are a weak proxy for "other people have found and reported the obvious problems" — useful as a sanity check, not a substitute for the checks above.

## Pin and lock

Commit the lockfile (`package-lock.json`, `poetry.lock`, `Cargo.lock`, etc.) so installs are reproducible across machines and CI. An unpinned dependency tree means "works on my machine" can quietly become true in the worst way — a teammate or CI pulls a newer transitive version with a breaking change nobody asked for.

## Avoid installing globally when a project-local install works

A global install escapes the project's lockfile entirely — it won't be reproduced on another machine or in CI, and "works for me" stops meaning anything. Prefer project-local installs and, if a CLI tool is genuinely needed system-wide, document that requirement explicitly rather than relying on it silently being present.

## When it's genuinely security- or infrastructure-sensitive

Adding a dependency that will handle secrets, run with elevated permissions, or sit in a critical path (auth, payments, crypto) is exactly the kind of decision the `risk-challenge` skill calls out — vet it thoroughly and, if there's any doubt, surface the choice and the trade-offs to the user rather than deciding alone.
