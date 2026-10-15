# Arcana Table workspace status

## Canonical project

This branch is the separate Arcana Table **Game Assistance Overhaul Build 85** (`0.15.66-targeting`, version code `85`) source tree on `experimental/assistance-overhaul`. It includes the Android app, Gradle wrapper, Cloudflare room Worker, and dependency lockfiles. The debug APK uses package `com.arcanatable.targeting`, so it installs beside the stable app. Stable Build 72 and the Worker are unchanged.

## Changes in Build 85

- Added a normalized game-event model carrying event ID, kind, acting player, subject card, zone movement, cause, phase, target, and amount.
- Moved trigger candidate evaluation into a pure rules-engine pass shared by existing cast, draw, ETB, death, combat, phase, life, and other event producers.
- Added explicit matched, rejected, and unsupported decisions with reasons; diagnostics now link the event to source-card checks and trigger prompts through queue, display, and resolution.
- Trigger prompts retain their originating event ID across online game-state snapshots.
- Added regression tests for event normalization, positive and negative trigger matches, unsupported trigger text, and known Rhystic Study handling.

This is an event-processing foundation for the currently supported rules patterns. It is not a complete Magic rules engine; unsupported or ambiguous card behavior remains manual. Build 85 verification and APK checksum will be recorded after the build completes.

## Changes in Build 84

- Uses a card's name and type line to recognize self-referencing ETB text such as “When this creature enters,” “When this artifact enters,” and named self-ETB conditions. The derived event is matched to creature, land, or other permanent entry.
- Adds regression coverage for creature, artifact, and named self-ETB cases.
- Adds an offline benchmark fixture of 1,000 distinct Oracle IDs across creature, land, and other permanent ETB conditions.

Build 84 `clean testDebugUnitTest assembleDebug` passed (55 tests, 0 failures). The benchmark recognized 1,000/1,000 self-referencing ETB conditions across 625 creature, 250 other permanent, and 125 land cases. The benchmark verifies condition parsing and event matching; it does not validate complete card-effect resolution or every gameplay prompt. The APK at `app/build/outputs/apk/debug/app-debug.apk` reports package `com.arcanatable.targeting`, version `0.15.65-targeting`, version code `84`, and label `Arcana Table Targeting 0.15.65 · Build 84`. APK Signature Scheme v2 verification passed. SHA-256: `d8603ba3580ac685a2902322930fc48dead612b9f87374f31ad868b093cf4cf6`.

## Changes in Build 81

- Replaces the library/zone strip with a configurable Quick Actions bar. Adjust UI can add, remove, and reorder zone shortcuts and actions such as Draw, Stack, Token, Combat, and Cast Commander; empty zones remain visible with a count of 0.
- Fixes the missing live Mana Pool section, enlarges mana pips throughout the app, and makes card three-dot menus visually smaller while keeping a 40 dp tap target.
- Advances the TURN counter once every eligible player has completed a turn; the display remains labeled TURN.
- Carries forward Build 80’s player battlefield height fix.

Build 81 `testDebugUnitTest` passed (36 tests, 0 failures), and `assembleDebug` completed successfully. The APK is `app/build/outputs/apk/debug/app-debug.apk`, package `com.arcanatable.targeting`, version `0.15.62-targeting`, version code `81`, app label `Arcana Table Targeting 0.15.62 · Build 81`; APK signature verification passed with APK Signature Scheme v2. SHA-256: `4ff4d1b4f83a152bac3c8c9922fd1b261681f758540e311307cf4101a22055d1`.

Build 73 adds a target picker for targeted spells, Auras, activated abilities, and recognized targeted triggers. It records chosen targets on stack items and syncs them in room snapshots. The picker presents visible objects from battlefield, stack, graveyard, exile, revealed zones, and eligible hands, plus a manual-entry field. It estimates target count from card text, but does not certify Magic target legality; users must check the card text and game state. Hidden library cards are not listed.

## Changes in Build 74

- Enlarges opening-hand card faces and scales hand-tray cards as the drawer height changes.
- Moves Draw, Combat, Stack, turn flow, and secondary game commands into the hand tray; keeps the view selector focused on player/table navigation.
- Pins the player-screen footer to the bottom and removes the outdated drag-to-play hint.
- Displays colored mana symbols as colored pips in mana controls and cost displays.
- Prompts for a creature type after a matching permanent resolves, shows the chosen type on the permanent, and synchronizes that choice with online game state.
- Adds a small game-log card preview button when a logged card is still present in a public zone.

## Changes in Build 79

- Automatically attempts close Scryfall name matches during import; the review menu appears only for unresolved card names or deck-limit issues, and successful corrections are reported.
- Table overview cards adapt to player-panel width instead of staying in two narrow fixed columns.
- The player battlefield window fills available space above the footer, and switching boards opens the battlefield list at its top.
- Library browsing randomizes the displayed card order and omits stack position numbers while leaving actual deck order unchanged.
- The Adjust UI editor highlights its selected list item when the matching sample element is tapped or dragged.
- Larger Cards and Compact presets now make visible changes to card dimensions and footer controls, with separate sliders covering wider ranges.
- Mana pool is a separate removable/reorderable player-screen element. Tapping the health/activity bar opens Player Status with life controls, commander damage, player counters, and turn/phase details.

