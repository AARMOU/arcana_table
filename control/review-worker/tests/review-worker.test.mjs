import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { sealSession, openSession, decide, cleanNote, FIX_LABEL, DECISION_MARKER } from "../src/index.mjs";

const SECRET = "s".repeat(40);
const ENV = { GITHUB_OAUTH_CLIENT_ID: "cid", GITHUB_OAUTH_CLIENT_SECRET: "csecret", SESSION_SECRET: SECRET };
const SHA = "a".repeat(40);
const PWA = "https://aarmou.github.io";
const W = "https://arcana-review.example.workers.dev";

// Minimal fake GitHub keeping state, recording every write.
function fakeGitHub({ head = SHA, checks = [{ name: "toolkit", status: "completed", conclusion: "success" }], reviews = [],
  author = "Copilot", prState = "open", unitTitle = "[Arcana Queue] Celestine — Cardkit SUPPORTED, unclaimed", user = "AARMOU" } = {}) {
  const writes = [];
  const fetchImpl = async (url, init = {}) => {
    const u = String(url); const method = init.method || "GET";
    const ok = (body, status = 200) => new Response(body === null ? null : JSON.stringify(body), { status });
    if (u === "https://github.com/login/oauth/access_token") return ok({ access_token: "gho_owner" });
    if (u === "https://api.github.com/user") return ok({ login: user });
    if (u.endsWith("/pulls/360") && method === "GET") return ok({ state: prState, user: { login: author }, head: { sha: head }, body: "- Fixes #358" });
    if (u.endsWith("/issues/358") && method === "GET") return ok({ state: "open", title: unitTitle });
    if (u.includes("/pulls/360/reviews") && method === "GET") return ok(reviews);
    if (u.includes(`/commits/${SHA}/check-runs`)) return ok({ check_runs: checks });
    if (u.endsWith("/pulls/360/reviews") && method === "POST") { const b = JSON.parse(init.body); writes.push(["review", b]);
      reviews.push({ user: { login: "AARMOU" }, commit_id: b.commit_id, state: b.event === "APPROVE" ? "APPROVED" : "CHANGES_REQUESTED" });
      return ok({ id: 991 }); }
    if (u.endsWith("/issues/358/labels") && method === "POST") { writes.push(["label", JSON.parse(init.body)]); return ok([]); }
    return ok({ message: "unexpected " + method + " " + u }, 500);
  };
  return { fetchImpl, writes };
}
const decision = async (gh, body, { session, origin = PWA } = {}) => {
  const s = session === undefined ? await sealSession(SECRET, { t: "gho_owner", login: "AARMOU", exp: Math.floor(Date.now() / 1000) + 600 }) : session;
  const res = await worker.fetch(new Request(W + "/v1/decision", { method: "POST",
    headers: { "Content-Type": "application/json", Origin: origin, ...(s ? { Authorization: "Bearer " + s } : {}) },
    body: JSON.stringify(body) }), ENV, {}, gh.fetchImpl);
  return { status: res.status, body: await res.json() };
};

test("approve records an owner APPROVE review pinned to the exact commit", async () => {
  const gh = fakeGitHub();
  const r = await decision(gh, { pr: 360, sha: SHA, decision: "approve" });
  assert.equal(r.status, 200);
  assert.equal(r.body.state, "approved");
  assert.equal(gh.writes.length, 1);
  const [, review] = gh.writes[0];
  assert.deepEqual([review.event, review.commit_id], ["APPROVE", SHA]);
  assert.ok(review.body.startsWith(DECISION_MARKER) && review.body.includes("not a merge"));
});

test("request fix records CHANGES_REQUESTED, queues the unit for guarded repair, and never pings anyone", async () => {
  const gh = fakeGitHub();
  const r = await decision(gh, { pr: 360, sha: SHA, decision: "request-fix", note: "@copilot cost reduction ignores tokens <!-- x -->" });
  assert.equal(r.status, 200);
  assert.equal(r.body.state, "changes-requested");
  const review = gh.writes.find(w => w[0] === "review")[1];
  assert.equal(review.event, "REQUEST_CHANGES");
  assert.ok(!review.body.includes("@"), "no mentions");
  assert.deepEqual(gh.writes.find(w => w[0] === "label")[1], { labels: [FIX_LABEL] });
  assert.equal((await decision(fakeGitHub(), { pr: 360, sha: SHA, decision: "request-fix", note: "" })).status, 400);
});

