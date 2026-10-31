import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { gzipSync, gunzipSync } from "node:zlib";
import { randomUUID } from "node:crypto";
import { loadDecks, runtimeCardIndex, validateDeck, selectAction, chooseBottomCards } from "./logic.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(resolve(HERE, "room-bot.mjs"), "utf8")
  .replace(/^#!.*\n/, "")
  .replace(/^import .*;\n/gm, "");

class FakeSocket {
  static OPEN = 1;
  static last = null;
  constructor(url) {
    this.url = url;
    this.readyState = 0;
    this.listeners = new Map();
    this.sent = [];
    this.closing = null;
    FakeSocket.last = this;
  }
  addEventListener(type, listener) { this.listeners.set(type, listener); }
  emit(type, event = {}) { this.listeners.get(type)?.(event); }
  send(text) { this.sent.push(JSON.parse(text)); }
  close(code, reason) { this.closing = { code, reason }; }
}

function harness() {
  const logs = [];
  const processMock = {
    argv: ["node", "room-bot.mjs", "--code", "ABC123", "--endpoint", "https://example.test", "--deck", "goosegeta-glue"],
    env: {}, on() {}, exit() { throw new Error("Unexpected process exit"); },
  };
  runInNewContext(source, {
    process: processMock, console: { log: line => logs.push(line), error: line => logs.push(line) },
    URL, WebSocket: FakeSocket, Buffer, gzipSync, gunzipSync, randomUUID,
    loadDecks, runtimeCardIndex, validateDeck, selectAction, chooseBottomCards, structuredClone,
  }, { timeout: 3000 });
  const socket = FakeSocket.last;
  socket.readyState = FakeSocket.OPEN;
  const send = value => socket.emit("message", { data: JSON.stringify(value) });
  let seq = 0;
  const hostLink = "trusted-host-link-for-replay-test";
  const relay = (value, opts = {}) => {
    const message = gzipSync(Buffer.from(JSON.stringify({ ...value, _relayLink: hostLink }), "utf8")).toString("base64");
    send({ type: "relay", from: opts.from ?? 0, seq: opts.seq ?? ++seq, data: message });
  };
  const outgoing = () => socket.sent.filter(x => x.type === "relay")
    .map(x => JSON.parse(gunzipSync(Buffer.from(x.data, "base64")).toString("utf8")));
  return { socket, logs, send, relay, outgoing };
}

test("full mock transport: friend seat, deck, ready, mulligan, first legal move", () => {
  const h = harness();
  assert.equal(h.socket.url, "wss://example.test/room/ABC123");
  h.socket.emit("open");
  assert.deepEqual(h.socket.sent[0], {
    type: "join", code: "ABC123", password: "arcana-argentum-ABC123", name: "GOOSEGETA",
  });
  h.send({ type: "welcome", relay: 1, seat: 1, seatToken: "0123456789abcdef0123456789abcdef" });
  const hello = h.outgoing()[0];
  assert.equal(hello.type, "lanHello");
  assert.equal(hello.commander, "Bello, Bard of the Brambles");
  assert.equal(hello.deck.length, 99);
  assert.equal(hello.name, "GOOSEGETA");
  assert.ok(hello._relayLink);
  h.relay({ type: "lanSeat", seat: "offline-ai", name: "GOOSEGETA" });
  h.relay({ type: "lanLobby", canStart: true });
  assert.equal(h.outgoing().filter(x => x.type === "lanReady").length, 1);
  h.relay({ type: "mulliganDecision", hand: ["cardA"], cards: { cardA: { typeLine: "Land" } }, mulliganCount: 0 });
  assert.equal(h.outgoing().filter(x => x.type === "keepHand").length, 1);
  h.relay({ type: "gameStarted" });
  const land = { type: "PlayLand", playerId: "offline-ai", cardId: "land-1" };
  const update = { type: "stateUpdate", interactive: true, interactionEpoch: "offline-42",
    legalActions: [{ action: land, isAffordable: true }], pendingDecision: null };
  h.relay(update);
  const submitted = h.outgoing().filter(x => x.type === "submitAction");
  assert.equal(submitted.length, 1);
  assert.deepEqual({ ...submitted[0].action }, land);
  assert.equal(submitted[0].interactionEpoch, "offline-42");
  h.relay(update);
  assert.equal(h.outgoing().filter(x => x.type === "submitAction").length, 1, "stale epoch must not replay");
  h.relay({ ...update, interactionEpoch: "offline-43", pendingDecision: { type: "ChooseTargets" } });
  assert.equal(h.outgoing().filter(x => x.type === "submitAction").length, 1, "unknown choices must not guess");
});

test("reject non-directed welcome and counterfeit seat credentials", () => {
  const h = harness();
  h.socket.emit("open");
  h.send({ type: "welcome", seat: 1, relay: 0, seatToken: "0123456789abcdef0123456789abcdef" });
  assert.equal(h.socket.closing?.code, 1008);
  assert.equal(h.outgoing().length, 0);
});

test("ignore messages from another player seat rather than acting on them", () => {
  const h = harness();
  h.socket.emit("open");
  h.send({ type: "welcome", relay: 1, seat: 1, seatToken: "0123456789abcdef0123456789abcdef" });
  h.relay({ type: "lanSeat" }, { from: 2 });
  assert.equal(h.outgoing().filter(x => x.type === "lanReady").length, 0);
});

test("guard matching epoch across received legal actions", () => {
  const h = harness();
  h.socket.emit("open");
  h.send({ type: "welcome", relay: 1, seat: 1, seatToken: "0123456789abcdef0123456789abcdef" });
  h.relay({ type: "stateUpdate", interactive: true, interactionEpoch: "offline-87",
    legalActions: [{ action: { type: "PassPriority", playerId: "offline-ai" }, isAffordable: true }],
    pendingDecision: null });
  assert.equal(h.outgoing().filter(x => x.type === "submitAction").length, 1);
  h.relay({ type: "stateUpdate", interactive: true, interactionEpoch: "offline-87",
    legalActions: [{ action: { type: "PassPriority", playerId: "offline-ai" }, isAffordable: true }],
    pendingDecision: null });
  assert.equal(h.outgoing().filter(x => x.type === "submitAction").length, 1);
});
