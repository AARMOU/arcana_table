# Arcana Table × Argentum — Build 90

This branch (`experimental/arcana-argentum-integration`) is Arcana Table's mobile table and customization UI running games on the **Argentum Engine**. Argentum owns the game state, rules, legal actions, decisions, turns and the AI opponent, and Arcana renders that state and sends the player's choices. How it fits together: [docs/ARGENTUM_INTEGRATION.md](docs/ARGENTUM_INTEGRATION.md). Build steps: [BUILD_AND_TOOLS.md](BUILD_AND_TOOLS.md).

## Play a real Argentum game

1. Install `app-debug.apk`. It appears as **Arcana · Argentum B90** next to any other Arcana build.
2. Tap **IMPORT PLAYER DECK → ARGENTUM DECKS**, pick a list such as *Animated Army* (Bello), then tap **IMPORT**. Any Commander deck works; Argentum validates it.
3. Tap **PLAY WITH ARGENTUM**. Argentum checks the deck, opens a practice table against its AI and deals. Choose **KEEP HAND** or **MULLIGAN**.
4. Play from Arcana's table:
   - The strip at the top shows the engine connection, whether the game is active, and the turn and step.
   - The primary button always names what it will do: **PASS PRIORITY**, **DECLARE ATTACKERS** or **DECLARE BLOCKERS**.
   - Open your board with the **YOU** tab, then **HAND**. Tap a card, or **ACTIONS**, to see the legal actions Argentum offers.
   - Argentum's choices (targets, modes, discard, library search, ordering, damage assignment) open as Arcana dialogs.
5. If anything shows **NEEDS HANDLING**, Arcana has no control for that Argentum prompt yet. Nothing was chosen for you, and the event is in **⋮ → Game tools → Diagnostic log (search)**.
6. To leave, use **⋮ → Room & game → Leave game…**, which concedes the game on Argentum.

## What's new in Build 90

- **No manual mana abilities:** tapping a land no longer offers "{T}: Add {G}" and similar mana abilities. Argentum pays costs itself by tapping your lands and mana sources, and in Build 89 these buttons were rejected (`INVALID_ACTION: This permanent is already tapped`) on a land that entered tapped. The card sheet now says that mana is paid automatically, and the **ACTIONS** count no longer includes mana abilities. Playable glow already ignored them.

## What's new in Build 89 — polished Argentum table

- **Game HUD:** a phase tracker (Begin · Main 1 · Combat · Main 2 · End), life totals that flash red or green when they change, a ring around whoever holds priority, the live stack (top first, with targets), and the latest two lines of Argentum's own game log, so you can see what the opponent just did. Tap the log lines for the full game log.
- **Playable glow and card sheet:** cards Argentum lets you play or activate right now glow. Tapping a card opens a sheet with large art, mana cost, type, power/toughness and rules text, plus every action Argentum allows for it. Actions you can't afford are labelled, not hidden.
- **Board badges:** ⚔ attacking, 🛡 blocking, power/toughness with remaining toughness after damage, and a dimmed "NEW" tag for summoning-sick creatures.
- **Every Argentum choice has a control:** spells with several targets or target groups, divided damage (split evenly to start), X, mana colour, pile splits, replacement-effect choices and mode budgets all go through Arcana dialogs, built only from Argentum's offered choices. *Needs handling* now appears only for decision types Argentum adds in the future.
- **Opening hand:** a full-screen opening hand with real cards, play/draw and mulligan info; the bottom-of-library step uses the same grid with tap-to-select.
- **Endings:** a Victory / Defeat / Game over screen with the reason, game length, **PLAY AGAIN**, **VIEW BOARD** and **HOME**.
- **Your move:** a short vibration when Argentum hands you priority or a choice. With Background alerts on, you get a notification instead while Arcana is in the background.
- **Fixes:** card targets in graveyards and other zones now use Argentum's zone names (they were rejected before). An open action sheet refreshes on every new state instead of offering stale actions. Arcana's old room "game ended" dialog no longer covers the Argentum result. Seat MOVE buttons are hidden where they can't work.

## What's new in Build 88

- **Combat damage:** combat damage no longer stops the game at *Needs handling*. Argentum's combat damage board (`CombatResolutionDecision`) opens with the engine's own default split, so **CONFIRM DAMAGE** applies it in one tap. You can also move damage between blockers and trample targets; lethal amounts are marked, and Arcana only allows a split Argentum will accept. This was found from a diagnostic log of a real game on a Galaxy Z Fold6.

## What's new in Build 87

