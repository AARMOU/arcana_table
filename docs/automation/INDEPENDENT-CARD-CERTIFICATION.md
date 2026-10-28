# Independent post-Copilot Oracle certification (issue #81)

## Scope and owner separation

This is Goose's verification lane. Claude owns Arcana Control's secure one-tap card decision UI. Neither lane may approve/merge a PR, dispatch a paid agent, alter the AI-credit reserve, or publish an APK/AAB without the separate existing owner authorization. All active card PRs retain their authorship/ownership. This lane is read-only except for an informational status comment written by trusted main-branch GitHub Actions on issue #78.

**Important: this is the initial evidence preflight, not a complete independent Oracle verifier.** Cardkit SUPPORTED/copilot is assignment feasibility; passing Cardkit and Android CI is necessary but *not* Oracle certification. Current workflow deliberately passes no independent proof and therefore never emits certified-candidate. Green results are reported as independent-proof-required.

## Evidence required to certify

1. Verify the candidate is a Copilot-owned PR linked to an **open** trusted [Arcana Queue] issue and record its exact 40-hex head SHA and integration base. Recheck claims before acting. Different SHA revokes all old proof.
2. Independently inspect current canonical Oracle text (cost, colors, types, triggers, costs, targeting, choices, timing, zones and owner/controller rules), implementation, engine primitives, registry and deterministic assets. The Copilot PR description is **not evidence**.
3. Create and execute **independently selected real Argentum positive and negative scenarios** (not mocks, placeholders, assertions that cannot fail, or skipped tests). Include adverse interactions appropriate to that card. Record test cases, executed count, failed/skipped count, exact tested composite source/engine revisions, raw JUnit/CI artifacts and immutable GitHub run URL.
4. Confirm required Cardkit toolkit and Development CI cards-and-android checks both conclude success on the exact PR head. Missing, cancelled, skipped, neutral, wrong-head and failed checks all block.
5. A trusted independent runner must authenticate the proof provenance and actual successful run, check its immutable source/target mapping, and only then supply the structured proof to the policy. The runner must be separately implemented and reviewed; never parse a Copilot-authored claim as proof.
6. Publish a bounded state: blocked / checks-incomplete / checks-running / checks-failed / independent-proof-required / certified-candidate. Owner approval is a **separate subsequent action**; combined integration regressions can revoke readiness.

## Implemented in this draft

- scripts/card-certification.cjs — pure exact-head policy, strictly optional independent proof input, mockable read-only GitHub collector, safe public report formatter and replaceable bot-owned #78 status.
- scripts/card-certification.test.cjs — cases for green-not-certified, stale SHA, check failures and skips, wrong queue linkage, missing/partial proof, new-commit invalidation, private provenance, bounded read-only collection and status update idempotence.
- .github/workflows/arcana-card-certification.yml — main-branch-only periodic/manual monitor. It is read-only except one display-only #78 comment. No PR comments, approvals or agent requests.

## Next implementation slice

Build the **independent real-engine Oracle scenario executor and proof attestation adapter**. Target one green and one intentionally red card at exact immutable SHAs; verify JUnit evidence and the scenario set against authoritative Oracle, then wire its authenticated output into the preflight collector (currently proof: null). Only after an end-to-end independent pass and a demonstrably blocked failure should Claude's UI expose a certified-candidate decision as evidence-based. This gate is distinct from the user's owner approval and from release/integration CI.

The status marker is: arcana-independent-card-certification-v1. The separate Arcana Control review inbox marker remains unchanged. GitHub Pages UI and Cloudflare Workers are not modified by this PR.
