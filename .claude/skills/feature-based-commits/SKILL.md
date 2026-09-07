---
name: feature-based-commits
description: How to structure commits and pull requests as small, atomic, feature-scoped units instead of one giant commit or PR with thousands of unrelated changed lines. Covers commit message conventions and how to split an already-large uncommitted diff. Use this whenever about to commit multiple changes, when a diff spans more than one logical concern, when preparing a pull request, or when the user asks how to organize or split their changes.
metadata:
  category: workflow
---

# Feature-Based Commits

A single commit (or PR) that bundles five unrelated changes forces every future reader — a reviewer, `git blame`, whoever bisects a regression six months from now — to untangle which lines belong to which change. Splitting by feature/concern up front costs a few minutes now and saves that untangling for everyone who touches this history later.

## What "atomic" means here

One commit = one logical change that could, in principle, be reverted on its own without breaking something unrelated. Not one file, not one line — one *concern*. A commit that adds a new field to a form, updates its validation, and updates its test is one atomic commit even though it touches three files. A commit that adds that field *and* also fixes an unrelated typo in a different module is two commits pretending to be one.

**Test:** if you had to revert this commit, would anything unrelated break or disappear with it? If yes, it's bundling more than one concern.

## Identifying the boundaries in a large diff

Before committing, look at the full diff (`git diff` / `git status`) as a reviewer would, and group changes by the question they answer:

- Which lines exist because of the actual feature/fix being built?
- Which lines are drive-by cleanup, formatting, or an unrelated fix noticed along the way?
- Which lines are generated/config/lockfile changes that follow mechanically from the above?

Each group is a candidate commit. If a change genuinely can't be separated (a rename that also had to touch every call site) that's fine as one commit — the goal is logical clarity, not mechanical minimalism.

## Splitting an already-large uncommitted diff

- `git add -p` (or `git add --patch`) walks through the diff hunk by hunk, letting you stage only the pieces belonging to the current commit. Use `s` to split a hunk further if it mixes concerns within a few lines.
- For whole files that belong to one concern, `git add <file>` is enough.
- Commit that staged slice, then repeat `git add -p` on what's left for the next concern.
- If concerns are tangled within the *same lines* (not just the same file), it's usually not worth surgical splitting — commit it as one unit and note why in the message, rather than producing an artificial split that doesn't actually isolate anything.

## Commit message conventions

A message has two jobs: a one-line summary usable in `git log --oneline`, and (when the change isn't self-explanatory) a body explaining *why*, not what — the diff already shows what.

```
<type>(<scope>): <short summary, imperative mood, under ~70 chars>

<optional body: why this change, what it fixes or enables,
anything a reviewer needs but wouldn't get from the diff alone>
```

Common `<type>` values: `feat` (new capability), `fix` (bug fix), `refactor` (no behavior change), `test`, `docs`, `chore` (tooling/deps). Using a consistent type makes changelogs and history scans mechanical instead of requiring re-reading every diff.

**Example:**

```
feat(checkout): apply percentage discount codes to cart total

Previously only fixed-amount codes were supported. Adds a
DiscountStrategy per code type so new discount kinds don't require
touching the checkout flow itself.
```

Avoid messages like `fix stuff`, `wip`, or `update` — they give a future reader (including you) nothing to go on when scanning history for the commit that caused a regression.

## Sizing a pull request

Apply the same logic one level up: a PR should represent one reviewable feature or fix, not "everything I did this week." A reviewer can hold a few hundred changed lines with a clear story in their head; a five-thousand-line PR touching six unrelated areas gets rubber-stamped, not actually reviewed, because nobody can hold it all at once.

If a task naturally produces multiple unrelated changes (e.g., "add feature X" surfaced an unrelated bug Y), prefer separate PRs — fix Y first and merge it independently, then build X on top. This also means Y ships to production sooner, without waiting on X's review.

When a single feature is unavoidably large, look for a way to land it incrementally behind something inert (an unused code path, a feature flag) so each PR is still independently reviewable and revertable, rather than one commit that changes everything at once.
