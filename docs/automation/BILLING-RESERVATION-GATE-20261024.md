# Billing reservation reconciliation — implementation gate (2026-10-24)

Control: #81. This branch is preparation only. No paid admission is authorized.

## Verified source-level gap
At the inspected main baseline, `scripts/admission.cjs` computes reservation exposure from currently slot-holding units and pending repairs. When a worker reaches review and releases its capacity slot, its estimated cost can disappear before the delayed AI-credit charge appears in the billing feed. Capacity release and financial settlement must be independent.

## Required invariant
For each attempted paid operation, record a durable unique reservation ID before the external side effect. Hold its estimated liability until a post-operation billing sample is reconciled or an explicitly authorized conservative settlement is recorded. An uncertain assignment or failed ledger write must never silently release liability.

## Minimal implementation sequence
1. Add a pure reservation reducer with tests for: assignment authorized / attempted / failed-uncertain; worker completed before billing posts; partial charge; multiple operations in one reporting window; stale/wrong-period sample; duplicate events; ledger write failure; month rollover; cancellation; and owner-approved settlement.
2. Reconcile observed billing deltas against outstanding reservations without treating aggregate deltas as proof of an individual worker's cost. When attribution is ambiguous, retain the conservative reservation and report uncertainty.
3. Make `admit()` include all unsettled liabilities in its budget calculation, including review-state units, without double-counting an active unit's same operation.
4. Persist reservation events in an append-only trusted ledger; serialize read/authorize/reserve/attempt and fail closed if any write fails. Prove a crash between reservation and assignment does not cause an automatic retry.
5. Add a read-only dry-run report with measurement timestamp, billing period, used credits, outstanding liability, available headroom, and denial reason. Redact tokens and personal billing payloads.
6. Validate exact-head CI and executed regression results. Do not enable readiness, dispatch, or repair based on source review alone.

## Evidence required before any paid admission
- Authenticated current AI Credits sample: account, SKU, unit, period, measured-at, used and allowance.
- Real post-session samples demonstrating reporting lag and conservative cost estimates (initial provisional session 60, repair 30 AI credits are *not calibrated*).
- Reconciled outstanding assignments, PR links, worker-start/finish events and reservations, with unknowns explicitly held.
- Exact-head successful shared CI on the intended integration base, and evidence failure-router observations/reconciliation work without duplicate paid repairs.
- Explicit owner approval of one actionable unit; safe one-shot authorization, durable ledger, no overlap, and verified pickup.

**Disposition:** DISPATCH BLOCKED; REPAIR BLOCKED. No production edits, merges, paid workers or CI reruns authorized by this document.
