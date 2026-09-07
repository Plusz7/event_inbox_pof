# SOLID Principles

Five design principles for keeping object-oriented (and, loosely, any modular) code easy to change. They're guidelines for judgment, not rules to satisfy mechanically — applying all five to a 20-line script produces worse code than applying none of them.

## S — Single Responsibility Principle

A module should have one reason to change. Not "one method" — one *axis of change*.

**Smell:** A `Report` class that both calculates totals and formats them as HTML. If the tax calculation changes, you touch the same file as when the HTML layout changes — two unrelated teams or reasons now collide in one place.

**Fix:** Split into `ReportCalculator` (the math) and `ReportRenderer` (the presentation). Each can change for its own reason without risking the other.

## O — Open/Closed Principle

Code should be open for extension but closed for modification: adding new behavior shouldn't require editing existing, already-tested code.

**Smell:** A `calculateDiscount(type)` function with a growing `if/else` or `switch` on `type` that gets a new branch every time a new discount type ships.

**Fix:** Define a `Discount` interface with a `calculate()` method, and add a new class per discount type. Adding a discount means adding a file, not editing a function everyone else's discounts also depend on.

This doesn't mean avoid all `if` statements — a small, stable set of cases (say, three fixed enum values that will never grow) is fine as a `switch`. Apply this principle where the branches are known to keep growing.

## L — Liskov Substitution Principle

A subtype must be usable anywhere its base type is expected, without breaking the caller's expectations.

**Smell:** A `Rectangle` base class with a `Square` subclass that overrides `setWidth` to also change the height. Code that does `rect.setWidth(5); rect.setHeight(10); assert(rect.area() == 50)` breaks silently when handed a `Square`.

**Fix:** If a subtype can't honor the base type's contract, it shouldn't be a subtype. Prefer composition, or model `Square` and `Rectangle` as siblings rather than one inheriting from the other.

## I — Interface Segregation Principle

Don't force a class to implement methods it doesn't need. Many small, focused interfaces beat one large one.

**Smell:** A `Worker` interface with `work()` and `eat()`. A `RobotWorker` is forced to implement `eat()` with a no-op or an exception — the interface is lying about what a robot can do.

**Fix:** Split into `Workable` and `Eatable`. `RobotWorker` implements only `Workable`. Callers that only care about work depend only on `Workable`, so they're unaffected by changes to break-room logic.

## D — Dependency Inversion Principle

High-level modules shouldn't depend on low-level modules directly — both should depend on an abstraction. This is what makes code testable and swappable.

**Smell:** An `OrderService` that directly `new`s up a `MySqlDatabase` inside its methods. You can't test `OrderService` without a real database, and you can't swap databases without editing `OrderService`.

**Fix:** `OrderService` takes a `Database` interface in its constructor. Production code wires it to `MySqlDatabase`; tests wire it to an in-memory fake. `OrderService` never needs to change when the storage layer does.

## Applying this in review

When reviewing a diff or deciding how to structure new code, ask:
- Does this class/module have more than one reason to change? → SRP
- Will adding the next variant require editing this function, or just adding a new one? → OCP
- Could a caller break if I swap in a subclass here? → LSP
- Am I implementing a method just to satisfy an interface, with no real behavior? → ISP
- Am I hard-wiring a concrete dependency that should be injected/abstracted? → DIP

If the answer reveals a real, current pain point, refactor. If it's a hypothetical future problem, note it and move on — see YAGNI in [other-principles.md](other-principles.md).
