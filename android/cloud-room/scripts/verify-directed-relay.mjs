/**
 * Run against "wrangler dev --local --port 8787". Uses Node's native WebSocket.
 * Tests the deployed code locally without publishing or accessing real users' rooms.
 */
import assert from "node:assert/strict";
const origin = process.env.ARCANA_RELAY_TEST_ORIGIN ?? "http://127.0.0.1:8787";
const health = await fetch(origin + "/health").then((res) => res.json());
assert.equal(health.relay, 1, "Worker must advertise per-recipient relay version 1");

const code = "R" + Math.random().toString(36).toUpperCase().replace(/[^A-Z0-9]/g, "").padEnd(8, "A").slice(0, 7);
const socketUrl = origin.replace(/^http/, "ws") + "/room/" + code;
const password = "arcana-argentum-" + code;
const sockets = [];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function open(name) {
  const socket = new WebSocket(socketUrl);
  sockets.push(socket);
  const incoming = [];
  const waiters = [];
  socket.addEventListener("message", (ev) => {
    const packet = JSON.parse(ev.data);
    const index = waiters.findIndex((waiter) => waiter.match(packet));
    if (index >= 0) {
      const waiter = waiters.splice(index, 1)[0];
      clearTimeout(waiter.timer);
      waiter.resolve(packet);
    } else incoming.push(packet);
  });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(Error("Socket open timeout: " + name)), 5000);
    socket.addEventListener("open", () => { clearTimeout(timeout); resolve(); }, { once: true });
    socket.addEventListener("error", () => { clearTimeout(timeout); reject(Error("Socket error: " + name)); }, { once: true });
  });
  return {
    socket,
    send(payload) { socket.send(JSON.stringify(payload)); },
    wait(match, timeoutMs = 5000) {
      const index = incoming.findIndex(match);
      if (index >= 0) return Promise.resolve(incoming.splice(index, 1)[0]);
      return new Promise((resolve, reject) => {
        const entry = { match, resolve, timer: null };
        entry.timer = setTimeout(() => {
          const i = waiters.indexOf(entry);
          if (i >= 0) waiters.splice(i, 1);
          reject(Error("Timed out waiting for " + match.toString() + " on " + name));
        }, timeoutMs);
        waiters.push(entry);
      });
    },
    has(match) { return incoming.some(match); },
  };
}
const msg = (type) => (packet) => packet.type === type;
try {
  const host = await open("host");
  host.send({ type: "create", code, password, name: "Host" });
  assert.equal((await host.wait(msg("welcome"))).seat, 0);
  host.send({ type: "relaySeats", kinds: ["friend", "ai", "friend"] });
  await host.wait(msg("relay-seats-ack"));

  const alice = await open("alice");
  alice.send({ type: "join", code, password, name: "Alice" });
  const aliceHello = await alice.wait(msg("welcome"));
  assert.equal(aliceHello.seat, 1);
  assert.equal(aliceHello.relay, 1);

  const charlie = await open("charlie");
  charlie.send({ type: "join", code, password, name: "Charlie" });
  assert.equal((await charlie.wait(msg("welcome"))).seat, 3, "AI seat 2 must remain reserved");

  const extra = await open("extra");
  extra.send({ type: "join", code, password, name: "Extra" });
  const rejectedExtra = await extra.wait(msg("rejected"));
  assert.match(rejectedExtra.reason, /four players|four-player|full/i);

  alice.send({ type: "relay", to: 3, seq: 1, data: "UNAUTHORIZED_GUEST_TO_GUEST" });
  assert.equal((await alice.wait(msg("rejected"))).reason, "Invalid relay destination or data.");
  await sleep(150);
  assert.equal(charlie.has(msg("relay")), false, "Guest-to-guest data leaked");

  host.send({ type: "relay", to: 1, seq: 1, data: "ALICE_SECRET_ONLY" });
  host.send({ type: "relay", to: 3, seq: 1, data: "CHARLIE_SECRET_ONLY" });
  assert.equal((await alice.wait(msg("relay"))).data, "ALICE_SECRET_ONLY");
  assert.equal((await charlie.wait(msg("relay"))).data, "CHARLIE_SECRET_ONLY");
  await sleep(150);
  assert.equal(alice.has((p) => p.type === "relay" && p.data === "CHARLIE_SECRET_ONLY"), false);
  assert.equal(charlie.has((p) => p.type === "relay" && p.data === "ALICE_SECRET_ONLY"), false);

  alice.send({ type: "relay", to: 0, seq: 2, data: "ALICE_ACTION" });
  const toHost = await host.wait(msg("relay"));
  assert.equal(toHost.from, 1);
  assert.equal(toHost.data, "ALICE_ACTION");
  assert.equal(charlie.has((p) => p.type === "relay" && p.data === "ALICE_ACTION"), false);

  alice.send({ type: "relaySeats", kinds: ["friend", "friend", "friend"] });
  assert.match((await alice.wait(msg("rejected"))).reason, /configuration/i);

  host.send({ type: "state", payload: JSON.stringify({
    players: [{ arcanaRelay: { msgs: [[1, "PRIVATE_NEVER_STORE"]] } }],
  }) });
  assert.match((await host.wait(msg("rejected"))).reason, /Snapshots are forbidden/i);

  host.send({ type: "relayKick", to: 1 });
  assert.equal((await alice.wait(msg("relay-kicked"))).type, "relay-kicked");
  host.send({ type: "relay", to: 3, seq: 2, data: "STILL_CONNECTED" });
  assert.equal((await charlie.wait(msg("relay"))).data, "STILL_CONNECTED");

  console.log("PASS: Worker relay v1; AI seat reservation; private directed messages; legacy snapshot blocked; invalid guest kick.");
} finally {
  for (const socket of sockets) try { socket.close(); } catch { /* done */ }
}
