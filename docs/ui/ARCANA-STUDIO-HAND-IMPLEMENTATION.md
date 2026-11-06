# Native hand Studio implementation checkpoint

November 6, 2026. #994 first implementation increment under #995; **not full
Studio acceptance, not an APK delivery**. The classic Adjust UI entry remains.
Debug builds additionally expose `Arcana Studio`, a dedicated native full-screen
hand editor. Other screens are explicitly not offered as implemented features.

## Implemented

- `StudioLayout.kt`: version-1 hand import/export, exact owner fixture, bounded
  schema, unique/consecutive card slots, known action bindings, overlap/required
  control validation, viewport rejection, bounded undo/redo, orientation-separated
  draft/applied/backup preferences. Disable retains the design; reset restores the
  native hand. Failed Apply leaves applied data intact.
- `StudioHandCanvas.kt`: shared native coordinate renderer for editor and actual
  hand; hold/drag, corner resize, selection, real card-slot data, live labels/counts,
  saved style/theme and reachable overflow cards. Invalid drafts are kept out of
  live gameplay. Existing callbacks/availability remain authoritative.
- `NativeStudioPage.kt`: layer selection, geometry/font/radius properties, locks,
  labels where safe, visibility, validated native action selection, duplicate/remove,
  seven-role palette/presets, owner fixture load, JSON text import/export/copy,
  undo/redo, Apply, enable/disable and native reset. Primary phase/card names remain
  live game labels and their label fields explain that they are not editable.
- `TableOverlays.kt`/`TableBoardViews.kt`: applied design drives the actual
  `HandSheet`, suppressing duplicate hand chrome. Real stack is distinct from legal
  actions; library reveals own count only. Existing close/resize remain outside
  editable content. At reference width412, the grip/close reservation places the
  first row at screen y344. Inaccessible geometry falls back to native with a
  visible explanation rather than silently rearranging exported elements.

## Automated evidence

Focused model/UI and original hand-action/UI regressions passed before the full
local suite. Separate real-app `ArgentumResilienceUiTest` scenario passed using
the existing recorded-server harness (not a live relay or physical engine/device
claim). It loads saved layout through the real app, opens actual stack/own zone
dialogs, and verifies disabled writes during network wait.

Key new executable checks:

| Feature | Test class / method |
|---|---|
| Exact fixture, schema/binding/geometry rejection | `StudioLayoutTest` |
| Restart preference read, backup recovery, draft/apply separation, undo/reset | `StudioLayoutTest` |
| Actual native bounds and distinct handlers | `NativeStudioUiTest.actualNativeControlsFollowAppliedCoordinatesAndDistinctActions` |
| Busy guards and hidden library | `NativeStudioUiTest.busyActionsStayDisabledAndLibraryShowsOnlyOwnCount` |
| Real selected card CAST | `NativeStudioUiTest.selectedCastUsesActualCardIdentityAndEmptyHandHasNoSampleArt` |
| Empty/large hands | `NativeStudioUiTest.emptyHandDoesNotRenderExportedSampleCards`, `largeHandExposesOverflowSlotsBelowTheOwnerDesign` |
| Editor drag, Apply and saved coordinates | `NativeStudioUiTest.editorDragApplyAndReopenPersistActualCoordinates` |
| Editor corner resize and binding retention | `NativeStudioUiTest.editorResizeChangesSavedDimensionsWithoutLosingActionBinding` |
| Actual app wiring | `ArgentumResilienceUiTest.studioSavedLayoutDrivesActualAppStackAndOwnZones` |

Generated local screenshots: `android/app/build/screenshots/studio-native-hand-editor.png`
and `studio-real-app-portrait.png`. They are Robolectric renderings, **not physical
Fold screenshots**. Earlier red runs found and fixed semantics-target, empty-hand
CAST and editor-gesture issues; overlap rejection is retained, not bypassed.

## Remaining acceptance and limitations

- S1 physical Fold portrait/landscape/touch/restart acceptance remains pending;
  existing ADB inventory returned no attached device. No USB requirement is added:
  owner phone evidence through an authorized path can provide device acceptance.
- Unsupported narrow/large-font/short landscape geometry deliberately keeps native
  UI. General responsive profiles/canvas transforms remain to be extended; do not
  call all-orientation Studio complete.
- This increment has one hand profile per orientation; named profile management,
  copy/profile/app reset and the full S2–S5 screen/control matrix remain open.
- JSON export is editable/copyable project text; system file-picker integration,
  icon properties and arbitrary new control types are not offered as working options.
- Required native controls cannot be removed without an alternate reachable binding.
  No stored binding grants permission to create an engine action or reveal private data.
- No #978 promotion, production default change, main/development merge, new engine,
  APK replacement/delivery, signing/release/deploy or additional spending.
