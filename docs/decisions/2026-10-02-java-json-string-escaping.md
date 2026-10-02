# Java audit JSON string escaping

## Problem

Java source code that builds JSON audit payloads with `String.format(...)` must escape JSON quotation marks inside the Java string literal.

A broken pattern such as `String.format("{"batchId":"%s"}", batchId)` does not compile because Java interprets the JSON quotation marks as the end of the Java string.

## Required pattern

Use escaped quotation marks: `String.format("{\\"batchId\\":\\"%s\\"}", batchId)`.

For every audit JSON payload in this repository:

- Escape every JSON quotation mark as `\"` inside Java string literals.
- Keep the payload valid JSON.
- Prefer a dedicated JSON serializer if the payload becomes complex enough that manual string construction becomes error-prone.
- Run the backend Maven test suite after changes to Java services.

This rule applies to all Java services, not only BroilerProductionService.