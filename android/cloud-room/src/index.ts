import { DurableObject } from "cloudflare:workers";

import { chatSettings, chatTrigger, cannedBanter, chatRequest } from '../../../tools/goosegeta/banter.mjs';
import { candidates, freshSession, receive, publicStatus } from '../../../tools/goosegeta/session.mjs';
import { modelRequest } from '../../../tools/goosegeta/model.mjs';
import manifest from './goose-decks.json';
import { pack, unpack } from './goose-wire.mjs';
import { GooseBudget, type BudgetEnv } from './goose-budget';
export { GooseBudget };

type SafeFallback = { epoch: number; version: number; interactionEpoch: string; action: Record<string,unknown>; started: number };
type GooseSession = Omit<ReturnType<typeof freshSession>, "reason"> & { reason: string | null; chatMode: string; lastChatId?: number; lastChatMs?: number; chatCount?: number; chatPending?: boolean; model: boolean; requestId: string; usedInvite: string; detached?: boolean; inFlight?: string; fallback?: SafeFallback };
interface Env extends BudgetEnv {
  GOOSEGETA_ENABLED?: string;
  GOOSEGETA_CONTROL_KEY?: string;
  GOOSEGETA_MODEL?: string;
  GOOSEGETA_GAME_TOKENS?: string;
  GOOSE_BUDGET?: DurableObjectNamespace<GooseBudget>;
  ROOMS: DurableObjectNamespace<GameRoom>;
}

type Player = Record<string, unknown> & { name?: string; isOnline?: boolean; deckLoaded?: boolean };
type RoomState = Record<string, unknown> & {
  players: Player[];
  readyPlayers: number[];
};
interface RoomRecord {
  code: string;
  passwordHash: string;
  state: RoomState;
  seats: Array<{ name: string; token: string; online: boolean; isComputer?: boolean; claimed?: boolean }>;
  relayMode?: boolean;
  invite?: { hash: string; seat: number; expires: number };
  goose?: GooseSession;
}

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});

function roomCode(raw: string): string | null {
  const code = raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  return /^[A-Z0-9]{6,8}$/.test(code) ? code : null;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (/^\/internal\/goosegeta\/[A-Za-z0-9]{6,8}$/.test(url.pathname)) {
      // This route is usable only by the separately owner-authenticated Control Worker.
      const key = env.GOOSEGETA_CONTROL_KEY;
      if (env.GOOSEGETA_ENABLED !== 'true' || !key || key.length < 32 || request.headers.get('Authorization') !== 'Bearer ' + key)
        return json({error:'goosegeta-disabled-or-unauthorized'}, 403);
      const code = url.pathname.split('/').at(-1)!.toUpperCase();
      return env.ROOMS.get(env.ROOMS.idFromName(code)).fetch(request);
    }
    if (url.pathname === "/health") return json({ ok: true, service: "arcana-table-rooms", protocol: 1, relay: 1 });
    const match = url.pathname.match(/^\/room\/([A-Za-z0-9]{6,8})$/);
    if (!match) return json({ error: "Use /health or /room/{roomCode}." }, 404);
    const code = roomCode(match[1]);
    if (!code) return json({ error: "Room codes must be 6 to 8 letters or numbers." }, 400);
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return json({ error: "A secure WebSocket connection is required." }, 426);
    }
    const id = env.ROOMS.idFromName(code);
    return env.ROOMS.get(id).fetch(request);
  },
} satisfies ExportedHandler<Env>;

const ROOM_KEY = "room";
const MAX_PLAYERS = 4;
const ROOM_TTL_MS = 24 * 60 * 60 * 1000;

function initialState(name: string): RoomState {
  return {
    players: [{ name: name.slice(0, 24) || "PLAYER", isOnline: true, deckLoaded: false }],
    activePlayer: 0,
    turnNumber: 1,
    phaseIndex: 0,
    elapsedGameSeconds: 0,
    elapsedTurnSeconds: 0,
    turnOrder: [],
    readyPlayers: [],
    gameLog: [],
    chat: [],
    gameStarted: false,
    endProposalBy: -1,
    endVotes: {},
    gameEnded: false,
    stackActions: [],
    priorityPlayer: 0,
    priorityPassed: [],
  };
}

async function passwordDigest(password: string): Promise<string> {
  const bytes = new TextEncoder().encode(password);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(hash)].map((part) => part.toString(16).padStart(2, "0")).join("");
}

export class GameRoom extends DurableObject<Env> {
  private room: RoomRecord | null = null;
  private gooseOut: Promise<void> = Promise.resolve();

  constructor(state: DurableObjectState, env: Env) {
    super(state, env);
    this.ctx.blockConcurrencyWhile(async () => {
      this.room = await this.ctx.storage.get<RoomRecord>(ROOM_KEY) ?? null;
      const g = this.room?.goose;
      if (g && !g.detached && this.env.GOOSEGETA_ENABLED !== 'true') await this.stopGoose();
      if (g && !g.detached && (g.inFlight || g.chatPending)) {
        // DO reloads must never leave an engine waiting for a model that cannot return.
        // The host-offered fallback is persisted before every provider request; the
        // alarm delivers it exactly once if the request was interrupted.
        if (g.inFlight) {
          g.model = false;
          g.reason = 'interrupted-model-free-fallback';
          if (!g.fallback) { g.status = 'needs attention'; g.reason = 'interrupted-model-missing-fallback'; }
        }
        if (g.chatPending) { g.chatPending = false; g.chatMode = 'muted'; }
        await this.save();
      }
    });
  }

