# Arcana Studio — Claude → Claude handoff

_Written Nov 6, 2026, 23:05 MT, after batch 1. Read this first, then #995 (plan), #883 (owner decisions), #1016 (GPT index)._

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
