# Arcana Studio native implementation audit

S0 source audit, November 6, 2026. Owner scope: #883 D31 / #995;
first functional milestone: #994. Baseline is delivered hand preview
`4fdefa87980778dba130c2f648ae12efcfce2a01`. This is an implementation plan,
not a claim that Studio or device acceptance is complete.

## Source findings and architecture decision

The current entry is `TableMainLayout.kt:227` (Adjust UI). Settings and tutorial
navigation also reach `layoutLabOpen` (`TableOverlaysB.kt:1004`,
`TableController.kt:498`). `AdjustUiPage.kt` is a native dialog that changes real
size, order and display preferences. `TableController.kt:185` onward caches
orientation-specific layout state; `TableOverlaysB.kt:1014` wires live values and
reset into `arcana_options`. Preserve these keys and their fallback route.

Held #978, inspected at `75f03114a5cd6f59d1af2e67100a3595f872f8d2`, adds
`ArcanaStudioPage.kt` and an offline HTML asset. Its bridge accepts a project,
persists it under `arcana_studio_project_v1`, and applies colors; no native
position/size/action renderer consumes those layouts. Its theme reader requires
hand, board AND combat arrays, so it rejects the owner's hand-only #994 export.
Its proposed default flip must not be imported. #978 remains HOLD.

Use a native full-screen Compose editor and a shared typed layout model consumed
by BOTH editing canvas and actual screen renderer. Import version-1 owner JSON
through an explicit adapter; do not embed a second layout implementation in a
WebView. Keep the owner export unchanged as a fixture. Sample labels/art are
design metadata; renderer binds dynamic state, counts and real cards separately.
Do not copy callback lambdas, engine objects, hidden card identities or session
state into saved JSON. Action bindings are catalog IDs resolved by each native
surface, with live availability checked again at invocation.

## Screen coverage matrix

Every row below is inventoried, NOT implemented Studio support. Existing native
features do not count as new Studio acceptance. A screen is selectable only after
its renderer, supported properties, callback guards and round-trip tests work.

| Stage / screen | Actual source surfaces | Modules and callback boundary | Permission / regression proof required |
|---|---|---|---|
| S1 hand | `TableOverlays.kt:956`, `TableBoardViews.kt:813`, `HandDrawerControls.kt`, `HandLayout.kt` | Four actions, four zones, real card slots; callback catalog below | Own hand only, busy/mulligan/decision guards; edit/apply/tap/restart/reset |
| S2 table | `TableMainLayout.kt`, `PlayerOverview` in `TableBoardViews.kt:212` | Seat summaries, life, active turn/phase, public cards, zone shortcuts; existing `onOpen/onZone/onPreview` | No hidden opposing hand/library; multiplayer seat order, public preview, density profiles |
| S2 player / battlefield | `PlayerBoard` in `TableBoardViews.kt:360`, `BattlefieldLayout.kt` | Commander/status, mana, quick actions, battlefield cards, counters; existing board callbacks | Engine-controlled life/actions remain authoritative; own/remote/spectator seats; zoom and grouping |
| S3 combat / targets | `CombatStackBoard.kt:153`, `TableDialogs.kt:1710`, Argentum decision UI | Attack/block/target selection, confirm/cancel, damage/ordering | Use current legal targets/mandatory decisions; edit cannot dismiss required choice or submit stale action |
| S3 stack / priority / legal actions | `CombatStackBoard.kt:341`, `TableDialogs.kt:1797`, `ArgentumScreens.kt:140` | Stack inspector, actual legal choices, mana/action counts | Priority/seat guard, updated choices, no fabricated actions or bypassed payment |
| S3 other gameplay overlays | `TableOverlays.kt`, `TableOverlaysB.kt`, `TableDialogs.kt`, `CardZoom.kt`, `PlayFeed.kt` | Zone lists, mana/search/type choices, opening hand, log, notices, game over, diagnostics | Mandatory prompts reachable; sanitization unchanged; restricted zone identities; scroll/touch/back behavior |
| S4 home / setup | `HomeScreen.kt:87`, home routing in `TableController.kt`, `ArgentumScreens.kt:221` | Play/resume/setup, mode/deck choice, opening hand | Resume requires existing consent; mulligan/keep real engine decisions; restart/navigation |
| S4 room / lobby | `TableMultiLobby.kt:115/166`, `TableWifi.kt:645`, `TableDialogs.kt:356`, connection UI in `TableOverlays.kt` | Host/join/ready/deck/code/reconnect/leave | Host/guest/seat permissions, disconnected state, real relay tests; no layout-triggered network activity |
| S4 deck selection/editor | `DeckBuilderScreen.kt:61/302`, `TableDialogs.kt:1391/2175`, `ArgentumUi.kt:353` | Deck list/import/edit/card preview/replacement/review | Saved deck integrity, commander identity/validation, search and keyboard fit |
| S4 settings / menus / help | `TableOverlaysB.kt`, `TableMainLayout.kt`, `TablePaletteOptions.kt`, tutorial dialogs | Settings, seven color roles, navigation, diagnostics/tutorials | Existing callbacks; no permission/signing/billing controls added; reset and Studio always reachable |
| S5 cross-screen lifecycle | `MainActivity.kt`, `TableController.kt`, all rows above | Orientation/profiles/migration/recovery and editor replacement | Fold/rotation/restart, import failure, accessibility, all visible options mapped/tested before default change |

