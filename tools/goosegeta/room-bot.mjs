#!/usr/bin/env node
/**
 * Opt-in, free/local Goosegeta client for an existing B140 directed-Argentum internet room.
 * No ChatGPT connection, API key, game-server deployment, or Android code changes.
 * Requires Node 22+ (native WebSocket). Host phone remains the sole rules authority.
 */
import { gzipSync, gunzipSync } from "node:zlib";
import { randomUUID } from "node:crypto";
import { loadDecks, runtimeCardIndex, validateDeck, selectAction, chooseBottomCards } from "./logic.mjs";

const args = process.argv.slice(2);
function arg(name, fallback) {
  const i = args.indexOf("--" + name);
  return i < 0 ? fallback : args[i + 1];
}
const decks = loadDecks();
if (args.includes("--help") || args.includes("-h")) {
  console.log("Goosegeta room bot (Node 22+)\n" +
    "  node tools/goosegeta/room-bot.mjs --list-decks\n" +
    "  node tools/goosegeta/room-bot.mjs --deck goosegeta-flock --check-deck\n" +
    "  node tools/goosegeta/room-bot.mjs --code ABC123 --deck goosegeta-flock\n" +
    "  Options: --endpoint https://arcana-table-rooms.arcanamtg.workers.dev");
  process.exit(0);
}
if (args.includes("--list-decks")) {
  for (const deck of decks) console.log(deck.id + ": " + deck.name + " [" + deck.commander + "]");
  process.exit(0);
}
const deckId = arg("deck", "goosegeta-flock");
const deck = decks.find(d => d.id === deckId);
if (!deck) throw new Error("Unknown deck " + deckId + ". Use --list-decks.");
// Refuse to start with names absent from the actual runtime card index.
// This does not prove a card's mechanics work or independently verify color identity.
const validated = validateDeck(deck, runtimeCardIndex());
console.log("Deck verified against local card registry: " + deck.name + ", commander " + validated.commander + ", 99 main-deck cards.");
if (args.includes("--check-deck")) process.exit(0);

const code = String(arg("code", "")).trim().toUpperCase();
if (!/^[A-Z0-9]{6,8}$/.test(code)) throw new Error("Provide a real 6–8 character room code with --code.");
const endpoint = String(arg("endpoint", process.env.ARCANA_ROOM_ENDPOINT || "https://arcana-table-rooms.arcanamtg.workers.dev")).trim().replace(/\/$/, "");
const url = new URL(endpoint);
if (!["https:", "wss:"].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== "/")
  throw new Error("Room endpoint must be a clean HTTPS/WSS origin, without credentials or a path.");
url.protocol = "wss:";
url.pathname = "/room/" + code;
if (typeof WebSocket !== "function") throw new Error("Node 22+ is required for the built-in WebSocket.");

