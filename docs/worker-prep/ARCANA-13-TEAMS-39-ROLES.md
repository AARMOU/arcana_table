# Arcana Table — Parallel Coding Worker Preparation

Prepared: October 21, 2026 (America/Denver).

**IMPORTANT:** These are **launch-ready role briefs, not running agents**. GitHub branch creation and documentation do not launch Codex workers. A coding-agent environment with worktree/agent support must actually start each worker; do not report any worker as active until that execution is confirmed.

Repository: `AARMOU/arcana_table`

Shared base for all unmodified teams: `gpt/build128-mana-reliability` at `7593b1d5d037216510c3117db3b8f36ed2b944f3`. Team 09 uses the pre-existing `gpt/table-manual-expand-pinch-zoom` branch, which already has unvalidated implementation/test commits.

## Team model

**14 teams × 3 coding workers each = 42 defined worker assignments.** Each team has Worker A (source and engine), Worker B (UI/integration), Worker C (independent tests/review). Independent workers should use unique worktrees or subbranches (suggestion: `<team-branch>-a`, `-b`, `-c`) and submit their results to the team's integration branch. **Only the integration worker lands the team's changes.**

Do not launch all 39 blindly if execution capacity is lower. Start teams in waves, avoiding shared file conflicts. These are prepared assignments, not claims that agent slots or builds exist.

## Non-negotiable safeguards

- Zero production merges, releases, APK/AAB publications, changes to signing keys, application id or Play Store deployment without separate authorization.
- Preserve all legitimate targets, triggers, token creation, priority decisions and AI strength. Do not approximate unsupported Argentum rules or invent metrics.
- Test with executable JVM/Robolectric/device checks as available; report exact commands run and limitations. **Never claim a textual checklist is an executed test.**
- No confidential credentials, room codes, hidden opponent cards or private data in diagnostic artifacts.
- Any worker needing files owned by another team defines a stable interface contract and waits for integration or edits the integration branch after coordination.
- Do not auto-merge workers. Reviewer C documents correctness gaps and tests, then integration lead resolves them.
- Tag done only after independent review and replayed tests, not just a commit.

## Shared interfaces / known conflicts

**01↔11 (game log):** Worker 01 owns engine-grounded *producer semantics* for rejection entries; Worker 11 owns immutable repeated-event grouping and log rendering. Agree on a schema including stable event identity, type, source, controller, target identifiers, outcome, visibility, sequence/turn and a human-readable description. Do not use string equality alone as evidence of identical event semantics.

**12↔13 (notifications):** Worker 12 owns routine popup queue/timing and `PlayFeed.kt`; Worker 13 owns a distinct persistent `TargetingAlert` surface and the data-model changes. A targeting alert does **not** use popup timeout logic and must not displace mandatory decisions.

**03↔09 (board):** Worker 03 owns counted stack semantics and expansion/select; Worker 09 owns table zoom/scroll and overview layout. Avoid parallel writes to `TableBoardViews.kt` and `BattlefieldLayout.kt` on the eventual integration branch.

**02↔06 and active engine-delay optimization:** Engine progress instrumentation cannot modify AI depth, the legal-action set, or auto-pass decision policy. Priority-stop logic cannot interpret an in-progress operation as a safe pass.

**04↔07:** A private save file is not automatically safe to export in a bug report. Separate exact recovery format from sanitized shareable repro bundle.

**05↔11:** Turn recap is a summary of underlying uncompressed events, not the display-only deduplicated log.

**08↔card-import work:** The report must be calculated from versioned scenarios and actual test evidence, never assumed support.

## Independent acceptance gates for every team

1. No lost rules/data/visibility semantics.
2. Existing unit, regression and UI tests pass (actual execution reported).
3. New edge-case tests cover the team's documented pitfalls.
4. UI remains usable on compact phone, landscape and unfolded foldable, where applicable.
5. Security/privacy review, including player-hidden state when applicable.
6. Human-readable report of changed paths, branch/commit, tests and unresolved risks.

