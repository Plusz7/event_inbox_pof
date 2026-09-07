---
name: testing-strategy
description: How to choose between unit, integration, and behavioral/BDD tests, structure them well, and avoid brittle or low-value tests. Covers the test pyramid, given-when-then structure, descriptive test naming, and mocking guidance. Use this whenever writing new tests, deciding what kind of test a change needs, reviewing test coverage, or when the user asks about test strategy, TDD, or why a test suite is slow or flaky — not just when they explicitly say "write tests."
metadata:
  category: testing
---

# Testing Strategy

Tests exist to let you change code with confidence. A test suite that's slow, flaky, or tightly coupled to implementation details fails at that job even if it has high coverage — it becomes something people route around instead of trust. Every choice below is in service of "can I change this code and trust the tests to tell me if I broke something."

## The test pyramid — picking the right level

Not every behavior needs the same kind of test. Roughly, from most to least of each:

- **Unit tests** — test one function/class in isolation, no I/O, no network, no real database. Fast (milliseconds), so you can run thousands of them constantly. Use for business logic, edge cases, calculations, branching — anything where you want to enumerate many input/output combinations cheaply.
- **Integration tests** — test that two or more real pieces actually work together (your code + a real database, your code + another internal service). Slower, fewer of them. Use for the seams: does this repository actually persist correctly against the real schema, does this API client parse the real service's response shape.
- **Behavioral / end-to-end tests** — test a full user-facing flow through the system as a user would experience it. Slowest, fewest of them. Use for the handful of critical paths (checkout, login) where you need confidence the whole system works together, not just its parts.

A healthy suite looks like a pyramid: many unit tests, a moderate number of integration tests, a small number of end-to-end/behavioral tests. If it's inverted — few unit tests, tons of slow end-to-end tests — the suite will be slow and painful, and people will stop running it locally.

**Rule of thumb for picking a level:** ask "what's the cheapest test that would actually catch this bug?" A bug in a discount calculation belongs in a unit test. A bug where the API returns 200 but the row never landed in the database belongs in an integration test. A bug where the checkout button doesn't advance to payment belongs in a behavioral test. Don't reach for end-to-end just because it's more "realistic" — it's realistic *and* expensive, so spend it only where the seam being tested can only be exercised end-to-end.

## Behavioral tests: given-when-then

Structure behavioral (and often unit) tests around a scenario, not a mechanism:

```
Given a cart with two items totaling $50
When the user applies a 10% discount code
Then the cart total becomes $45 and the discount is itemized on the receipt
```

This maps directly onto test code structure — arrange (given), act (when), assert (then) — and it keeps the test readable as a specification of behavior, not a trace of implementation steps. A test file should read like a list of the rules the system actually follows.

Name the test after the scenario, not the method under test: `returns_45_dollars_when_10_percent_discount_applied_to_50_dollar_cart` tells you what broke from the test name alone, in a way that `test_applyDiscount_2` never will.

## Unit tests

- One behavior per test. If a test has multiple unrelated assertions and one fails, you don't know what actually broke without reading the whole test.
- Test behavior, not implementation. Assert on the return value or observable side effect, not on which private method got called internally — otherwise every refactor that doesn't change behavior still breaks the suite.
- Cover the edges deliberately: empty input, boundary values (0, -1, max), and the "surprising" case, not just the happy path.

## Integration tests

- Use a real (or realistically faked) version of the thing you're integrating with — a real test database, not a mock of the ORM. The whole point of an integration test is to catch the mismatch between your assumptions and reality; mocking the boundary defeats that purpose.
- Keep the number of integration tests focused on actual seams (I/O boundaries: database, filesystem, network, external services) — logic that doesn't touch a boundary belongs in a unit test instead, where it's cheaper and faster to run.

## Mocking guidance

- Mock at the boundary of your system (the third-party API, the database client), not the objects you own and can test directly. Mocking your own internal collaborators usually just means you're testing that your mocks return what you told them to.
- If a test needs five mocks wired together to work, that's often a sign the function under test is doing too much — see the `clean-code` skill's guidance on function size.
- Prefer a real in-memory/fake implementation over a mock with recorded expectations when one is available — fakes behave like the real thing (you can call it twice and get consistent results) where mocks just assert "was this called."

## Signs a test suite needs attention

- **Flaky tests** (pass/fail inconsistently) — usually timing, shared state between tests, or a hidden dependency on real time/network. Fix the root cause; don't just retry until green.
- **Tests that break on every refactor** even when behavior didn't change — a sign tests are coupled to implementation details instead of observable behavior.
- **Tests nobody can explain the purpose of** — if you can't tell what real bug a test would catch, it may be dead weight; question whether to keep it before adding more like it.
