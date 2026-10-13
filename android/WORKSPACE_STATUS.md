# Arcana Table workspace status

## Canonical project

This is the complete Arcana Table **Build 70 source tree** (`0.15.51`, version code `70`). It contains the Android app, Gradle wrapper, Cloudflare room Worker, and dependency lockfiles. Build 70 fixes duplicate permanent instances when resolving activated abilities, with a regression test.

The separate `ArcanaTable-build52-source/` folder is stale and should not be used as the project source. Its Gradle metadata still labels it Build 52 and its code is materially different.

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
- A persistent Stack button beside the view tabs shows the stack count and highlights while items are waiting. The hand tray also has a Stack shortcut.
- The What's New dialog now contains confirmed release notes for Builds 60–64 and the Build 65 changes, in a scrollable panel.

## Build status

Build 70 (`0.15.51`, version code `70`) is the current source. Build 69 (`0.15.50`, version code `69`) is the latest compiled debug APK. Build 70 has not been compiled.

Build 62 gives every tutorial step a `NEXT` button, explains that the Join Game action needs a room code and password, and lets the tutorial continue without connecting.

Build 62 also adds an `AUTO FIX` action to the imported-card review dialog. It uses Scryfall fuzzy name lookup and applies only close matches (name similarity at least 0.78 with card art available); unresolved cards remain marked for manual review. Over-limit cards have a separate `TRIM EXCESS` action.

Build 62 centers the hand-sheet drag handle horizontally.

Build 62 adds a per-card three-dot menu in the Graveyard and Exile windows. It can move cards to hand, battlefield, the top or bottom of the library, graveyard, exile, or revealed; designated commanders also get a Command Zone option. Online movement controls are shown only for the local player's zones.

Build 62 hides `Set as commander` once a commander name or card is already assigned, in both battlefield and hand actions, and guards the action against stale menu state.

Build 62 hides a face-down card's name, mana cost, type, and rules text on the stack, disables its preview/details, and groups face-down battlefield cards without exposing their types or triggered payment abilities.

Build 62 makes `CAST FROM HAND` the filled primary stack action. Manual ability entry is tucked behind a small, low-emphasis button and appears as a compact field only when opened.

Build 62 replaces the screen-wide swipe detector and long animated view transition with a horizontal pager for the table and player boards. Swiping the view area and selecting a view tab stay synchronized, while horizontal tab scrolling remains independent. The reduced-motion option skips the animated jump when selecting a tab.

Build verification: `testDebugUnitTest` and `assembleDebug` passed. APK metadata reports package `com.arcanatable.build52test2`, version `0.15.47`, version code `66`; `apksigner verify` passed with APK Signature Scheme v2 and one signer. APK SHA-256: `03cbd2a969a1513ee91ba451b07fd03fae5186a7a506e769ca92563366c72aec`.
