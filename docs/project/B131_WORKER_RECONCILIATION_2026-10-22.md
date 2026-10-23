# Build 131 — 14-worker reconciliation and guarded restart (2026-10-22)

## Baseline, method, limits

- **Exact delivered candidate:** `gpt/b131-combined-apk-preview-20261022@af6bad65f67c5c2fc5acffd9b14de7d5d1b0527b`.
- **Combined feature candidate:** `gpt/b131-combined-integration-20261022@98626c7709023346d5f45756bceb864bc3041766`, draft PR #47.
- Compared live GitHub worker-branch ancestry and file-blob hashes, original team issues #12–25, worker progress comments, and the actual B131 Kotlin/test source.
- B131 checks passed at [Actions #39879225111](https://github.com/AARMOU/arcana_table/actions/runs/39879225111); this does **not** demonstrate phone gameplay or end-to-end validation of every capability.
- Eight placeholder team branches (#01–08) were still at `7593b1d5`, ancestor of B131, without work specifically on those named branches. Other work was developed on different branches, especially `gpt/b129-priority-three`.
- No current remote CI run or privately running agent was established by this audit. GitHub cannot expose local, unpushed work.
- **This audit must not be taken as approval to delete or merge any branch, weaken rules/AI, or release an APK.**

## Team-by-team evidence

| Team | Requirement | Present in exact B131 source? | Verified scope / remaining gap |
|---|---|---|---|
| 01 | Accurate action failure explanations in persistent game log | **No** general blocked-action log | B131 `TableArgentum.chooseArgentumLegalAction` showed transient messages and occasional diagnostic notes but did not append known non-submissions to `gameLog`. **First limited patch in this branch** logs local non-submission causes only, without guessing engine rejection. Further coverage/QA required. |
| 02 | Honest engine-processing progress | **Yes, partly** | `EngineProcessingPanel.kt`, `EngineProcessingInfoTest.kt`, and B129 source are byte-identical in B131. Real multi-phase stress tests and responsive UI/device checks still required. |
| 03 | Group tokens/permanents safely; select individuals | **Partial** | `BattlefieldLayout.stackIdentical` and `TableBoardViews.kt` already group by several visible fields. No expandable/individual card selection per stack; equality omits several distinctions such as attachments/ownership where not represented by key. Must audit before changing. |
| 04 | Exact offline save/resume | **Yes, explicit only** | `OfflineCheckpointStore.kt`, `TableController.saveOfflineGame/resumeOfflineGame`, `OfflineCheckpointStoreTest.kt` are present in B131 and match B129 blobs. Not proven automatic recovery following process death or mid-stack on device. |
| 05 | Recap since viewer's previous turn | **Partial** | `PlayFeed.TurnRecap`, `TurnRecapEntriesTest`, and `TableArgentum` track basic plays/attacks/life/deaths. Full all-seat/event coverage, rich tappable recap, disconnect/privacy cases absent/unverified. |
| 06 | Configurable legal priority stops | **No full preference editor** | `ArgentumPassing.next` and `Pass to my turn` exist; no verified editable stop rules such as opponent end step. Requires true engine-priority sequencing tests. |
| 07 | Replayable diagnostic bug bundles | **No** | `DiagnosticLog.kt` and `TableDiagnostics.diagnosticReport` export redacted text/context, but not replayable seed/engine-state/action bundles, nor a determinism claim. Privacy and determinism remain prerequisites. |
| 08 | Pre-game per-card evidence-based support report | **No full report** | `ArgentumDeckValidation.unsupportedCards` and `TableArgentum` can warn about registry rejections; recognition is not evidence of behavior support. Missing verified/partial/unsupported per-card evidence and deck-level report. |
| 09 | Manual table expansion/pinch; no auto switching | **Yes, partly** | `BattlefieldLayout.kt` and `ui/TableOverviewInteractionTest.kt` B131 blobs equal worker branch `929dedf5`; focused CI [#39729020743](https://github.com/AARMOU/arcana_table/actions/runs/39729020743) previously passed. Foldable/touch/real-device QA outstanding. |
| 10 | Menu text/layout repairs | **Yes, partly** | `DecisionDisplayTextTest.kt` matches worker branch `5b2669b3`; focused CI [#39727464475](https://github.com/AARMOU/arcana_table/actions/runs/39727464475) passed. Full adaptive layouts/screens QA outstanding. |
| 11 | Consecutive-identical log grouping | **Yes, in code** | `GameLogGrouping.kt` and `GameLogGroupingTest.kt` in B131 are blob-identical to worker branch `74fe1e2e`; focused CI [#39727467578](https://github.com/AARMOU/arcana_table/actions/runs/39727467578) passed. Gameplay stress/UX verification still outstanding. |
| 12 | Longer readable popups and timing | **Yes, partly** | `PlayFeed.kt` and `ReadableNotificationDurationTest.kt` match `gpt/work-popup-notifications@88b8894e`; focused CI [#39727471127](https://github.com/AARMOU/arcana_table/actions/runs/39727471127) passed. Burst queue/accessibility/device QA outstanding. |
| 13 | Persistent specific targeting alerts | **Yes, partly** | `TargetingAlert.kt`, `IncomingTargetAlertTest.kt`, matching B129-derived source present in B131. Needs full end-to-end verification, retarget/reconnect cases and device UI review. |
| 14 | Casting legally from graveyard/exile | **Yes, partly** | `gpt/zone-casting-graveyard-exile@8c62a3f0` is an ancestor of B131; `ZonePlaySourceTest.kt` and `ui/ZoneCardActionsUiTest.kt` blobs match. Focused CI [#39728542228](https://github.com/AARMOU/arcana_table/actions/runs/39728542228) passed. Full flashback/escape/alternate-destination engine and device cases not established. |

## Work to continue, without duplication

1. **Team 01** (started on this new isolated branch): write factual, local action *not-submitted* causes into the persistent game log. This is not an engine-rejection interpretation. Add unit regression and full B131 Android checks before considering integration.
2. **Team 03:** separately audit and fix stacked-permanent identity/individual targeting with 1/100/1000-token regression tests. Do not alter engine objects or remove legal targets.
3. **Team 06:** create legal-priority stop preferences, with strict mandatory-decision protections and actual engine tests.
4. **Team 08:** construct an evidence-backed deck compatibility matrix keyed to the *pinned* Argentum version and actual card behavior tests, distinguishing recognized from verified.
5. **Team 07:** investigate whether deterministic engine replay/state snapshots exist before promising replayable diagnostic bundles; preserve redaction.
6. **Team 05:** extend viewer-last-turn recap from engine events with privacy and multiseat scenarios; defer UI until event fidelity verified.

**Existing features needing QA, not reimplementation:** teams 02, 04, 09, 10, 11, 12, 13, 14; finish their specifically noted limitations on isolated follow-on branches. No team work has been deleted or assigned to a fictional background agent.

## Integration order

- Keep this branch based on the exact APK source `af6bad65` and target only that preview branch in its draft PR. Never modify `main`, PR #47 or active worker heads.
- Every subsequent task should use its own isolated branch and record base SHA, code diff, independent tests, any device check and current verification status.
- A green CI check does not grant production-merge or release approval; no production signing and no APK packaging is requested here.