As screens are implemented, split the broad overlay rows into one record per
actual dialog and one record per visible Studio option, with exact binding,
unit/Compose test, phone result and commit. This is the initial inventory, not
the final exhaustive per-option acceptance ledger.

## S1 explicit native binding catalog

Current controller wiring is `TableOverlays.kt:1019-1103`; visual callbacks are
passed through `HandSheet`, never reconstructed from button text.

| Owner element ID | Proposed stable action/data ID | Existing callback and guard |
|---|---|---|
| pass | `hand.primary` | `onPassTurn` -> `argentumPrimary()`; current primary label, busy state; preserve auto-pass STOP/hold behavior |
| undo | `hand.undo` | `requestArgentumUndo()` only when available and not awaiting update; UI also disables while busy |
| actions | `hand.actions` | Set `argentumActionChoices = handActions`; filtered current snapshot, not saved list/count |
| cast | `hand.cast` | Selected legal card -> `openArgentumActions(card)`; otherwise current legal hand cast picker; never cast by saved sample name |
| metric0 | `zone.stack` | `onStack` -> `stackOpen = true`; actual stack inspector, distinct from ACTIONS |
| metric1 | `zone.own-library-count` | Existing library-info dialog; own count only, no ordered contents or card identities |
| metric2 | `zone.own-graveyard` | `onMoreAction("GRAVEYARD")` -> existing local zone path |
| metric3 | `zone.own-exile` | `onMoreAction("EXILE")` -> existing local zone path |
| card0..card6 | `hand.card[index]` | Real local hand index and stable card ID; selection/hold preview then legal cast; current snapshot controls |

`handDrawerActions` and `handDrawerCastActions` already filter affordability,
supported choices and actual server card IDs. Preserve those functions and the
controller's `handActionsBusy` (including mulligan/decision/view-only guards).
The catalog must reject unknown/cross-screen bindings; duplicated controls share
the same live action guard, not independent authority. Dynamic labels render the
real phase/action count; optional owner captions must not mask action meaning.

## Shared geometry contract

Import owner reference as 412 x 928 design units. For hand content, use explicit
origin (0,344): local x = owner x, local y = owner y - 344. No normalization to
minimum element x/y: that would erase intentional offsets. At a 412dp usable
drawer width, one design unit is one dp. Other widths use uniform scale
`usableWidth / 412`; include safe insets exactly once, outside the content origin.
Content extent follows elements; it is not stretched to fill drawer height.
Reference first row remains y=0, metrics y=56, cards y=120/256. Scrolling retains
coordinates and exposes overflow rather than silently rearranging the design.

