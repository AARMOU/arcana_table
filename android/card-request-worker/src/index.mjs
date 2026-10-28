// Separate, opt-in public intake for Arcana missing cards.
// Never expose GitHub credentials in the APK or accept arbitrary GitHub API paths.
const TRACKER = "https://github.com/AARMOU/arcana_table/issues/321";
const API = "https://api.github.com/repos/AARMOU/arcana_table";
const MAX_BATCH = 10;
const MAX_BODYSIZE = 4096;
const MAX_DAILY_BATCHES = 3;
const MAX_GLOBAL_DAILY_BATCHES = 60;
const MAX_NAMES = 500;
const respond = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  }});
function canonicalName(raw) {
  if (typeof raw !== "string") return null;
  const name = raw.normalize("NFKC").replace(/\s+/gu, " ").trim();
  // Deliberately exclude GitHub mentions, markdown controls, newlines, URL delimiters.
  if (name.length < 2 || name.length > 120 ||
      !/^[\p{L}\p{N}][\p{L}\p{N}\p{M} ,.'"’&!?:+\/()—–-]*$/u.test(name)) return null;
  return name;
}
const keyOf = name => name.split(/\s+\/\/\s+/u)[0].trim().toLocaleLowerCase("en-US");
function parseBatch(json) {
  if (!json || typeof json !== "object" || !Array.isArray(json.cardNames) ||
      json.cardNames.length < 1 || json.cardNames.length > MAX_BATCH ||
      !/^[0-9a-fA-F-]{36}$/.test(json.requestId || "")) return null;
  const names = new Map();
  for (const raw of json.cardNames) {
    const name = canonicalName(raw);
    if (!name) return null;
    const key = keyOf(name);
    if (!names.has(key)) names.set(key, { key, name });
  }
  return { requestId: json.requestId, names: [...names.values()] };
}
async function readLimitedBody(request, maxBytes) {
  const headerLength = Number(request.headers.get("content-length") || 0);
  if (headerLength > maxBytes) return null;
  const reader = request.body?.getReader();
  if (!reader) return "";
  const chunks = [];
  let length = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    length += value.byteLength;
    if (length > maxBytes) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const merged = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { merged.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder().decode(merged);
}
async function requestKey(ip, salt) {
  const bytes = new TextEncoder();
  const secret = await crypto.subtle.importKey("raw", bytes.encode(salt), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signed = await crypto.subtle.sign("HMAC", secret, bytes.encode(ip));
  return [...new Uint8Array(signed)].map(x => x.toString(16).padStart(2, "0")).join("");
}
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET")
      return respond({ ok: true, service: "arcana-card-requests", protocol: 1 });
    if (url.pathname !== "/v1/card-requests") return respond({ error: "Not found" }, 404);
    if (request.method !== "POST") return respond({ error: "Use POST" }, 405);
    if (!env.RATE_SALT || !env.GITHUB_REQUESTS_TOKEN || !env.REQUEST_QUEUE)
      return respond({ error: "Card requests are not configured" }, 503);
    if (!(request.headers.get("content-type") || "").startsWith("application/json"))
      return respond({ error: "Expected JSON" }, 415);
    const raw = await readLimitedBody(request, MAX_BODYSIZE);
    if (raw === null) return respond({ error: "Batch too large" }, 413);
    let json;
    try { json = JSON.parse(raw); } catch { return respond({ error: "Invalid JSON" }, 400); }
    const parsed = parseBatch(json);
    if (!parsed) return respond({ error: "Invalid card names or request ID (maximum 10 names)" }, 400);
    const ip = request.headers.get("CF-Connecting-IP");
    if (!ip) return respond({ error: "Request verification unavailable" }, 503);
    // Daily rotating HMAC prevents correlating a request source across dates.
    const opaque = await requestKey(ip, env.RATE_SALT + ":" + new Date().toISOString().slice(0, 10));
    const internal = new Request("https://internal/submit", {
      method: "POST", headers: { "content-type": "application/json", "x-opaque-client": opaque },
      body: JSON.stringify(parsed)
    });
    return env.REQUEST_QUEUE.get(env.REQUEST_QUEUE.idFromName("global")).fetch(internal);
  },
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(env.REQUEST_QUEUE.get(env.REQUEST_QUEUE.idFromName("global"))
      .fetch(new Request("https://internal/sync", { method: "POST" })));
  },
};
function publicComment(name, count) {
  return [
    "<!-- arcana-card-request-v1 -->",
    "**Requested card:** " + name,
    "**Opt-in requests:** " + count,
    "**Status:** Needs library, duplicate-claim and rules-engine verification.",
    "",
    "Automated intake only. No playable-card claim or Copilot authorization.",
  ].join("\n");
}
async function githubWrite(env, record) {
  const update = Number.isInteger(record.commentId);
  const url = update ? API + "/issues/comments/" + record.commentId : API + "/issues/321/comments";
  const response = await fetch(url, {
    method: update ? "PATCH" : "POST",
    headers: {
      "Authorization": "Bearer " + env.GITHUB_REQUESTS_TOKEN,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "Content-Type": "application/json",
      "User-Agent": "ArcanaCardRequestWorker/1.0",
    },
    body: JSON.stringify({ body: publicComment(record.name, record.count) })
  });
  if (!response.ok) return null;
  const body = await response.json();
  return Number.isInteger(body.id) ? body.id : null;
}
export class CardRequestQueue {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.chain = Promise.resolve();
  }
  async fetch(request) {
    // Serialize all writes and GitHub synchronizations per single Durable Object.
    const next = this.chain.then(() => this.handle(request));
    this.chain = next.catch(() => {});
    try { return await next; }
    catch { return respond({ error: "Request unavailable; retry later" }, 503); }
  }
  async handle(request) {
    const url = new URL(request.url);
    if (request.method !== "POST") return respond({ error: "Method" }, 405);
    if (url.pathname === "/sync") {
      const synced = await this.sync(10);
      return respond({ synced });
    }
    if (url.pathname !== "/submit") return respond({ error: "Not found" }, 404);
    const parsed = parseBatch(await request.json());
    const client = request.headers.get("x-opaque-client");
    if (!parsed || !/^[a-f0-9]{64}$/.test(client || "")) return respond({ error: "Bad request" }, 400);
    const now = Date.now();
    const seen = (await this.state.storage.get("seen")) || {};
    for (const [id, when] of Object.entries(seen))
      if (now - when > 48 * 3600_000) delete seen[id];
    if (seen[parsed.requestId]) return respond({ ok: true, duplicate: true, tracker: TRACKER, accepted: 0 });
    const day = new Date(now).toISOString().slice(0, 10);
    const rateKey = "rate:" + day + ":" + client;
    const globalKey = "rate-global:" + day;
    const rate = (await this.state.storage.get(rateKey)) || 0;
    const globalRate = (await this.state.storage.get(globalKey)) || 0;
    if (rate >= MAX_DAILY_BATCHES || globalRate >= MAX_GLOBAL_DAILY_BATCHES)
      return respond({ error: "Daily request limit reached" }, 429);
    const records = (await this.state.storage.get("records")) || {};
    let newKeys = 0;
    for (const { key } of parsed.names) if (!records[key]) newKeys++;
    if (Object.keys(records).length + newKeys > MAX_NAMES)
      return respond({ error: "Intake temporarily full" }, 503);
    for (const { key, name } of parsed.names) {
      const record = records[key] || { name, count: 0, syncedCount: 0, commentId: null };
      record.count++;
      records[key] = record;
    }
    seen[parsed.requestId] = now;
    await this.state.storage.put("records", records);
    await this.state.storage.put("seen", seen);
    await this.state.storage.put(rateKey, rate + 1);
    await this.state.storage.put(globalKey, globalRate + 1);
    const synced = await this.sync(10);
    // sync() persists a fresh copy; the pre-sync map above is intentionally stale.
    const published = (await this.state.storage.get("records")) || {};
    const pending = parsed.names.filter(({ key }) => published[key]?.syncedCount < published[key]?.count).length;
    return respond({ ok: true, tracker: TRACKER, accepted: parsed.names.length,
      pending: pending > 0, synced, message: pending > 0 ? "Queued; public tracker synchronization pending" : "Added to tracker" },
      pending > 0 ? 202 : 200);
  }
  async sync(limit = 10) {
    if (!this.env.GITHUB_REQUESTS_TOKEN) return 0;
    const records = (await this.state.storage.get("records")) || {};
    let synced = 0;
    for (const record of Object.values(records)) {
      if (synced >= limit) break;
      if (record.syncedCount >= record.count) continue;
      const id = await githubWrite(this.env, record);
      if (!id) break; // Fail closed; scheduled retry will resume without losing queued names.
      record.commentId = id;
      record.syncedCount = record.count;
      await this.state.storage.put("records", records);
      synced++;
    }
    return synced;
  }
}
export const _test = { canonicalName, parseBatch, keyOf, publicComment, requestKey };