Build 79 `testDebugUnitTest` passed (34 tests, 0 failures), and `assembleDebug` completed successfully. The APK is `app/build/outputs/apk/debug/app-debug.apk`, package `com.arcanatable.targeting`, version `0.15.60-targeting`, version code `79`; APK signature verification passed with APK Signature Scheme v2. SHA-256: `05d7014f166a091ce2b926adc60f73033ecfbb2ed054993e1498df11fc043168`.

## Changes in Build 78

- The UI editor's sample layout supports long-press drag reordering for player sections and hand-tray buttons. The moving element lifts and follows the gesture while the surrounding items animate into their new positions.
- The player footer stays aligned to the bottom edge, with a taller hand control than the two side buttons.
- The battlefield takes up the available player-screen height and resizes automatically; excess cards remain scrollable.
- Removed the obsolete Large View toggle from the table view.

Build 78 source is on `experimental/targeting`. `testDebugUnitTest` passed (34 tests, 0 failures), and `assembleDebug` completed successfully. The installable APK is `app/build/outputs/apk/debug/app-debug.apk`, package `com.arcanatable.targeting`, version `0.15.59-targeting`, version code `78`; APK signature verification passed with APK Signature Scheme v2. SHA-256: `74f69e2fa5f335d8efc0fa28e2bd38c661d0a88831ffd642a7412e81671ce4fa`.

The separate `ArcanaTable-build52-source/` folder is stale and should not be used as the project source. Its Gradle metadata still labels it Build 52 and its code is materially different.

## Changes in Build 71

- Chat messages support long-press text selection and Android's copy action.
- Added a PASTE button that appends clipboard text to the message draft. The message field also keeps Android's normal paste context menu.

## Changes in Build 72

- Player view tabs show cropped commander artwork with the player name overlaid at the bottom.
- On the full table, the active player's board expands automatically; the prior active player's board collapses when the turn changes.
- The full-table grid scrolls to the active player's card when the active player changes.
- The player footer has full-width PASS TURN, HAND, and BACK TO TABLE buttons; HAND is centered and larger. ADD TOKEN is in MORE, and the footer no longer shows the library count.
- Mana-pool access is a compact MANA chip in the player status header instead of a bottom action button.
- The command-zone strip uses a smaller commander thumbnail and condensed status text while retaining commander casting, cast count, tax, and commander status.

## Changes in Build 70

- Resolving activated and loyalty abilities no longer moves the source permanent; only spell and commander stack items change zones when they resolve.
- Added a regression test confirming abilities and triggers do not move their source card, while spell and commander resolution still does.

## Changes in Build 69

- Recognizes simple triggered abilities from spell casts, card draws, and creature entries, applies actor and controller filters, presents each detected trigger with its event and effect, and synchronizes pending prompts across online seats.
- Keeps Rhystic Study and Smothering Tithe pay-or-resolve decisions, adds target-player selection for supported player life/draw/token effects, and prompts for manual resolution when the parser encounters choices, conditions, or unsupported effects.
- The Activity Log includes turn phase, spell cost/payment detail, trigger events, automatic life/draw/token changes, and manual-resolution notes. Payments, triggers, and effects receive distinct visual emphasis.

## Changes in Build 68

- The opening-hand screen displays actual card faces in a horizontally scrollable hand.
- Tapping a card opens the large card preview. During bottom selection, a separate button selects or deselects each card so previewing does not change the selection.
- The selection count and required bottom count remain visible before confirming.

## Changes in Build 67

- Added a multiplayer opening-hand step before Ready Up. Players can keep or mulligan; a mulligan shuffles the hand into the library and deals a fresh seven. The first multiplayer mulligan is free.
- After keeping, players select the required number of cards to put on the bottom of the library.
- Ready Up is blocked until the player's deck is loaded and opening hand is kept. LAN and internet room servers validate the same requirement.
- Lobby game start preserves each chosen hand and library order.
- Automatic untap removes one stun counter from a tapped permanent instead of untapping it; permanents without stun counters untap normally.
- Horizontal gestures on a player board stay in its card and zone lists; use the player tabs to switch boards.

## Changes in Build 66

- Updated the What’s New screen to list every change since Build 65.
- Added a persistent drag-resize hand panel, automatic stack-window closing after stack items and pending choices clear, and tighter player-view spacing with content-sized battlefield lists.
- Expanded diagnostics with repeated deck quantities, visible card moves, zone counts, duplicate instance checks, and inbound snapshot sequence numbers.

## GitHub Pages project site

