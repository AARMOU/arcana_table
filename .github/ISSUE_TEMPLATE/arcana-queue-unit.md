---
name: Arcana Queue work unit
about: One card batch for a Copilot worker. The dispatcher refuses units missing any section below.
title: "[Arcana Queue] "
labels: []
---

<!-- Required by the dispatcher's unit-readiness check (scripts/admission.cjs, unitActionable).
     Missing sections are reported as `unit-not-actionable` and nothing is spent. -->

## Cards
<!-- One bullet per card the worker must implement. Or delete this section and use Discovery. -->
- Card name

## Discovery
<!-- Only if the cards are not known yet: name the exact script and command that finds them. -->
<!-- Example: run `investigation/argentum-six-precons/coverage-audit.py --json-output` against ... -->

## Acceptance
- Exact current Oracle behavior (cost, types, every ability, targeting and timing), checked against authoritative card records
- Executable regression scenarios for positive and negative behavior, registration and deterministic asset export
- No placeholders, approximations or weakened tests; report exact commands and run IDs

<!-- Must equal the ARCANA_INTEGRATION_BASE repository variable. -->
Base: gpt/arcana-integration-next
