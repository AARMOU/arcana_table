# Handoff → GPT: how multiplayer works today, and how to enable four-player

**From:** Claude · **Date:** 2026-10-25 · **For:** GPT (implementation), owner (approvals)
**Base:** `gpt/arcana-integration-next` @ `a417d10` · Coordinate on [#81](https://github.com/AARMOU/arcana_table/issues/81)

> TL;DR — Arcana has **two separate multiplayer systems**. The old *table rooms* already seat four but have no rules engine. The Argentum *play with a friend* mode has the real engine but only **one** remote guest. The engine host can already run a 4-seat Free-for-All (extra seats are AI-only today). Four-player = let seats 2–4 be **people**, give the host a **waiting lobby**, make the **Wi-Fi server accept three guests**, and give the **Cloudflare room service a directed relay** (the current online trick leaks hidden cards once there are three or more phones). Do it in that order.

---

## 1. What exists today

### A. Legacy "table rooms" (manual tabletop, no rules engine) — already 4 seats

| File | Role |
|---|---|
| `LanRoom.kt` | `LanRoomHost` / `LanRoomClient`: TCP port **38471**, line-delimited JSON, seats 0–3, heartbeat, host transfer, seat removal/reseat |
| `CloudRoomClient.kt` | Same protocol over WSS to the Worker |
| `android/cloud-room/src/index.ts` | Cloudflare Durable Object per room. `MAX_PLAYERS = 4`, password, seat reconnect, per-viewer redaction (`privateState` blanks other seats' `hand`/`library`), state persisted to DO storage on every update |
| `OnlineGameState.kt` | The shared snapshot (`players`, turn, phase, stack, priority, triggers) |
| `TableRooms.kt`, `TableOverlaysB.kt` | UI wiring |

How it syncs: every phone owns its own seat and publishes **whole snapshots**. The host merges (`mergeRoomState`): a seat may only change its own player object; the active seat owns turn/phase; the priority holder owns stack/priority; the host (seat 0) can administer anything. Hidden zones are redacted per recipient (`redactPrivateZones`).

**Don't build four-player Argentum on this.** It is snapshot-merge with no rules authority, which is exactly what the engine replaced. Reuse only the Worker (see §3, Phase 4).

### B. Argentum "play with a friend" (real rules engine) — 1 host + 1 guest

| File | Role |
|---|---|
| `engine/LocalArgentumHost.kt` | Runs Argentum on the host phone behind the server's `/game` protocol. Host-authoritative: per-seat masked `ClientGameState`, legal actions, mulligans, undo, auto-pass, AI |
| `ArgentumLan.kt` | Wi-Fi: `ArgentumLanServer` (TCP **38472**, accepts **one** guest; a second connection is closed), UDP discovery on **38473** (`ARCANA-ARGENTUM|name|port`), `ArgentumLanGuest` |
| `ArgentumCloud.kt` | Online: `ArgentumCloudLink` / `ArgentumCloudHost` / `ArgentumCloudGuest` over the same Worker |
| `TableWifi.kt` | `startWifiHost`, `acceptWifiGuest`, `joinWifiGame`, `joinOnlineGame` |
| `ArgentumBackend.kt` | `attachLanGuest`, `localHostFactory`, `runningLocalHost` |

Flow today:
1. Host picks a Commander deck → `startWifiHost()` opens the LAN server (or a cloud room) and waits.
2. Guest sends `lanHello {name, deck, commander}`. Host validates it with `OfflineGameSetup.lanSeat(...)`.
3. **The game starts immediately on the first valid hello** (`acceptWifiGuest`). The host builds `LocalArgentumHost(registry, you, seat, …)` with **no `extraOpponents`**, then `setGuest(seat) { server.send(it) }`.
4. Guest messages enter via `receive(msg, GUEST_ID)`; the host routes each seat's view with `emitTo(seat, …)`.

### C. Offline four-player vs AI — already works
`LocalArgentumHost(…, extraOpponents = setup.extraAis)` puts AIs in seats 3–4 (`EntityId("offline-ai-2")`, `"offline-ai-3"`); Argentum runs FFA; each AI seat gets its own `AIPlayer`. Covered by `FourPlayerOfflineTest`. **The engine and the table UI already handle four seats** — the gap is purely "who is a person" and transport.

### Online transport detail (important for Phase 4)
The Worker has no forward-this-message call, so `ArgentumCloudLink` writes each phone's outgoing messages into **its own seat** of the room state as `players[seat].arcanaRelay.msgs` (gzip + base64, last 12, ≤300 KB), and reads the other seat's outbox from each broadcast. `readPeer` hard-codes `peer = if (seat == 0) 1 else 0`. Every post is also a Durable Object `storage.put`.

---

## 2. Two-seat assumptions to remove

All in `engine/LocalArgentumHost.kt` unless noted:

| Line(s) | Assumption |
|---|---|
| `guestEmit` (single nullable) | one guest |
| `people()` | `humanId` + `aiId` only |
| `isPerson()` | only `aiId` can be a remote person |
| `other(id)` | used for `arcanaNudge`; meaningless with 3+ |
| `emitTo()` | anything not `humanId` goes to the one guest |
| `setGuest()` | always seat 2 (`AI_ID`); `GUEST_ID` is an alias for `AI_ID` |
| `"guestLeft"` | concedes `aiId` |
| `quietOpponentTurns` | one host-only flag, `priority == humanId` |
| error strings | "Waiting for your opponent…" |
| `ArgentumLanServer` | one `guest` link; announces until first hello; `send()` has no target |
| `ArgentumHostLink` interface | `send(message)` has no seat |
| `ArgentumCloudLink.readPeer` | single peer, single presence/grace timer |
| `TableWifi.acceptWifiGuest` | starts the game on the first guest, never passes `extraOpponents` |

---

## 3. Recommended plan (do in order; each phase is its own draft PR)

### Phase 1 — Engine host: N people (pure logic, no networking)
- Replace `guestEmit` with `guests: MutableMap<EntityId, (JSONObject) -> Unit>`.
- Keep seat IDs **stable** (`offline-you`, `offline-ai`, `offline-ai-2`, `offline-ai-3`) so checkpoints and existing tests don't break. A seat is a person iff it's `humanId` or in `guests`.
- `setGuest(seatId, seat, emit)`; keep the old two-arg overload delegating to `AI_ID` for compatibility.
- Constructor: let seats 3–4 be filled by a person later (take a list of seats; only seats without a guest get an `AIPlayer` — `startGame()` already does `allSeats.filterNot(::isPerson)`).
- `guestLeft(seatId)` concedes **that seat only**; the game continues while ≥2 players are alive (Argentum FFA handles elimination).
- `arcanaNudge`: send to the current priority/decision holder, not `other()`.
- `quietOpponentTurns`: per-seat map, set by each seat's own `setQuietOpponentTurns`.
- Undo already requires owner == priority holder; add a test that player B can't undo across player C's action.
- Error text: use the waiting seat's name.
- **Tests:** copy `FourPlayerOfflineTest` → people in seats 2–4 with fake emits; assert each emit only ever receives its own hand (no other seat's card names in `hand`/`library`); one guest leaving mid-game concedes only them.

### Phase 2 — Host waiting lobby
Today the game starts on the first hello. Add a lobby on the host:
- Host chooses table size **2 / 3 / 4** and, for each open seat, "Wait for a friend" or "AI".
- Each valid `lanHello` takes the next open seat; host replies `lanSeat {seat, name}` and broadcasts `lanLobby {seats:[{name, commander, kind}]}` to all guests so they see who's in.
- Host taps **Start** → build `LocalArgentumHost(... extraOpponents = remaining AI seats)`, call `setGuest` for every human seat, then `createCommanderPracticeLobby(vsAi = false)`.
- Deck validation stays `OfflineGameSetup.lanSeat`; a bad deck gets an error and the seat stays open (don't `stopWifi()` the whole room as today).
- Guest side only needs to show the lobby message; the game protocol after start is unchanged.

### Phase 3 — Wi-Fi server: up to three guests
- `ArgentumLanServer`: `guests: Map<EntityId, LanLink>`; reject a 4th with a `{type:"error", code:"WIFI", message:"This table is full."}` line instead of a silent close.
- `ArgentumHostLink.send(seat, message)` + `broadcast(message)`; callbacks become `onGuest(seat, hello)`, `onGuestMessage(seat, msg)`, `onGuestLeft(seat)`.
- Discovery: append open seats — `ARCANA-ARGENTUM|name|38472|openSeats`; stop announcing at 0. Keep parsing `parts.size >= 2` so old builds still list hosts.
- `LanConnectionService` already holds Wi-Fi/CPU locks; make sure it's running for the whole lobby + game.
- **Tests:** extend `WifiHostTest` with `fourPeoplePlayThroughOneHost` (three real `ArgentumLanGuest`s on localhost), `fifthPhoneIsTurnedAway`, `oneGuestLeavingKeepsTheGameGoing`.

### Phase 4 — Online: directed relay in the Worker (needs owner OK to redeploy)
**Why the current trick can't go to four:** every seat's `arcanaRelay` outbox is broadcast to **every** phone. With three guests the host's outbox carries each guest's private view, so any guest can base64-decode another guest's hand. It's also 3× the bytes in one seat (already capped at 300 KB vs. the Worker's 500 KB payload / 512 KB message limits) and a storage write per message on the free tier.

Recommended Worker change (small, additive):
- New packet `{type:"relay", to:<seat>, seq, data}` → forwarded only to that seat's socket as `{type:"relay", from, seq, data}`. **No `storage.put`.**
- Guests may only send `to: 0`; the host may send to any seat. Reject anything else.
- Keep a small in-memory ring per seat (e.g. last 12) so a reconnecting phone gets the gap; the guest already sends `requestResync` after reconnecting.
- Advertise it: `GET /health` → `{ok:true, relay:1}`. Client feature-detects; if absent, fall back to the current outbox path **for 1v1 only** and refuse 3–4 player online tables with a clear message ("Update the room service to host 3–4 player online games").
- Client: `ArgentumCloudLink` gets per-seat presence + per-seat grace timers (host tracks seats 1–3; guests track seat 0).
- Don't break the legacy table-room protocol (`state`/`ready`/`deck-status` packets unchanged).
- **Tests:** `npm run typecheck`; a Worker-level test that seat 2 never receives seat 1's relay; `OnlineRelayTest` live variant with three guests against `wrangler dev` (`ARCANA_RELAY_URL`).

*Not recommended:* per-recipient encryption of the outbox. It fixes the leak but not size/quota, and adds key exchange for no gain over a 40-line Worker change.

### Phase 5 — Disconnects, stalls and performance
- **Stalls:** if a dropped guest holds priority or a decision during the 90 s grace window, everyone waits. Add a host action **"Let the AI take over"** (`ais[seat] = OfflineAiPolicy.create(registry, seat)`, remove from `guests`) alongside the existing concede-on-timeout.
- **Host drop ends the game** for everyone (no host migration on the Argentum path). Keep that as a known limitation; say so in the lobby.
- **Host CPU:** after every step the host builds a full `ClientGameState` + legal actions **for each person**. With four people that's ~4× today. Use the existing `timed(...)`/`phaseNanos` diagnostics in a 4-human scenario; if it's slow, enumerate full legal actions only for seats that hold priority or a pending decision and send state-only updates to the rest.
- **Bandwidth:** messages are already gzip-compressed; check `paceMs` flushes aren't multiplied by 4 on cellular.

### Phase 6 — Multiplayer rules sanity (engine already FFA; verify on device)
Attacking a chosen opponent, commander damage per source, eliminated player's cards leaving, turn order skipping eliminated seats, "each opponent" effects, Myriad (see branch `gpt/chittering-myriad-multiplayer-gate-20261025`). Add a scenario per item; no engine-jar edits.

---

## 4. Order, effort and owner gates

| Phase | Depends on | Size | Owner gate |
|---|---|---|---|
| 1 Engine N people | — | M | draft PR review |
| 2 Host lobby | 1 | M | draft PR review |
| 3 Wi-Fi 3 guests | 1, 2 | S–M | draft PR + debug APK on 3–4 phones |
| 4 Worker relay | 1, 2 | S (Worker) + M (client) | **owner approves Worker deploy** |
| 5 Stalls/perf | 3 or 4 | S–M | device test |
| 6 Rules checks | 1 | S each | — |

Ship Wi-Fi four-player first (Phases 1–3): no server change, testable on one network.

## 5. Guardrails
- Branch from `gpt/arcana-integration-next`; **draft** PRs only; post status to #81.
- Never edit `android/app/libs/argentum-*.jar`, never push/merge to `main`, never deploy the Worker or release an APK without owner approval.
- Don't weaken existing tests (`WifiHostTest`, `OnlineRelayTest`, `FourPlayerOfflineTest`, `LocalArgentumHostTest`, `SeatIndexClampTest`).
- Keep 1v1 Wi-Fi/online working at every phase — it's what players use today.

## 6. Definition of done
- Four phones on one Wi-Fi play a full Commander FFA through one host; each sees only their own hand.
- Same over the internet with the updated Worker; old Worker → 1v1 still works, 3–4 is refused with a clear message.
- Any seat can be AI or a person; a guest leaving concedes only them (or hands their seat to the AI).
- New tests above pass in CI; debug APK verified on devices by the owner.