## Workstream roster

| # | Item | Integration branch | Worker count |
|---|---|---|---|
| 01 | Action explanations in game log | `gpt/work-log-events` | 3 (A/B/C) |
| 02 | Engine processing progress | `gpt/work-engine-progress` | 3 (A/B/C) |
| 03 | Grouped tokens and card stacks | `gpt/work-token-groups` | 3 (A/B/C) |
| 04 | Exact save and resume for offline games | `gpt/work-local-resume` | 3 (A/B/C) |
| 05 | Recap since last turn | `gpt/work-turn-recap` | 3 (A/B/C) |
| 06 | Configurable priority stops | `gpt/work-priority-stops` | 3 (A/B/C) |
| 07 | Reproducible diagnostic bug bundles | `gpt/work-repro-reports` | 3 (A/B/C) |
| 08 | Pre-game deck behavior support report | `gpt/work-deck-support` | 3 (A/B/C) |
| 09 | Table overview refinements and verification | `gpt/table-manual-expand-pinch-zoom` | 3 (A/B/C) |
| 10 | Comprehensive menu layout cleanup | `gpt/work-menu-layout` | 3 (A/B/C) |
| 11 | Collapse consecutive identical game-log events | `gpt/work-log-grouping` | 3 (A/B/C) |
| 12 | Readable notification popups and timing | `gpt/work-popup-notifications` | 3 (A/B/C) |
| 13 | Dedicated targeting alerts | `gpt/work-targeting-alerts` | 3 (A/B/C) |
| 14 | Graveyard/exile plays — finish and verify | `gpt/zone-casting-graveyard-exile` | 3 (A/B/C) |

## Launch sequencing (once an actual coding-agent runner is available)

- **Wave A (low-conflict):** 07 diagnostic repro, 10 menu cleanup, 12 popup usability, 08 deck support report, 09 Table branch validation, 14 graveyard/exile play validation.
- **Wave B (shared contract first):** 01 log failure reasons and 11 grouping; 13 targeting alert; 05 turn recap.
- **Wave C (engine/persistence-sensitive):** 02 progress, 03 tokens, 06 priority stops, 04 exact save/resume.
- Worker C review may start with test plans, fixtures and baseline evidence before implementation. No team merges directly to `main`.

## Assigned role briefs
### 01. Action explanations in game log

**Branch:** `gpt/work-log-events`

**Worker 01-A — Core / source:** Trace explicit rule-engine action rejection/availability responses through `ArgentumInteraction.kt`, `TableArgentum.kt` and/or `ArgentumBackend.kt`. Define a noninvented reason payload with action id, source, target, timing and visibility; if engine gives no definitive explanation, label it as unknown rather than attributing a guessed cause.

**Worker 01-B — UI / integration:** Emit verified explanations as persistent **game log entries**, not automatic extra popups. Preserve game-log chronology and link the originating visible card/action where possible. Agree on the common immutable event contract with team 11 before changing shared rendering files.

**Worker 01-C — Independent QA / adversarial review:** Add tests for exact insufficient-mana, timing, legal-target, missing-choice and unspecified reasons; ambiguous cases must not invent detail. Check repeated failures, concealed information, and ongoing valid actions.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 02. Engine processing progress

**Branch:** `gpt/work-engine-progress`

**Worker 02-A — Core / source:** Trace host pipeline in `engine/LocalArgentumHost.kt` and state updates; surface measured phase names, elapsed duration and progress counters where accurate. Do not change engine action enumeration/AI or clamp legitimate token and trigger counts.

**Worker 02-B — UI / integration:** Design a nonblocking progress strip/details panel that differentiates processing, connected but idle, waiting for user, and hung/unknown. Display determinate percentage **only** if a known numerator and denominator exist.

