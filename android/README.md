# Arcana Table 0.15.53 — Build 72 source update

Arcana Table is a native Android Magic tabletop prototype. Build 72 adds player view tabs with cropped commander artwork, automatically expands and scrolls to the active player's card on the full table, and reorganizes the player footer into full-width PASS TURN, HAND, and BACK TO TABLE buttons with a larger centered HAND button. Mana-pool access is in the player status header; ADD TOKEN is in MORE, the footer library count is removed, and the command-zone strip is condensed without removing its controls or status. Build 71 makes table-chat message text selectable and adds a PASTE control for clipboard text in the draft. Build 70's fix for duplicate permanents on ability resolution, Build 69's trigger prompts and Activity Log updates, and Build 68's card-face mulligan screen with tap-to-enlarge previews are included.

## Included

- Landscape and portrait layouts with taskbar/system-bar safe spacing
- Adaptive room home and player layouts for narrow phones, wide phones, tablets, and foldable screen changes; keeps game state through orientation and screen-size changes
- Big create/join room actions on startup, with Continue to Table and Tutorial shortcuts
- Add up to four players, including a basic deck-driven computer opponent, with a full-table overview and a focused view for each player
- Create or join a same-Wi-Fi room by host IP, room code, and password; share board state, turn/phase, ready status, chat, and game log across connected phones
- Each connected phone gets one player seat. Other boards are read-only, player hands stay closed on other seats, and the host can remove players
- Show connected-player status in the lobby and table
- Remove a disconnected remote seat from the host table and reseat remaining players
- Start a lobby game once each joined seat has imported a deck, completed its opening-hand decision, and marked ready
- Choose Keep Hand or Mulligan before Ready Up; each mulligan shuffles the current hand into the library and deals a fresh seven, with the first multiplayer mulligan free
- After keeping, tap the cards to put on the bottom of the library; the app requires the correct count and preserves the chosen hand when the host starts the game
- Display a persistent game status indicator and stop timers until a game starts
- Require unanimous player approval before ending an online game; a decline cancels the proposal
- Highlight the active player and step through turn phases; show session and current-turn timers
- Use Next Step for manual progression or Pass Turn to advance through the remaining phases and begin the next player's turn; automatic untap removes a stun counter from a tapped permanent instead of untapping it
- Use the player tabs to change boards; horizontal card-list scrolling inside a player board will not switch to another player
- Automatically draw when advancing into the draw step, open the hand, and highlight the drawn card
- Snap the hand tray to a newly drawn card
- Group battlefield cards into a vertically scrolling list by card type
- Animate tapped permanents and open per-card menus for counters, tokens, zones, and other actions
- Show tapped permanents rotated in table and player views
- Hand tray peeks up from the bottom of the player view; tap it to open your cards, then dismiss it to see the battlefield
- Tap a hand card for Play, Play Face Down, Discard, Reveal, and Inspect actions
- Reveal or hide your full hand to every connected player; the set-commander action hides after a commander is assigned
- Compact the hand action menu while keeping large touch targets
- Long-press a card to inspect only the card, without covering the rest of the table
- Manual life total changes, draw, reveal top card, shuffle, and tap/untap
- Move permanents to the graveyard or exile
- Draw opens the hand; Reveal Top and confirmed Shuffle are in the board menu
- A game action log, elapsed session/turn timers, and a room-synchronized text chat panel
- Table view shows each player's status, life, hand/library counts, commander art, current phase, turn timer, and public-zone counts; expanded cards show battlefield and public cards, with compact or large views and seat controls
- Swipe between table and player views with a more pronounced animated transition; Next Step remains available from the table view
- Start, stop, and reset controls for the game and turn timers live in the timer menu; the turn timer can also be hidden
- Reset Game in the app menu reshuffles non-token cards, deals new hands, and resets life, board, timers, and log while keeping players, decks, and commanders
- Basic combat declaration panel for attackers, blockers, and manually entered damage
- Edit imported cards in the hand/library with grouped card counts, per-card menus, and Scryfall search to add cards from outside the deck
- Imported decks and table zones are saved on the device and restored when the app restarts
- Warn when an import exceeds the 99-card Commander or 100-card deck limit and offer to remove overflow cards or open the editor; imports replace the player's existing deck
- Reset and clear all local app data from the main menu
- Switchable light and dark themes
- Trigger reminders for imported cards with matching opponent-cast or card-draw text; Rhystic Study and Smothering Tithe have pay/resolve prompts
- Searchable Scryfall token picker
- Dedicated chat button with unread-message highlight and a panel that slides in from the right
- Options page for appearance and timer display; Developer Options are nested inside Options and can switch local player view, generate randomized test decks or mid-game states with card images, and open a step-by-step walkthrough
- The main menu groups deck actions, game tools, and room/game actions into short submenus
- Main-menu Tutorial runs a guided spotlight tour of the app, one feature at a time, with Next, Back, and Exit controls
- The host can remove player seats with a confirmation; removed seats are removed from readiness and turn order
- Searchable counter picker with a custom counter option; counters are shown on permanents
- Publicly revealed cards are shown on the table and in a readable card viewer
- Paste or choose a text/CSV deck export from Moxfield, Archidekt, or ManaBox
- Import card names/counts plus optional set codes and collector numbers; exact-printing matches are preferred with card-name fallback
- Basic computer player draws, plays one land, casts affordable spells using generic mana-value estimates, and passes; import a deck for the AI
- Mark a commander through commander sections or metadata in supported deck lists and show it on the table card
- Opening hand, draw pile, and hand-to-battlefield flow for imported decks
- Large card images loaded on demand from Scryfall and kept in a small in-memory cache

