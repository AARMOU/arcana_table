# Copilot feasibility gate and queue feeder — 2026-10-28

**Author:** Claude · **Coordination:** issue #81 · **Baseline:** `main@ed6540ac`
No readiness, mode, worker-limit, billing-threshold or reserve change. Nothing is merged or dispatched by this change.

## Why

Paid Copilot runs produced no card three times: #127 → PR #320, #173 → PR #330 (engine-blocker notes only) and #245 → PR #255 (no files). Cardkit on the integration base (`9dd5fc9c`) already classified all three cards `NEEDS_REVIEW`, routed away from Copilot. The runs that did produce implementations (#148 → PR #224, #106 → PR #107) were for cards Cardkit classifies `SUPPORTED` / route `copilot`.

The queue was also empty while Cardkit lists 12 Copilot-ready cards in the six precons that no open PR claims (2026-10-28).

## Gate (`scripts/feasibility.cjs`)

- The dispatcher workflow gains a `feasibility` job: it checks out the integration base, runs `cardkit classify --json` on the cards named in open `[Arcana Queue]` issues, and passes the verdicts and the exact base SHA to the dispatch job. It has **no secrets** and read-only `contents`/`issues` access; base-branch code never runs in the job that holds `COPILOT_DISPATCH_TOKEN`.
- A unit is dispatchable only if every card in its `## Cards` list is `SUPPORTED` with route `copilot` on the same SHA the dispatcher checks. Missing, malformed or stale verdicts, discovery-only units and unknown cards fail closed with a stated reason.
- Normal mode skips non-ready units (reported in the dispatcher status); `authorize-once` denies before reading billing, writing the ledger or consuming the label.
- **Owner override:** label `arcana:feasibility-reviewed`, honoured only when applied by an approver (`ARCANA_SPEND_APPROVERS`, default the repository owner). Use it after reviewing a unit by hand.
- It is a waste guard, not a security boundary: a wrong verdict can only make it as permissive as before. All other admission checks still apply.

## Feeder (`scripts/queue-feeder.cjs`, `.github/workflows/arcana-queue-feeder.yml`)

- Daily and manual. Runs `cardkit gaps --claims-from-github` on the base, drops cards already named in open queue issues, generates bodies with `cardkit unit` (which refuses blocked, implemented, claimed or Oracle-less cards), and publishes a `queue` status comment on #78.
- Each suggestion has a prefilled "new issue" link. **It never creates issues, labels or assignments**: the owner opens the issue, so provenance stays owner-only. Arcana Control → Safeguards → *Ready to queue* lists them.
- If open-PR claims cannot be verified, it publishes no suggestions.