- **Combat:** attack and block declarations come from Argentum's legal actions. Before, Arcana could not attack or block, and the game stalled at declare attackers.
- **More of Argentum's choices in Arcana's dialogs:** modes, library search, ordering, mana auto-pay, damage assignment and distribution. Other prompts show a **Needs handling** panel with Cancel (when Argentum allows it), Refresh, or View table, instead of a dialog that could not be closed.
- **Unsupported deck cards:** these open a review instead of being swapped out silently. You can play without them for this game, play the full deck in Arcana's sandbox, or edit the deck. Your saved deck is never changed.
- **No automatic resume:** after a relaunch, Arcana offers **Resume game** or **End that game**. If the connection drops while the app is in the background, Arcana shows **Paused** and waits for **Resume**. In the foreground it reconnects automatically.
- **Status:** an engine/game status strip with recovery actions, plus clear messages when Argentum can't be reached.
- **Diagnostic log:** searchable, with category filters and redacted export. Server addresses, IPs, tokens, credentials, IDs and room codes are removed, and private card details are left out of exports.
- **Hand drawer:** cards wrap into rows and resize as the drawer is dragged. The tray never gets too short to show cards, even on landscape phones. **Adjust UI → Hand** sets the largest card size and the arrangement (fit to tray or single row). **Adjust UI → Status** toggles the strip. **Reset** restores defaults.
- **Buttons:** in Argentum games, manual-only Arcana controls (draw, token, shuffle, life +/−, local chat) are hidden instead of doing nothing.
- **Fixes:** server messages were sometimes lost when several arrived together (for example, the game start). Duplicate connections could appear after a manual reconnect. Card names now show while card art loads.

---

# Arcana Table Targeting 0.15.62 — Experimental Build 81

Arcana Table is a native Android Magic tabletop prototype. Experimental Build 81 carries forward the targeting experiment, adds a configurable Quick Actions bar with zone counts, fixes the live Mana Pool section, enlarges mana pips, reduces card-menu visuals, and advances the TURN counter once per full table cycle. It also keeps Build 80’s player-screen height fix. Build 79's table-view cards expand to use the available panel width; the separate player mana-pool element and tap-to-open Player Status panel can be customized in Adjust UI; library browsing hides stack order; and deck imports automatically attempt close-match corrections before showing any review prompt. The editor highlights the selected list item when you select or drag it in the sample layout. The targeting experiment lives on the `experimental/targeting` branch and installs as a separate app so it cannot replace the stable Arcana Table install.

Target legality still depends on the card's full wording, game state, and rules interactions. The picker estimates how many targets to select, but players must verify restrictions such as controller, color, type, shroud, hexproof, and protection. Hidden library cards are not listed; use manual target entry only when the card text makes an unlisted target legal and known to the choosing player.

## Included

- Choose targets while a spell or ability is on the stack; the selector includes visible players and game objects and allows a manual target entry
- Show chosen targets on the stack and in the game log, and synchronize them in internet/LAN rooms
- Keep priority passing and resolution unavailable until the responsible player confirms target choices

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
- Draw, Combat, Stack, turn flow, and additional game commands are available from the hand tray; Shuffle still asks for confirmation
- A game action log, elapsed session/turn timers, and a room-synchronized text chat panel
- Table view shows each player's status, life, hand/library counts, commander art, current phase, turn timer, and public-zone counts; expanded cards show battlefield and public cards, with seat controls
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

The source is labeled version 0.15.60-targeting, build 79. This experimental branch builds a separate debug app package `com.arcanatable.targeting`. The optional full visual guide remains available from the home screen and app menu and never launches automatically.

Build 66 adds a resizable hand panel, automatic stack-window closing after all resolutions, tighter player-view spacing, and stronger import/movement/sync diagnostics. It also includes the Build 65 import review, supported automatic life/draw/token effects with manual-resolution notices, quick Stack access, and earlier tutorial, card movement, privacy, and navigation updates.

## Project page

The GitHub Pages site lives in `docs/`. Its published project page remains on Build 66; Build 67 source and APK are separate from the page. See [`docs/README.md`](docs/README.md) for the Pages workflow setup.

## Repeat builds in this workspace

Run `./build-codex.sh` from this folder. It puts Gradle's distribution and dependency downloads in `.codex-build-cache/gradle`, beside the project, so they can be reused when the same workspace is reopened. If you install an Android SDK in `.codex-build-cache/android-sdk`, the script will use that too; otherwise set `ANDROID_HOME` to the SDK path already installed on the machine. Keep this workspace folder between build sessions. The cache folder is generated and should not be included in source ZIPs.

## Open the source in Android Studio

Open this folder as a Gradle project. The project includes the Gradle wrapper and targets Android SDK 36. Android Studio can sync and run the `app` configuration on a phone or emulator.

## Product direction

The game is a shared sandbox: the app moves cards and tracks common state, while players apply Magic rules themselves. Imports use the standard text/CSV list formats supported by deck builders; card details and large card art are fetched from Scryfall when needed. Direct service links are not fetched by this build, so export/copy the list and paste it or choose the exported file.