**Worker 02-C — Independent QA / adversarial review:** Benchmark representative simple and large token/lifegain states with per-stage timings and count equivalence. Test cancellation, reconnect, and visibility during longer operations without inventing progress.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 03. Grouped tokens and card stacks

**Branch:** `gpt/work-token-groups`

**Worker 03-A — Core / source:** Audit `BattlefieldLayout.kt::stackIdentical` and engine state metadata; define equivalence including tapped state, all counters, damage, ownership/controller, attachments, commander state, combat state and other distinguishing game facts. Never modify MTG objects, only presentation grouping.

**Worker 03-B — UI / integration:** Expose counted stacks with expand/collapse and individual-token selection. Keep targets and actions bound to individual engine ids. Integrate into full board/Table view without making huge token arrays unscrollable.

**Worker 03-C — Independent QA / adversarial review:** Test 1/100/1000 tokens; tokens with different attachments, counters, targets and combat status stay distinct; every individual token remains selectable and rules behavior unchanged. Flag missing attachment data explicitly.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 04. Exact save and resume for offline games

**Branch:** `gpt/work-local-resume`

**Worker 04-A — Core / source:** Inspect `TablePersistence.kt`, `engine/OfflineGame.kt` and Argentum engine serialization. Specify and implement a full-fidelity checkpoint only if supported: RNG, game objects, hidden zones, turn/priority, stack, state-based effects, pending choices, controllers and AI state.

**Worker 04-B — UI / integration:** Add clear save, resume and discard controls for solo/offline games. Detect incompatible saved engine versions and surface recovery/failure without silently dropping partial game state.

**Worker 04-C — Independent QA / adversarial review:** Cold restart midstack/midchoice, before and after random effects, with large boards and invalid/corrupted saves. Require exact reproducible game-state equality; if no engine serializer exists, report prerequisite rather than fake a resume.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 05. Recap since last turn

**Branch:** `gpt/work-turn-recap`

**Worker 05-A — Core / source:** Extend existing `PlayFeed.kt` `TurnRecap` and `TurnRecapEntriesTest.kt`: preserve public card plays, life changes, counters and relevant stack effects since the viewer last received their turn, respecting authorized visibility.

**Worker 05-B — UI / integration:** Offer a compact, readable per-turn recap with tappable publicly known cards and optional expansion into log detail. Avoid redundant notices during busy turns.

**Worker 05-C — Independent QA / adversarial review:** Verify turn boundaries, controller changes, disconnected state, multi-seat play, hidden information, simultaneous events and large recaps. Avoid inferred events not supplied by engine.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 06. Configurable priority stops

**Branch:** `gpt/work-priority-stops`

**Worker 06-A — Core / source:** Review `ArgentumPassing.kt` and `engine/AutoPassManager.kt`; define stop rules for precombat, opponent end step and other legal priority windows. Follow actual Argentum priority; never create windows that do not exist.

**Worker 06-B — UI / integration:** Provide an unobtrusive preference editor and current auto-pass/stop status, with reversible toggles and mandatory-decision override. Keep existing hold-to-auto-pass behavior.

**Worker 06-C — Independent QA / adversarial review:** Test full priority sequence, multi-opponent passes, stack interactions, mana abilities, mandatory choices, AI actions and rapid step transitions; prove no meaningful legal action lost.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 07. Reproducible diagnostic bug bundles

**Branch:** `gpt/work-repro-reports`

**Worker 07-A — Core / source:** Extend `DiagnosticLog.kt`, `TableDiagnostics.kt` and local engine traces with a replayable case format: build/engine versions, prior actions, deterministic seeds/state when supportable and privacy classification. Never include connection credentials or inaccessible hidden card identities.

**Worker 07-B — UI / integration:** Add one-touch sanitized report export with summary of what's included, sensitive-content preview and optional attachment of permitted state data. Don't duplicate existing diagnostic-log export.

