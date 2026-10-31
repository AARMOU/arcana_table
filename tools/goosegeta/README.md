# Goosegeta in an Arcana room — isolated prototype

**Status: experimental, not integrated or deployed.** This is a Node 22+ external guest client for the **B140 directed-Argentum internet room** transport, not the old snapshot-only room, and not the current ChatGPT conversation. No OpenAI API calls, credentials, subscriptions or paid workers. Nothing in this directory modifies the Android APK, Cloudflare Worker, Control dashboard, or Argentum rules.

## What it does

- Joins an existing room **as a Friend**, presenting a normal guest deck and using the room's verified per-seat credential.
- Uses only the authenticated directed relay and the host's per-seat masked messages; no opponent hands or full-state access.
- Waits for the host to accept its deck, then marks ready. The **human host presses Start**.
- Responds to ordinary mulligan and bottom-card prompts using only supplied hand IDs.
- Plays a land, casts a no-target/no-X affordable spell, attacks or blocks conservatively, or passes priority **only when that exact action is offered by Argentum**. Unknown choices stop instead of guessing. It submits the state interaction epoch and won't repeat that epoch.
- Caps autonomous action submissions at 400 per process. Operator stops it with Ctrl+C. No auto-reconnect or remote service control.

## Original Commander decks

The decks.json manifest contains two **new recombinations**, not copies of either source deck:

- **goosegeta-flock** — Baylen, the Haymaker; Naya token generation, mana from tokens, and explosive finishes. Built from B140 Baylen and Rocco (both Naya) lists.
- **goosegeta-glue** — Bello, Bard of the Brambles; Gruul ramp, enchantments, and attacking value. Built from two B140 Bello lists.

Each has exactly 100 cards, its commander once, and every name in B140's bundled all-cards.index.tsv or custom-cards.index.tsv. The bot rechecks against the **local installed registry snapshot before connecting** and rejects missing names or unapproved multiples. The host independently resolves the names. A name being in a card registry is **not** proof every rule works: these decks are **not independently game-certified**, and a later runtime update can change support. Their commander-color provenance comes from source decks of the same color identity, not from a newly implemented rules checker.

## Run (on your own machine)

From the repository root using Node 22 or later, with a B140-source checkout that includes the bundled card indexes:

    node --test tools/goosegeta/*.test.mjs
    node tools/goosegeta/room-bot.mjs --list-decks
    node tools/goosegeta/room-bot.mjs --deck goosegeta-flock --check-deck
    node tools/goosegeta/room-bot.mjs --code ABC123 --deck goosegeta-flock

Or choose --deck goosegeta-glue. Use --endpoint https://your-room-worker.workers.dev if your room service uses a different URL. The app's current configured URL remains the default.

In Arcana on the other device: host an **Internet Commander** room, leave an opponent seat as **Friend** (not AI), select a Commander deck, and share only the **room code** with the machine running the bot. The bot knows the B140 project's code-derived room password; **do not use public room codes or assume this is a hardened public-invite scheme**. Don't paste secrets into issue comments. Keep the host app connected and start the match after the bot announces ready.

## Deliberate limits

- Not this live ChatGPT conversation: it is a Goosegeta-themed **deterministic bot**. Bringing an actual model into the game later requires an explicitly opted-in bridge and budget/security review.
- Not a competitive AI yet. It cannot solve complex choices, modes, targeting, X, commander replacements, priority loops, mana-color choices, or advanced triggered decisions. It can stall safely at one of those prompts. Attack/block heuristics are simple and may lose.
- B140 guest/host protocol is what this was written against; no compatibility guarantee for other branches or deployed room Workers.
- No end-to-end two-client/Android test has been performed, nor any live room test, signing, build, release, merge, or deployment.

### Acceptance for the next stage

1. Run local unit tests and verify the deck indexes in the same build as the host.
2. In an isolated test room, confirm real seat, deck admission, readiness, opening-hand and normal turns.
3. Verify negative cases: wrong room/no friend seat, malformed relay, unsupported cards, stale epoch, invalid targets, unexpected decisions, disconnects.
4. Only after a genuine room smoke test consider a limited OpenAI-backed decision service using masked state + legal actions and user-controlled spending.
