'use strict';
// Owner decision 2026-10-30: owner and app card requests are prioritized, and blocked ones are started
// automatically (an engineer may claim first; otherwise a review-only Copilot session), not by one agent.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const A = require('./admission.cjs');
const Q = require('./queue-feeder.cjs');
const D = require('./dispatch-runner.cjs');
const { claimedAtOf } = require('./units.cjs');

const SHA = 'a'.repeat(40);
const ORACLE = JSON.stringify({ name: 'Knuckles the Echidna', type_line: 'Legendary Creature — Echidna', oracle_text: 'Double strike',
  scryfall_uri: 'https://scryfall.com/card/sld/2082/knuckles' });
const workRequest = (extra = {}) => ({ name: 'Knuckles the Echidna', issue: 350, source: 'owner', requests: 1, outcome: 'engine-work',
  readiness: 'NEEDS_REVIEW', route: 'claude', cardkitReasons: ['1 line(s) without a close implemented match (best 0.77)'], oracle: ORACLE, ...extra });
const body = r => Q.cardWorkBody(r, { runId: 7, baseSha: SHA, base: 'gpt/arcana-integration-next' });
const HOUR = 3600e3;
const review = (n, b, extra = {}) => ({
  raw: { number: n, title: Q.cardWorkTitle(workRequest()), body: b, labels: [], createdAt: Date.now() - 3 * HOUR, claimedAt: null, ...extra },
  derived: { state: 'queued', provenance: { ok: true } }, prs: [] });
const card = n => ({ raw: { number: n, title: '[Arcana Queue] ' + n, body: 'valid', labels: [] }, derived: { state: 'queued', provenance: { ok: true } }, prs: [] });
const ok = () => ({ ok: true });

test('card-work body carries the trusted Oracle record and priority, and is review-actionable', () => {
  const b = body(workRequest());
  assert.equal(A.CARD_WORK_MARKER, Q.CARD_WORK_MARKER);
  assert.equal(A.CARD_WORK_TITLE, Q.CARD_WORK_PREFIX);
  assert.match(b, /^ {4}\{.*"oracle_text"/m);
  assert.match(b, /priority:1001/);
  assert.deepEqual(A.reviewActionable(b), { ok: true, missing: [] });
  // No Oracle record (or a mismatched one): never auto-started.
  assert.ok(A.reviewActionable(body(workRequest({ oracle: null }))).missing.includes('oracle-record'));
  assert.ok(A.reviewActionable(body(workRequest({ oracle: ORACLE.replace('Knuckles the Echidna', 'Other Card') }))).missing.includes('oracle-record'));
});

test('trusted only when the feeder bot wrote it unedited', () => {
  const raw = { title: Q.cardWorkTitle(workRequest()), body: body(workRequest()), authorLogin: 'github-actions[bot]', authorType: 'Bot',
    authorAssociation: 'NONE', autoQueueUnedited: true, commenters: [] };
  assert.equal(A.provenance(raw).ok, true);
  assert.equal(A.provenance({ ...raw, autoQueueUnedited: false }).ok, false);
  assert.equal(A.provenance({ ...raw, authorLogin: 'someone', authorType: 'User' }).ok, false);
});

test('engineers get first pick: grace period and a fresh CLAIM hold the automatic start', () => {
  const b = body(workRequest());
  assert.equal(D.verdictOf(review(1, b), 'base', ok, ok).cls, 'eligible');
  assert.equal(D.verdictOf(review(1, b, { createdAt: Date.now() - 30 * 60e3 }), 'base', ok, ok).cls, 'waiting');
  assert.equal(D.verdictOf(review(1, b, { claimedAt: Date.now() - HOUR }), 'base', ok, ok).cls, 'engineer-claimed');
  assert.equal(D.verdictOf(review(1, b, { claimedAt: Date.now() - 30 * HOUR }), 'base', ok, ok).cls, 'eligible', 'a stale claim expires');
  const linked = review(1, b); linked.prs = [{ number: 9 }];
  assert.equal(D.verdictOf(linked, 'base', ok, ok).cls, 'claimed');
});

test('selection: suites, then card builds, then reviews by priority (owner before app votes)', () => {
  const owner = review(10, body(workRequest()));
  const app = review(5, body(workRequest({ source: 'app', votes: 40, issue: 321 })));
  assert.equal(D.selectCandidate([app, owner], 'normal', 'base', ok, ok).candidate.raw.number, 10);
  assert.equal(D.selectCandidate([app, owner, card(99)], 'normal', 'base', ok, ok).candidate.raw.number, 99);
});

test('claim detection: only trusted people, comment must start with CLAIM', () => {
  const at = '2026-10-30T12:00:00Z';
  const c = (body, assoc = 'OWNER', type = 'User') => ({ body, author_association: assoc, user: { type }, created_at: at });
  assert.equal(claimedAtOf([c('CLAIM — GPT Work')]), Date.parse(at));
  assert.equal(claimedAtOf([c('**CLAIM** Goose')]), Date.parse(at));
  assert.equal(claimedAtOf([c('I will not claim this')]), null);
  assert.equal(claimedAtOf([c('CLAIM', 'NONE')]), null);
  assert.equal(claimedAtOf([c('CLAIM', 'OWNER', 'Bot')]), null);
});

test('legacy card-work items without an Oracle record are superseded so they can be reopened', () => {
  const legacy = { number: 369, state: 'open', title: '[Card Work] Knuckles the Echidna — needs engine work',
    body: Q.CARD_WORK_MARKER + '\nold body', user: { login: 'github-actions[bot]', type: 'Bot' } };
  const payload = { ok: true, requestedClassified: true, resolvedRequests: [], workRequests: [workRequest()] };
  const plan = Q.planCardWork(payload, [legacy]);
  assert.deepEqual(plan.supersede, [{ issue: 369, name: 'Knuckles the Echidna' }]);
  assert.equal(plan.create, null, 'reopened on the next pass, once the old item is renamed');
  const renamed = { ...legacy, state: 'closed', title: Q.SUPERSEDED_PREFIX + 'Knuckles the Echidna' };
  assert.equal(Q.planCardWork(payload, [renamed]).create.name, 'Knuckles the Echidna');
  const current = { ...legacy, body: body(workRequest()) };
  assert.deepEqual(Q.planCardWork(payload, [current]).supersede, [], 'an item with a record stays');
});