**Worker 07-C — Independent QA / adversarial review:** Verify replay determinism where possible, roundtrip, redaction, oversized logs, corrupted data and failure without state snapshot. Reports must not claim reproducibility when engine support is absent.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 08. Pre-game deck behavior support report

**Branch:** `gpt/work-deck-support`

**Worker 08-A — Core / source:** Inspect imported deck metadata and Argentum custom-card test evidence; distinguish verified behavior from implemented/unverified, partial and unsupported. Use versioned evidence for card/ability variants; do not treat recognition as proof of support.

**Worker 08-B — UI / integration:** Display a pregame summary with counts, exact affected card names and links to evidence/explanations without blocking legitimate deck editing. Warn before starting rather than disguising unsupported cards.

**Worker 08-C — Independent QA / adversarial review:** Cross-check six official WotC precons, known test gaps and sample unsupported cards; verify totals, version changes, duplicates and correct status classifications. Coordinate with card import branch.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 09. Table overview refinements and verification

**Branch:** `gpt/table-manual-expand-pinch-zoom`

**Worker 09-A — Core / source:** Audit existing committed manual-expand/pinch-to-resize implementation on this distinct branch. Check gesture competition with `LazyVerticalGrid` scrolling, tapping, tapped-card width, changing seat and zoom persistence; fix only confirmed defects.

**Worker 09-B — UI / integration:** Improve player/opponent inspection and narrow/landscape/foldable layout without changing the existing dark-green/mint/gold design or restoring auto-expand. Keep expansion purely user-driven.

**Worker 09-C — Independent QA / adversarial review:** Compile and run `TableOverviewInteractionTest.kt` and size/screenshot tests. Device-check multi-touch, large scrolls, 1–4 players, foldable and turn changes. Existing commits are not build/device validated.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 10. Comprehensive menu layout cleanup

**Branch:** `gpt/work-menu-layout`

**Worker 10-A — Core / source:** Normalize explicit JSON null/missing labels at their source, particularly Argentum yes/no decisions and descriptions, without changing game decision values. Audit `TableOverlays.kt`, `ArgentumInteraction.kt`, `ArgentumScreens.kt`.

**Worker 10-B — UI / integration:** Fix crowded button rows and clipping across `TableDialogs.kt`, `ArgentumUi.kt`, `AdjustUiPage.kt` and navigation; use adaptive wrapping/stacking, scroll where needed, readable text, sensible touch targets. Preserve visual identity.

**Worker 10-C — Independent QA / adversarial review:** Capture screenshots and assert content/tap-ability for 344dp cover, 411dp portrait, landscape and unfolded size at increased font scales. Include null, blank and extra-long dynamic labels.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 11. Collapse consecutive identical game-log events

**Branch:** `gpt/work-log-grouping`

**Worker 11-A — Core / source:** Define an immutable event-level grouping function with a canonical key from type, source, controller, target ids, effect/result, visibility and any relevant context. Collapse **consecutive** equivalent events only; retain all originals, their order and timestamps. Coordinate event schema with team 01.

**Worker 11-B — UI / integration:** Show one game-log row with ×N badge that expands to original individual events. Ensure long entries remain readable and cards can be inspected. Do not change underlying engine behavior or rewrite original diagnostic export.

**Worker 11-C — Independent QA / adversarial review:** Test 1000+ identical entries, interleaving, near matches, different targets/recipients/outcomes, turn boundaries, hidden data, expanding, and full fidelity export.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 12. Readable notification popups and timing

**Branch:** `gpt/work-popup-notifications`

**Worker 12-A — Core / source:** Audit `PlayFeed.kt`, `TableController.kt`, `TableOverlays.kt` and `TableOverlaysB.kt`: existing popup duration is clamped to 1–8 seconds and banner/recap text truncates. Define severity, deduplication and queue rules so notifications are neither dropped nor blindly prolonged.

**Worker 12-B — UI / integration:** Wrap long messages with adaptive sizing and a scroll/expand path. Make durations length-sensitive/configurable and allow tap-to-read or pin; allow manual dismissal. Critical game decisions must remain until resolved, not merely until timeout.

