# Arcana Table — cumulative changelog since Build 121

**Baseline:** Build 121. **Current verified full-feature test preview:** Build 134, exact no-goose [run 40368920029](https://github.com/AARMOU/arcana_table/actions/runs/40368920029). **Next candidate:** lobby + corrected tutorial source-only [run 40379321158](https://github.com/AARMOU/arcana_table/actions/runs/40379321158). This file is the maintained long-form source for the short in-app **What's new** menu. It distinguishes *developed*, *compiled/CI-tested*, *deployed server-side*, *device-verified* and *released*.

> **Important:** Build 122 was intentionally based on Build 120, **not** Build 121. Intermediate developer builds were not a linear release train. Grouping a change under a development period does not imply it was in every numbered APK. Original indexed Eldrazi card-slot coverage measures inventory, not rules correctness of every card. Passing GitHub Actions does not prove Samsung Fold, cross-network or Google Play acceptance.

## Build 122 — rules fixes, speed and introductory help

- Fixed copied **Smaug** death triggers: a dying copy produces the intended 14 Treasures without multiplying the effect.
- Fixed **Valkyrie's Call / Cid** Angel return, flying, +1/+1 counter, and negative subtype death-loop behavior.
- Reduced offline AI searches on crowded boards and added action/legal-action/AI-resolution timing diagnostics.
- Added **Quick UI Tips**, dismiss and persistent “Don't remind me again.”
- Suppressed redundant post-opening-hand custom-card notice while retaining diagnostic evidence.
- Corrected compiled build version display and retained the seven premade WotC decks.
- Evidence: [BUILD-122.md](https://github.com/AARMOU/arcana_table/blob/gpt/arcana-integration-next/BUILD-122.md).

## Build 123 — late-game responsiveness

- The offline AI avoids redirect target-search paths for spells without targets.
- Nested simulations share an overall timing budget; larger action sequences emit more useful diagnostics/progress evidence.
- Addresses a specific major end-step AI path, not a guarantee that 1,000-token turns finish instantly.
- Both Build 122 and 123 entries already existed in `ChangelogDialog` and remain verbatim in the app.

## Builds 124–130 — development work between milestones

Detailed, reliably version-mapped per-build release receipts were not established in this audit, so **these are grouped**, not misleadingly assigned to a particular integer:

- Iterative engine/Android fixes for Treasure mana payments, cast legality indicators, choice timing, large token/trigger bursts and automatic pass behavior.
- Investigations and new regression scenarios for **Iron Spider** counters, **The Lunar Whale** Crew 1/combat/top-library, **Myriad** attack-copy behavior, opening-hand preview cleanup and stack interaction. **Do not treat the entire issue list as fixed.**
- Development of coherent Argentum runtime/JAR rebuilds, reproducible source-provenance checking, debug scenarios and official-precon/custom-card coverage.
- Better diagnostics for AI action enumeration versus rules resolution, including the high-cost legal-actions path.
- Relevant outstanding regressions: [#51](https://github.com/AARMOU/arcana_table/issues/51), [#9](https://github.com/AARMOU/arcana_table/issues/9).

## Build 131 — upgraded on-device Argentum and growing Commander support

- Upgraded Argentum's on-device rules and AI engine with matched, Java-17 runtime bundles and a hash-checkable engine packaging pipeline.
- Expanded card data shipped with the engine (Build 131 workflow evidence reports **16,694 exported cards**) and validation of card-data/index consistency.
- Continued integration of official **Warhammer 40K** and **Eldrazi** precon cards and custom-card scenarios; incomplete original deck slots/rules remain outstanding.
- Added authoritative game-over cause/winning-card diagnostics as an isolated candidate, plus rules-reliability test work; not every candidate was merged into the first Build 131 APK.
- [PR #35](https://github.com/AARMOU/arcana_table/pull/35), [PR #47](https://github.com/AARMOU/arcana_table/pull/47), [issue #150](https://github.com/AARMOU/arcana_table/issues/150).

## Build 132 — 93-slot Eldrazi and deck-editor safety candidate

- Assembled a candidate with **93 original Eldrazi Incursion slots**, updated Argentum engine and Ulalek stack-copying/restricted mana mechanics.
- Added **Eldrazi Temple** and **Corrupted Crossroads** tested original-deck candidate behavior.
- Developer-only deck builder/card library improvements: offline search/filter, Commander validation, editing/saving, suggested alternatives, undo/redo, Fold layouts. **Import and Editor remain gated in production-style release.**
- Resolved the Android 17 deck-editor regex crash with Android-incompatible-expression scanner and regression fixtures.
- **The Build 132 CI attempt failed a strict terminal-AI scenario**; don't characterize that attempt as a shipped APK.
- [PR #231](https://github.com/AARMOU/arcana_table/pull/231), [PR #207](https://github.com/AARMOU/arcana_table/pull/207), [PR #234](https://github.com/AARMOU/arcana_table/pull/234).

## Build 133 — Eldrazi coverage and regression discovery

- Added candidate original card **Azlask, the Swelling Scourge**, with three real-engine Oracle scenarios and LKI/rules patches, reaching **94 original indexed Eldrazi Incursion slots** in the combined candidate.
- Strengthened fail-closed mirror-AI terminal-state tests; no relaxing required cases to skips.
- Detected an Android fetched shock-land payment test failure. Investigated it rather than declaring the failed build successful.
- [PR #235](https://github.com/AARMOU/arcana_table/pull/235), [PR #237](https://github.com/AARMOU/arcana_table/pull/237).

## Build 134 — current successful **no-goose preview**, not production release

The exact no-goose combined CI at [run 40368920029](https://github.com/AARMOU/arcana_table/actions/runs/40368920029) completed **SUCCESS** and uploaded:
- `arcana-b134-full-feature-no-goose-debug` (debug preview: includes debug scenario picker).
- `arcana-b134-release-look-no-goose-test-signed` (production-style UI, **CI TEST certificate**, not the Google Play upload key).

**Engine, cards and gameplay**
- Source-built upgraded Argentum with executable Ulalek stack-copying, Eldrazi mana/colorless/restricted mana and Azlask scenarios on the matching runtime.
- At least **94/100 indexed original Eldrazi Incursion slots** in the compiled preview; do not equate indexing with all 94 playing perfectly.
- Retains two-player offline gameplay, integrated multiplayer work, existing Commander/stack/mana and diagnostic paths.

**Offline AI**
- Per-seat **Easy, Normal, Hard, Brutal** difficulty with persistent selection, bounded search and safer fallback on rejected actions/AI exceptions.
- Hard's initial benchmarks were stronger than Normal, but Brutal superiority and broad matchup coverage are **not** established; four-player simulation crash is a distinct engine issue.
- [PR #270](https://github.com/AARMOU/arcana_table/pull/270), [issue #241](https://github.com/AARMOU/arcana_table/issues/241).

**Multiplayer candidate**
- Normal-app 2–4-seat internet/Wi-Fi hosting on an Argentum-authoritative host; human/AI seats, per-recipient masked state, directed WebSocket relay, resynchronization and reconnection handling.
- Old one-on-one paths preserved. **Physical 2–4-phone cross-network gameplay is not yet certified.**
- [PR #268](https://github.com/AARMOU/arcana_table/pull/268).

**Tutorial / UI / polish**
- Twenty guided chapters, 160 steps, 31 glossary terms and four real-engine offline practice lessons, with safe saved-game handling and engine-confirmed action milestones. **Known Build 134 tutorial step-two Table-view HAND issue remains until the next APK.**
- Original Arcana launcher logo restored. No giant goose on home screen. Help & Diagnostics `Engine running notice` toggle, cleaner navigation/controls.
- Popup/menu clarity fixes: consistent action labels, protected Rhystic/Tithe choices, improved button sizing and Fold handling.
- [PR #265](https://github.com/AARMOU/arcana_table/pull/265), [PR #250](https://github.com/AARMOU/arcana_table/pull/250), [PR #277](https://github.com/AARMOU/arcana_table/pull/277).

**Testing and packaging**
- Source-built matching engine/card assets, tests, original logo/package verification; reproducible temporary test-signed release-look and separate debug APK.
- **Not** a production-signed/released Play Store build. Debug scenarios are **absent** from the production-style variant.

## Next candidate — **staged** vs. already live backend

- **Full-screen 2–4-player lobby**: commander art, ready/waiting indicators, copy/share room code, guest READY UP / CANCEL READY, host 2–4 seat count, AI/friend seat switching, remove guest/close room confirmation, START only after all required guests are seated and ready. Source in Claude [PR #283](https://github.com/AARMOU/arcana_table/pull/283).
- **Fixed tutorial step two**: Table-view HAND tap must advance exactly once to **Play a land** while preserving saved-game protections and Player-view control. Source in [PR #282](https://github.com/AARMOU/arcana_table/pull/282).
- **Combined source preflight is GREEN**, [PR #286](https://github.com/AARMOU/arcana_table/pull/286), [run 40379321158](https://github.com/AARMOU/arcana_table/actions/runs/40379321158). **Combined APK compilation and Fold/multi-phone acceptance not yet completed.**
- **Worker only is already live:** the updated `arcana-table-rooms` Cloudflare Worker from exact Claude #283 passed local + live Internet privacy/seat-edit/reconnect tests in [run 40380341632](https://github.com/AARMOU/arcana_table/actions/runs/40380341632). This does **not** mean the updated lobby Android UI has shipped.
- **Requested but not completed:** re-enable **Import new** and the existing Deck Editor in the release app and connect both to the updated unified card library/importer. Do not advertise these buttons as released. [PR #207](https://github.com/AARMOU/arcana_table/pull/207) is developer-only; production feature-gating requires separate work.
- **Other pending**: popup suppression in [PR #287](https://github.com/AARMOU/arcana_table/pull/287), replayable bug bundles, full engine/regression coverage, token-heavy performance and 4-player physical acceptance.

## Changelog maintenance rules

1. Add a new numbered build entry only with an exact source, test run and artifact identifying the change; don't invent build-to-feature mappings from PR creation dates.
2. The in-app `ChangelogDialog` at `android/app/src/main/java/com/arcanatable/TableDialogs.kt` is exposed via **What's new** in the game menu and Help & Diagnostics.
3. Keep *developed but not integrated*, *CI-built*, *server deployed*, *device validated* and *production released* separate.
4. Update both this long-form file and `tools/next_apk_changelog_since_b121.py` at each new candidate. The latter adds the latest entries to the **actual runner-assembled Android source after AI/multiplayer/UI merges**, without overriding Claude's branch.
5. The old Build 123/122 prose remains intact. Earlier versions (Build 105 and below) remain available under **Earlier history**, not deleted.
