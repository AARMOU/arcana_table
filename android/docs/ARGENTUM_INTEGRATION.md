# Arcana × Argentum integration (Build 88)

Arcana Table provides the mobile UI. Argentum Engine
([wingedsheep/argentum-engine](https://github.com/wingedsheep/argentum-engine)) is the game engine:
it owns game state, rules, legal actions, turns, decisions and the AI opponent. Arcana never
computes a rules result for an Argentum game.

Verified against Argentum `main` at commit `7a9663d8` (2026-10-16) and the live server at
`magic.wingedsheep.com`.

## Integration path

```text
Arcana Compose UI (MainActivity.kt, ArgentumUi.kt)
  │  taps → "intent" (pass, play this card, attack with these, answer this decision)
  ▼
Adapter (ArgentumInteraction.kt, ArgentumBackend.kt)
  │  builds Argentum protocol JSON from server-supplied payloads only
  ▼
ArgentumGameSocket ── WebSocket  /game ──►  Argentum game-server (Spring Boot)
ArgentumDeckApi    ── HTTPS /api/decks/* ─►      │
  ▲                                              ▼
  │  stateUpdate / pendingDecision / errors   Argentum rules-engine + AI
  │
ArgentumTableSnapshot.parse → Arcana's TablePlayer / GameCard model → existing Arcana table UI
```

Arcana and Argentum run as separate processes: the APK contains Arcana plus the adapter, and the
engine runs on an Argentum server. This is Argentum's own supported client boundary (its web
client uses the same `/game` protocol). Embedding the engine in the APK was not attempted. The
rules engine is a JVM library, but its card corpus (~36,000 source files across `mtg-sets`) and
its server-side lobby, AI and session layers make that a much larger change than this integration
needed.

## Protocol messages used (all verified in Argentum source)

| Direction | Message | Source of truth |
|---|---|---|
| → | `connect {playerName, token?}` | `game-server/.../protocol/ClientMessage.kt` |
| → | `createQuickGameLobby {vsAi, format: COMMANDER}`, `joinQuickGameLobby`, `leaveQuickGameLobby` | same |
| → | `submitQuickGameLobbyDeck {deckList, commander}`, `setQuickGameLobbyReady {ready}` | same |
| → | `keepHand`, `mulligan`, `chooseBottomCards {cardIds}` | same |
| → | `submitAction {action, interactionEpoch}` — action is the server's `LegalActionInfo.action`, optionally with targets / `xValue` / attackers / blockers filled in, or `SubmitDecision {playerId, response}` | `ClientMessage.kt`, `rules-engine/.../core/GameAction.kt`, `PendingDecision.kt` |
| → | `requestResync`, `concede` | `ClientMessage.kt` |
| ← | `connected`, `reconnected {context}`, `sessionReplaced` | `ServerMessage.kt` |
| ← | `quickGameLobbyState`, `gameStarted`, `mulliganDecision`, `chooseBottomCards`, `mulliganComplete` | same |
| ← | `stateUpdate {state, legalActions, pendingDecision, stateVersion, interactionEpoch}` | same |
| ← | `stateDeltaUpdate` → Arcana requests a full resync (no delta guessing) | same |
| ← | `gameOver`, `error {code, message}` | same |
| HTTP | `POST /api/decks/validate`, `GET /api/decks/examples`, `GET /api/cards` | game-server deck controller |

## Adapter files

| File | Responsibility |
|---|---|
| `ArgentumBackend.kt` | `ArgentumGameSocket` (connection, reconnect/pause policy, token), `ArgentumDeckApi` (validation, premade decks), `ArgentumTableSnapshot` (masked state → Arcana model), `ArgentumEndpoints` (server origin) |
| `ArgentumInteraction.kt` | Pure Kotlin: decision response builders, needs-handling classification, `ArgentumAttackPlan` / `ArgentumBlockPlan` from `DeclareAttackers` / `DeclareBlockers` legal actions, X-cost ranges |
| `ArgentumUi.kt` | Status strip, attack/block dialogs, X picker, unsupported-deck review, diagnostic log viewer, order/amount editors |
| `DiagnosticLog.kt` | Categorized, searchable, redacted log |
| `HandLayout.kt` | Hand drawer reflow math |

## Rules the adapter follows

- **Only server choices.** Every list Arcana shows (legal actions, targets, attackers, blockers, decision options) comes from the latest `stateUpdate`. Every action is sent with the server's `interactionEpoch`.
- **No simulation.** Arcana's own rules helpers, manual life/draw/token tools and its local combat panel are disabled or hidden on an Argentum board.
- **Needs handling, never guessing.** If Argentum sends a decision or action Arcana cannot present, Arcana shows a *Needs handling* notice and logs it. It offers Argentum's own cancel (when the decision allows it), a refresh, or *View table*. Nothing is auto-answered.
  - Decision types with native controls: yes/no, batch yes/no, option, number, colour, card selection, targets, modes, library search, ordering (objects and library), mana sources (Argentum auto-pay or decline), combat damage assignment (`AssignDamageDecision` and the combat damage board `CombatResolutionDecision`, defined in `core/CombatResolution.kt`), distribution.
  - Decision types that show *Needs handling*: `SplitPilesDecision`, `ChooseReplacementDecision`, `BudgetModalDecision`, and any future type.
  - Actions that show *Needs handling* instead of being sent: multi-target spells, damage-division spells, and mana-colour choices.
- **Unsupported deck cards.** When Argentum's validator rejects cards, a review lists them and nothing changes until the player chooses one of three options:
  - Play on Argentum without those cards, for this game only, with supported basic lands filling the gap.
  - Play the full deck in Arcana's sandbox, where Argentum is not running the game.
  - Edit the deck.
  The saved deck is never rewritten.
- **No automatic resume.** Argentum pushes game state as soon as a client reconnects with its token (verified in `ConnectionHandler.handleReconnect` and against the live server). So Arcana reconnects on its own only while the app is in the foreground:
  - A connection dropped while Arcana is in the background goes to *Paused* and waits for *Resume*.
  - After a relaunch or activity recreation, the home screen offers *Resume game* or *End that game*, which concedes it. New games stay disabled until the player picks one.
- **Ordered message handling.** Server messages go through a queue and are handled once, in order. A malformed message is logged as an error and triggers a resync; it never crashes the app.

## Server address

The default is `https://magic.wingedsheep.com`. To use a self-hosted Argentum (`just server` in
the Argentum repo), set the `server_origin` string in the `argentum_session` shared preferences,
for example `http://10.0.2.2:8080` for a server on the emulator's host. On a debug build:

```sh
adb shell "run-as com.arcanatable.argentum sh -c 'cat > shared_prefs/argentum_session.xml'" <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="server_origin">http://10.0.2.2:8080</string></map>
EOF
```

Restart the app afterwards. The origin is never written to the diagnostic log.