The exported PASS height44 and UNDO width63 constrain accessible hit areas.
Expand touch bounds only where disjoint safe space exists; reject or flag a
layout when minimum target sizes, font scale or safe-area geometry cannot be
met without overlap. Do not silently clamp/reflow positions or shrink text.
Unsupported orientation keeps the known-good native layout with an explanation;
never overwrite another orientation automatically. Landscape and folded/wide
profiles are explicit. Account for IME when editing properties.

For hand sizes >7, preserve the first seven slot geometries exactly. Add a
validated overflow-card region/continuation policy to schema and editor before
enabling Apply; all additional real cards must be reachable. Reject overlapping
or inaccessible continuation geometry. Empty hands show no sample cards.

Custom mode suppresses existing hand title/context/selection hint and duplicate
action rows (`HandSheet`) and resize hint (`TableOverlays`). Accessible close and
resize remain outside the editable content, with their reserved region included
in the viewport contract and reference comparisons. Never add hidden padding.

## Persistence and recovery contract

Separate draft and applied versioned documents by screen, profile, orientation
and width bucket. Keep enabled state separate so disabling retains the design.
Parse with bounded sizes/counts and finite numbers; reject duplicate IDs,
unsupported versions/kinds/bindings, missing critical controls, invalid colors,
inaccessible geometry and hidden-zone data. No partial preference writes on error.
Retain last-known-good applied data, migrate explicitly, restore native defaults
if both current and backup are invalid. Do not clear existing `arcana_options`.

Editor undo/redo operates on bounded immutable draft snapshots; Apply validates
the entire document then updates observed native state and persistence together.
Cancel leaves applied state unchanged. Autosave drafts separately. Reset screen,
profile and all Studio data are distinct operations with explicit scope. Import
uses the same validator as Apply; export serializes the same model. Copy profile
creates a new identity. Studio/back/reset access is outside removable modules.

## Verification and visible-option ledger

Before exposing an option, record its native effect, test name and device result.
Initial S1 ledger (all pending): screen selection (hand only), element/layer
selection, drag, resize, property edit (x/y/w/h/font/radius/color/label/visibility),
validated binding selection, supported duplicate/add/remove, palette selection,
orientation/profile selection/copy, undo/redo, draft autosave, Apply, cancel/back,
JSON import/export, enable/disable, reset screen/profile/all. Unsupported options
stay absent or explicitly disabled in development; do not offer fake callbacks.

Pure/model tests: exact #994 fixture import/round-trip and geometry; malformed,
duplicate/missing IDs; finite/bounded geometry; binding compatibility; orientation
isolation; overflow/empty hand; undo/redo; transactional persistence, backup,
migration and reset. Compose: drag/resize -> actual native bounds change; handler
dispatch and disabled state; Apply vs cancel; restore after recreation; custom
mode no extra chrome; font-scale/safe-area/scroll recovery. Retain all existing
hand/game tests and real-engine legality tests. Run Android regex/cardkit checks
and serialized full app/card tests before a future build candidate.

Device acceptance is still pending, not blocked implementation: same-resolution
owner-reference comparison, Fold portrait/landscape/folding and actual taps,
restart restore, game action/payment/stack/zone privacy, malformed config recovery.
Compose screenshots/green CI cannot substitute for real Fold evidence. Obtain
owner-provided device evidence through an explicitly authorized path when needed;
no new background capture, service, paid worker or USB requirement inferred.

## Delivery boundaries

S0: this audit only. S1: functional hand editor + exact-reference/device proof.
S2-S4: extend the same renderer/model with separately verified surface bindings.
S5: exhaustive per-option ledger, lifecycle/device acceptance and independent
review before owner-approved replacement. Keep delivered APK and old editor
intact throughout. No build, APK delivery, default flip, #978 promotion,
main/development merge, engine replacement, release/sign/deploy or spend follows
from this document.