  async fetch(request: Request): Promise<Response> {
    if (new URL(request.url).pathname.startsWith('/internal/goosegeta/')) return this.controlGoose(request);
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return json({ error: "WebSocket required." }, 426);
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    const code = roomCode(new URL(request.url).pathname.split("/").at(-1) ?? "");
    if (!code) return json({ error: "Invalid room code." }, 400);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ seat: -1, code, authenticated: false });
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string" || message.length > 512_000) {
      this.send(socket, { type: "rejected", reason: "Invalid or oversized message." });
      return;
    }
    let packet: Record<string, unknown>;
    try { packet = JSON.parse(message) as Record<string, unknown>; }
    catch { this.send(socket, { type: "rejected", reason: "Message was not valid JSON." }); return; }

    if (!packet || typeof packet !== 'object' || Array.isArray(packet)) { this.send(socket, {type:'rejected',reason:'Invalid packet.'}); return; }
    const attachment = (socket.deserializeAttachment() ?? { seat: -1, code: "", authenticated: false }) as { seat: number; code: string; authenticated: boolean };
    if (!attachment.authenticated) {
      await this.join(socket, packet, attachment.code);
      return;
    }
    if (!this.room) {
      this.room = await this.ctx.storage.get<RoomRecord>(ROOM_KEY) ?? null;
      if (!this.room) { this.send(socket, { type: "rejected", reason: "This room has expired." }); return; }
    }
    const seat = attachment.seat;
    switch (packet.type) {
      case "ping": this.send(socket, { type: "pong" }); break;
      case "state":
        if (this.room.relayMode) this.send(socket, { type: "rejected", reason: "Snapshots are forbidden in directed Argentum rooms." });
        else await this.acceptSnapshot(seat, String(packet.payload ?? ""));
        break;
      case "relay": await this.directRelay(socket, seat, packet); break;
      case "goosegetaInvite": await this.inviteGoose(socket, seat, packet); break;
      case "goosegetaMute": if (seat === 0 && this.room.goose) { this.room.goose.chatMode = 'muted'; await this.save(); } break;
      case "relayKick": this.kickRelayGuest(socket, seat, packet); break;
      case "relaySeats": await this.configureRelaySeats(socket, seat, packet); break;
      case "ready": this.setReady(socket, seat, packet); break;
      case "deck-status": this.setDeck(socket, seat, packet); break;
      case "leave": this.leave(socket, seat); break;
      default: this.send(socket, { type: "rejected", reason: "Unsupported room message." });
    }
  }

  async webSocketClose(socket: WebSocket): Promise<void> { await this.handleDisconnect(socket); }
  async webSocketError(socket: WebSocket): Promise<void> { await this.handleDisconnect(socket); }

  async alarm(): Promise<void> {
    const g = this.room?.goose;
    if (g && g.expires <= Date.now()) { await this.stopGoose(); return; }
    if (g?.inFlight && g.fallback) {
      if (Date.now() < g.fallback.started + 12000) { await this.save(); return; }
      const fallback = g.fallback;
      delete g.inFlight; delete g.fallback;
      g.model = false;
      if (g.status === 'playing' && !g.detached && this.room?.seats[0].online &&
          g.epoch === fallback.epoch && !g.awaiting) {
        g.reason = 'model-timeout-free-fallback';
        g.actions++;
        g.awaiting = {epoch:fallback.epoch,version:fallback.version,type:String(fallback.action.type)} as any;
        await this.sendGoose({type:'submitAction',action:fallback.action,interactionEpoch:fallback.interactionEpoch});
      } else await this.save();
      return;
    }
    for (const socket of this.ctx.getWebSockets()) try { socket.close(4001, 'room expired'); } catch { /* closed */ }
    this.room = null;
    await this.ctx.storage.deleteAll();
  }

  private async join(socket: WebSocket, packet: Record<string, unknown>, requestedCode: string): Promise<void> {
    const code = roomCode(String(packet.code ?? ""));
    const password = String(packet.password ?? "");
    const name = String(packet.name ?? "PLAYER").trim().slice(0, 24) || "PLAYER";
    const creating = packet.type === "create";
    if (!code || code !== requestedCode || password.length < 4) { this.send(socket, { type: "rejected", reason: "Room code or password is invalid." }); return; }
    if (!this.room) this.room = await this.ctx.storage.get<RoomRecord>(ROOM_KEY) ?? null;
    if (this.room && !this.room.seats.some((candidate) => candidate.online)) {
      this.room = null;
      await this.ctx.storage.deleteAll();
    }
    if (creating) {
      if (this.room) { this.send(socket, { type: "rejected", reason: "That room code is already in use." }); return; }
      let state = initialState(name);
      if (typeof packet.payload === "string") {
        try {
          const proposed = JSON.parse(packet.payload) as RoomState;
          if (Array.isArray(proposed.players) && proposed.players.length > 0) {
            state = { ...state, ...proposed, players: proposed.players.slice(0, MAX_PLAYERS), readyPlayers: [] };
            state.players[0] = { ...state.players[0], name, isOnline: true };
          }
        } catch { /* start from an empty room state if the initial snapshot is malformed */ }
      }
      this.room = { code, passwordHash: await passwordDigest(password), state, seats: [{ name, token: crypto.randomUUID(), online: true }] };
      await this.save();
      await this.attach(socket, 0);
      return;
    }
    if (!this.room || this.room.code !== code || (await passwordDigest(password)) !== this.room.passwordHash) {
      this.send(socket, { type: "rejected", reason: "Room code or password is incorrect." }); return;
    }
    const preferred = Number(packet.preferredSeat ?? -1);
    let seat: number;
    if (this.room.relayMode) {
      // Once occupied, a private-hand seat can only be reclaimed using its
      // server-generated per-seat credential. A room code is NOT seat auth.
      if (packet.preferredSeat !== undefined) {
        const previous = this.room.seats[preferred];
        if (!Number.isInteger(preferred) || preferred < 0 || preferred >= MAX_PLAYERS ||
            !previous || previous.online || previous.isComputer ||
            !previous.claimed || packet.seatToken !== previous.token) {
          this.send(socket, { type: "rejected", reason: "Your saved player seat could not be verified." });
          return;
        }
        seat = preferred;
      } else {
        seat = this.room.seats.findIndex((candidate, index) =>
          index > 0 && !candidate.online && !candidate.isComputer && !candidate.claimed);
      }
    } else {
      seat = Number.isInteger(preferred) && preferred >= 0 && preferred < MAX_PLAYERS &&
          this.room.seats[preferred]?.online === false && this.room.seats[preferred]?.isComputer !== true
        ? preferred
        : this.room.seats.findIndex((candidate, index) => index > 0 && !candidate.online && !candidate.isComputer);
      if (seat < 0) seat = Math.max(1, this.room.seats.length);
    }
    if (seat < 0 || seat >= MAX_PLAYERS) { this.send(socket, { type: "rejected", reason: "This room already has four players." }); return; }
    while (this.room.seats.length <= seat) this.room.seats.push({ name: `PLAYER ${this.room.seats.length + 1}`, token: crypto.randomUUID(), online: false });
    const existing = this.room.seats[seat];
    if (this.room.goose && seat !== this.room.goose.seat) this.room.goose.chatMode = 'muted';
    existing.name = name;
    existing.online = true;
    if (this.room.relayMode) existing.claimed = true;
    const players = this.room.state.players;
    while (players.length <= seat) players.push({ name: `PLAYER ${players.length + 1}`, isOnline: false, deckLoaded: false });
    players[seat] = { ...players[seat], name, isOnline: true };
    this.room.state.readyPlayers = this.room.state.readyPlayers.filter((item) => item !== seat);
    await this.save();
    await this.attach(socket, seat);
    this.broadcastState();
  }

  private async attach(socket: WebSocket, seat: number): Promise<void> {
    if (!this.room) return;
    socket.serializeAttachment({ seat, authenticated: true });
    this.send(socket, { type: "welcome", seat, relay: 1, seatToken: this.room!.seats[seat]?.token });
    this.send(socket, { type: "state", payload: this.privateState(this.room.state, seat) });
  }

  /**
   * The Argentum host is the sole rules authority. Guests can send only to the
   * host, and only the host can send a hidden, masked engine view to a guest.
   * This packet is never written to Durable Object storage or room snapshots.
   */
  private async directRelay(socket: WebSocket, from: number, packet: Record<string, unknown>): Promise<void> {
    if (!this.room) return;
    const to = packet.to;
    const seq = packet.seq;
    const data = packet.data;
    if (!Number.isSafeInteger(to) || !Number.isSafeInteger(seq) ||
        Number(seq) <= 0 || typeof data !== "string" || data.length > 400_000 ||
        (from === 0 ? Number(to) < 1 || Number(to) >= MAX_PLAYERS : to !== 0) ||
        !this.room.seats[Number(to)]?.online || this.room.seats[Number(to)]?.isComputer === true) {
      this.send(socket, { type: "rejected", reason: "Invalid relay destination or data." });
      return;
    }
    if (from === 0 && this.room.goose && this.room.goose.seat === to && this.room.goose.status !== 'stopped') {
      await this.relayToGoose(Number(seq), data as string);
      return;
    }
    this.sendToSeat(Number(to), { type: "relay", from, seq, data });
  }

  /** Kick an invalid/unvalidated guest without disturbing other players. */
  private kickRelayGuest(socket: WebSocket, from: number, packet: Record<string, unknown>): void {
    const seat = packet.to;
    if (!this.room || from !== 0 || !Number.isInteger(seat) ||
        Number(seat) < 1 || Number(seat) >= MAX_PLAYERS) {
      this.send(socket, { type: "rejected", reason: "Only the host may reject a guest." });
      return;
    }
    if (this.room.goose && this.room.goose.seat === seat && !this.room.goose.detached) {
      void this.stopGoose();
      return;
    }
    for (const peer of this.ctx.getWebSockets()) {
      const attachment = peer.deserializeAttachment() as { seat?: number; authenticated?: boolean } | null;
      if (attachment?.authenticated && attachment.seat === seat) {
        this.send(peer, { type: "relay-kicked", reason: "Your deck was rejected by the host." });
        try { peer.close(4000, "deck rejected"); } catch { /* disconnect handler cleans up */ }
      }
    }
    // Rotate the secret before admitting another player to this seat.
    // An invalid guest must never be able to reclaim it via preferredSeat.
    const victim = this.room.seats[Number(seat)];
    if (victim) {
      victim.online = false;
      victim.claimed = false;
      victim.token = crypto.randomUUID();
      if (this.room.state.players[Number(seat)]) this.room.state.players[Number(seat)].isOnline = false;
      void this.save().then(() => this.broadcastState());
    }
  }

  /**
   * The host declares AI-only slots before inviting guests. This prevents a
   * joining phone from occupying an AI seat and later seeing the wrong view.
   * Before the game starts the host may send it again to change the table:
   * open seats can switch between friend and AI, and the number of seats can
   * change, but a seat a guest holds (online or claimed) must stay a friend
   * seat and keep its guest, name and credential.
   */
  private async configureRelaySeats(socket: WebSocket, from: number, packet: Record<string, unknown>): Promise<void> {
    const reject = (reason: string) => this.send(socket, { type: "rejected", request: "relaySeats", reason });
    if (!this.room || from !== 0 || !Array.isArray(packet.kinds) ||
        packet.kinds.length < 1 || packet.kinds.length > MAX_PLAYERS - 1 ||
        packet.kinds.some((kind) => kind !== "ai" && kind !== "friend")) {
      reject("Seat configuration cannot be changed.");
      return;
    }
    const kinds = packet.kinds as string[];
    const occupied = (index: number) => {
      const seat = this.room?.seats[index];
      return !!seat && !seat.isComputer && (seat.online || seat.claimed === true);
    };
    for (let index = 1; index < this.room.seats.length; index++) {
      if (occupied(index) && kinds[index - 1] !== "friend") {
        reject("A player is in that seat. Remove them before changing it.");
        return;
      }
    }
    for (let index = 1; index <= kinds.length; index++) {
      if (occupied(index)) continue;
      const isComputer = kinds[index - 1] === "ai";
      const previous = this.room.seats[index];
      this.room.seats[index] = {
        name: isComputer ? `AI ${index + 1}` : `PLAYER ${index + 1}`,
        token: isComputer ? "" : (previous?.token || crypto.randomUUID()),
        online: false,
        isComputer,
        claimed: false,
      };
      while (this.room.state.players.length <= index) this.room.state.players.push({ name: `PLAYER ${index + 1}`, isOnline: false, deckLoaded: false });
      this.room.state.players[index] = {
        name: this.room.seats[index].name, isOnline: false, isComputer, deckLoaded: isComputer,
      };
    }
    this.room.relayMode = true;
    // No legacy outbox may survive mode negotiation or enter a broadcast.
    for (const player of this.room.state.players) delete player.arcanaRelay;
    // Host seat must also be claimed so its private token authorizes reconnect.
    this.room.seats[0].claimed = true;
    this.room.seats.length = kinds.length + 1;
    this.room.state.players.length = kinds.length + 1;
    await this.save();
    this.send(socket, { type: "relay-seats-ack", kinds });
    this.broadcastState();
  }

  private async setReady(socket: WebSocket, seat: number, packet: Record<string, unknown>): Promise<void> {
    if (!this.room) return;
    const players = this.room.state.players;
    const requested = packet.ready === true;
    const accepted = !requested || (players[seat]?.deckLoaded === true && players[seat]?.openingHandKept === true);
    const ready = new Set(this.room.state.readyPlayers);
    if (requested && accepted) ready.add(seat); else ready.delete(seat);
    this.room.state.readyPlayers = [...ready].sort((a, b) => a - b);
    await this.save();
    this.send(socket, { type: "ready-ack", seat, ready: requested && accepted, accepted, requestId: packet.requestId });
    this.broadcastState();
  }

  private async setDeck(socket: WebSocket, seat: number, packet: Record<string, unknown>): Promise<void> {
    if (!this.room) return;
    const player = this.room.state.players[seat];
    if (!player) return;
    player.name = String(packet.name ?? player.name ?? `PLAYER ${seat + 1}`).slice(0, 24);
    player.isOnline = true;
    player.deckLoaded = packet.loaded === true;
    player.mulligansTaken = 0;
    player.openingHandKept = false;
    if (typeof packet.commanderName === "string" || packet.commanderName === null) player.commanderName = packet.commanderName;
    this.room.seats[seat].name = String(player.name);
    this.room.state.readyPlayers = this.room.state.readyPlayers.filter((item) => item !== seat);
    await this.save();
    this.send(socket, { type: "deck-status-ack", seat, loaded: player.deckLoaded });
    this.broadcastState();
  }

  private async acceptSnapshot(seat: number, payload: string): Promise<void> {
    if (!this.room || payload.length > 500_000) return;
    let incoming: RoomState;
    try { incoming = JSON.parse(payload) as RoomState; }
    catch { this.sendToSeat(seat, { type: "rejected", reason: "Game state was invalid." }); return; }
    if (!Array.isArray(incoming.players) || !incoming.players[seat]) return;
    const current = this.room.state;
    const players = current.players.slice();
    for (let index = 0; index < incoming.players.length && index < MAX_PLAYERS; index++) {
      const candidate = incoming.players[index];
      // A player may update only their own seat. The host can also maintain AI seats.
      if (index === seat || (seat === 0 && candidate.isComputer === true)) {
        while (players.length <= index) players.push({ name: `PLAYER ${players.length + 1}`, isOnline: false, deckLoaded: false });
        players[index] = {
          ...(players[index] ?? {}),
          ...candidate,
          ...(index === seat ? { name: this.room.seats[seat].name, isOnline: this.room.seats[seat].online } : {}),
        };
      }
    }
    const next: RoomState = { ...current, players };
    const activeSeat = Number(current.activePlayer ?? 0);
    const prioritySeat = Number(current.priorityPlayer ?? activeSeat);
    if (seat === 0 || seat === activeSeat) {
      const allowed = seat === 0
        ? ["activePlayer", "turnNumber", "phaseIndex", "elapsedGameSeconds", "elapsedTurnSeconds", "turnOrder", "readyPlayers", "gameLog", "chat", "gameStarted", "endProposalBy", "endVotes", "gameEnded"]
        : ["activePlayer", "turnNumber", "phaseIndex", "elapsedGameSeconds", "elapsedTurnSeconds", "gameLog", "chat", "endProposalBy", "endVotes"];
      for (const key of allowed) {
        if (key in incoming) next[key] = incoming[key];
      }
    } else {
      for (const key of ["gameLog", "chat", "endProposalBy", "endVotes"]) if (key in incoming) next[key] = incoming[key];
    }
    // Stack actions and priority belong to whoever currently has priority, not
    // necessarily the active player. The host (seat 0) may still administer them.
    if (seat === 0 || seat === prioritySeat) {
      for (const key of ["stackActions", "priorityPlayer", "priorityPassed"]) {
        if (key in incoming) next[key] = incoming[key];
      }
    }
    if (seat === 0) {
      for (let index = 1; index < incoming.players.length && index < MAX_PLAYERS; index++) {
        const player = incoming.players[index];
        if (player.isComputer === true) {
          while (this.room.seats.length <= index) this.room.seats.push({ name: `PLAYER ${this.room.seats.length + 1}`, token: crypto.randomUUID(), online: false });
          this.room.seats[index] = { name: String(player.name ?? `COMPUTER ${index + 1}`), token: "", online: false, isComputer: true };
        }
      }
    }
    this.room.state = next;
    await this.save();
    this.broadcastState();
  }

  private async leave(socket: WebSocket, seat: number): Promise<void> {
    socket.serializeAttachment({seat:-1,authenticated:false});
    await this.setOffline(seat);
    this.send(socket, { type: "left" });
    try { socket.close(1000, "left room"); } catch { /* already closed */ }
  }

  private async handleDisconnect(socket: WebSocket): Promise<void> {
    const attachment = socket.deserializeAttachment() as { seat?: number; authenticated?: boolean } | null;
    if (!this.room) this.room = await this.ctx.storage.get<RoomRecord>(ROOM_KEY) ?? null;
    if (attachment?.authenticated && Number.isInteger(attachment.seat)) await this.setOffline(attachment.seat!);
  }

  private async setOffline(seat: number): Promise<void> {
    if (!this.room || !this.room.seats[seat] || !this.room.seats[seat].online) return;
    if (seat === 0 && this.room.goose && this.room.goose.status !== 'stopped') await this.stopGoose();
    this.room.seats[seat].online = false;
    if (this.room.state.players[seat]) this.room.state.players[seat].isOnline = false;
    this.room.state.readyPlayers = this.room.state.readyPlayers.filter((item) => item !== seat);
    if (this.room.state.gameStarted === true && Number(this.room.state.activePlayer ?? -1) === seat) {
      const order = Array.isArray(this.room.state.turnOrder)
        ? (this.room.state.turnOrder as unknown[]).map(Number).filter((item) => Number.isInteger(item) && item >= 0 && item < MAX_PLAYERS)
        : [];
      const seats = order.length > 0 ? order : this.room.state.players.map((_, index) => index);
      const oldPosition = Math.max(0, seats.indexOf(seat));
      const next = Array.from({ length: seats.length }, (_, offset) => seats[(oldPosition + offset + 1) % seats.length])
        .find((candidate) => this.room?.seats[candidate]?.online && this.room?.state.players[candidate]?.isEliminated !== true);
      if (next !== undefined) {
        this.room.state.activePlayer = next;
        this.room.state.phaseIndex = 0;
        this.room.state.elapsedTurnSeconds = 0;
        if (seats.indexOf(next) <= oldPosition) this.room.state.turnNumber = Number(this.room.state.turnNumber ?? 1) + 1;
        const log = Array.isArray(this.room.state.gameLog) ? this.room.state.gameLog as unknown[] : [];
        this.room.state.gameLog = [...log, `${this.room.state.players[seat]?.name ?? "A player"} disconnected; turn passed to ${this.room.state.players[next]?.name ?? `Player ${next + 1}`}.`].slice(-250);
      }
    }
    if (!this.room.seats.some((candidate) => candidate.online)) {
      this.room = null;
      await this.ctx.storage.deleteAll();
      return;
    }
    await this.save();
    this.broadcastState();
  }

  private privateState(source: RoomState, viewer: number): RoomState {
    const state = structuredClone(source);
    state.players = state.players.map((player, index) => {
      const copy = { ...player };
      if (this.room?.relayMode) delete copy.arcanaRelay;
      if (index !== viewer) {
        copy.library = [];
        if (copy.handRevealedToAll !== true) copy.hand = [];
      }
      return copy;
    });
    return state;
  }

  private broadcastState(): void {
    if (!this.room) return;
    for (const socket of this.ctx.getWebSockets()) {
      const attachment = socket.deserializeAttachment() as { seat?: number; authenticated?: boolean } | null;
      if (attachment?.authenticated && Number.isInteger(attachment.seat)) {
        this.send(socket, { type: "state", payload: this.privateState(this.room.state, attachment.seat!) });
      }
    }
  }

  private sendToSeat(seat: number, packet: unknown): void {
    for (const socket of this.ctx.getWebSockets()) {
      const attachment = socket.deserializeAttachment() as { seat?: number } | null;
      if (attachment?.seat === seat) this.send(socket, packet);
    }
  }

  private send(socket: WebSocket, packet: unknown): void {
    try { socket.send(JSON.stringify(packet)); } catch { /* disconnect handler will clean up */ }
  }

  private async inviteGoose(socket: WebSocket, seat: number, packet: Record<string, unknown>): Promise<void> {
    const target = Number(packet.seat);
    const candidate = this.room?.seats[target];
    if (this.env.GOOSEGETA_ENABLED !== 'true' || seat !== 0 || !this.room?.relayMode ||
        !Number.isInteger(target) || target < 1 || target > 3 || !candidate ||
        candidate.online || candidate.claimed || candidate.isComputer ||
        (this.room.goose && this.room.goose.status !== 'stopped')) {
      this.send(socket, {type:'goosegeta-invite',requestId:packet.requestId,error:'Choose an empty Friend seat; cloud guest may be disabled.'}); return;
    }
    const capability = crypto.randomUUID() + crypto.randomUUID();
    this.room.invite = {hash:await passwordDigest(capability), seat:target, expires:Date.now()+120000};
    await this.save();
    this.send(socket, {type:'goosegeta-invite',requestId:packet.requestId,capability,seat:target,expires:this.room.invite.expires});
  }

  private async controlGoose(request: Request): Promise<Response> {
    const key = this.env.GOOSEGETA_CONTROL_KEY;
    if (this.env.GOOSEGETA_ENABLED !== 'true' || !key || key.length < 32 || request.headers.get('Authorization') !== 'Bearer '+key)
      return json({error:'disabled-or-unauthorized'},403);
    if (!this.room?.relayMode) return json({error:'no-directed-room'},409);
    if (request.method === 'GET') return json(publicStatus(this.room.goose));
    if (request.method !== 'POST') return json({error:'use-post'},405);
    const raw = await request.text(); if (raw.length > 2048) return json({error:'too-large'},413);
    let input: Record<string,unknown>;
    try { input = JSON.parse(raw); } catch { return json({error:'bad-json'},400); }
    if (!input || Array.isArray(input) || typeof input !== 'object') return json({error:'bad-json'},400);
    if (input.action === 'chat') {
      const g = this.room.goose;
      if (!g || g.id !== input.sessionId || g.status === 'stopped') return json({error:'session-mismatch'},409);
      g.chatMode = this.otherPeople() ? 'muted' : chatSettings(input.chatMode);
      await this.save(); return json(publicStatus(g));
    }
    if (input.action === 'dismiss') {
      // A stale browser cannot dismiss a newer game.
      if (!this.room.goose || input.sessionId !== this.room.goose.id) return json({error:'session-mismatch'},409);
      await this.stopGoose(); return json(publicStatus(this.room.goose));
    }
    if (input.action !== 'summon' || typeof input.capability !== 'string' || input.capability.length > 100 ||
        typeof input.requestId !== 'string' || !/^[a-zA-Z0-9-]{16,80}$/.test(input.requestId)) return json({error:'bad-request'},400);
    const hash = await passwordDigest(input.capability);
    const existing = this.room.goose;
    if (existing?.requestId === input.requestId && existing.usedInvite === hash) return json(publicStatus(existing));
    if (existing && existing.status !== 'stopped') return json({error:'guest-already-active'},409);
    const invite = this.room.invite;
    if (!invite || invite.hash !== hash || invite.expires <= Date.now() || !this.room.seats[0].online)
      return json({error:'host-invite-required'},403);
    const seat = this.room.seats[invite.seat];
    if (!seat || seat.online || seat.claimed || seat.isComputer) return json({error:'seat-unavailable'},409);
    const deck = manifest.decks.find(d => d.id === input.deckId);
    if (!deck) return json({error:'unknown-deck'},400);
    // Runtime index is build-checked, but ANDROID performs final authoritative registry/deck validation.
    const validated = deck;
    if (input.model === true && (this.env.GOOSEGETA_OPENAI_ENABLED !== 'true' || !this.env.GOOSE_BUDGET))
      return json({error:'model-disabled'},403);
    const session = Object.assign(freshSession(crypto.randomUUID(), invite.seat, deck.id, Date.now()),
      {model:input.model === true, requestId:input.requestId, usedInvite:hash, chatMode:this.otherPeople() ? 'muted' : chatSettings(input.chatMode)});
    this.room.goose = session;
    delete this.room.invite; // single use, reserved before any delivery
    seat.online = true; seat.claimed = true; seat.name = 'GOOSEGETA';
    this.room.state.players[session.seat] = {name:'GOOSEGETA',isOnline:true,deckLoaded:false};
    await this.save(); this.broadcastState();
    await this.sendGoose({type:'lanHello',name:'GOOSEGETA',commander:validated.commander,deck:validated.cards,strictDeck:true});
    return json(publicStatus(session)); // 'joining', never falsely claim Android accepted.
  }

  private sendGoose(packet: Record<string,unknown>): Promise<void> {
    const sessionId = this.room?.goose?.id;
    // Compression and model/chat continuations can finish out of order. Host RelayInbox
    // accepts increasing sequence numbers only, so serialize reservation AND emission.
    const operation = this.gooseOut.then(() => this.deliverGoose(packet, sessionId));
    this.gooseOut = operation.catch(() => {});
    return operation;
  }

  private async deliverGoose(packet: Record<string,unknown>, sessionId?: string): Promise<void> {
    const g = this.room?.goose;
    if (!g || g.id !== sessionId || g.status === 'stopped' || g.detached || g.expires <= Date.now()) return;
    const seq = ++g.seq;
    await this.save(); // reserve sequence before emission, including across restart
    const data = await pack({...packet,_relayLink:g.link});
    if (this.room?.goose?.id !== g.id || g.status === 'stopped') return;
    this.sendToSeat(0,{type:'relay',from:g.seat,seq,data});
  }

  private async relayToGoose(seq: number, data: string): Promise<void> {
    const g = this.room?.goose; if (!g || g.status === 'stopped') return;
    if (g.expires <= Date.now()) { await this.stopGoose(); return; }
    try {
      const packet = await unpack(data);
      if (typeof packet._relayLink !== 'string' || !packet._relayLink) throw Error('host-link');
      if (g.hostLink && g.hostLink !== packet._relayLink) {
        g.reason = 'host-process-restarted';g.status = 'needs attention'; await this.save(); return; // host process restart needs a fresh invite/game
      }
      if (seq <= g.hostSeq) return;
      g.hostLink = packet._relayLink; g.hostSeq = seq;
      delete packet._relayLink;
      const trigger = chatTrigger(g, packet, Date.now());
      // Sideband work can await a provider concurrently; it cannot select or mutate an engine action.
      if (trigger && packet.type === 'gameOver') await this.banter(g, trigger);
      else if (trigger) this.ctx.waitUntil(this.banter(g, trigger));
      if (packet.type === 'arcanaChat') { await this.save(); return; }
      const oldEpoch = g.epoch;
      const reply = receive(g,packet,Date.now());
      if (g.status === 'stopped') { await this.stopGoose(); return; }
      await this.save();
      if (reply) await this.sendGoose(reply);
      if (packet.type !== 'stateUpdate' || g.epoch === oldEpoch || g.status !== 'playing' || g.failures) return;
      const playerId = g.seat === 1 ? 'offline-ai' : 'offline-ai-'+g.seat;
      const options = candidates(packet,playerId);
      if (!options.length) {
        // The host also sends interactive snapshots to seats without priority. Waiting is normal.
        if (!packet.pendingDecision && (!Array.isArray(packet.legalActions) || packet.legalActions.length === 0)) return;
        g.reason = 'unsupported-decision-or-action'; g.status = 'needs attention'; await this.save(); return;
      }
      g.reason = null;
      const epoch = g.epoch; let selected = options[0];
      if (g.model && options.length > 1) {
        try {
          const limit = Number(this.env.GOOSEGETA_GAME_TOKENS);
          const {body,reserve} = modelRequest(this.env.GOOSEGETA_MODEL,packet,options);
          if (!Number.isSafeInteger(limit) || limit < 1 || limit > 9000000 || g.reservedTokens+reserve > limit) throw Error('game-budget');
          g.reservedTokens += reserve; g.inFlight = String(epoch);
          // One fallback action is derived exclusively from the Android host's
          // offered candidates; no guessed target, illegal spell or hidden data.
          g.fallback = {epoch,version:packet.stateVersion,interactionEpoch:String(packet.interactionEpoch),
            action:selected.action,started:Date.now()};
          await this.save();
          // Bound the entire round trip, including a provider that ignores
          // AbortSignal. A slow OpenAI request must not block a Magic turn.
          let timeoutId: ReturnType<typeof setTimeout> | undefined;
          const deadline = new Promise<Response>((_,reject)=>{
            timeoutId = setTimeout(()=>reject(Error('model-deadline')),10000);
          });
          let result: Response;
          try {
            result = await Promise.race([
              this.env.GOOSE_BUDGET!.get(this.env.GOOSE_BUDGET!.idFromName('account-daily')).fetch('https://budget/decide',
                {method:'POST',body:JSON.stringify({body,reserve,options})}),
              deadline
            ]);
          } finally { if(timeoutId !== undefined) clearTimeout(timeoutId); }
          if (!result.ok) throw Error('model-paused');
          const answer = await result.json() as {candidateId:string};
          selected = options.find(o => o.id === answer.candidateId)!;
          if (!selected) throw Error('model-output');
          if (g.inFlight !== String(epoch)) return; // watchdog already resolved this epoch
        } catch {
          // A spent one-shot allowance or provider error must not deadlock a valid host game.
          // Disable paid inference permanently for this session and use the already offered
          // deterministic legal candidate; never retry the paid provider or invent an action.
          if (this.room?.goose !== g || g.detached || g.epoch !== epoch ||
              g.status !== 'playing' || g.expires <= Date.now() || !this.room.seats[0].online ||
              (g.inFlight && g.inFlight !== String(epoch))) return;
          if (!g.inFlight && !g.model) return; // watchdog has already resolved it
          delete g.inFlight; delete g.fallback;
          g.model = false;
          g.reason = 'model-paused-free-fallback';
          await this.save();
        }
      }
      // Dismiss, newer state, host departure or expiry during an async model call invalidates result.
      if (this.room?.goose !== g || g.epoch !== epoch || g.status !== 'playing' || g.expires <= Date.now() || !this.room.seats[0].online) return;
      delete g.inFlight; delete g.fallback;
      g.actions++;
      g.awaiting = {epoch,version:packet.stateVersion,type:selected.action.type} as any;
      await this.sendGoose({type:'submitAction',action:selected.action,interactionEpoch:packet.interactionEpoch});
    } catch { if (this.room?.goose === g && g.status !== 'stopped') { g.status = 'needs attention'; await this.save(); } }
  }

  private otherPeople(): boolean {
    return this.room?.seats.some((seat,index) => index > 0 && index !== this.room?.goose?.seat && !seat.isComputer && (seat.online || seat.claimed)) ?? true;
  }

  private async banter(g: GooseSession, trigger: {kind:string;text:string}): Promise<void> {
    if (g.chatMode === 'muted' || g.chatPending || g.status === 'stopped') return;
    g.chatPending = true; g.lastChatMs = Date.now(); g.chatCount = (g.chatCount ?? 0)+1;
    const mode = g.chatMode;
    await this.save();
    let text = cannedBanter(mode,trigger,g.chatCount);
    try {
      if (g.model) {
        const {body,reserve} = chatRequest(this.env.GOOSEGETA_MODEL, mode, trigger);
        const limit = Number(this.env.GOOSEGETA_GAME_TOKENS);
        if (!Number.isSafeInteger(limit) || limit < 1 || limit > 9000000 || g.reservedTokens+reserve > limit) throw Error('game-budget');
        g.reservedTokens += reserve;
        await this.save();
        const result = await this.env.GOOSE_BUDGET!.get(this.env.GOOSE_BUDGET!.idFromName('account-daily')).fetch('https://budget/decide',
          {method:'POST',body:JSON.stringify({kind:'chat',body,reserve})});
        if (!result.ok) throw Error('chat-paused');
        text = (await result.json() as {text:string}).text;
        if (typeof text !== 'string' || text.length > 300) throw Error('chat-output');
      }
      if (this.room?.goose === g && !g.detached && g.chatMode === mode && !this.otherPeople() && g.expires > Date.now())
        await this.sendGoose({type:'arcanaChatSend',text}); // no author/seat fields: Android assigns identity and echoes
    } catch { g.chatMode = 'muted'; g.reason = 'chat-paused-or-budget-limit'; }
    finally { g.chatPending = false; if (this.room?.goose === g) await this.save(); }
  }

  private async stopGoose(): Promise<void> {
    const room = this.room, g = room?.goose; if (!room || !g || g.detached) return;
    g.detached = true;
    g.status = 'stopped'; delete room.invite;
    const seat = room.seats[g.seat];
    // Keep private seat claimed until host's existing guestLeft/remove flow rotates it.
    if (seat) { seat.online = false; seat.claimed = true; }
    if (room.state.players[g.seat]) room.state.players[g.seat].isOnline = false;
    room.state.readyPlayers = room.state.readyPlayers.filter(s => s !== g.seat);
    const seq = ++g.seq;
    await this.save();
    const data = await pack({type:'arcanaBye',_relayLink:g.link});
    this.sendToSeat(0,{type:'relay',from:g.seat,seq,data});
    this.broadcastState();
  }

  private async save(): Promise<void> {
    if (!this.room) return;
    await this.ctx.storage.put(ROOM_KEY, this.room);
    const g = this.room.goose;
    const idleExpiry = g && g.status !== 'stopped' ? Math.min(g.expires, Date.now() + ROOM_TTL_MS) : Date.now() + ROOM_TTL_MS;
    await this.ctx.storage.setAlarm(g?.inFlight && g.fallback
      ? Math.min(idleExpiry, g.fallback.started + 12000) : idleExpiry);
  }
}
