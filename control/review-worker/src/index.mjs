// Arcana Control card-review Worker (#81, owner request 2026-10-28).
//
// Lets the owner Approve / Request Fix a Copilot card PR from the Arcana Control PWA without any GitHub
// credential in the PWA:
//   1. Sign-in: GitHub OAuth web flow run by this Worker. Only the repository owner's account is accepted.
//   2. The owner's OAuth access token never reaches the browser. The PWA receives an AES-GCM encrypted,
//      expiring session blob that only this Worker can open and that only works for /v1/decision.
//   3. A decision is recorded as a real GitHub PR review BY THE OWNER, pinned to the exact reviewed commit
//      (commit_id). A newer head is refused (409), a repeated decision is idempotent, and Approve is refused
//      while checks are failing or still running.
//   4. Request Fix also labels the card's [Arcana Queue] unit. The trusted dispatcher then decides, through
//      the existing repair admission (readiness, 10% reserve, capacity, ceilings), whether Copilot is asked
//      to repair. Nothing here starts a paid worker, and the review never mentions @copilot.
// Approval is card sign-off for integration review only: no merge, build, signing or release.

import { publicGithub } from './public-github.mjs';
import { authorizationInfo, authorizeUnit } from './authorize.mjs';

const REPO = "AARMOU/arcana_table";
const OWNER = "aarmou";
const API = "https://api.github.com/repos/" + REPO;
const PWA_ORIGIN = "https://aarmou.github.io";
const RETURN_PREFIX = PWA_ORIGIN + "/arcana_table/control/";
const SESSION_TTL_S = 12 * 3600;
const STATE_TTL_S = 600;
export const FIX_LABEL = "arcana:owner-fix-requested";
export const DECISION_MARKER = "<!-- arcana-owner-decision-v1 -->";
const COPILOT = new Set(["copilot", "copilot-swe-agent[bot]"]);
const GREEN = new Set(["success", "skipped", "neutral"]);

const enc = new TextEncoder();
const dec = new TextDecoder();
const b64u = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const unb64u = s => Uint8Array.from(atob(String(s).replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
const now = () => Math.floor(Date.now() / 1000);

async function aesKey(secret) {
  const raw = await crypto.subtle.digest("SHA-256", enc.encode("arcana-review-session:" + secret));
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
}
async function hmacKey(secret) {
  return crypto.subtle.importKey("raw", enc.encode("arcana-review-state:" + secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}
export async function sealSession(secret, data) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await aesKey(secret), enc.encode(JSON.stringify(data)));
  return b64u(iv) + "." + b64u(ct);
}
export async function openSession(secret, blob) {
  try {
    const [iv, ct] = String(blob || "").split(".");
    if (!iv || !ct) return null;
    const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64u(iv) }, await aesKey(secret), unb64u(ct));
    const s = JSON.parse(dec.decode(pt));
    if (!s || typeof s.t !== "string" || String(s.login).toLowerCase() !== OWNER || !(s.exp > now())) return null;
    return s;
  } catch { return null; }
}
async function signState(secret, data) {
  const body = b64u(enc.encode(JSON.stringify(data)));
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(body));
  return body + "." + b64u(sig);
}
async function verifyState(secret, state) {
  try {
    const [body, sig] = String(state || "").split(".");
    if (!body || !sig) return null;
    if (!(await crypto.subtle.verify("HMAC", await hmacKey(secret), unb64u(sig), enc.encode(body)))) return null;
    const d = JSON.parse(dec.decode(unb64u(body)));
    return d && d.exp > now() && typeof d.r === "string" && d.r.startsWith(RETURN_PREFIX) ? d : null;
  } catch { return null; }
}

const json = (data, status = 200, origin = null) => new Response(JSON.stringify(data), {
  status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
    ...(origin === PWA_ORIGIN ? { "Access-Control-Allow-Origin": PWA_ORIGIN, "Vary": "Origin" } : {}) } });