**Worker 12-C — Independent QA / adversarial review:** Test very long notification text, several simultaneous events, 344dp cover and landscape, pause/hold duration, app background/foreground and accessibility font scale. Keep alerts distinct from team 13 targeting warning.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.


### 13. Dedicated targeting alerts

**Branch:** `gpt/work-targeting-alerts`

**Worker 13-A — Core / source:** Extend `ArgentumBackend.kt` stack target projection to carry stable target ids/types/owner-or-controller identity and source instance id where available, not merely display names. Fire on newly declared visible **actual** targets of local player or cards/objects they own/control (as rules and visibility permit). Do not infer targeting from non-targeting effects.

**Worker 13-B — UI / integration:** Implement a persistent, high-visibility targeting indicator **separate** from `PlayFeedOverlay` popup/banner, with source card, target card(s), optional short vibration, tap to inspect stack and highlight targets. De-duplicate snapshots; persist during the pending action and clear on change/resolution, without blocking decisions.

**Worker 13-C — Independent QA / adversarial review:** Test opponent targets player, one card, multiple cards, same-named objects, self-targeting (avoid unnecessary warnings), non-targeting global effects, retargeting, countered/resolved spells, hidden data, offscreen panels, stack reorder, reconnect and 1–4 players.

**Required result:** Committable implementation on the isolated branch, new automated regressions, exact execution results, limitations, source change log and approval-ready integration notes; no release.

### 14. Graveyard/exile play controls — complete and certify

**Branch:** `gpt/zone-casting-graveyard-exile`

**Worker 14-A — Core/source:** Audit the committed source-zone-aware casting and payment logic and all legal-action lookups. Verify Argentum engine actions reference the exact spell/object ID; no client-simulated permissions, lost triggers or inappropriate command-zone tax. Track flashback/escape/adventure and exile-to-other-zone destination semantics as engine requirements, not guessed Android effects.

**Worker 14-B — UI/integration:** Verify the graveyard/exile action buttons and hand-drawer shortcuts are functional and readable; preserve inspection and manual sandbox actions separately; handle stale selections and changed zones without losing objects. Coordinate with menu layout team 10.

**Worker 14-C — Independent QA:** Run `ZonePlaySourceTest.kt`, `GraveyardExileLegalActionsTest.kt`, `ui/ZoneCardActionsUiTest.kt` and all existing applicable unit/UI/device regressions. Test special permissions, alternate casting costs, zone-destination replacement, land plays, priority, hidden cards, stale ids and 4-seat games. Record exact failures and build/device evidence.

**Required result:** Compile-verified and scenario-tested source code with independent review, evidence, and unresolved mechanics explicitly listed. Initial implementation is committed but **not certified**.

## Existing branch reminder

`gpt/table-manual-expand-pinch-zoom` already contains manual expand/pinch changes and a new Robolectric test from the preceding conversation. These have not been compiled or exercised on device; treat any claim of completion as unverified until Worker 09-C certifies the behavior.

The 14 team issues are [#12–#25](https://github.com/AARMOU/arcana_table/issues). Maintain dated evidence in [the notes ledger](./ARCANA-TEAM-NOTES.md); creation of these issues does not start an agent.

## Instructions to the eventual orchestration runner

1. Check repo branch heads before beginning; if base advanced, rebase or intentionally pin and record the chosen sha.
2. Launch independent A/B/C workers into **separate worktrees**. Parallelize only different paths or agreed interfaces. Never let multiple workers write the same files concurrently.
3. Review and run deterministic tests on each worktree; integrate A/B into team branch after C's review; preserve failing evidence.
4. Publish concise per-team results and dependencies; hold all merges/ship steps pending human authorization.
5. For engines with potentially unbounded state, benchmark realistic and exponential stress decks without imposing false token limits or dropping events.