test("authentication failures: no session, tampered, expired, non-owner, wrong origin", async () => {
  const gh = fakeGitHub();
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" }, { session: null })).status, 401);
  const good = await sealSession(SECRET, { t: "x", login: "AARMOU", exp: Math.floor(Date.now() / 1000) + 600 });
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" }, { session: good.slice(0, -4) + "AAAA" })).status, 401);
  const expired = await sealSession(SECRET, { t: "x", login: "AARMOU", exp: Math.floor(Date.now() / 1000) - 1 });
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" }, { session: expired })).status, 401);
  const stranger = await sealSession(SECRET, { t: "x", login: "someone", exp: Math.floor(Date.now() / 1000) + 600 });
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" }, { session: stranger })).status, 401);
  const other = await sealSession("o".repeat(40), { t: "x", login: "AARMOU", exp: Math.floor(Date.now() / 1000) + 600 });
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" }, { session: other })).status, 401);
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" }, { origin: "https://evil.example" })).status, 403);
  assert.equal(gh.writes.length, 0);
});

test("a stale commit is refused and nothing is recorded", async () => {
  const gh = fakeGitHub({ head: "b".repeat(40) });
  const r = await decision(gh, { pr: 360, sha: SHA, decision: "approve" });
  assert.deepEqual([r.status, r.body.error, r.body.head], [409, "stale-commit", "b".repeat(40)]);
  assert.equal(gh.writes.length, 0);
});

test("duplicate submissions are idempotent; a changed mind on the same commit is recorded", async () => {
  const gh = fakeGitHub();
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" })).status, 200);
  const again = await decision(gh, { pr: 360, sha: SHA, decision: "approve" });
  assert.deepEqual([again.status, again.body.duplicate], [200, true]);
  assert.equal(gh.writes.filter(w => w[0] === "review").length, 1);
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "request-fix", note: "wrong cost" })).status, 200);
  assert.equal(gh.writes.filter(w => w[0] === "review").length, 2);
});

test("failed or running verification blocks approval but still allows a fix request", async () => {
  for (const checks of [[{ name: "cards-and-android", status: "completed", conclusion: "failure" }],
    [{ name: "toolkit", status: "in_progress", conclusion: null }], []]) {
    const gh = fakeGitHub({ checks });
    const r = await decision(gh, { pr: 360, sha: SHA, decision: "approve" });
    assert.deepEqual([r.status, r.body.error], [422, "checks-not-green"]);
    assert.equal(gh.writes.length, 0);
    assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "request-fix", note: "tests fail" })).status, 200);
  }
});

test("only open Copilot card PRs on open [Arcana Queue] units can be decided", async () => {
  for (const [opts, code] of [[{ author: "someone" }, 422], [{ prState: "closed" }, 409], [{ unitTitle: "Other" }, 422]]) {
    const gh = fakeGitHub(opts);
    assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "approve" })).status, code);
    assert.equal(gh.writes.length, 0);
  }
  assert.equal((await decision(fakeGitHub(), { pr: 360, sha: "short", decision: "approve" })).status, 400);
  assert.equal((await decision(fakeGitHub(), { pr: 360, sha: SHA, decision: "merge" })).status, 400);
});

