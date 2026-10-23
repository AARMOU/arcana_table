# Stabilization control plan — 2026-10-23

Control issue: https://github.com/AARMOU/arcana_table/issues/81. All work is isolated. No merges, main/production changes, APK release or signing changes are authorized.

## Verified baseline and backup

- Current main: `b60d2e96ca5334fedc79cb1f312614fc9d0aeb9a`. Original `backup/precleanup-20261022/main`: `c533d0520feeab5a9c98d1d61ddb034b34672bf3`. Their diff changes only the dispatcher and router workflows.
- Retained new refs: `backup/stabilization-20261023/main` at current main and `backup/stabilization-20261023/b131-combined` at `98626c7709023346d5f45756bceb864bc3041766`. No backup was overwritten or deleted.
- B131 source: `98626c7709023346d5f45756bceb864bc3041766`, upstream Argentum `61e71e586b100faa661f86cac827ac3c3bbb5a78`, runtime bundle run `39844575500`. All seven pinned runtime files match SHA256; custom JSONL/index contain 211 unique entries with no upstream shadowing. An archive restore compared all 654 tracked file contents successfully.
- Exact-source CI: https://github.com/AARMOU/arcana_table/actions/runs/39875693567, job `120160624986`. Executed `:custom-cards:test`, `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` successfully. This is historical execution refreshed from logs, not a newly executed Android suite.
- Original preview APK provenance: source `af6bad65f67c5c2fc5acffd9b14de7d5d1b0527b`, tested parent above, run `39879225111`, artifact `12469498777`. Downloaded original artifact and independently recomputed APK SHA256 `b77fd69eceada562fb045f4ad9a4f8dfd38d34cf27108108e7030f8937886940`. Artifact records ephemeral debug signing and app ID `com.arcanatable.b131combined`; it is not a production-signed replacement. No APK was built or published during this stabilization pass.
- Toolchain: JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.4.0, Android SDK/build tools 36. Local Gradle bootstrap cannot reach services.gradle.org; do not call this a card implementation failure.
- Reproduce provenance/restore: `python docs/stabilization/verify-baseline.py /path/to/pinned-b131-checkout --restore`.

## CI causes and proposed fixes

