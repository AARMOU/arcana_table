# Arcana Table — two-day stale open-work reconciliation

**Snapshot:** 2026-10-25 12:37 America/Denver. **Cutoff:** 2026-10-23T18:37:00Z (2026-10-23 12:37 MDT). **Repository:** AARMOU/arcana_table.

## Meaning and evidence limits

- GitHub `updated_at` was independently fetched for each item; entries below had **no activity recorded on their own open GitHub issue/PR thread since before the cutoff**. This is a *stale-thread proxy*, **not proof that a card or feature had zero work in another branch, another issue, a local session, or an outside agent**. The `Next action` column explicitly reconciles known cross-thread progress.
- Inventory before any audit mutation: **37 open PRs + 22 open issues = 59** stale items. During this audit, PR **#70** and **#72** were verified plan-only zero-file-change duplicates and closed without merge; **35 PRs + 22 issues = 57** of this cohort remain open. Original 59 are preserved in the rows for auditability.
- No PR/code integration is accepted merely because a card exists in an index. No automatic worker is considered live from the presence of a queued issue. Historic green CI is not today's full integration/head proof.
- Audit activities: read-only inventory/PR descriptions and existing CI evidence; two reversible duplicate-PR closures; this documentation PR. **No branch deletion, runtime JAR replacement, Copilot dispatch, production merge, signing, APK release, or weakened test**.
- The more recent Eldrazi/Argentum work **is excluded from the stale cohort**: [#211](https://github.com/AARMOU/arcana_table/pull/211), [#213](https://github.com/AARMOU/arcana_table/pull/213), [#214](https://github.com/AARMOU/arcana_table/pull/214), [#215](https://github.com/AARMOU/arcana_table/pull/215), [#216](https://github.com/AARMOU/arcana_table/pull/216), [#217](https://github.com/AARMOU/arcana_table/pull/217), and coordination [#81](https://github.com/AARMOU/arcana_table/issues/81).

## Immediate completion order

1. **Finish integration evidence rather than replay old implementations.** Parent B131 and six-precon branches (#31/#35/#43/#47/#82, #33/#37/#38/#39/#40/#32, #74/#76/#77/#58) have valuable tests, but require source/runtime/export comparison before promoting the work.
2. **Resolve engine-rule blockers first:** Myriad defender choice (#41, current #183/#214), Ulalek colorless-hybrid/stack copy (#60 and current #132), then missing Oracle card primitives. Do not call indexed Chittering Dispatcher fully playable.
3. **Handle player-facing debts:** #9 (token/auto-pass performance), #11 (mana/payment), #25/#27 (graveyard/exile legal casts), #20/#26 (Fold table), #15 (full-fidelity offline save). Each requires a current-runtime and Android/device-specific gate.
4. **Usage-aware queue admission:** do not start any of queued teams/issues #12–25/#53–68 without a fresh verified budget/ownership check under #81/#78. No issue title indicates a coding agent is running.
5. **Reconcile stale control documentation (#44/#57/#62/#69)** with the present main branch. As recorded later on #81, **weekly upgrade #204 and #205 were merged by a separate explicitly owner-authorized lane**; Port Town Cardkit cleanup is draft #215. Do not revive old dispatcher permissions or outdated 74-branch snapshots.

## Stale pull requests — 37 at snapshot (2 closed as duplicates during this audit)

| PR | Last updated (UTC) | Disposition | Concrete next action / completion condition |
|---|---|---|---|
| [#8](https://github.com/AARMOU/arcana_table/pull/8) | 2026-10-21 12:59:38 UTC | Historical tested candidate | Keep Build 123 performance proof; compare with later B126/B131 before any cherry-pick. Fold/device replay and token-scaling gap remain. |
| [#10](https://github.com/AARMOU/arcana_table/pull/10) | 2026-10-21 17:11:55 UTC | Test-only performance candidate | Rebase or replace against latest auto-pass path; run parity, timing and stress tests before integration. |
| [#11](https://github.com/AARMOU/arcana_table/pull/11) | 2026-10-22 02:15:32 UTC | Unintegrated gameplay candidate | Reproduce Treasure/Goose/Jaws and persistent mana behavior on current runtime; Fold and large-token checks required. |
| [#27](https://github.com/AARMOU/arcana_table/pull/27) | 2026-10-22 05:31:45 UTC | Partial UI and legal-action tests | Team 14: preserve CI 39728542228, confirm engine-side zone permissions and actual device behavior; no merge without end-to-end. |
| [#26](https://github.com/AARMOU/arcana_table/pull/26) | 2026-10-22 05:36:47 UTC | Partial UI candidate | Team 09: compile/test pinch/expand against current table screen, Fold sizes, multitouch and scroll; no auto-expand regression. |
| [#28](https://github.com/AARMOU/arcana_table/pull/28) | 2026-10-22 17:38:20 UTC | Old upgrade repair assignment | Reconcile targeted Treasure/custom-deck tests with completed #31, #82 and current weekly engine path; close only after ancestry/diff check. |
| [#29](https://github.com/AARMOU/arcana_table/pull/29) | 2026-10-22 17:38:22 UTC | Old upgrade repair assignment | Reconcile obsolete navigation/resilience fixture investigation with #31 and #82; recover any unique failing scenarios. |
| [#30](https://github.com/AARMOU/arcana_table/pull/30) | 2026-10-22 17:38:25 UTC | Old upgrade repair assignment | Reconcile layout/design fixes and test evidence with #31/#82 before retiring the branch. |
| [#31](https://github.com/AARMOU/arcana_table/pull/31) | 2026-10-22 19:48:23 UTC | Historical green upgrade repair | CI 39840218785 passed; compare its fixture changes with current development and #82; device/live-server skips remain. |
| [#33](https://github.com/AARMOU/arcana_table/pull/33) | 2026-10-22 19:51:28 UTC | Isolated card candidate | Zoanthrope: verify exact-head scenarios/asset export and detect duplicate integration before taking into six-precon branch. |
| [#35](https://github.com/AARMOU/arcana_table/pull/35) | 2026-10-22 20:13:32 UTC | Historical preview completed | Build 131 packaging run 39844575500 passed; retain provenance, do not republish, compare current runtime and hardware evidence. |
| [#38](https://github.com/AARMOU/arcana_table/pull/38) | 2026-10-22 20:14:56 UTC | Isolated card candidate | Triarch Praetorian: verify unearth/ETB tests and current integration coverage; no blind cherry-pick. |
| [#39](https://github.com/AARMOU/arcana_table/pull/39) | 2026-10-22 20:17:09 UTC | Isolated card candidate | And They Shall Know No Fear: check resolution-time type/indestructible tests and incorporation into #43/#47. |
| [#32](https://github.com/AARMOU/arcana_table/pull/32) | 2026-10-22 20:21:11 UTC | Isolated two-card candidate | Hexmark Destroyer/Canoptek Spyder: reconcile tests and card definitions with later precon exports. |
| [#37](https://github.com/AARMOU/arcana_table/pull/37) | 2026-10-22 20:25:36 UTC | Isolated green card candidate | Deny the Witch: historical full regression 39848237583 green; verify current runtime and stacked integration. |
| [#40](https://github.com/AARMOU/arcana_table/pull/40) | 2026-10-22 20:27:22 UTC | Isolated green card candidate | Canoptek Tomb Sentinel: historical CI 39848598051 green; reconcile registrations and latest exports. |
| [#41](https://github.com/AARMOU/arcana_table/pull/41) | 2026-10-22 21:40:48 UTC | Engine patch staged; current blocker | Myriad defender-choice patch awaits integrated tests; newer #183/#214 work is active elsewhere. No standalone runtime promotion. |
| [#42](https://github.com/AARMOU/arcana_table/pull/42) | 2026-10-22 23:31:44 UTC | Unintegrated diagnostics feature | Compare game-over attribution with combined B131 #47/current logging; verify winner/loss event semantics and visibility. |
| [#43](https://github.com/AARMOU/arcana_table/pull/43) | 2026-10-22 23:34:35 UTC | Historical twelve-card integration | Re-audit each card and dependency against #47 and later 91-slot work; initial green not final combined acceptance. |
| [#47](https://github.com/AARMOU/arcana_table/pull/47) | 2026-10-23 00:02:59 UTC | Pinned historical B131 integration | Retained B131 baseline 98626c77; compare present custom/runtime changes and avoid overwriting current development. |
| [#44](https://github.com/AARMOU/arcana_table/pull/44) | 2026-10-23 00:48:59 UTC | Historical governance snapshot | 74-branch registry is stale against newer #81 control data; extract still-unique policy, do not apply outdated inventory. |
| [#48](https://github.com/AARMOU/arcana_table/pull/48) | 2026-10-23 01:39:36 UTC | Partial worker reconciliation | Review 14-team audit against present issue/PR statuses and update ownership; preserve backups. |
| [#49](https://github.com/AARMOU/arcana_table/pull/49) | 2026-10-23 01:43:52 UTC | Partial UI stacking fix | Verify stackIdentical respects identity, counters, controller/attachments, and more recent battlefield layout. |
| [#50](https://github.com/AARMOU/arcana_table/pull/50) | 2026-10-23 02:02:01 UTC | Partial priority-stop preferences | Validate STOP/PASS semantics, settings persistence and AI/engine transitions on newer builds. |
| [#52](https://github.com/AARMOU/arcana_table/pull/52) | 2026-10-23 03:16:27 UTC | Documentation-only WIP | Implement real engine-backed rules regression cases with executable receipts or mark as proposal; issue #51 remains open. |
| [#57](https://github.com/AARMOU/arcana_table/pull/57) | 2026-10-23 05:22:57 UTC | Older coverage tooling | Compare JSON missing-card export with newer Cardkit/100-slot audit; retire only if no unique output remains. |
| [#61](https://github.com/AARMOU/arcana_table/pull/61) | 2026-10-23 12:37:01 UTC | Isolated registration guard | Confirm three upstream-owned B131 card registrations are still deduplicated in newer manifests; preserve exact test. |
| [#60](https://github.com/AARMOU/arcana_table/pull/60) | 2026-10-23 13:22:25 UTC | Isolated mechanics validation | Carry exact stack-copy/colorless-hybrid evidence into current Ulalek/Argentum runtime gate; Myriad separately blocked. |
| [#62](https://github.com/AARMOU/arcana_table/pull/62) | 2026-10-23 14:18:36 UTC | Read-only CI draft | Validate against existing Development/Cardkit workflows; keep parent for review. Plan-only child #70 closed in this audit. |
| [#58](https://github.com/AARMOU/arcana_table/pull/58) | 2026-10-23 14:19:14 UTC | Unintegrated Be'lakor card | Verify Oracle triggers and current asset/export test; duplicate plan-only child #72 closed in this audit. |
| [#72](https://github.com/AARMOU/arcana_table/pull/72) | 2026-10-23 14:20:39 UTC | CLOSED as no-op duplicate | Closed without merge after comparing with #58: one plan-only commit, zero file changes. Branch/history preserved. |
| [#70](https://github.com/AARMOU/arcana_table/pull/70) | 2026-10-23 14:21:58 UTC | CLOSED as no-op duplicate | Closed without merge after comparing with #62: one plan-only commit, zero file changes. Branch/history preserved. |
| [#69](https://github.com/AARMOU/arcana_table/pull/69) | 2026-10-23 14:42:25 UTC | Outdated dispatcher design | Reconcile with usage-gated controller already on main (#81/#78); do not deploy old dispatch credentials or permissions. |
| [#74](https://github.com/AARMOU/arcana_table/pull/74) | 2026-10-23 15:34:15 UTC | Isolated Forces of Imperium batch | Arco-Flagellant, Honour Guard, Severina Raine: audit collisions against upstream and modern weekly upgrade; verify all tests. |
| [#77](https://github.com/AARMOU/arcana_table/pull/77) | 2026-10-23 15:50:00 UTC | Isolated Necron batch | Cranial Plating, Chronomancer, Royal Warden: current runtime checks, registry/export review, actual scenario evidence. |
| [#76](https://github.com/AARMOU/arcana_table/pull/76) | 2026-10-23 16:21:08 UTC | Isolated Tyranid batch | Bone Sabres and Tervigon: check official Oracle, current engine behavior and verified integration before count. |
| [#82](https://github.com/AARMOU/arcana_table/pull/82) | 2026-10-23 18:08:31 UTC | Historical Android fixture fix | CI 39999450302 passed; verify whether current integration already contains exact 16-fixture repair and preserve 4 existing skips. |

## Stale issues — 22 at snapshot

| Issue | Last updated (UTC) | Disposition | Concrete next action / completion condition |
|---|---|---|---|
| [#9](https://github.com/AARMOU/arcana_table/issues/9) | 2026-10-21 19:38:10 UTC | Performance remains unresolved | Build 123/125/126 measurements exist; reproduce 1,000-token target on current runtime and capture legal-action/AI timings. |
| [#21](https://github.com/AARMOU/arcana_table/issues/21) | 2026-10-22 05:41:39 UTC | Queued UI Team 10 | No launch implied. Audit menu labels and layout; add Fold-size screenshots and tap-target tests. |
| [#22](https://github.com/AARMOU/arcana_table/issues/22) | 2026-10-22 05:41:41 UTC | Queued UI Team 11 | No launch implied. Define immutable grouping identity with team 01; test mixed event sequences and 1,000+ events. |
| [#23](https://github.com/AARMOU/arcana_table/issues/23) | 2026-10-22 05:41:43 UTC | Queued UI Team 12 | No launch implied. Specify popup priority/duration and accessibility tests without suppressing required decisions. |
| [#20](https://github.com/AARMOU/arcana_table/issues/20) | 2026-10-22 05:41:46 UTC | Partly worked Team 09 | PR #26 has unintegrated table-zoom work; compile and test on current Fold/table branch, then reconcile issue. |
| [#25](https://github.com/AARMOU/arcana_table/issues/25) | 2026-10-22 05:41:48 UTC | Partly worked Team 14 | PR #27 and CI 39728542228 support UI/legal-action checks; complete engine-zone and hardware end-to-end. |
| [#24](https://github.com/AARMOU/arcana_table/issues/24) | 2026-10-22 14:53:52 UTC | Queued UI Team 13 | No launch implied. Preserve exact targeting visibility/identity; independent alert UI requires source-proven events. |
| [#13](https://github.com/AARMOU/arcana_table/issues/13) | 2026-10-22 14:53:54 UTC | Queued engine progress Team 02 | No launch implied. Instrument truthful engine phases/timing; avoid fictitious percentages or interrupting real actions. |
| [#15](https://github.com/AARMOU/arcana_table/issues/15) | 2026-10-22 14:53:57 UTC | Queued offline save Team 04 | No launch implied. First prove exact engine checkpoint serialization/RNG/hidden state before any resume UI. |
| [#12](https://github.com/AARMOU/arcana_table/issues/12) | 2026-10-23 01:40:58 UTC | Queued Team 01 | No launch implied. Report engine-sourced action refusal details in persistent log; coordinate event contract with #22. |
| [#16](https://github.com/AARMOU/arcana_table/issues/16) | 2026-10-23 01:41:01 UTC | Queued Team 05 | No launch implied. Implement privacy-safe turn recap using engine-confirmed, visible event chronology. |
| [#18](https://github.com/AARMOU/arcana_table/issues/18) | 2026-10-23 01:41:05 UTC | Queued Team 07 | No launch implied. Repro bundle schema needs deterministic seed, redaction and replay tests. |
| [#19](https://github.com/AARMOU/arcana_table/issues/19) | 2026-10-23 01:41:07 UTC | Queued Team 08 | No launch implied. Base pregame support report on tested Cardkit coverage and explicit unknowns, not name matches. |
| [#14](https://github.com/AARMOU/arcana_table/issues/14) | 2026-10-23 01:45:10 UTC | Partly worked Team 03 | PR #49 narrows grouping bug but is not full team completion; review state equivalence and Fold UI. |
| [#53](https://github.com/AARMOU/arcana_table/issues/53) | 2026-10-23 05:49:22 UTC | Queued card worker | Do not assume Copilot pickup. Reconcile Warhammer six-precon unique work with #43/#58/#74/#76/#77 first. |
| [#55](https://github.com/AARMOU/arcana_table/issues/55) | 2026-10-23 05:55:12 UTC | Queued engine worker | Do not assume pickup. Reconcile colorless hybrid, stack copy and Myriad against newer Ulalek/#214 source gates. |
| [#56](https://github.com/AARMOU/arcana_table/issues/56) | 2026-10-23 05:55:26 UTC | Queued compatibility worker | Do not assume pickup. Check B131 duplicate registrations and pending engine runtime compatibility (#47/#61). |
| [#64](https://github.com/AARMOU/arcana_table/issues/64) | 2026-10-23 15:27:44 UTC | Queued Tyranid batch | Before any dispatch, count integrated gaps and review #76, #33, #43, current engine + cost controls. |
| [#65](https://github.com/AARMOU/arcana_table/issues/65) | 2026-10-23 15:27:46 UTC | Queued Necron batch | Before any dispatch, exclude #32/#38/#40/#77 and Cardkit pilot #164; verify fresh coverage. |
| [#63](https://github.com/AARMOU/arcana_table/issues/63) | 2026-10-23 16:23:10 UTC | Queued Imperium batch | Before any dispatch, review #37/#39/#74, upstream Port Town and six upgrade collisions. |
| [#66](https://github.com/AARMOU/arcana_table/issues/66) | 2026-10-23 18:05:02 UTC | Queued Ruinous Powers batch | Before any dispatch, review #58 Be'lakor, #84 Keeper/Nurgle, and separate upgrade-collision list. |
| [#67](https://github.com/AARMOU/arcana_table/issues/67) | 2026-10-23 18:05:05 UTC | Queued Eldrazi Unbound batch | Before any dispatch, review #85, shared Ugin/Temple/Forsaken, and exact inventory on latest source. |

## Completion and closure rules

- **Do not close a feature PR just because another branch mentions the same card.** Verify its unique diff/commit, integrated card behavior/ownership, deterministic export and current exact-head CI. Record the surviving PR or new head in the thread first.
- **Do not mark a queued issue complete** until its authorized code/test acceptance is present; absence of a worker is explicitly unresolved. No new paid dispatch under this audit.
- **Safe duplicate cleanup performed:** [#70](https://github.com/AARMOU/arcana_table/pull/70) vs [#62](https://github.com/AARMOU/arcana_table/pull/62) and [#72](https://github.com/AARMOU/arcana_table/pull/72) vs [#58](https://github.com/AARMOU/arcana_table/pull/58). Exact SHA-to-SHA GitHub compare showed one added plan-only commit, **zero changed files** for each. Both were closed without merge; parents remain.
- **Device gate remains separate:** proof on Samsung Fold and a real, >=90-original-indexed **and playable** Eldrazi developer candidate are not supplied by this stale audit.
- **Next owner/agent action:** consume this document as a checklist and attach exact source/head CI receipts per surviving row. Reconcile overlapping rows by dependency before editing or dispatch. Coordinate status on [#81](https://github.com/AARMOU/arcana_table/issues/81).
