# Arcana Studio — Claude → Claude handoff

_Written Nov 6, 2026, 23:05 MT, after batch 1. Read this first, then #995 (plan), #883 (owner decisions), #1016 (GPT index)._

## Update: Nov 8, ~10:30 MT (owner: "Finish the studio project")
- **Landed** (`a49cbe6`, `220c84f`, `4af8d38`):
  - **Per-player IDs (note 28 / J8).** `LocalStudioSeat` / `StudioSeat(index, localSeat, count)` wraps seat chips, overview tiles and player boards. IDs inside become `native:<site>/s<screen>@<you|opp1|opp2…>:<n>`, with opponents counted from your seat. The screen-order ID is still claimed and aliased (`runtime.alias`): an old edit shows until the item is next edited, then moves to the player's ID (`withStyle`). Test: `StudioSeatIdsTest`.
  - **Fill it up (note 29).** Studio ⋮ > Layout > Editing switch (`runtime.fillItUp`, never saved). MainActivity keeps the real players in `TableController.studioRealPlayers` and shows `StudioBusyPreview.fill(...)`. A new engine state refills from it. Off puts back `argentumSnapshot.players ?: saved`. `savePlayers` is skipped while filling. Tests: `StudioBusyPreviewTest`, `StudioFillItUpUiTest`.
  - **Two-finger scroll (note 5).** `studioScrollTarget(state, horizontal, dragDelta)` registers scrollers in `StudioScrollTargets` while the shell is open. It covers `hintedHorizontalScroll`, `studioRowScroll`, the battlefield LazyColumn, the hand grid and the overview grid. Two pointers on `StudioSelectionCanvas` scroll the innermost target under them and edit nothing. The toolbar Scroll toggle stays for any list not registered.
  - **Presets (batch 4).** `StudioPresets`: Classic, Big Table, Big & Clear, Command Center, Pocket, Spread Out. Each sets card/board/button/hand sizes (`StudioLookHost`, the same prefs Adjust UI wrote), density, Show on screen, turn label and palette. Keep / Back and "Keep my colours" are offered. Left-Handed was not done: it needs per-item moves, and presets deliberately never move single items.
  - **Light/Dark switch** hidden in Studio builds (`TableDialogs`), which always start dark; **Light** is a Studio palette chip.
  - **Game options.** Studio ⋮ > Layout > Game options opens `AdjustUiPage(inStudio = true)`, which has no tray-height slider because the hand editor owns that. Before this, Adjust UI was unreachable in Studio builds.
  - **J11 (Red Bull, 272 vs 320 px).** In Studio builds, `tutorialTarget` uses the full unclipped bounds of a visible target.
  - **Show me is back on.** The CI "hang" was the test: an animated `performScrollTo` to an off-screen tab under the frozen test clock. `StudioShowMeUiTest` runs one test per sample with a watchdog that writes a `*-hang-*.txt` report, and CI prints `app/build/screenshots/*-hang-*.txt` on failure.
- **Local tests:** Maven Central rate-limits this container. Robolectric needs `android-all-instrumented-14-robolectric-10818077-i7.jar`; curl it into a dir and pass `-Parcana.robolectricJarDir=<dir>`. `nativeruntime-dist-compat` can go in `~/.m2` with an init script that puts `mavenLocal()` first. The CI artifact zip is unreadable from here (Azure blob 403), so read the job log instead.
- **Still open:** chip reflow inside the scrolling seat row, Left-Handed preset, real preset thumbnails, and device (Fold) acceptance of everything above.

