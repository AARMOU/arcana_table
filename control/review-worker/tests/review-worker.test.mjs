import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { sealSession, openSession, decide, prReadFailure, cleanNote, FIX_LABEL, DECISION_MARKER } from "../src/index.mjs";

const SECRET = "s".repeat(40);
const ENV = { GITHUB_OAUTH_CLIENT_ID: "cid", GITHUB_OAUTH_CLIENT_SECRET: "csecret", SESSION_SECRET: SECRET };
const SHA = "a".repeat(40);
const PWA = "https://aarmou.github.io";
const W = "https://arcana-review.example.workers.dev";

// Minimal fake GitHub keeping state, recording every write.
const CERTIFIED = { name: "Arcana independent certification", status: "completed", conclusion: "success", head_sha: SHA,
  app: {slug:'github-actions',id:15368},
  output: {title:'Resurrection Orb: independent rules scenarios passed',
    summary:'4 trusted-main JUnit scenarios passed against the exact current Copilot PR commit'} };
function fakeGitHub({ head = SHA, checks = [{ name: "toolkit", status: "completed", conclusion: "success" }, CERTIFIED], reviews = [],
  author = "Copilot", prState = "open", draft = false,
  files = ["android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Celestine.kt",
    "android/custom-cards/src/test/kotlin/com/arcanatable/customcards/CelestineScenarioTest.kt"],
  unitTitle = "[Arcana Queue] Celestine — Cardkit SUPPORTED, unclaimed", user = "AARMOU",
  prError = null } = {}) {
  const writes = [];
  const fetchImpl = async (url, init = {}) => {
    const u = String(url); const method = init.method || "GET";
    const ok = (body, status = 200) => new Response(body === null ? null : JSON.stringify(body), { status });
    if (u === "https://github.com/login/oauth/access_token") return ok({ access_token: "gho_owner" });
    if (u === "https://api.github.com/user") return ok({ login: user });
    if (u.endsWith("/pulls/360") && method === "GET" && prError) return new Response(
      JSON.stringify({message:prError.message||"GitHub rejected the request"}),
      {status:prError.status,headers:prError.headers||{}});
    if (u.endsWith("/pulls/360") && method === "GET") return ok({ state: prState, draft, user: { login: author }, head: { sha: head }, body: "- Fixes #358" });
    if (u.includes("/pulls/360/files?") && method === "GET") return ok(files.map(filename => ({ filename })));
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

test("approval requires independent certification of the exact head; fix requests stay available", async () => {
  const toolkit = { name: "toolkit", status: "completed", conclusion: "success" };
  const cases = [
    [[toolkit], "not-run"],                                                             // green checks only (PR #344 case)
    [[toolkit, { ...CERTIFIED, status: "in_progress", conclusion: null }], "running"],
    [[toolkit, { ...CERTIFIED, conclusion: "failure" }], "failed"],
    [[toolkit, { ...CERTIFIED, head_sha: "c".repeat(40) }], "not-run"],               // stale: certified an older commit
  ];
  for (const [checks, certification] of cases) {
    const gh = fakeGitHub({ checks });
    const r = await decision(gh, { pr: 360, sha: SHA, decision: "approve" });
    assert.ok(r.status === 422 && ["not-certified", "checks-not-green"].includes(r.body.error), certification);
    if (r.body.error === "not-certified") assert.equal(r.body.certification, certification);
    assert.equal(gh.writes.length, 0, certification);
    assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "request-fix", note: "needs rules check" })).status, 200, certification);
  }
  const ok = await decision(fakeGitHub({ checks: [toolkit, CERTIFIED] }), { pr: 360, sha: SHA, decision: "approve" });
  assert.equal(ok.status, 200);
});

test("a draft, audit, engine blocker, or card without executable tests cannot be approved", async () => {
  const cases = [
    { draft: true },
    { files: [] },
    { files: ["tools/cardkit/data/known-blockers.json"] },
    { files: ["android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Celestine.kt"] },
    { files: Array(100).fill("android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Celestine.kt") }
  ];
  for (const opts of cases) {
    const gh = fakeGitHub(opts);
    const response = await decision(gh, { pr: 360, sha: SHA, decision: "approve" });
    assert.equal(response.status, 422, JSON.stringify(opts).slice(0, 120));
    assert.ok(["draft-pr", "not-a-verified-card-implementation"].includes(response.body.error));
    assert.equal(gh.writes.length, 0);
  }
  const gh = fakeGitHub({ files: [] });
  assert.equal((await decision(gh, { pr: 360, sha: SHA, decision: "request-fix", note: "This is a blocker, not a card" })).status, 200);
});

test("fake certificate names/apps cannot cause owner approval, even with green CI", async () => {
  const toolkit={name:'toolkit',status:'completed',conclusion:'success'};
  const invalid=[
    {...CERTIFIED,name:'totally-independent-certification'},
    {...CERTIFIED,app:{slug:'github-actions',id:111}},
    {...CERTIFIED,app:{slug:'other',id:15368}},
    {...CERTIFIED,output:{title:'Fake independent cert',summary:'fake tests'}},
    {...CERTIFIED,head_sha:'b'.repeat(40)}
  ];
  for(const cert of invalid){
    const gh=fakeGitHub({checks:[toolkit,cert]});
    const r=await decision(gh,{pr:360,sha:SHA,decision:'approve'});
    assert.deepEqual([r.status,r.body.error],[422,'not-certified']);
    assert.equal(gh.writes.length,0);
  }
  const legitimate=fakeGitHub({checks:[toolkit,CERTIFIED]});
  assert.equal((await decision(legitimate,{pr:360,sha:SHA,decision:'approve'})).status,200);
});

test("GitHub PR-read error categorization preserves status but never exposes owner credential or upstream response", async () => {
  const cases = [
    [{ status:401, message:"Bad credentials" },401,"github-credential-rejected"],
    [{ status:403, message:"API rate limit exceeded" },503,"github-rate-limited"],
    [{ status:403, message:"Access forbidden", headers:{"x-ratelimit-remaining":"0"} },503,"github-rate-limited"],
    [{ status:403, message:"Resource not accessible by integration" },403,"github-access-denied"],
    [{ status:429, message:"Rate quota",headers:{"retry-after":"60"} },503,"github-rate-limited"],
    [{ status:404, message:"Not Found" },404,"github-pr-not-accessible"],
    [{ status:502, message:"upstream internal failure" },502,"github-pr-read-failed"]
  ];
  for(const [prError,code,error] of cases){
    const gh=fakeGitHub({prError});
    const r=await decision(gh,{pr:360,sha:SHA,decision:"approve"});
    assert.equal(r.status,code,JSON.stringify(prError));
    assert.equal(r.body.error,error,JSON.stringify(prError));
    assert.equal(gh.writes.length,0,"a failed first read never submits a review");
    assert.ok(!JSON.stringify(r.body).includes(prError.message),"no upstream details escape");
  }
  assert.deepEqual(prReadFailure({status:403,rateLimited:false}),[403,{error:"github-access-denied",upstreamStatus:403}]);
  assert.deepEqual(prReadFailure({status:503}),[502,{error:"github-pr-read-failed",upstreamStatus:503}]);
});
