# Arcana Studio handoff: Claude → next Claude (Nov 7, 2026, 15:40 MT)

**Repo:** AARMOU/arcana_table · **branch:** `claude/arcana-studio-ux-20261106` · **PR:** #1013 (draft) · **plan/feedback:** #995 · **jobs board:** #950 · **coordination:** #81
**Head:** `b99666d` (exact-head CI green, run 42419505927, 652 tests) plus a docs-only commit on top. **Test APK `b99666d` was sent to the owner** (app id `com.arcanatable.claudestudio`, label "Arcana Studio Test (Claude)").

## Owner rules (binding)
- Never, without explicit owner approval: merge, push to main, release, sign or publish builds, replace engine jars or snapshots, change billing, dispatch, `ARCANA_*` variables or Copilot settings, or spend money.
- Goose (GPT) feedback is advisory; the owner decides.
- Settings/options menu must stay unaffected by Studio. Nothing named "cheat". Studio is test builds only (`BuildConfig.STUDIO_ENABLED`).
- Studio is unobtrusive: red marking, never blocking. The exceptions: Pass and the way back to Studio can't be hidden.
- No heavy jobs on the owner's PC. Don't manage other workers; post jobs on #950 only with owner approval.
- Talk plainly ("dumb it down"). The owner is on a phone (Fold). Don't go silent for more than about 3 minutes; send a one-line status before long waits.

## How to work
- Local: `cd android && ./gradlew -q :app:compileDebugKotlin --offline`. Plain JUnit tests run locally. **Robolectric UI tests only run in GitHub CI**: Maven Central returns 429 for the android-all jar here.
- CI: "Arcana Development CI" on push. The unit-test task now names each test as it starts and times out after 25 min (Claude B's `8b963c4`/`c295597`). A healthy run takes about 15 min in total.
- Before an APK, run `python3 tools/check_android_regex.py`.
- Build the APK with `./gradlew -q :app:assembleDebug --offline "-Parcana.debugApplicationIdSuffix=.claudestudio" "-Parcana.debugLabel=Arcana Studio Test (Claude)"`.
- Use gh REST only (GraphQL is blocked). Sign GitHub posts "— Claude".
- Studio log: Studio ⋮ > Project > Share Studio log. The owner sends these; they're the best bug evidence.

## Done today (all on the branch)
- **Multi-select:** hold an item to pick everything from the same code site on the same screen (`runtime.holdSelect`, `toggleMember`, `group`, `members()`). Edits apply to all as one Undo (`editGroup`, `StudioEditorGeometry.groupEdit`). Dragging the lead moves all of them.
- **Line up:** Left edges, Top edges, Centres, In a row, In a column, Same size, against the first picked (`StudioEditorGeometry.align`, `runtime.align`).
- **Button types (note 26):** Action, Panel, Counter, Phase tab, Tray bar (`StudioButtonTypes.kt`, style field `type`). Picked in the Style tab, for buttons only.
- **Table background (16/17):** tap empty table space to pick the table (`runtime.tablePicked`) and get the Table panel (`StudioTablePanel.kt`): picture (private shrunk copy, `StudioBackdrop.kt`), Fill/Whole picture, Dim, Remove, Match picture and Shuffle (`StudioPictureColors`, pure and tested). Project fields: `backdrop`, `backdropFit`, `backdropDim`.
- **Back up / Restore / Share layout (34).**
- **Note 12:** plain names (`StudioNames`), commander `art_crop` (`commanderArtOnly`), and the commander name wraps.
- **Note 33:** tutorials show hidden items and screen pieces while one is running.
- **CI hang found:** `StudioCoverageAuditTest.showMeSamplesOpenWithoutTheRealMoment`. It still hung after remembering the samples, so **"Show me" is OFF** (`StudioPreviews.ENABLED=false`), the test is `@Ignore`d, and Combat is editable during combat as before.

## Next, in order
1. **Bugs from the owner's Studio log** (sent 15:36 MT, recorded on the earlier APK `3ccba4e`):
   - **Hand drawer +/- still unreliable.** Lines 324–349: the panel shows "was 164" while Pass is really 44, so the Width shown is stale. Then 9 "+ Width (was 24)" taps make no edit, then a single jump to 128×88. The panel's layout (`StudioHandEditing.layout` → `runtime.handDraft() ?: StudioLayout.responsive(...)`) and the tap-time draft (`StudioHandEditing.edit`) differ. Suspect `handDraft()` falling back to the stored draft or the responsive layout while `pendingHand` is null, or a size below 44 being treated as a problem somewhere. Check `StudioEditorShell.kt:82`, `StudioHandEditor.kt:52–64` and `HandLayoutTab` steppers (min 24). Write a Robolectric test: select Pass, tap − to 24, then +, and assert each tap changes the size and the displayed value.
   - **A panel's name changes to what it grew over.** Lines 368–371: after "Open hand tray" grew to 648 tall, its edits were logged as "40". `runtime.bounds()` re-picks the caption child from whatever sits inside the panel's current bounds. Fix: keep the first caption (`captions[id]`) instead of re-picking it after an edit.
   - **Many items are just "Control"** in the log and panels (lines 11–76, 106–107, 357–358), probably seat chips or player panels. Add their sites to `StudioNames`.
   - **Screen "Table Dialogs 22"** is the game log dialog (`TableDialogs.kt:2220`). Give it a plain screen name such as "Game log". Check whether `StudioSites` and the tests key on that screen string before renaming.
   - "Apply refused: Keep required action App menu reachable" still appears as a blocking message (lines 140–147). The owner's rule is that only Pass and the way back can't be hidden; App menu counts as the way back. Make it red marking that can't be hidden instead of a refused Apply, or confirm it already is.
2. **Find the Show me hang.** Bisect by tab (Combat, Opening hand, Actions). Note that the test deals a real opening hand (`fake.dealOpeningHand=true`), so a real mulligan pop-up may sit underneath. Re-enable it only when CI passes.
3. **Note 28 / J8:** tie per-player edits to the player (you, opponent 1…) instead of screen order. IDs change, so this needs a migration like `withStableIds()`.
4. **Note 29:** the "Fill it up" busy preview (J10 fixture `argentum/studio-busy-four-player.json`).
5. Two-finger scroll (needs touches passed through to the game; the toolbar Select/Scroll switch stays for now), and chip reflow in the scrolling seat row.

## Pointers
- Detailed engineering notes: `docs/studio-ux/HANDOFF-claude.md` on the branch.
- Owner's 36-item feedback list: #995 comment 6703609046. Latest status: #1013 comment 6713867695.
- Goose J7/J11 source audits: #950 (J7 semantic names, J11 tutorial steps); J8 on #995.
- Second Claude account handoff (engine/rules queue; never touches #1013): #81 comment 6712053976.
