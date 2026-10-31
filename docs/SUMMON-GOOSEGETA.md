# Operation Summon Goosegeta — engineering candidate

Owner authority and coordination: issue #81, handoff 6545387018; chat/persona addenda
6545526968 and 6545558346. Claimed by Boneknuckles in 6545418071. No paid worker,
OpenAI call, deployment, merge, APK/AAB, signing or release is authorized by this candidate.

## Architecture and concrete paths

The Android host remains the only Argentum rules authority. The existing `GameRoom`
Durable Object owns a virtual **Friend** seat. There is no outbound WebSocket,
Node daemon, home-computer dependency, new cron, or independent game engine.
Existing server-side hibernating sockets carry the host's per-seat masked packets.

- `tools/goosegeta/game-core.mjs`, `session.mjs`: extracted #572 deck/action helpers,
  finite move candidates and persisted sequence/epoch/session guards. The Node prototype
  remains available for historical tests. The cloud path is independent of its CLI.
- `build-decks.mjs` → `android/cloud-room/src/goose-decks.json`: exact lists checked
  against both current packaged runtime indexes, with SHA-256 provenance. Index presence
  is not rules certification. Android must accept the unchanged deck in **strict** mode;
  unknown cards, wrong count/color identity and illegal duplicates reject instead of
  silently substituting basic lands. Old hosts without strict acknowledgment cannot ready.
- `android/cloud-room/src/index.ts`: host-only, two-minute, one-use invitation; separate
  owner-Control service credential; one virtual session; room/guest expiry, dismiss,
  host-disconnect teardown, sequence/epoch reservation before delivery; no stored hand/state/chat.
- `goose-wire.mjs`: B140 gzip/base64 envelope with bounded streaming decompression.
- `goose-budget.ts`, `tools/goosegeta/model.mjs`: optional Responses API adapter,
  disabled by default, fixed endpoint, manual redirects, strict candidate-ID enum,
  bounded input/output, timeout, no tools/storage, persisted account-wide daily token
  reservation and circuit breaker. A game shares one allowance between moves and chat.
  Reservations are never refunded on ambiguous failures. Limits are token allowances,
  not a claimed dollar invoice or verified tokenizer guarantee. Production cost/pricing
  bounds still need owner approval and measurement with the selected model.
- Android `ArgentumCloudMulti.kt`, `TableMultiLobby.kt`: debug-only host invite action
  opens existing Control with a capability in the URL fragment, never the reconnect token.
  No release button is enabled. Compatible Android source/build is a prerequisite.
- `engine/OfflineGame.kt`, `TableWifi.kt`: strict guest deck admission/acknowledgment.
- `engine/LocalArgentumHost.kt`: sanitized receipt emitted **after** processor success
  and state commit. Receipts and later state transitions are reported separately;
  a later state update alone is not proof an action was accepted.
- Chat is a forward-port of #308 at `abcae2a5e3b9bfb8bff80aa393c34112199b1bbd`,
  not a competing transport: `ArgentumRoomChat.kt`, `TableChatTransport.kt`, native chat
  entry/UI/unread badge and seven original tests. Canonical host delivery is `arcanaChat`;
  bot sends only `arcanaChatSend` text. The host supplies author and seat. Chat never
  enters Argentum's action processor. History remains last 200 messages.
- `tools/goosegeta/banter.mjs`: explicit muted/playful/ruthless consent, limited free
  canned banter, optional fresh model banter under the same spend gates, host-only replies,
  own-echo/replay guards, 15-second minimum gap, 30-message session cap, host `/mute goose`,
  Control mute/dismiss, and public cast/outcome gloat/salt/HONK. Another human joining
  forces mute until a future individual-consent design is implemented.
- Separate main-based Control draft: `control/index.html`, `goosegeta.js`, review-worker
  owner-session route and `src/goosegeta.mjs`, existing service-worker shell. OAuth
  tokens never reach the room service. Service binding is deliberately unconfigured.
  Status starts **joining**, not “working”, until Android actually acknowledges the deck.

## Operational limits that must remain visible

