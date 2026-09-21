---
name: observability-and-logging
description: How to add production-grade logging, metrics, and tracing — structured log format, correct log levels, what must never be logged (secrets, PII), correlation IDs, and when a new code path needs instrumentation at all. Make sure to apply this whenever adding a new service, endpoint, background job, or external call, whenever writing error-handling code, and whenever the user asks about logging, monitoring, metrics, tracing, alerts, or debugging a production issue with no visibility.
metadata:
  category: infrastructure
---

# Observability and Logging

You can't fix what you can't see, and in production you can't attach a debugger. Logs, metrics, and traces are the only window into what actually happened during an incident — so the question when writing any code that can fail or that matters operationally isn't "should I add observability" but "what would I need to see, at 3am, to understand this without reading the source code."

## Structured logging over string concatenation

Log as structured data (JSON or key-value pairs), not free-form sentences built by string concatenation. `log.info("user_login", user_id=123, method="oauth")` can be filtered, aggregated, and alerted on; `log.info("User " + id + " logged in via oauth")` can only be grepped, and grepping breaks the moment someone tweaks the wording.

Every log line should carry a consistent baseline: timestamp, level, service/component name, and — critically — a **correlation ID** (request ID / trace ID) that ties every log line touched by a single request or job together across services. Without it, debugging a distributed request means guessing which of ten thousand interleaved log lines belong to the one that failed.

## Log levels mean something — use them that way

- **debug**: detail useful only when actively investigating something, too noisy to leave on in production by default.
- **info**: normal operational events worth a permanent record (a request completed, a job started) — not everything that happens, or the signal drowns.
- **warn**: something unexpected but recovered from automatically (a retry succeeded, a fallback was used) — worth noticing in aggregate, not urgent individually.
- **error**: something failed and did *not* recover — this request/job did not complete as intended.
- **fatal/critical**: the process can no longer continue safely and is stopping.

Getting this wrong in either direction breaks the same thing: logging routine events at `error` trains everyone to ignore error-level alerts ("it's always red, that's normal"); logging real failures at `debug` means nobody sees them until someone happens to be looking. Calibrate to what a human should actually be paged or notified for.

## Never log secrets or unmasked personal data

Passwords, API keys, tokens, full credit card numbers, and raw personally identifiable information must never appear in a log line — logs are typically retained longer, replicated wider, and access-controlled more loosely than the primary datastore, so anything logged there has effectively leaked to a bigger audience. Mask or omit these fields explicitly (`***`, last 4 digits only) rather than logging the object that happens to contain them and hoping nothing sensitive is in it.

## The three pillars, and when to reach for each

- **Logs** — discrete events with context: "this specific thing happened, here's the detail." Best for understanding one particular occurrence, especially failures.
- **Metrics** — aggregated numbers over time: request rate, error rate, latency percentiles, queue depth. Best for "is the system healthy right now" and for alerting, since they're cheap to compute over huge volumes and don't require reading individual events.
- **Traces** — the path of a single request across multiple services/functions, with timing at each hop. Best for "why is this one request slow" when the bottleneck could be in any of several services.

Don't reach for a trace to answer a question a metric would answer more cheaply, and don't try to reconstruct aggregate health from grepping logs — pick the pillar that matches the question.

## When to add instrumentation

Add it at meaningful boundaries — a new external call, a new endpoint, a retry loop, a background job, anything that can fail independently of the code calling it — enough to answer three questions later without reading source: is it working, how fast, and how often is it failing. Don't instrument every internal function call; that produces volume without insight and makes the genuinely important signal harder to find. If you're not sure a boundary matters, ask: "if this broke, would I be able to tell from the outside right now?" — if no, that's the gap to close.

## Alert on symptoms, not on every possible cause

Alerting rules should trigger on user-visible or system-health symptoms (elevated error rate, latency past a threshold, a queue backing up) rather than on every individual exception type that could theoretically cause them. Symptom-based alerts stay meaningful as the codebase changes; cause-based alerts multiply endlessly and train people to ignore pages — the exact failure mode this skill is trying to prevent one level up.

## Write actionable messages

A log line at error level should tell the reader what was being attempted and with what key identifiers (order id, user id, request id), not just "an error occurred" or the raw exception message with no context. Compare `"payment failed"` to `"payment failed for order_id=8842, user_id=501, gateway=stripe, reason=card_declined"` — the second one is something a human can act on immediately without reproducing the request. This pairs directly with the `exception-handling` skill's guidance on preserving context when an error is caught and logged.