## Prototype limits

- Internet play requires the separate Cloudflare room service in `cloud-room/` to be deployed; LAN room traffic is limited to the same Wi-Fi/LAN
- The room service is a prototype. It does not enforce all Magic rules or provide full anti-cheat protection; use a unique room password and avoid sensitive information in chat
- Robust conflict resolution for simultaneous actions, reconnection, host migration, and persistent rooms
- A hosted relay/backend for players behind different routers or carrier networks
- Advanced computer opponent strategy, colored mana payment, stack interaction, blocking, and threat assessment
- Full AI or rules-engine coverage for triggered abilities; the current reminder helper only recognizes cast/draw patterns
- Complete Magic rules, stack resolution, commander damage, and automatic turn progression for every game event
- Direct import by deck URL; export or copy the list from the source service first
- Drag-and-drop from hand to battlefield
- Notifications while the app is backgrounded; LAN sockets currently live with the app screen
- Randomly selecting and importing highly rated Commander decks from Moxfield
- Offline card database and persistent image storage; first lookup needs internet

## Build status

The source is labeled version 0.15.53, build 72. No Build 72 APK has been compiled. The optional full visual guide starts with the welcome message and follows the app screens with dimmed/blurred backgrounds, highlighted controls, and straight red arrows with solid arrowheads. It is available from the home screen and app menu and never launches automatically.

Build 66 adds a resizable hand panel, automatic stack-window closing after all resolutions, tighter player-view spacing, and stronger import/movement/sync diagnostics. It also includes the Build 65 import review, supported automatic life/draw/token effects with manual-resolution notices, quick Stack access, and earlier tutorial, card movement, privacy, and navigation updates.

## Project page

The GitHub Pages site lives in `docs/`. Its published project page remains on Build 66; Build 67 source and APK are separate from the page. See [`docs/README.md`](docs/README.md) for the Pages workflow setup.

## Repeat builds in this workspace

Run `./build-codex.sh` from this folder. It puts Gradle's distribution and dependency downloads in `.codex-build-cache/gradle`, beside the project, so they can be reused when the same workspace is reopened. If you install an Android SDK in `.codex-build-cache/android-sdk`, the script will use that too; otherwise set `ANDROID_HOME` to the SDK path already installed on the machine. Keep this workspace folder between build sessions. The cache folder is generated and should not be included in source ZIPs.

## Open the source in Android Studio

Open this folder as a Gradle project. The project includes the Gradle wrapper and targets Android SDK 36. Android Studio can sync and run the `app` configuration on a phone or emulator.

## Product direction

The game is a shared sandbox: the app moves cards and tracks common state, while players apply Magic rules themselves. Imports use the standard text/CSV list formats supported by deck builders; card details and large card art are fetched from Scryfall when needed. Direct service links are not fetched by this build, so export/copy the list and paste it or choose the exported file.
