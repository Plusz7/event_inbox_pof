# Other Core Design Principles

## DRY — Don't Repeat Yourself

Every piece of knowledge should have a single, unambiguous representation in the system. This is about *knowledge*, not *text* — two blocks of code that happen to look similar but represent different business rules are not a DRY violation, and merging them creates a false coupling that breaks the moment one rule changes and the other doesn't.

**Real violation:** The tax rate `0.23` is hardcoded in three different files. When it changes, someone has to remember to update all three — and inevitably, one gets missed.

**Not a violation:** Two validation functions that both check "is this string non-empty" but for genuinely unrelated fields (a username and a product SKU) that may need different rules tomorrow. Merging them into one `validateNotEmpty` used everywhere is fine *only* if they really are, and will stay, the same rule.

## KISS — Keep It Simple

Prefer the simplest design that solves the actual problem. Complexity should be proportional to the problem's real complexity, not to how clever a solution could theoretically be.

A sign you've violated KISS: you need a diagram to explain a function that could have been ten straightforward lines. Simplicity is a feature — it's what makes code debuggable at 2am and reviewable by someone new to the codebase.

## YAGNI — You Aren't Gonna Need It

Don't build functionality on the speculation that it will be needed later. Every unused configuration option, plugin hook, or generic parameter is code that must be maintained, tested, and understood by every future reader — for a benefit that may never materialize.

**Smell:** Adding a `strategy` parameter to a function so it can theoretically support multiple algorithms, when only one algorithm exists and no second one is planned.

**Fix:** Write the function for the one case that actually exists. Add the abstraction when — and only when — a second real case shows up. Refactoring later, once you know the actual shape of the second case, is usually cheaper than guessing wrong now.

YAGNI and SOLID's Open/Closed principle are in tension by design: OCP says make it easy to extend later; YAGNI says don't build the extension point until you need it. Resolve this by writing the current case as cleanly and simply as possible (small functions, clear boundaries) — clean code is easy to extend later even without a speculative abstraction baked in today.

## Law of Demeter (principle of least knowledge)

A method should only talk to its immediate collaborators — not reach through them to grab something two or three levels deep.

**Smell:** `order.getCustomer().getAddress().getCity()`. Any change to how `Customer` stores its address breaks `order`'s caller, even though the caller never cared about `Customer` internals — it just wanted a city.

**Fix:** Give `Order` a `getCityForShipping()` method that hides the traversal. Callers depend on one stable interface instead of the whole chain of internal structure.

This isn't about counting dots — a fluent builder (`query.where(...).orderBy(...).limit(...)`) isn't a violation because each call returns the same object type by design. The concern is reaching into *unrelated* objects' internals through a chain.

## Composition over inheritance

Prefer building behavior by combining small, focused objects rather than through deep inheritance hierarchies. Inheritance creates tight coupling between parent and child — a change to the base class can silently break every subclass, and a subclass can only ever be one thing.

**Smell:** A `FlyingCar` that inherits from both `Car` and `Airplane` (or contorts around single inheritance to fake it), because behavior was modeled as "is-a" when it's really "has-a" / "can-do."

**Fix:** Give `Car` a `flightCapability: FlightBehavior` field, injected with whichever implementation applies. Adding a new kind of vehicle means composing existing behaviors, not re-deriving a class hierarchy.

Rule of thumb: reach for inheritance only when the relationship is truly "is-a" and stable (a `SavingsAccount` really is an `Account`); reach for composition when you're modeling "has a" or "can do."
