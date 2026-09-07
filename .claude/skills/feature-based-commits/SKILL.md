---
name: feature-based-commits
description: How to branch and structure commits/pull requests as small, atomic, feature-scoped units instead of one giant commit or PR with thousands of unrelated changed lines. Covers when and how to create a new branch per feature or fix, branch naming, commit message conventions, and how to split an already-large uncommitted diff. Use this whenever starting a new feature or fix, about to commit multiple changes, when a diff spans more than one logical concern, when preparing a pull request, or when the user asks how to organize or split their changes.
metadata:
  category: workflow
---

# Feature-Based Commits

A single commit (or PR) that bundles five unrelated changes forces every future reader — a reviewer, `git blame`, whoever bisects a regression six months from now — to untangle which lines belong to which change. Splitting by feature/concern up front costs a few minutes now and saves that untangling for everyone who touches this history later.

## Branch per feature or fix

Start each piece of work — a feature, a bug fix, a refactor — on its own branch, cut from the up-to-date default branch (`main`/`master`/`develop`, whatever this repo uses). Don't commit directly to the default branch and don't pile unrelated work onto a branch that already has an open PR for something else. This is what makes atomic commits actually pay off downstream: a branch that maps to exactly one concern can be reviewed, tested, merged, and reverted independently — a branch mixing three concerns forces the same all-or-nothing bottleneck that oversized commits do, just one level up.

- **Before branching**, make sure the base is current (`git pull` / `git fetch` + rebase or merge) so the new branch doesn't start from stale history — this avoids painful conflicts later and keeps the diff focused on the actual change instead of also including catch-up noise from the base branch.
- **Naming**: a short, descriptive, kebab-case name that says what the branch is for, optionally prefixed by type and/or ticket id: `feat/discount-codes`, `fix/checkout-rounding-error`, `chore/bump-eslint`, or `JIRA-123-discount-codes` if the team keys work off ticket IDs. Avoid generic names like `patch`, `updates`, or your own username — six branches named `dave-fixes` are indistinguishable in a branch list.
- **One branch, one concern.** If, mid-branch, you find an unrelated bug worth fixing, don't fix it here — branch off the base again for that fix so it can ship and be reviewed on its own (see `feature-based-commits`' PR-sizing guidance below), then return to the original branch.
- **Keep it short-lived.** A branch that lives for weeks drifts further from the base every day, making the eventual merge harder and the diff harder to review. Prefer smaller branches merged sooner over one branch accumulating a feature's entire scope.
- **Before opening a PR**, sync with the base again (rebase or merge) so the diff shown for review is just this branch's actual changes, not also a pile of unrelated commits the base picked up in the meantime.

If a task's scope is genuinely ambiguous — is this one feature or several? — err toward more, smaller branches; splitting a branch that turned out to be too broad is easy, but a branch that grew into three unrelated concerns is hard to untangle after the fact without redoing the git history work described below.

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