## Update: Nov 7, ~15:00 MT (owner: "No I want you to finish studio")
- **CI hang found:** `StudioCoverageAuditTest.showMeSamplesOpenWithoutTheRealMoment` (not J10). The Show me samples were rebuilt on every recomposition; now `remember`ed (`bfbf49f`). Claude B's per-test names and 25-min timeout made this visible.
- **Still hung with remembered samples** (run 42417032930), so "Show me" is switched off (`StudioPreviews.ENABLED=false`, `b99666d`) and the test is `@Ignore`d. Next: bisect by tab (Combat / Opening hand / Actions); note the test deals a real opening hand (`fake.dealOpeningHand=true`), so a real mulligan pop-up may be up underneath. Test APK `b99666d` built.
- **Landed:**
  - Multi-select: hold an item to pick all like it (same code site, same screen); tap adds/removes; drag or any inspector change applies to all as one Undo (`StudioEditorGeometry.groupEdit`)
  - Line up: Left edges, Top edges, Centres, In a row, In a column, Same size, against the first picked (`StudioEditorGeometry.align`)
  - Button types (note 26): Action, Panel, Counter, Phase tab, Tray bar (`StudioButtonTypes`, field `type`)
  - Table background (16/17): tap empty table space → Table panel: picture (private copy), Fill/Whole, Dim, Remove, Match picture, Shuffle (`StudioBackdrop`, `StudioPictureColors`, `runtime.tablePicked`)
  - Back up / Restore / Share layout (34); plain names for the player panel etc. (`StudioNames`, 12); commander `art_crop` and wrapping name (12); tutorials show hidden items while running (33)
- **Still open:** two-finger scroll (needs pass-through; the toolbar toggle stays), J8 per-player keys (28), Fill it up preview (29), chip reflow in the scrolling seat row.