The finite candidate adapter handles basic no-target casts/lands/passes and a limited
subset of engine targets/decisions/combat. Coupled targets, many extra costs, mode/card
selection and other complex decisions can pause. It is not a complete strategic Magic
player or a demonstrated completed game. The model selects a client-generated candidate
ID: B140 does not supply engine action IDs. Android revalidates every selected move.
A host process restart pauses; a host socket disconnect revokes the guest rather than
silently rejoining a private seat. A crashed in-flight model/chat operation may require
dismiss/new invite, with reserved consumption retained. This is safe failure, not a
claim of seamless session recovery.

No persistent game/chat payload or full prompts are written to GitHub logs. Cloudflare
stores only session identity, counters, timestamps, sequence/epoch and quota ledger.
When opted in, the model provider receives only the bot's masked view or one permitted
chat/public-event context. Other chat text is untrusted data. No unrelated conversations,
other-room reads, hidden-information tools, arbitrary code or endpoint selection exist.

## Reproducible no-deploy checks

```sh
node tools/goosegeta/build-decks.mjs --check
node --test tools/goosegeta/*.test.mjs
npm --prefix android/cloud-room ci --ignore-scripts --no-audit --no-fund
npm --prefix android/cloud-room run typecheck
node --test android/cloud-room/scripts/goosegeta.local.test.mjs
python3 -m unittest discover -s tools/cardkit/tests
tools/cardkit/cardkit check
tools/cardkit/cardkit docs-check
python3 tools/check_android_regex.py
```

The local suite executes the actual bundled Worker under workerd/SQLite, including
forced DO eviction with hibernated sockets and the original human relay tests.
Its Android packets and provider responses are fixtures. It makes **zero live OpenAI
calls**. They are neither a real Android game nor model quality/cost evidence.
Wrangler dev's interface discovery fails in this managed environment, so the suite
uses the installed Miniflare compatibility adapter with in-process dispatch.

`.github/workflows/goosegeta-room-prototype.yml` retains the original tests and adds
isolated Worker and Android source/unit checks. The Android job compiles source and
runs tests; it does not assemble or publish an APK. Review its exact-head result before
approving a test build.

## Gate status and required real evidence

| Gate | Status at draft preparation | Evidence still required |
|---|---|---|
| A: isolated code/local runtime | Node and local Worker gates passing; native CI pending | Exact-head native compile/chat/strict-deck JUnit; independent review |
| B: Android-hosted remote game | NOT PERFORMED | Host-accepted exact deck, mulligan, accepted PlayLand/CastSpell, full turn each way, rejection and disconnect cases |
| C: actual model gameplay/chat | NOT PERFORMED; disabled | Separately approved key/model/budget, fixed scenarios and live model-backed Android turn/chat |
| D: owner experience / PC off | NOT PERFORMED | Phone on cellular, owner confirms PC off, remote guest lifecycle, native chat echo/taunt, gloat/HONK after a public event, dismiss |

## One-screen owner setup — after engineering and approvals

Nothing is deployed or ready to summon today. No subscription is required by this source.
After the exact-head native gate and review, approve an **isolated test deployment** and
compatible **test Android build**. The engineer configures the existing Control OAuth
service binding, matching random service secret, disabled model, and the test room
endpoint. No production room or Control deployment is implicit.

On the Fold: host Internet Commander with an empty Friend seat → tap **Invite Goosegeta ·
Test** → Control opens with the private invitation → select deck and optional trash-talk
intensity → **Summon Goosegeta** → wait for Android's lobby acceptance → start game.
**Dismiss** stops the session; **Apply chat / mute** or native `/mute goose` stops banter.
If sign-in interrupts the short-lived invite, sign in then issue a fresh invite.
No PowerShell, home PC process, terminal command, or per-move owner input is intended.

Keep OpenAI disabled for the first remote Android test. API secrets/model selection,
daily/game consumption caps and any paid calls require a separate explicit approval.
Record sanitized session/build/engine SHAs, accepted receipt counts/types, actual turns,
canonical native chat delivery IDs across participants, mute/dismiss results, time range,
and the owner's PC-off confirmation. Never publish private hands, chat transcripts,
keys or invitation capabilities. Do not label the mission complete before these gates pass.

Primary references used for implementation:
- https://developers.cloudflare.com/durable-objects/best-practices/websockets/
- https://developers.openai.com/api/docs/guides/structured-outputs