- Added a responsive landing page in `docs/index.html` with current Build 66 details and a direct test-APK download.
- Added `.github/workflows/deploy-pages.yml` to deploy the page and APK through GitHub Pages Actions.
- The published Pages content remains on Build 66; Build 69 source and the Build 67 APK are maintained separately from the project page.

## Fixes carried forward from Build 61

These three fixes are in `app/src/main/java/com/arcanatable/MainActivity.kt` and are compiled into the Build 61 APK:

- The room URL tutorial step says the default address is already set and advances with Continue, without highlighting or opening the editable field.
- The tutorial overlay has a larger panel, text, and controls.
- The card action dialog wraps its title and actions instead of taking the full available height.

These fixes are retained in the Build 62 source and APK.

## Tooling status

- Java 17 is available.
- Gradle wrapper 8.13 is included (`./gradlew`).
- The Android project targets `compileSdk = 36`. The SDK used to compile this APK is not included in the source archive.
- Cloudflare Worker dependencies are recorded in `cloud-room/package-lock.json`; `node_modules/` is excluded from the source archive.
- Wrangler CLI was not authenticated or used to deploy. Cloudflare credentials stay outside the source bundle.
- `build-codex.sh` keeps Gradle and Android SDK files in `.codex-build-cache/` when available. Generated caches and `node_modules/` are excluded from the source bundle.

## Fixes in Build 63

- The stack dialog now wraps its content for short stacks and caps the stack list and overall dialog height for long stacks. **Cast from Hand** remains the primary action; manual ability entry remains compact and secondary.
- The deck editor now shows tracked cards across hand, library, battlefield, graveyard, exile, and revealed zones, with per-zone counts. Rename and remove actions apply to the selected card across all zones, preventing cards from disappearing from the editor after moving zones.
- AI lobby readiness is preserved: an imported AI deck marks that seat ready, and the game starts only when every seat is ready. Unexpected AI turn errors now log a diagnostic and pass the turn so the game remains playable.
- Mana parsing now recognizes ordinary tap-for-mana rocks, mana abilities offering any color or mana type, dual-color choices, and basic land types. It continues to avoid automatically activating abilities with additional costs.

## Table page layout fix in Build 64

- Each horizontal-pager page now owns a full-height column. This gives the overview grid and player board a valid parent for their weighted height, keeping the Table view's player panels visible instead of collapsing the grid.

## Changes in Build 65

- The import review now reports unmatched or unverifiable cards, closes after Auto Fix matches every flagged card, and offers the editor when manual correction is still needed.
- Supported fixed life, draw, and token effects from resolving spells are applied automatically, along with simple creature-entry life triggers. Each automatic action is announced and logged; unsupported effects prompt the player to resolve them manually.
- A persistent Stack button in the hand tray shows the stack count; the view tabs are reserved for table/player navigation.
- The What's New dialog now contains confirmed release notes for Builds 60–64 and the Build 65 changes, in a scrollable panel.

## Build status

Targeting Experimental Build 81 (`0.15.62-targeting`, version code `81`) is the current source on `experimental/targeting`. Its debug package ID is `com.arcanatable.targeting`. Stable Build 72 remains on `main`; the Worker was not deployed for this build.

Build 62 gives every tutorial step a `NEXT` button, explains that the Join Game action needs a room code and password, and lets the tutorial continue without connecting.

Build 62 also adds an `AUTO FIX` action to the imported-card review dialog. It uses Scryfall fuzzy name lookup and applies only close matches (name similarity at least 0.78 with card art available); unresolved cards remain marked for manual review. Over-limit cards have a separate `TRIM EXCESS` action.

Build 62 centers the hand-sheet drag handle horizontally.

Build 62 adds a per-card three-dot menu in the Graveyard and Exile windows. It can move cards to hand, battlefield, the top or bottom of the library, graveyard, exile, or revealed; designated commanders also get a Command Zone option. Online movement controls are shown only for the local player's zones.

Build 62 hides `Set as commander` once a commander name or card is already assigned, in both battlefield and hand actions, and guards the action against stale menu state.

Build 62 hides a face-down card's name, mana cost, type, and rules text on the stack, disables its preview/details, and groups face-down battlefield cards without exposing their types or triggered payment abilities.

Build 62 makes `CAST FROM HAND` the filled primary stack action. Manual ability entry is tucked behind a small, low-emphasis button and appears as a compact field only when opened.

Build 62 replaces the screen-wide swipe detector and long animated view transition with a horizontal pager for the table and player boards. Swiping the view area and selecting a view tab stay synchronized, while horizontal tab scrolling remains independent. The reduced-motion option skips the animated jump when selecting a tab.

Build verification: `testDebugUnitTest` and `assembleDebug` passed. APK metadata reports package `com.arcanatable.build52test2`, version `0.15.47`, version code `66`; `apksigner verify` passed with APK Signature Scheme v2 and one signer. APK SHA-256: `03cbd2a969a1513ee91ba451b07fd03fae5186a7a506e769ca92563366c72aec`.