## Update: Nov 7, ~14:00 MT (owner hit usage limits; GPT workers stopped)
- **Second Claude account handoff** (J12, engine/rules queue, reviews; never touches #1013): [#81 comment 6712053976](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6712053976).
- **Landed since 37188ed:**
  - Show on screen covers phase bar, chips, TABLE, FULL TABLE label, mana bar, button row, commander chip, thinking bar and timer (`studioPieceShown`); hidden rows free their space (test `hidingScreenPiecesGivesTheirSpaceToTheBoard`)
  - Scroll arrows on all sideways bars (J15)
  - **Permanent IDs** (`d61a18b`): `studioStableId(site, screen, n)`, `withStableIds()` migration from owners, `_v1_original` rollback copy; clashes and missing owners are never guessed
  - J10 fixture tests copied into this PR (`63f4d46`)
  - **Neighbours make room** (`2185a22`): `StudioFixedSlotElement` replaces Row/Column weight for resized controls
  - **"Show me…"** (`af90a09`): `studioPreview` + `StudioPreviews` for Combat, Opening hand and Actions tabs
- **CI note:** runs from `494595c` took 45–75 min or hung; suspect the optionsExportImport test's close/reopen of the More panel, replaced by semantics clicks in `2ef17d3`. If CI still runs >30 min, bisect the new real-app tests (J10 UI, Show me, Show on screen).
- **Still open:** multi-select, Align, button types (26), selectable battlefield and background (16/17), plain names from J7, two-finger scroll, chip reflow inside the scrolling seat row (it scrolls; neighbours don't shrink there).
- **APK:** the owner said to build when the soonest sound point is reached (owner: "Whatever will get this done and into an apk as soon and as soundly as possible"), after CI is green on the head. J10's runtime proof comes from this PR's CI; J12 is with Claude B.

## Update: Nov 7, ~10:30 MT, batch 2b in progress (owner: no APK until GPT's jobs are verified DONE)
- **Owner feedback** on #995: [2a notes](https://github.com/AARMOU/arcana_table/issues/995#issuecomment-6707718742) and [2b part 1 notes plus the button-text finding](https://github.com/AARMOU/arcana_table/issues/995#issuecomment-6709459150). GPT jobs in priority order (J9 → J7 → J8, J13–J15): [#950 comment 6709459381](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6709459381). **Don't build or send an APK until the owner confirms GPT is done.**
- **2b done:**
  - `StudioLog` (separate file; Share and Clear in ⋮ > Project; logs edits, stepper taps, Apply, advice, messages)
  - +/- fix (`StudioHandEditing.edit` reads `runtime.handDraft()` at tap time)
  - `turnLabelText` and `StudioControlProject.turnLabel` ("R2 · T3", changeable)
  - Editor chrome colours from the palette (`StudioEditorRequests.chromeGold/chromeAction`; square handles; APPLY)
  - `studioFitContents()` plus always-clip for resized controls (text no longer leaves buttons)
  - Drawer top with a custom design uses the one-line `compactHandLine` (editor shows the same line); the grab handle is drawn directly
  - Thinking strip waits 0.5s and can be hidden (`hiddenPieces`, `SCREEN_PIECES`, ⋮ > Layout > Show on screen)
  - `hitTest(..., current)`: a small group (≤12× the part's area) is picked first, a second tap goes inside
  - Minimal keeps the log text
  - Log entries share an edited sibling's style
  - `hintedHorizontalScroll()` arrows
  - Legacy mint grip off while the shell is active
- **2b still open:**
  - "Neighbours make room" (Table group height clipped by weight, the YOU chip pushing OFFLINE AI off, hand tray frame vs drawn size). Needs per-panel changes in `TableMainLayout`; do it with J7/J13.
  - The rest of Show on screen (chips, phase bar, mana bar, TABLE, button row) after J13
  - "Show me…" samples after J9
  - Multi-select, Align, button types (note 26), selectable battlefield and background (16/17)
  - Plain names after J7
- **Risk, check first:** Studio IDs are `native:<site>/<currentCompositeKeyHash>`. If the compiler's group keys depend on source position, editing a file can change IDs after the edit point and detach saved owner edits between builds. J7's permanent names are the fix (Nov 6 note 27); confirm whether the owner's applied edits survived 2a → 2b.

## Update: Nov 7, 00:45 MT, batch 2a first drop
- **Owner feedback on the batch-1 APK** is the plan now: 36 items on #995 ([comment 6703609046](https://github.com/AARMOU/arcana_table/issues/995#issuecomment-6703609046)). Goose jobs J7–J12 are on #950 (comment 6703633081); they feed notes 27–30, 32 and 33.
- **2a done** (commits `5bac020`, `4165b7c`, `2f0d15c`):
  - Bugs 18–21: Leave keeps the whole draft; `discard()` and "Discard draft" in the Leave dialog.
  - Note 14: hard vs advice. `problems()` is only lock-outs and malformed data. `advice()` (runtime: id → line; `StudioLayout.advice()`) drives the red dashed outline and corner flag in `StudioSelectionCanvas` and `StudioHandCanvas`, and live red while dragging (`StudioEditorGeometry.dragTrouble`). Resizing can go down to `FLOOR_DP` = 16. The warning bar is gone; `studio-notice` shows only `message`.
  - Bug 22: Studio size modifiers sit outside the call-site modifiers (`StudioWidgets.kt`).
  - Bug 23: no in-game `recover()` from bounds.
  - Bug 24: `STUDIO_LAYOUT_KEY`, one layout for every orientation.
  - Note 8: `viewportProblems` no longer rejects; the hand canvas grows buttons to 44 in play.
  - Bug 25: hand-canvas drag starts immediately.
  - Note 5 (interim): Select/Scroll moved into the toolbar.
  - Note 3: the canvas paints no background.
  - Note 9: compact drawer line, Studio builds only.
  - Notes 4/10: `StudioEditorRequests.screenTopInset` pads `TableMainLayout`.
  - Note 6: rounding.
  - Note 32: `onGloballyPositioned` only while editing.
- **2a still open:**
  - 5: true two-finger scroll, and removing the toggle
  - 12: plain names, commander text fit, `art_crop`
  - 15: content scaling, the ghost slot, past-edge
  - 27/28: stable IDs and player keys. Wait for Goose J7/J8.
  - 36: verify on device that PASS and the hand tray stay visible in Studio
- **Then 2b:** 7, 13, 16, 1, 2, 26, 35. **Then 3:** 17, 16-background, 29, 30, 33, 34.
- **Local tests:** Maven Central rate-limits this container (429), and Robolectric's `android-all-instrumented` jar won't download, so Robolectric tests can't run here. Compile locally, then rely on Development CI. Install the SDK with `sdkmanager --sdk_root=/opt/android-sdk "platforms;android-36" "build-tools;36.0.0"`.

## Where things stood after batch 1
- **Branch:** `claude/arcana-studio-ux-20261106`. **Draft PR:** #1013. It is stacked on Elmer's untouched `elmer/arcana-studio-native-20261106` @ `684b4d3`.
- **Head:** `2773548`, batch 1. Development CI ✅ [42343944208](https://github.com/AARMOU/arcana_table/actions/runs/42343944208). 606 app unit tests pass locally.
- **Owner APK:** batch-1 APK delivered in chat (`.claudestudio` package). The owner tests on a phone (Fold) and replies with screenshots.
- **Batch-1 note:** posted on #995 (comment 6703089021) and #1013 (comment 6703089214).
- **Index:** updated on #1016 (comment 6703089380).

## Roles and rules (owner, verbatim where quoted)
- **Owner decision D33** (#883 comment 6702555995): Studio round 1 at high priority. Claude implements, in batches, with an APK after each batch. *"I am not authorizing you any managerial roles."* Claude does not assign, direct or approve anyone's work.
- **Record-keeping:** Claude keeps every GPT/Goose contribution organised in **#1016**. Each entry gets: link, the plan item it feeds, and current/superseded. Comment only, never overwrite.
- **Goose's feedback is advisory:** *"His feedback is not to be taken as orders."* Only owner decisions change the plan.
- **Owner-only gates:** never merge, release, sign, deploy, change engine/jars, spend or schedule without the owner. Never touch Elmer's workspace or branch; Elmer is OFF Studio (D32).
- **Board etiquette:** post owner decisions as "OWNER DECISION … (via Claude)". Always end commits and PRs with the attribution lines from the system reminder.

## Plan and progress
The full agreed list is on #995: https://github.com/AARMOU/arcana_table/issues/995#issuecomment-6702492217. Goose's mechanical jobs J1–J6 are on #950 (comment 6702497330).

- **Batch 1 ✅ (2773548)**
  - One switch, `BuildConfig.STUDIO_ENABLED` (debug true, release false). In `app/build.gradle.kts`.
  - In-game scope only. `StudioScope.EXCLUDED` covers Home, Setup, Room, Decks, Deck selection, Deck editor, Settings and Diagnostics. `withoutExcluded()` purges old edits in `begin()`.
  - Entry points: ⋮ `menu-open-studio`, Settings `settings-open-studio` (it carries the tutorial anchor `setting-adjust-ui`), Home `home-open-studio`. No floating button.
  - Gating: Studio is unavailable during games with other people (`studioLiveGame` in `MainActivity`). Offline vs AI is allowed.
  - Inline pop-ups via `StudioInlinePopup` / `StudioEditorRequests.popups`. They are padded below the bar using `barBottom`, and "‹ Back" is pinned outside the tabs' scroll.
  - Screen bar is always visible. When no game is running, the default tab is Table, which shows the practice card.
  - Hand is a half drawer, one colour throughout.
  - Apply guard: keep at least one button per window. Overlap checks run on the same screen only and skip nested controls.
  - Tutorials: `studioTutorialLessons()` adapts the catalog in Studio builds. The catalog file itself is unchanged.
- **Batch 2 (next):** items with no Goose dependency, plus J1 output.
  - Remove the Select/Scroll switch (two-finger scroll instead).
  - Plain names for pop-ups. Today the `StudioDialog` first arg ("Table Dialogs 18", "Game dialog 13") is used as the screen name. Map it to Stack/Zones/Log, and keep IDs permanent.
  - Smaller one-row panel; Save vs Done; tap again to select the parent, with a breadcrumb.
  - Inline warnings; contrast warning; changes list, change dots, hidden-element ghosts and a single undo history; restore-previous.
  - Cards auto-size as before Studio.
  - Merge Adjust UI into Studio, but only settings verified to work, and drop redundant ones. Use Goose's J1 finding: `studioHandFraction` overrides `hand_sheet_height_fraction` (#950 comments 6702616071 and 6702831866). Drawer height must be editable and auto-fit on by default.
  - Hand drawer: top gap, X and hint each editable or toggleable.
  - Commander chips show `art_crop`, centred, scaling with the chip.
  - Table "Expand to fill"; empty rows collapse.
  - Resizing changes the visible thing; outlines only on visible items; buttons keep their normal look while editing.
  - Every informational object can be hidden (none hidden by default).
  - Reopening Studio shows the true state; editor prefs persist.
  - AI pause while editing an offline game.
- **Batch 3:** apply Goose's J2–J6 findings. Then round-2 items:
  - multi-select and align, alignment guides, copy/paste style and apply to similar
  - side panel on unfolded phones and tablets
  - first-run guide
  - layouts by player count; "Add info" modules
  - named undo, haptics, share sheet, "tidy labels"
  - large-text and screen-reader certification
- **Batch 4:**
  - Presets: Classic, Big Table, Big & Clear, Command Center, Left-Handed, Pocket, Spread Out. Markedly different, real thumbnails, Keep/Back, "Keep my colours".
  - Light/Dark switch removed; Light becomes a palette.
  - Custom local table background.
  - Options sheet (Look/Cards/Info/Backup) and a Fine-tune switch; per-size layout under Fine-tune.
  - Busy 4-player practice and combat practice; performance check.
  - Studio guide in `docs/studio-ux/`; update the PR #1013 description.
- **After Studio, the rules fixes:**
  - Ratchet: test defect plus command-zone and save/resume tests (#996).
  - Alms tests (#999).
  - Lighthouse tests (#1001).
  - Goblin Welder simultaneous exchange (#1004/#1003). This is an engine change and needs owner approval to merge.

## How to work
- **Worktree:** `/home/claude/studio-wt/android`. `local.properties` has `sdk.dir=/opt/android-sdk`.
- **Compile:**
  ```
  timeout 1200 ./gradlew --no-daemon -q --max-workers=4 :app:compileDebugKotlin
  ```
- **Studio tests:**
  ```
  :app:testDebugUnitTest --tests '*Studio*' --tests '*ArgentumResilienceUiTest.studio*'
  ```
  The full suite (`:app:testDebugUnitTest`) takes about 5 min. Delete `app/build/test-results/testDebugUnitTest` before reading results.
- **APK:**
  ```
  :app:assembleDebug -Parcana.debugApplicationIdSuffix=.claudestudio "-Parcana.debugLabel=Arcana Studio Test (Claude)"
  ```
  Send it with SendUserFile.
- **Before pushing:** `python3 tools/check_android_regex.py` and `git diff --check`.
- **GitHub:** gh REST only (GraphQL is blocked). Find CI with `gh api "repos/AARMOU/arcana_table/actions/runs?head_sha=<sha>"`; the workflow is "Arcana Development CI". Retry Maven 429s.
- **Test gotchas:**
  - In-game ⋮ menu: open it via `tableController.topMenuExpanded=true`. A synthetic tap after the opening hand doesn't open it.
  - Debug a missing node with `onAllNodes(isRoot()).onFirst().printToString(maxDepth=12)`.
  - Screenshots go to `app/build/screenshots`. Copy key ones to `docs/studio-ux/`.
  - Never weaken tests. When an owner decision changes a contract, rewrite the test to the new contract and say so in its KDoc.
- **Key files:**
  - `StudioControls.kt`: runtime, `problems()`, `withoutExcluded`.
  - `StudioScreenScope.kt`: exclusion.
  - `StudioWidgets.kt`: control registration.
  - `StudioDialogGuards.kt`: inline pop-ups.
  - `StudioEditorShell.kt`: shell, screen bar, practice card.
  - `StudioEditorPanels.kt`, `StudioInspector.kt`.
  - `StudioHandEditor.kt` / `StudioHandCanvas.kt`.
  - `MainActivity.kt`: Studio block, destinations, live-game gating.
  - `TableMainLayout.kt` (⋮ menu), `TableDialogs.kt` (Settings), `HomeScreen.kt`, `TutorialHubDialog.kt`.

## Open questions / watch-outs
- Combat can only be edited during combat in a practice game, so it isn't covered by tests yet. Batch 4 adds combat practice.
- The classic `StudioControlsPage` fallback still exists. Removal (#1014) is deferred until the owner accepts the new Studio.
- The owner's phone screenshots drive priorities. Fix what they hit first, then continue the batch order.
