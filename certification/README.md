# Independent card certification — shared registry (#81)

Arcana Control's Verify / Approve gate intentionally requires **independent real-engine evidence** on the exact current Copilot PR commit. Cardkit, generated assets, and Android CI are prerequisites, not Oracle rules certification.

## Shared path (no new workflow per card)

1. A Copilot PR refers to an open, trusted `[Arcana Queue]` work unit. Its Cardkit toolkit/scaffold and Android checks must be green on that exact SHA.
2. On either CI completion, `.github/workflows/arcana-independent-card-certification.yml` reads `certification/suites.json` **from main**, not from the Copilot PR. The central `certification/policy.cjs` requires exact card name, issue number, trusted queue provenance, source repository, Copilot author, integration base, and three successful current-head check runs.
3. Only cards with a registered, reviewed independent Kotlin suite are eligible. A read-only job injects that trusted suite into a separate exact-SHA candidate checkout, runs real Argentum JUnit scenarios, and validates **every expected test actually ran**. Agent-authored card tests by themselves cannot certify.
4. A separate checks-write job (never executing the candidate code or carrying its credentials) revalidates the live PR and issues `Arcana independent certification` on that exact SHA, success **only** on passing independent tests. New commits need new certification.
5. The existing owner-authenticated Arcana Control decision endpoint independently checks current SHA, all-green checks and certification. Nothing here clicks Approve, merges, deploys Cloudflare, signs or releases an APK.

## How to add another card

Add one **trusted-main** manifest entry (key, exact Magic card name, its queue issue number, independent Kotlin source, JUnit test class, expected number of cases), and a reviewed test source in `certification/`. No new copy of the YAML workflow, Cloudflare Worker, dispatcher, or agent subscription is needed. Reuse support helpers/mechanic scenarios where appropriate; still test the card's own exceptions, timing, ownership, and negative cases. Validation fails on missing or ambiguous suites and unsafe paths. A card without independent test coverage **remains blocked**, not auto-certified.

Resurrection Orb (#408 / Copilot PR #410) is the initial four-scenario example. Its premerge engine run [#40800975564](https://github.com/AARMOU/arcana_table/actions/runs/40800975564) passed four independent tests against PR #410's exact source SHA `56f1842`. This is evidence for that card, not a general claim that every supported card already has coverage.

## Safeguards and limits

- The default-branch workflow requires a fresh completed Cardkit/Android event; a completed historical CI run alone won't activate a newly registered suite.
- Only the trusted manifest's explicit suites unlock approvals. Current matching requires queue-feeder provenance `arcana-auto-queue-v1` and the expected Copilot integration base. Other worker types need an separately designed pathway.
- No privileged token is available during Gradle execution; only a separated post-test job can publish a check.
- A green card-specific suite does not certify its entire multiplayer integration, whole deck, release build, or exotic interactions not covered by those scenarios.
- **Outstanding security hardening:** Arcana Control's UI and review Worker currently accept any check-run name matching `/certif/i`. Before broad multi-card production rollout, require the exact certification check name and trusted app/provenance in both UI and server. This requires a separately authorized Cloudflare Worker deployment. Do not remove the server-side gate or substitute ordinary CI.
- Other card suites are not registered until their independent behavior assertions exist. Growth is incremental, but the verification *pipeline* is shared.

## Tests

`node --test certification/policy.test.cjs` exercises manifest schema, registration for a synthetic second card, trust origin and refusal of stale SHA/CI. The existing premerge workflow also executes the four real independent Resurrection Orb scenarios as a regression on PRs changing certification infrastructure. Certification outcomes must be checked on the exact head; do not report dashboard unlock without observing the published check.