const NAME = "GOOSEGETA";
const linkId = randomUUID();
const socket = new WebSocket(url.toString());
let ownSeat = -1;
let seatToken = null;
let outbound = 0;
let hostSeq = 0;
let hostLink = null;
let deckAccepted = false;
let readySent = false;
let lastEpoch = "";
let lastMulligan = "";
let actionCount = 0;
const MAX_ACTIONS = 400;
const log = text => console.log("[Goosegeta] " + text);
function send(data) {
  if (socket.readyState !== WebSocket.OPEN) return false;
  socket.send(JSON.stringify(data));
  return true;
}
function relay(message) {
  if (ownSeat < 1 || !seatToken) return false;
  const body = { ...message, _relayLink: linkId };
  const compressed = gzipSync(Buffer.from(JSON.stringify(body), "utf8")).toString("base64");
  if (compressed.length > 400000) throw new Error("Outgoing room packet too large.");
  return send({ type: "relay", to: 0, seq: ++outbound, data: compressed });
}
function join() {
  if (!send({ type: "join", code, password: "arcana-argentum-" + code, name: NAME })) throw new Error("WebSocket was not ready.");
}
function handleGame(packet) {
  const type = packet.type;
  if (type === "lanSeat") {
    deckAccepted = true;
    log("Host accepted the deck; waiting for the lobby.");
    if (!readySent && relay({ type: "lanReady", ready: true })) readySent = true;
    return;
  }
  if (type === "lanLobby") {
    if (deckAccepted && !readySent && relay({ type: "lanReady", ready: true })) readySent = true;
    log("In Commander lobby. Ready=" + readySent + ". Waiting for the human host to start.");
    return;
  }
  if (type === "mulliganDecision") {
    const key = JSON.stringify([packet.hand, packet.mulliganCount, packet.cardsToPutOnBottom]);
    if (key === lastMulligan) return;
    lastMulligan = key;
    if (relay({ type: "keepHand" })) log("Keeping my opening hand.");
    return;
  }
  if (type === "chooseBottomCards") {
    const ids = chooseBottomCards(packet);
    if (ids == null) { log("Cannot verify the mulligan-bottom choice; stopping."); return; }
    if (relay({ type: "chooseBottomCards", cardIds: ids })) log("Bottomed " + ids.length + " cards.");
    return;
  }
  if (type === "gameStarted") { log("THE MONSTER LIVES. Prepare your squirrels."); return; }
  if (type === "gameOver") { log("Game ended. " + String(packet.message || packet.reason || "")); return; }
  if (type === "error") {
    log("Host reported: " + String(packet.message || packet.code || "unknown").slice(0, 180));
    return;
  }
  if (type !== "stateUpdate") return;
  if (actionCount >= MAX_ACTIONS) { log("Local action safety limit reached; no further moves."); return; }
  const selection = selectAction(packet);
  if (!selection) {
    if (packet.interactive && packet.pendingDecision != null && packet.pendingDecision?.type !== "PlayCardDecision")
      log("Unimplemented engine decision " + packet.pendingDecision.type + "; holding rather than guessing.");
    return;
  }
  const epoch = String(selection.info.interactionEpoch || packet.interactionEpoch || "");
  if (!/^offline-[0-9]+$/.test(epoch) || epoch === lastEpoch) return;
  let action = selection.action;
  if (packet.pendingDecision?.type === "PlayCardDecision") {
    const decision = packet.pendingDecision;
    if (!decision.id || !decision.playerId || !["CastSpell", "PlayLand"].includes(action.type)) return;
    action = {
      type: "SubmitDecision", playerId: decision.playerId,
      response: { type: "PlayCardResponse", decisionId: decision.id, action },
    };
  }
  if (relay({ type: "submitAction", action, interactionEpoch: epoch })) {
    lastEpoch = epoch;
    actionCount++;
    log(selection.reason + " (" + selection.action.type + "), action " + actionCount + ".");
  }
}
socket.addEventListener("open", () => {
  log("Connected to room relay; joining one friend seat.");
  join();
});
socket.addEventListener("message", event => {
  try {
    if (typeof event.data !== "string" || event.data.length > 600000) return;
    const packet = JSON.parse(event.data);
    if (packet.type === "welcome") {
      if (packet.relay !== 1 || !Number.isInteger(packet.seat) || packet.seat < 1 || packet.seat > 3 ||
          typeof packet.seatToken !== "string" || packet.seatToken.length < 16)
        throw new Error("The server did not authenticate a directed room guest.");
      if (ownSeat >= 0 && (packet.seat !== ownSeat || packet.seatToken !== seatToken))
        throw new Error("Unexpected seat reassignment");
      ownSeat = packet.seat;
      seatToken = packet.seatToken;
      log("Claimed seat " + (ownSeat + 1) + "; submitting deck. The host must leave this seat set to Friend.");
      relay({ type: "lanHello", name: NAME, commander: validated.commander, deck: validated.cards });
      return;
    }
    if (packet.type === "rejected" || packet.type === "relay-kicked") {
      throw new Error("Room rejected this client: " + String(packet.reason || packet.type).slice(0, 160));
    }
    if (packet.type !== "relay") return;
    if (packet.from !== 0 || !Number.isSafeInteger(packet.seq) || packet.seq < 1 || typeof packet.data !== "string" || packet.data.length > 400000) return;
    const bytes = gunzipSync(Buffer.from(packet.data, "base64"), { maxOutputLength: 512000 });
    const message = JSON.parse(bytes.toString("utf8"));
    if (typeof message._relayLink !== "string" || !message._relayLink) return;
    if (hostLink !== message._relayLink) { hostLink = message._relayLink; hostSeq = 0; }
    if (packet.seq <= hostSeq) return;
    hostSeq = packet.seq;
    delete message._relayLink;
    handleGame(message);
  } catch (error) {
    console.error("[Goosegeta] Protocol/error: " + error.message);
    socket.close(1008, "protocol failure");
  }
});
socket.addEventListener("error", () => log("WebSocket failed; check the relay and internet connection."));
socket.addEventListener("close", event => {
  log("Connection closed (" + event.code + "). Bot stopped; no automatic paid retries.");
});
process.on("SIGINT", () => {
  log("Shutting down.");
  if (socket.readyState === WebSocket.OPEN) {
    relay({ type: "arcanaBye" });
    send({ type: "leave" });
    socket.close(1000, "operator stopped");
  } else socket.close();
});