const page = (text, status) => new Response(text, { status, headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });
// Native Workers Caching is enabled globally; OAuth redirects carry session state or signed
// return URLs and must NEVER be cached at Cloudflare or by a browser.
const authRedirect = location => new Response(null, { status: 302, headers: {
  Location: location, "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" } });

function gh(token, fetchImpl) {
  return async (path, init = {}) => {
    const res = await fetchImpl(path.startsWith("https://") ? path : API + path, { ...init, headers: {
      Authorization: "Bearer " + token, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": "ArcanaReviewWorker/1.0", ...(init.body ? { "Content-Type": "application/json" } : {}) } });
    const body = res.status === 204 ? null : await res.json().catch(() => null);
    return { ok: res.ok, status: res.status, body };
  };
}

export function cleanNote(note) {
  // No mentions (so no agent or person is pinged), no HTML comments, bounded length.
  return String(note || "").replace(/@/g, "＠").replace(/<!--|-->/g, "").replace(/\r/g, "").trim().slice(0, 1000);
}

// Core decision logic. api: authenticated-as-owner GitHub caller.
export async function decide(api, { pr, sha, decision, note }) {
  if (!Number.isSafeInteger(pr) || pr < 1 || !/^[0-9a-f]{40}$/.test(String(sha)) || !["approve", "request-fix"].includes(decision))
    return [400, { error: "bad-request" }];
  const text = cleanNote(note);
  if (decision === "request-fix" && text.length < 3) return [400, { error: "describe-the-fix" }];
  const p = await api(`/pulls/${pr}`);
  if (!p.ok) return [p.status === 404 ? 404 : 502, { error: "pr-unavailable" }];
  if (p.body.state !== "open") return [409, { error: "pr-not-open" }];
  if (!COPILOT.has(String(p.body.user?.login || "").toLowerCase())) return [422, { error: "not-a-copilot-card-pr" }];
  if (p.body.head?.sha !== sha) return [409, { error: "stale-commit", head: p.body.head?.sha || null }];
  const unitNo = Number((String(p.body.body || "").match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]);
  const unit = unitNo ? await api(`/issues/${unitNo}`) : { ok: false };
  if (!unit.ok || unit.body.state !== "open" || !String(unit.body.title || "").startsWith("[Arcana Queue]"))
    return [422, { error: "no-open-card-unit" }];
  const event = decision === "approve" ? "APPROVED" : "CHANGES_REQUESTED";
  const reviews = await api(`/pulls/${pr}/reviews?per_page=100`);
  if (!reviews.ok) return [502, { error: "reviews-unavailable" }];
  const mine = (reviews.body || []).filter(r => String(r.user?.login || "").toLowerCase() === OWNER && r.commit_id === sha &&
    ["APPROVED", "CHANGES_REQUESTED"].includes(r.state));
  if (mine.length && mine[mine.length - 1].state === event)
    return [200, { ok: true, duplicate: true, state: decision === "approve" ? "approved" : "changes-requested", sha }];
  if (decision === "approve") {
    const checks = await api(`/commits/${sha}/check-runs?per_page=100`);
    if (!checks.ok) return [502, { error: "checks-unavailable" }];
    const runs = checks.body?.check_runs || [];
    const notGreen = runs.filter(r => r.status !== "completed" || !GREEN.has(r.conclusion)).map(r => r.name);
    if (!runs.length || notGreen.length) return [422, { error: "checks-not-green", checks: notGreen.slice(0, 10) }];
  }
  const body = [DECISION_MARKER, `sha:${sha}`, decision === "approve"
    ? "**Owner verified this card** (Arcana Control). Sign-off for integration review only: not a merge, build, signing or release approval."
    : "**Owner requested a fix** (Arcana Control):\n\n" + text, decision === "approve" && text ? "\nNote: " + text : ""].join("\n");
  const r = await api(`/pulls/${pr}/reviews`, { method: "POST", body: JSON.stringify({ commit_id: sha, event: decision === "approve" ? "APPROVE" : "REQUEST_CHANGES", body }) });
  if (!r.ok) return [r.status === 422 ? 422 : 502, { error: "review-not-recorded", status: r.status }];
  if (decision === "request-fix") {
    const l = await api(`/issues/${unitNo}/labels`, { method: "POST", body: JSON.stringify({ labels: [FIX_LABEL] }) });
    if (!l.ok) return [207, { ok: true, state: "changes-requested", sha, warning: "fix-request-not-queued" }];
  }
  return [200, { ok: true, state: decision === "approve" ? "approved" : "changes-requested", sha, review: r.body?.id || null, unit: unitNo }];
}

export default {
  async fetch(request, env, ctx, fetchImpl = fetch) {
    const url = new URL(request.url);
    const origin = request.headers.get("Origin");
    if (url.pathname === "/health") return json({ ok: true, service: "arcana-review", protocol: 1 }, 200, origin);
    if (url.pathname === "/v1/public/github") return publicGithub(request, { fetchImpl });
    if (!env.GITHUB_OAUTH_CLIENT_ID || !env.GITHUB_OAUTH_CLIENT_SECRET || !env.SESSION_SECRET || String(env.SESSION_SECRET).length < 32)
      return json({ error: "not-configured" }, 503, origin);

    if (url.pathname === "/auth/login" && request.method === "GET") {
      const ret = url.searchParams.get("return") || RETURN_PREFIX;
      if (!ret.startsWith(RETURN_PREFIX) || /[#\s]/.test(ret)) return page("Return address not allowed.", 400);
      const state = await signState(env.SESSION_SECRET, { r: ret, n: b64u(crypto.getRandomValues(new Uint8Array(16))), exp: now() + STATE_TTL_S });
      const to = new URL("https://github.com/login/oauth/authorize");
      to.searchParams.set("client_id", env.GITHUB_OAUTH_CLIENT_ID);
      to.searchParams.set("scope", "public_repo");
      to.searchParams.set("state", state);
      to.searchParams.set("allow_signup", "false");
      to.searchParams.set("redirect_uri", url.origin + "/auth/callback");
      return authRedirect(to.toString());
    }

    if (url.pathname === "/auth/callback" && request.method === "GET") {
      const st = await verifyState(env.SESSION_SECRET, url.searchParams.get("state"));
      const code = url.searchParams.get("code");
      if (!st || !code) return page("Sign-in expired or invalid. Start again from Arcana Control.", 400);
      const tok = await fetchImpl("https://github.com/login/oauth/access_token", { method: "POST",
        headers: { Accept: "application/json", "Content-Type": "application/json", "User-Agent": "ArcanaReviewWorker/1.0" },
        body: JSON.stringify({ client_id: env.GITHUB_OAUTH_CLIENT_ID, client_secret: env.GITHUB_OAUTH_CLIENT_SECRET, code,
          redirect_uri: url.origin + "/auth/callback" }) });
      const t = await tok.json().catch(() => null);
      if (!tok.ok || !t?.access_token) return page("GitHub sign-in failed.", 502);
      const me = await gh(t.access_token, fetchImpl)("https://api.github.com/user");
      if (!me.ok || String(me.body?.login || "").toLowerCase() !== OWNER) return page("Only the repository owner can review cards.", 403);
      const session = await sealSession(env.SESSION_SECRET, { t: t.access_token, login: me.body.login, exp: now() + SESSION_TTL_S });
      return authRedirect(st.r + "#review-session=" + encodeURIComponent(session));
    }

    // Read-only session confirmation: lets the PWA distinguish a valid owner session
    // from a lost/expired OAuth callback without ever posting a review.
    // Must remain no-store even when the public-feed Worker cache is enabled.
    if (url.pathname === "/v1/session") {
      if (request.method === "OPTIONS") return new Response(null, {
        status: origin === PWA_ORIGIN ? 204 : 403,
        headers: origin === PWA_ORIGIN ? {
          "Access-Control-Allow-Origin": PWA_ORIGIN,
          "Access-Control-Allow-Methods": "GET",
          "Access-Control-Allow-Headers": "Authorization",
          "Access-Control-Max-Age": "600", "Vary": "Origin",
          "Cache-Control": "no-store",
        } : { "Cache-Control": "no-store" }
      });
      if (request.method !== "GET") return json({ error: "use-get" }, 405, origin);
      if (origin !== PWA_ORIGIN) return json({ error: "origin-not-allowed" }, 403, origin);
      const session = await openSession(env.SESSION_SECRET, (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, ""));
      if (!session) return json({ error: "sign-in-required" }, 401, origin);
      return json({ ok: true, login: session.login, expiresAt: session.exp }, 200, origin);
    }

    // Owner-only work permission. An uncached GET pins the live issue before the single tap's
    // POST, which labels ONLY that issue. The existing dispatcher independently admits spending.
    if (url.pathname === "/v1/authorization" || url.pathname === "/v1/authorize") {
      if (request.method === "OPTIONS") return new Response(null, { status: origin === PWA_ORIGIN ? 204 : 403,
        headers: origin === PWA_ORIGIN ? { "Access-Control-Allow-Origin": PWA_ORIGIN,
          "Access-Control-Allow-Methods": "GET, POST", "Access-Control-Allow-Headers": "Authorization, Content-Type",
          "Access-Control-Max-Age": "600", "Vary": "Origin", "Cache-Control": "no-store" }
          : { "Cache-Control": "no-store" } });
      const isInfo = url.pathname === "/v1/authorization";
      if (request.method !== (isInfo ? "GET" : "POST")) return json({ error: "wrong-method" }, 405, origin);
      if (origin !== PWA_ORIGIN) return json({ error: "origin-not-allowed" }, 403, origin);
      const session = await openSession(env.SESSION_SECRET,
        (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, ""));
      if (!session) return json({ error: "sign-in-required" }, 401, origin);
      const api = gh(session.t, fetchImpl);
      if (isInfo) {
        const n = Number(url.searchParams.get("issue"));
        const [status, result] = await authorizationInfo(api, n);
        return json(result, status, origin);
      }
      const len = Number(request.headers.get("content-length") || 0);
      if (len > 4096) return json({ error: "too-large" }, 413, origin);
      let input; try { input = JSON.parse((await request.text()).slice(0, 4096)); }
      catch { return json({ error: "bad-json" }, 400, origin); }
      const [status, result] = await authorizeUnit(api, input);
      return json(result, status, origin);
    }

    if (url.pathname === "/v1/decision") {
      if (request.method === "OPTIONS") return new Response(null, { status: origin === PWA_ORIGIN ? 204 : 403, headers: origin === PWA_ORIGIN ? {
        "Access-Control-Allow-Origin": PWA_ORIGIN, "Access-Control-Allow-Methods": "POST", "Access-Control-Allow-Headers": "Authorization, Content-Type",
        "Access-Control-Max-Age": "600", "Vary": "Origin" } : {} });
      if (request.method !== "POST") return json({ error: "use-post" }, 405, origin);
      if (origin !== PWA_ORIGIN) return json({ error: "origin-not-allowed" }, 403, origin);
      const session = await openSession(env.SESSION_SECRET, (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, ""));
      if (!session) return json({ error: "sign-in-required" }, 401, origin);
      const len = Number(request.headers.get("content-length") || 0);
      if (len > 4096) return json({ error: "too-large" }, 413, origin);
      let input;
      try { input = JSON.parse((await request.text()).slice(0, 4096)); } catch { return json({ error: "bad-json" }, 400, origin); }
      const [status, out] = await decide(gh(session.t, fetchImpl), input || {});
      return json(out, status, origin);
    }
    return json({ error: "not-found" }, 404, origin);
  },
};