test("sign-in: owner only, signed state, allow-listed return, session never contains a readable token", async () => {
  const login = await worker.fetch(new Request(W + "/auth/login?return=" + encodeURIComponent(PWA + "/arcana_table/control/")), ENV, {}, fakeGitHub().fetchImpl);
  assert.equal(login.status, 302);
  assert.equal(login.headers.get('Cache-Control'), 'no-store', 'GitHub login redirect must not be cached');
  const to = new URL(login.headers.get("Location"));
  assert.equal(to.origin + to.pathname, "https://github.com/login/oauth/authorize");
  assert.equal(to.searchParams.get("scope"), "public_repo");
  const state = to.searchParams.get("state");
  const bad = await worker.fetch(new Request(W + "/auth/login?return=" + encodeURIComponent("https://evil.example/")), ENV);
  assert.equal(bad.status, 400);
  const cb = await worker.fetch(new Request(W + `/auth/callback?code=c&state=${encodeURIComponent(state)}`), ENV, {}, fakeGitHub().fetchImpl);
  assert.equal(cb.status, 302);
  assert.equal(cb.headers.get('Cache-Control'), 'no-store', 'OAuth callback with encrypted session must not be cached');
  const back = cb.headers.get("Location");
  assert.ok(back.startsWith(PWA + "/arcana_table/control/#review-session="));
  const blob = decodeURIComponent(back.split("#review-session=")[1]);
  assert.ok(!blob.includes("gho_owner"));
  assert.equal((await openSession(SECRET, blob)).t, "gho_owner");
  const nonOwner = await worker.fetch(new Request(W + `/auth/callback?code=c&state=${encodeURIComponent(state)}`), ENV, {}, fakeGitHub({ user: "intruder" }).fetchImpl);
  assert.equal(nonOwner.status, 403);
  const forged = await worker.fetch(new Request(W + `/auth/callback?code=c&state=${encodeURIComponent(state.slice(0, -3) + "xyz")}`), ENV, {}, fakeGitHub().fetchImpl);
  assert.equal(forged.status, 400);
});

test("GET owner session checks authentication without writing or exposing OAuth tokens", async () => {
  const gh = fakeGitHub();
  const encrypted = await sealSession(SECRET, { t: "gho_owner", login: "AARMOU",
    exp: Math.floor(Date.now() / 1000) + 600 });
  const probe = (token, origin = PWA) => worker.fetch(new Request(W + "/v1/session", {
    headers: { Origin: origin, ...(token ? { Authorization: "Bearer " + token } : {}) }
  }), ENV, {}, gh.fetchImpl);
  const valid = await probe(encrypted);
  assert.equal(valid.status, 200);
  assert.equal(valid.headers.get("Cache-Control"), "no-store");
  assert.equal(valid.headers.get("Access-Control-Allow-Origin"), PWA);
  const body = await valid.json();
  assert.equal(body.ok, true);
  assert.equal(body.login, "AARMOU");
  assert.equal(typeof body.expiresAt, "number");
  assert.ok(!JSON.stringify(body).includes("gho_owner"));
  assert.equal((await probe(null)).status, 401);
  assert.equal((await probe("invalid")).status, 401);
  assert.equal((await probe(encrypted, "https://evil.example")).status, 403);
  const preflight = await worker.fetch(new Request(W + "/v1/session", {
    method: "OPTIONS", headers: { Origin: PWA, "Access-Control-Request-Method": "GET",
      "Access-Control-Request-Headers": "authorization" }
  }), ENV, {}, gh.fetchImpl);
  assert.equal(preflight.status, 204);
  assert.equal(preflight.headers.get("Access-Control-Allow-Headers"), "Authorization");
  assert.equal(preflight.headers.get("Access-Control-Allow-Methods"), "GET");
  assert.equal(preflight.headers.get("Cache-Control"), "no-store");
  assert.equal(gh.writes.length, 0, "Authentication status is read only");
});

test("health responds with CORS only for the Arcana Control origin", async () => {
  const allowed = await worker.fetch(new Request(W + "/health", { headers: { Origin: PWA } }), ENV);
  assert.equal(allowed.status, 200);
  assert.deepEqual(await allowed.json(), { ok: true, service: "arcana-review", protocol: 1 });
  assert.equal(allowed.headers.get("Access-Control-Allow-Origin"), PWA);
  assert.equal(allowed.headers.get("Vary"), "Origin");

  const denied = await worker.fetch(new Request(W + "/health", { headers: { Origin: "https://evil.example" } }), ENV);
  assert.equal(denied.status, 200);
  assert.equal(denied.headers.get("Access-Control-Allow-Origin"), null);

  const noOrigin = await worker.fetch(new Request(W + "/health"), ENV);
  assert.equal(noOrigin.headers.get("Access-Control-Allow-Origin"), null);
});

test("unconfigured Worker refuses everything but health", async () => {
  assert.equal((await worker.fetch(new Request(W + "/health"), {})).status, 200);
  assert.equal((await worker.fetch(new Request(W + "/v1/decision", { method: "POST" }), {})).status, 503);
  assert.equal(cleanNote("hi @x <!-- y -->"), "hi ＠x  y");
});
