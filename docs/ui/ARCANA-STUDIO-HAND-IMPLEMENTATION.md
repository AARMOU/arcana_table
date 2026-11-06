# Native Arcana Studio implementation and acceptance ledger

November 6, 2026. Issues #994/#995. Development-only native implementation;
physical Fold acceptance and owner merge/release approval remain required.
The known-good Adjust UI editor and prior hand-preview APK remain available.

## Shared renderer and exact hand project

The editor manipulates the same `StudioLayout` used by the real HandSheet.
Version 1 imports preserve the owner's fixture exactly; version 2 explicitly
records canvas width/height and screen origin. Reference 412×928 first action
y=344 is retained. Separate responsive starters cover narrow cover, portrait,
open fold and landscape. Geometry is explicit, never silently rescaled on import.
An unsafe applied viewport recovers to native controls and explains recovery.
Overflow cards use actual current hand IDs; no exported sample card enters play.

Hand properties include coordinates, dimensions, stacking order, radius, safe
static labels, fill/text colors, icons, visibility, lock, known action binding,
duplicate/remove card slots, and seven-role palettes. Printed card text/art and
phase data remain live. Inapplicable art/font/icon fields are absent or disabled.
Native close/resize chrome remains reachable outside the editable canvas.

## All-screen native adapters

`studio-native-control-catalog.json` inventories 2,121 admitted call sites;
`StudioSites` validates imported identities. These are sites, not proof that
2,121 simultaneously visible controls or every game state was device-tested.

| Native surfaces | Actual implementation | Automated evidence |
|---|---|---|
| Home, setup, deck selection/editor | Shared native widgets; actual callbacks and screen scopes | Real MainActivity edit/apply/recreation; existing deck/setup regressions |
| Table/player/battlefield, hand | Native custom helpers/CardFace plus shared hand canvas | Native hand geometry/drag/resize/privacy; real-app stack/own-zone navigation |
| Combat, targets, stack, legal actions | Current engine-provided controls, native dialog scopes | Existing combat/target/action regressions; current-action/window guard tests |
| Room/lobby, settings, menus/dialogs | Native widgets/input adapters and guarded dismiss | Existing app regressions; real settings registry and input-type checks |
| Repeated rows/cards/buttons | Distinct instance claims; current callbacks, protected data | Ten repeated buttons invoke ten distinct callbacks; face-down hand tests |

Default unconfigured gameplay does not run the control registry. Editing or an
applied project enables it. Native callbacks remain current, guarded by enabled
state, source/target screen and real Android window. Bindings never create an
engine action. Dynamic/private labels are not project values. Printed card art
cannot be replaced with a fabricated static label. Rebound controls show their
actual target caption. Required native actions recover when no reachable alternate
exists. Duplicate buttons execute the genuine source callback in its real window.
Live information modules duplicate or rebind a current native text source in the
same screen/window. Full current text is held only in runtime memory, updates
with state, survives a hidden original, and never enters exported project JSON.
Information cannot bind to action callbacks or another window.

| Visible Studio option | Effect / handler | Focused proof |
|---|---|---|
| Inspect/select/layers, hold move/corner resize | Real native control metadata/modifier, no game action during editing | `StudioNativeControlsUiTest`, `NativeStudioUiTest` |
| Coordinates/dimensions/font/radius/color/icon/layer | Native modifiers/material properties; only applicable fields offered | Model rejection; actual bounds/style UI checks |
| Live information binding, add/remove module | Current native text source, no serialized values or callbacks | Live update/rebind/reload/hidden-original UI test; wrong-kind/window rejection |
| Action binding, duplicate/remove button | Current same-window callback catalog and native copy renderer | Guarded rebound, repeated IDs, duplicate/reload/hidden-original recovery |
| Minimal/Balanced/Detailed, custom profiles | Per-orientation applied/draft stores, optional per-screen density | Profile copy/isolation, logical-screen reset, serialization/history tests |
| Apply/cancel/undo/redo | Atomic controls+pending hand+active profile commit; bounded revisions | Invalid bundle no partial write; undo and apply persistence |
| Reset screen/profile/all | Only Studio preferences; logical-screen ownership persists | Reset isolation, required-action recovery, corrupt backup tests |
| Import/export/copy | Validated versioned JSON bundle; hand also supports Android document picker | Schema/unknown binding/version/orientation rejection and round trip |
| Palette and labels | Real TableColors/native text, dynamic values protected | Palette serialization and protected-label rejection |
| Screen navigation | Opens actual available app surface, never advances engine state | Real Home/settings app test; game-only destinations explicitly disabled |

Named profile copy includes hand and controls. Draft autosave never changes the
applied design. Both orientations have separate active profiles. Reset all removes
only Studio keys. Version 3 export bundles controls and optional hand data; invalid
imports leave both applied and draft state unchanged. Screen reset restores native
Balanced density for that screen without erasing other screen properties.

## Verification and remaining acceptance

The earlier exact 3491846 checkpoint passed app537 tests (5 existing live-service
skips) and unchanged card1082 tests. Expanded source focused model/native UI plus
real-activity recreation checks passed before the current complete-suite run.
Final exact commit and fresh complete-suite receipt are recorded in the handoff;
these earlier totals must not be reused as current-head certification.

Screenshots in `android/app/build/screenshots` are Robolectric renders. The
412dp real hand starts at y344; native editor drag/resize and actual Home movement
were inspected. They are not physical Fold screenshots or live room acceptance.
The catalog is implementation coverage; per-screen physical acceptance is PENDING.
No attached Android device was available in the last inventory. Final S5, removal
of the classic editor, production default flip and owner acceptance remain OPEN
until real portrait/landscape/folding/touch/restart evidence and independent review.
No engine jars, Argentum data, gameplay legality, signing/release or owner gates
are changed by this implementation. #978 remains HOLD.
