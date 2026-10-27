# Arcana Table — NEXT TEST APK BUNDLE (after Build 136)
Updated: 2026-10-26 | Coordination: [GitHub Issue #81](https://github.com/AARMOU/arcana_table/issues/81)

## Control and scope

- **NO APK/AAB build, merge, distribution, production signing, Cloudflare deployment, or Play upload** without a subsequent owner instruction. This is a **source staging and acceptance ledger** only.
- Exact baseline: [successful Build 136 full workflow](https://github.com/AARMOU/arcana_table/actions/runs/40416263760) from `gpt/build136-everything-integrated-20261026`. Its APKs do NOT include changes below.
- Integration staging is draft [PR #297](https://github.com/AARMOU/arcana_table/pull/297); production/default branch unchanged. Use only SHA-pinned inputs; do not import arbitrary moving branch heads.
- Existing B136 bundle includes full-screen multiplayer source and lobby diagnostics, deck editor/Import new, no developer goose, larger commander image, 20-chapter tutorial assets, upgraded Argentum + custom cards/Ugin/Eldrazi Temple, AI improvements, combat-outcome and commander-damage tracking, original launcher logo, engine notice toggle, and post-B121 What's new. **Do not drop these when composing next build.**

## New fixes and requests to include

| ID | Item | Owner | Exact source / evidence | Status | Required before next APK |
|---|---|---|---|---|---|
| B137-01 | Wi-Fi and internet lobby created but never shown on Fold | GPT | [PR #296](https://github.com/AARMOU/arcana_table/pull/296), `cdcfe759501d044c73b35cba4e6838ffa1ef8b82`; CI [40421474235](https://github.com/AARMOU/arcana_table/actions/runs/40421474235) SUCCESS | **Fixed in isolated source**; staged as `LOBBY_FIX_SHA` in #297 | Run `MultiplayerLobbyVisibilityTest` inside combined app; verify actual Fold host and guest lobby |
| B137-02 | Repeated `INVALID_ACTION: Blockers have already been declared` after accepted first block | GPT | [PR #299](https://github.com/AARMOU/arcana_table/pull/299), `c7f95db09560be4249d89b1288f2dccebe14a45e`; device log `ArcanaTable-diagnostics-20261026-174036.txt` | **Fixed in isolated source, CI GREEN**; staged as `OUTCOME_SHA` in #297 | Development CI [40423092104](https://github.com/AARMOU/arcana_table/actions/runs/40423092104) **SUCCESS**; source-only combined join [40424208100](https://github.com/AARMOU/arcana_table/actions/runs/40424208100) **SUCCESS**; combined Android execution and real Fold replay still required |
| B137-03 | Claude tutorial rework (again) | Claude | **Exact PR/commit not yet supplied**; do not substitute old PR #282 or prior tutorial | **WAITING for Claude** | Record final exact SHA, diff, assets and chapter count; confirm first lessons, step 2 HAND, stack/priority, and actual Fold tutorial; require Android tests and bundle integration |
| B137-04 | Hosted internet lobby/seat configuration backend parity | Claude / Worker owner (deployment separate) | Client source PR #283 and subsequent lobby fixes; last confirmed Worker deploy [40365400541](https://github.com/AARMOU/arcana_table/actions/runs/40365400541) predates its seat-management semantics | **BLOCKED** on backend deploy verification | Worker typecheck, directed relay tests, privacy and multi-phone network QA; owner separately approves deploy; do not equate successful APK with functioning relay |
| B137-05 | Unresolved card coverage reported by B136 diagnostic | Card pipeline, owner assignment pending | `Goblin Welder`; `Middle School`; `Voter's Dilemma`; `Ratchet, Field Medic // Ratchet, Rescue Racer` | **INVESTIGATE** — missing indexed entries, not all proven broken gameplay; Ratchet was replaced with basic land | Audit card registries/upstream support and legality, add implementation/scenario tests only where supported; verify full deck remains unchanged |
| B137-06 | AI turn latency / action enumeration | AI worker, no overlap | Device trace shows 4.235 s at turn 12, `aiChoice` dominating engine timing | **PROFILE, NOT YET A FIX** | Replay with exact engine and seed before asserting a regression; no arbitrary profile flags |
| B137-07 | Other current next-build fixes already in B136 | Integration only | PRs #293/#294, source pins in B136 workflow (combat result and commander damage) and previously verified Build136 test | **PRESERVE** | Assert unchanged tests and sign-off; cannot omit on #299 source update |

## Source integration and QA contract

1. **No branches promoted by merely changing a comment or title.** The next-B136 runner assembly must pin #296 as `LOBBY_FIX_SHA=cdcfe759501d044c73b35cba4e6838ffa1ef8b82` and #299 as `OUTCOME_SHA=c7f95db09560be4249d89b1288f2dccebe14a45e`. The latter is stacked on #294/293. The source join must retain earlier deck, gameplay, logo, cards, tutorial and UI changes. **Actual combined-source rehearsal passed** at [40424208100](https://github.com/AARMOU/arcana_table/actions/runs/40424208100), using the temporary, revised two-conflict combat reconciliation; no APK was compiled.
2. The assembled Android source must contain both `MultiplayerLobbyVisibilityTest` and `ArgentumBlockSubmissionGuardTest`; explicitly execute them in the focused gate **and** execute all Android unit tests. Missing or skipped tests fail closed.
3. Reconfirm corrected tutorial after Claude completes; **do not pin an unfinished or guessed Claude SHA**.
4. App behavior gate on real Samsung Fold: host/join Wi-Fi, host/join cross-network after Worker deploy, lobby visible, seat counts and ready/start/leave; blocking assignment accepted exactly once, illegal blocking can be corrected, combat advances, subsequent combat works, commander damage shown correctly, no early loss.
5. Verify package ID/version, Arcana logo, large home commander art, no developer goose, deck editor/import link, revised tutorial assets, all card assets, current notice setting and in-app changelog. Ensure full and fast-build scripts have consistent hashes/identity and do not silently skip source pins. Test certificate only for previews; Play signing separate.
6. Final readiness gate: one exact assembled source SHA, full passing CI job + artifact SHA256, final on-device smoke, explicit owner approval to build. **Do not start compilation during notes/feature collection.**

## Hold / owner decision

The user is still adding items. This ledger is intentionally **OPEN**. After source-only validation (staging commit `f0552ab7380a506f921909cf3b09169109b74257`), next blockers are Claude's tutorial, cloud-worker parity/physical tests, any additional owner requests and explicit APK authorization. Change statuses to verified only with exact run links. Never treat a draft PR, a green unit test alone, or a Worker typecheck as end-to-end multiplayer proof.
