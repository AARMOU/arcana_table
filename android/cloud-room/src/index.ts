import { DurableObject } from "cloudflare:workers";

interface Env {
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
  seats: Array<{ name: string; token: string; online: boolean; isComputer?: boolean }>;
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
    if (url.pathname === "/health") return json({ ok: true, service: "arcana-table-rooms", protocol: 1 });
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

  constructor(state: DurableObjectState, env: Env) {
    super(state, env);
  }

  async fetch(request: Request): Promise<Response> {
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
      case "state": await this.acceptSnapshot(seat, String(packet.payload ?? "")); break;
      case "ready": this.setReady(socket, seat, packet); break;
      case "deck-status": this.setDeck(socket, seat, packet); break;
      case "leave": this.leave(socket, seat); break;
      default: this.send(socket, { type: "rejected", reason: "Unsupported room message." });
    }
  }

  async webSocketClose(socket: WebSocket): Promise<void> { await this.handleDisconnect(socket); }
  async webSocketError(socket: WebSocket): Promise<void> { await this.handleDisconnect(socket); }

  async alarm(): Promise<void> {
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
    let seat = Number.isInteger(preferred) && preferred >= 0 && preferred < MAX_PLAYERS && this.room.seats[preferred]?.online === false && this.room.seats[preferred]?.isComputer !== true
      ? preferred
      : this.room.seats.findIndex((candidate, index) => index > 0 && !candidate.online && !candidate.isComputer);
    if (seat < 0) seat = Math.max(1, this.room.seats.length);
    if (seat >= MAX_PLAYERS) { this.send(socket, { type: "rejected", reason: "This room already has four players." }); return; }
    while (this.room.seats.length <= seat) this.room.seats.push({ name: `PLAYER ${this.room.seats.length + 1}`, token: crypto.randomUUID(), online: false });
    const existing = this.room.seats[seat];
    existing.name = name;
    existing.online = true;
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
    this.send(socket, { type: "welcome", seat });
    this.send(socket, { type: "state", payload: this.privateState(this.room.state, seat) });
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
    if (seat === 0 || seat === Number(current.activePlayer ?? -1)) {
      const allowed = seat === 0
        ? ["activePlayer", "turnNumber", "phaseIndex", "elapsedGameSeconds", "elapsedTurnSeconds", "turnOrder", "readyPlayers", "gameLog", "chat", "gameStarted", "endProposalBy", "endVotes", "gameEnded", "stackActions", "priorityPlayer", "priorityPassed"]
        : ["activePlayer", "turnNumber", "phaseIndex", "elapsedGameSeconds", "elapsedTurnSeconds", "gameLog", "chat", "endProposalBy", "endVotes", "stackActions", "priorityPlayer", "priorityPassed"];
      for (const key of allowed) {
        if (key in incoming) next[key] = incoming[key];
      }
    } else {
      for (const key of ["gameLog", "chat", "stackActions", "endProposalBy", "endVotes"]) if (key in incoming) next[key] = incoming[key];
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

  private async save(): Promise<void> {
    if (!this.room) return;
    await this.ctx.storage.put(ROOM_KEY, this.room);
    await this.ctx.storage.setAlarm(Date.now() + ROOM_TTL_MS);
  }
}
