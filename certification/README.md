# Independent card certification — shared registry (#81)

Arcana Control's Verify / Approve gate intentionally requires **independent real-engine evidence** on the exact current Copilot PR commit. Cardkit, generated assets, and Android CI are prerequisites, not Oracle rules certification.

## Shared path (no new workflow per card)

1. A Copilot PR refers to an open, trusted `[Arcana Queue]` work unit. Its Cardkit toolkit/scaffold and Android checks must be green on that exact SHA.
2. On either CI completion, `.github/workflows/arcana-independent-card-certification.yml` reads `certification/suites.json` **from main**, not from the Copilot PR. The central `certification/policy.cjs` requires exact card name, issue number, trusted queue provenance, source repository, Copilot author, integration base, and three successful current-head check runs.
3. Only cards with a registered, reviewed independent Kotlin suite are eligible. A read-only job injects that trusted suite into a separate exact-SHA candidate checkout, runs real Argentum JUnit scenarios, and validates **every expected test actually ran**. Agent-authored card tests by themselves cannot certify.
4. A separate checks-write job (never executing the candidate code or carrying its credentials) revalidates the live PR and issues `Arcana independent certification` on that exact SHA, success **only** on passing independent tests. New commits need new certification.
5. The existing owner-authenticated Arcana Control decision endpoint independently checks current SHA, all-green checks and certification. Nothing here clicks Approve, merges, deploys Cloudflare, signs or releases an APK.

## How to add another card

**Preferred (conflict-free):** add `certification/suites/<key>.json` containing one suite object (same fields as a `suites.json` entry; the file name must equal its `key`) plus the reviewed test source in `certification/`. Do **not** edit `suites.json` or the premerge workflow: the premerge job builds its matrix from the manifest and runs the suites a PR adds or changes against their open Copilot card PR. After the suite merges, `arcana-certification-backfill.yml` re-runs that card PR's latest successful CI so certification runs without a manual re-run.

**Automatic (default for Copilot card PRs):** the backfill opens an `[Arcana Suite]` request per uncovered card; the dispatcher assigns it to Copilot on `main` under admission; `arcana-suite-automerge.yml` merges the suite after a green premerge. See `docs/automation/ADMISSION-CONTROL-DESIGN.md` §16.


Add one **trusted-main** manifest entry (key, exact Magic card name, its queue issue number, independent Kotlin source, JUnit test class, expected number of cases), and a reviewed test source in `certification/`. No new copy of the YAML workflow, Cloudflare Worker, dispatcher, or agent subscription is needed. Reuse support helpers/mechanic scenarios where appropriate; still test the card's own exceptions, timing, ownership, and negative cases. Validation fails on missing or ambiguous suites and unsafe paths. A card without independent test coverage **remains blocked**, not auto-certified.

Resurrection Orb (#408 / Copilot PR #410) is the initial four-scenario example. Its premerge engine run [#40800975564](https://github.com/AARMOU/arcana_table/actions/runs/40800975564) passed four independent tests against PR #410's exact source SHA `56f1842`. This is evidence for that card, not a general claim that every supported card already has coverage.

## Integrated cards (idle-time work)

Most custom cards the app plays were integrated before this pipeline existed. When the dispatcher has nothing
eligible, `certification/idle-suites.cjs` (run hourly by the backfill workflow) opens one `[Arcana Suite]` request
for such a card. The owner approved this on 2026-11-01, capped at 6 a day.
- **Order:** deck cards first. Only while billing is normal, with at most one request waiting at a time.
- **Request:** its `Card unit` / `Card PR` lines read `integrated`. The suite registers `"issue": <the request's
  number>` and `"target": "build"`.
- **Premerge:** runs it against the integration build branch named in main's `integration/policy.json`.
- **Dispatcher:** ranks these after every other kind of work.
- **If it fails:** the suite worker gets its repair turn first. If it still fails, the router posts a one-time
  regression note (suite PR and #81) for the card lane, instead of looking for a card PR.

## Safeguards and limits

### Celestine certification (#81)

The new Celestine suite was authored from Wizards' release notes and the existing
`support/` helpers before reading any candidate implementation or candidate tests.
Candidate metadata and changed filenames were read only to bind queue issues and
import names. No worker implementation or worker test source was read.

| Card / candidate | Cases | Independent behavior checks |
| --- | ---: | --- |
| Celestine, the Living Saint / #360 | 7 | Casting/flying/lifelink, zero-value targets, gross life gain and exact value boundary, controller/type/turn restrictions, target choice before later life gain |

Rules sources: [Commander Masters Release Notes](https://magic.wizards.com/en/news/feature/commander-masters-release-notes)
and [Warhammer 40,000 Release Notes](https://magic.wizards.com/en/news/feature/warhammer-40000-commander-release-notes-2022-09-19).
The premerge matrix runs each suite on its own live candidate SHA and uploads that
SHA with raw JUnit XML. This PR's green policy checks alone cannot certify a card.

**Known blocked card:** #443 demonstrated that Monolith cannot grant its free cast
on an opponent's turn. Its failing scenario is preserved, with no exception in
the runner or certification policy. Do not approve #370 on ordinary CI alone.
The existing #443 retains Monolith's independent suite and registration. It was
re-run unchanged at `f79b95dc` (7/8 pass; opponent-turn permission fails) and is kept
separate so it cannot prevent review of Celestine's passing suite. No Monolith
scenario, expectation, runner requirement or implementation is weakened.

- The default-branch workflow requires a fresh completed Cardkit/Android event; a completed historical CI run alone won't activate a newly registered suite.
- Only the trusted manifest's explicit suites unlock approvals. Current matching requires queue-feeder provenance `arcana-auto-queue-v1` and the expected Copilot integration base. Other worker types need an separately designed pathway.
- No privileged token is available during Gradle execution; only a separated post-test job can publish a check.
- A green card-specific suite does not certify its entire multiplayer integration, whole deck, release build, or exotic interactions not covered by those scenarios.
- **Outstanding security hardening:** Arcana Control's UI and review Worker currently accept any check-run name matching `/certif/i`. Before broad multi-card production rollout, require the exact certification check name and trusted app/provenance in both UI and server. This requires a separately authorized Cloudflare Worker deployment. Do not remove the server-side gate or substitute ordinary CI.
- Other card suites are not registered until their independent behavior assertions exist. Growth is incremental, but the verification *pipeline* is shared.

## Tests

`node --test certification/policy.test.cjs` exercises manifest schema, registration for a synthetic second card, trust origin and refusal of stale SHA/CI. The existing premerge workflow also executes the four real independent Resurrection Orb scenarios as a regression on PRs changing certification infrastructure. Certification outcomes must be checked on the exact head; do not report dashboard unlock without observing the published check.