1. Router run `39995928593`, job `120559317212`: HTTP 403 posting a PR conversation comment, `Resource not accessible by integration`. The installed router declares pull-requests read. The isolated replacement declares pull-requests write, checks trusted workflow code, exact PR head and draft state, deduplicates identical SHA/workflow failures, caps attempts, and records a mention-free observation when budget is missing. A synthetic test exercises the 403; live permission verification remains pending installation.
2. Runs `39968781131` (#70) and `39981918281` attempt 2 (#76) execute the same 167 Android tests with 16 failures and 4 skips. Six resilience, six design-preview and four screen-size failures share obsolete server-menu startup. Existing B131 repair commits `eca6f5026dec3b53d688e5586f65f75fd19e2cbf`, `9ff4f424f5a8d3c75544d8cffc8cfa24d3560800`, `69e0b51bc1ee10249ba1a1d375ad73e1353d202d` already address that fixture family. PR #82 forwards those files onto the current card base and retains real physical gesture coverage. Full new CI run `39999450302` must finish before claiming repair success.
3. Many workflows write generated exports back to development branches. This creates an untested child SHA after a green run. PRs #58, #61, #74 and #77 have no exact-current-head run in refreshed API results; earlier green runs do not make those SHAs verified. New shared CI detects export drift and never commits it.
4. PR #70 has the same tree as its target #62. PR #72 has the same tree as its target #58 and does not implement a distinct batch. Hold both for review; no closing, rebasing or duplicate dispatch was performed.

## Ownership and integration queue

The inventory covers 39 pre-existing open PRs, 30 issues, 173 branches and 81 distinct workflow blobs across 16 PR target branches. It records refs, exact-head runs, tree equality, lineage, changed paths and dependencies. It is not a semantic review of every changed line.

See `repository-inventory-20261023.md` for every PR. Order: shared fixture/CI repair #82; verified B131 runtime; card base #43; independently reviewed worker deltas; isolated combined compatibility; then controlled usage-gated dispatch. Do not reapply changes already preserved in B131 or use a worker branch as the next unrelated batch's base.

| Claim | Existing PR | Scope to reserve |
|---|---|---|
| Warhammer core | #58 | Be'lakor, the Dark Master |
| Eldrazi | #59 | Rise of the Eldrazi, Transmogrifying Wand, Ulamog's Dreadsire |
| Shared engine | #60 / #41 | B131 mechanics compatibility / Myriad engine patch; no new card claim |
| B131 registration | #61 | Upstream-owned registration guards |
| Imperium | #74 | Arco-Flagellant, Commissar Severina Raine, Ultramarines Honour Guard |
| Tyranid | #76 | Bone Sabres, Tervigon |
| Necron | #77 | Chronomancer, Cranial Plating, Royal Warden |

PR #59 current SHA `562cf57f215e6376a13417a492d31f99fa12378e`, run `39996157477`, job `120560124926`: downloaded artifact `12519089845` XML confirms 341 tests, zero failures/errors/skips, including six Rise scenarios. Source now has the correct cost, targets, draw count, extra turn and exile. Uncounterability currently has a metadata assertion, not an attempted Counterspell scenario: do not label complete Oracle certification or combined Android compatibility from this result.

## Usage and automation containment

- No verified live Copilot consumption feed is accessible through the current connector. Billing endpoints are outside its supported repository surface. Premium requests, AI credits, Actions billing and ChatGPT usage remain separate metrics. No remaining allowance is inferred from the stale 338/1500 note.
- Official personal billing API supports both `users/AARMOU/settings/billing/premium_request/usage` and `users/AARMOU/settings/billing/ai_credit/usage`, using a user token with **Plan: read**. Source: https://docs.github.com/en/rest/billing/usage. The measured unit and an owner-configured monthly budget must match; never silently convert credits, dollars and requests.
- The replacement gate requires trusted collector provenance, matching account/unit/current period, positive configured budget and a measurement at most one hour old. Missing, stale, invalid, wrong-account or wrong-metric snapshots deny new work. Thresholds remain 50% warn, 75% one new claim, 90% pause.
- `test_issue` bypasses no gate. Outstanding Copilot issue claims conservatively occupy capacity, even when session state is unavailable; they are not described as running. Both control workflows share one concurrency group.
- **Keep `ARCANA_AUTOMATION_READY` unset.** Live usage collector authorization, consumption-lag/reservation reconciliation across dispatch and repair, claim cleanup, exact-base CI, and one controlled pickup test remain release gates. The present scripts/workflows are reviewable preparation, not a completed autonomous billing controller.
- GitHub's current main-branch schedule remains installed and ungated because changing main is forbidden and this connector does not expose workflow disable. Minimum immediate user action: open https://github.com/AARMOU/arcana_table/actions/workflows/arcana-card-dispatcher.yml and choose **… → Disable workflow** until a separately authorized rollout. Do not delete or paste tokens into chat.
- The existing hourly ChatGPT dispatcher task was updated to enforce #81, fail closed for missing usage/CI/ownership, and avoid new agent mentions. The existing daily report was updated, preserving its daily morning America/Denver schedule. No duplicate automation was created.
- A live session endpoint is not exposed by the current connector. Latest refreshed Actions query found zero in-progress/queued Copilot Actions runs before our new CI; this does not prove every possible Copilot session is idle. Dispatch run `39978437435` assigned #64/#65, and completed cloud-agent runs `39978487430`/`39978490982` independently confirm pickup for that one invocation. Scheduled ongoing pickup is not established.

## Six-deck measurement and resumption gate

Index presence recomputed against both the pinned B131 indexes and current card base gives Imperium 74/100, Tyranid 68/100, Necron 70/100, Ruinous Powers 68/100, Eldrazi Unbound 70/100 and Eldrazi Incursion 69/100: 176 distinct names / 181 deck slots missing. This is index presence only; it is not executable rules coverage. Unintegrated worker PRs are not included.

No new paid worker was launched, no repeated CI retry was requested, and no new card batch was started while the usage and shared-CI gates remain unresolved. Resume only after #82's full suite, exact-head worker evidence, correct ownership and verified usage controls. Every new card needs full Oracle source, scenario tests of all abilities and actual exported-registry parity. Combined runtime validation remains mandatory after any isolated integration.
