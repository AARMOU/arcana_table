'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const I = require('./idle-suites.cjs');
const P = require('./policy.cjs');
const A = require('../scripts/admission.cjs');
const D = require('../scripts/dispatch-runner.cjs');
const { mergeBlockers, PREMERGE } = require('./suite-automerge.cjs');

const manifest = P.readManifest(__dirname + '/suites.json');
const NOW = Date.parse('2026-11-01T15:00:00Z');
const status = (extra = {}) => ({ state: 'nothing-eligible', checkedAt: new Date(NOW - 10 * 60e3).toISOString(), billing: { level: 'normal' }, ...extra });
const cards = [{ name: 'Zeta Card', ident: 'ZetaCard', decks: 0 }, { name: 'Alpha Card', ident: 'AlphaCard', decks: 0 },
  { name: 'Deck Card', ident: 'DeckCard', decks: 2 }, { name: manifest.suites[0].name, ident: 'Certified', decks: 5 }];
const idleIssue = (key, extra = {}) => ({ number: 900, state: 'closed', assignees: [{ login: 'Copilot' }], created_at: new Date(NOW - 30 * 3600e3).toISOString(),
  title: A.SUITE_TITLE + key, body: `${A.SUITE_MARKER}\n${I.idleMarker(key)}\n`, ...extra });

test('played custom cards: Argentum-adopted cards are skipped unless overridden; deck use counted; registry identifiers kept', () => {
  const r = I.playedCustomCards({
    registry: 'Entry(UrzasSaga, "MH2", ARTIFICER),\nEntry(SifterOfSkulls, "OGW", "x"),\nEntry(BlackMarketConnections, "MSC", "x"),',
    customIndex: "Urza's Saga\tcard\nSifter of Skulls\tcard\nBlack Market Connections\tcard\n",
    libraryIndex: 'Sifter of Skulls\tcard\nBlack Market Connections\tcard\n',
    overrides: '# header\nSifter of Skulls\n',
    deckLists: ["1 Urza's Saga\n1 Sol Ring", "1x Urza's Saga"] });
  assert.deepEqual(r, [{ name: "Urza's Saga", ident: 'UrzasSaga', decks: 2 }, { name: 'Sifter of Skulls', ident: 'SifterOfSkulls', decks: 0 }]);
});

test('plan: only when the dispatcher is idle, fresh and billing is normal; deck cards first; certified cards never', () => {
  const p = I.plan({ cards, manifest, requests: [], status: status(), now: NOW });
  assert.deepEqual(p.candidates.map(c => c.name), ['Deck Card', 'Alpha Card', 'Zeta Card']);
  assert.match(I.plan({ cards, manifest, requests: [], status: status({ state: 'dispatched' }), now: NOW }).skip, /eligible work/);
  assert.match(I.plan({ cards, manifest, requests: [], status: status({ checkedAt: new Date(NOW - 3 * 3600e3).toISOString() }), now: NOW }).skip, /stale/);
  assert.match(I.plan({ cards, manifest, requests: [], status: status({ billing: { level: 'slow' } }), now: NOW }).skip, /billing/);
  assert.match(I.plan({ cards, manifest, requests: [], status: null, now: NOW }).skip, /no status/);
});

test('plan: one waiting request at a time, a rolling cap of 6 a day, never the same card twice', () => {
  const waiting = idleIssue('alpha-card', { state: 'open', assignees: [] });
  assert.match(I.plan({ cards, manifest, requests: [waiting], status: status(), now: NOW }).skip, /still waiting/);
  const recent = Array.from({ length: I.DAILY_CAP }, (_, i) => idleIssue('x' + i, { created_at: new Date(NOW - i * 3600e3).toISOString() }));
  assert.match(I.plan({ cards, manifest, requests: recent, status: status(), now: NOW }).skip, /daily cap reached \(6\/6\)/);
  const older = recent.map((r, i) => ({ ...r, created_at: new Date(NOW - (25 + i) * 3600e3).toISOString() }));
  assert.ok(I.plan({ cards, manifest, requests: older, status: status(), now: NOW }).candidates, 'yesterday\'s requests do not count');
  const done = I.plan({ cards, manifest, requests: [idleIssue('deck-card')], status: status(), now: NOW });
  assert.deepEqual(done.candidates.map(c => c.name), ['Alpha Card', 'Zeta Card'], 'a closed request is not reopened');
});

test('the request is a valid integrated suite unit, ranked after every other kind of work', () => {
  const req = I.idleRequest({ card: { name: "Urza's Saga", ident: 'UrzasSaga' }, oracleLine: '{"name":"Urza\'s Saga","oracle_text":"I —"}', target: 'gpt/b141-candidate-20261101' });
  assert.equal(req.key, 'urzas-saga');
  assert.deepEqual(A.suiteActionable(req.body), { ok: true, missing: [] });
  assert.equal(A.isIntegratedSuite(req.body), true);
  assert.match(req.body, /"target":"build"/);
  assert.match(req.body, /com\.arcanatable\.customcards\.cards\.UrzasSaga/);
  assert.equal(req.body.includes('@'), false, 'mentions nobody');
  const ordinary = A.SUITE_MARKER + '\nCard unit: #5\nCard PR: #6\nSuite key: a-b\nBase: main\n    {"oracle_text":"x"}';
  assert.equal(A.suiteActionable(ordinary).ok, true);
  assert.equal(A.isIntegratedSuite(ordinary), false);
  const half = req.body.replace('Card unit: integrated', 'Card unit: #5');
  assert.deepEqual(A.suiteActionable(half).missing, ['card-unit']);

  const ok = () => ({ ok: true });
  const unit = (n, title, body) => ({ raw: { number: n, title, body, labels: [] }, derived: { state: 'queued', provenance: { ok: true } }, prs: [] });
  const idle = unit(1, A.SUITE_TITLE + "Urza's Saga", req.body);
  const suite = unit(50, A.SUITE_TITLE + 'Other', ordinary);
  const card = unit(60, '[Arcana Queue] 60', 'valid');
  assert.equal(D.selectCandidate([idle, card], 'normal', 'base', ok, ok).candidate.raw.number, 60, 'card work first');
  assert.equal(D.selectCandidate([idle, suite], 'normal', 'base', ok, ok).candidate.raw.number, 50, 'card-PR suites first');
  assert.equal(D.selectCandidate([idle], 'normal', 'base', ok, ok).candidate.raw.number, 1, 'idle suite fills spare capacity');
});

test('auto-merge: an integrated suite must register its own request issue and target build', () => {
  const SHA = 'b'.repeat(40), REPO = 'AARMOU/arcana_table', TEST = 'certification/UrzasSagaIndependentCertificationTest.kt';
  const req = I.idleRequest({ card: { name: "Urza's Saga", ident: 'UrzasSaga' }, oracleLine: '{"oracle_text":"x"}', target: 'gpt/b141-candidate-20261101' });
  const g = () => ({
    run: { id: 9, name: PREMERGE, event: 'pull_request', conclusion: 'success', head_sha: SHA, head_repository: { full_name: REPO } },
    pr: { number: 950, state: 'open', user: { login: 'Copilot' }, base: { ref: 'main', repo: { full_name: REPO } }, head: { sha: SHA, repo: { full_name: REPO } } },
    request: { number: 940, state: 'open', title: req.title, body: req.body, user: { login: 'github-actions[bot]' } },
    files: [{ filename: 'certification/suites/urzas-saga.json', status: 'added' }, { filename: TEST, status: 'added' }],
    jobs: [{ name: 'Execute trusted independent card scenarios (urzas-saga, 0, 940, integrated card on the build branch, build)', conclusion: 'success' }],
    suite: { key: 'urzas-saga', issue: 940, source: TEST, target: 'build' },
  });
  assert.deepEqual(mergeBlockers(g()), []);
  for (const mutate of [x => { x.suite.issue = 1; }, x => { delete x.suite.target; }, x => { x.suite.target = 'card'; }]) {
    const x = g(); mutate(x); assert.ok(mergeBlockers(x).includes('suite-registration-mismatch'));
  }
});

test('oracle: exact name from the build branch cache first, then Scryfall; never a different card', async () => {
  const cache = '{"name":"All Is Dust","mana_cost":"{7}","type_line":"Kindred Sorcery — Eldrazi","oracle_text":"Each player sacrifices…","rarity":"mythic"}\n' +
    '{"name":"All Is Dust Too","oracle_text":"nope"}';
  const fromCache = JSON.parse(await I.oracleFor('All Is Dust', { cache, fetchImpl: async () => { throw Error('no network needed'); } }));
  assert.equal(fromCache.oracle_text, 'Each player sacrifices…'); assert.equal(fromCache.rarity, undefined);
  const mdfc = { name: 'Two Faces', layout: 'transform', card_faces: [{ name: 'Front', type_line: 'Creature', oracle_text: 'A' }, { name: 'Back', type_line: 'Creature', oracle_text: 'B' }] };
  const fetched = JSON.parse(await I.oracleFor('Two Faces', { fetchImpl: async () => ({ ok: true, json: async () => mdfc }) }));
  assert.equal(fetched.oracle_text, 'Front: A\n//\nBack: B');
  assert.equal(await I.oracleFor('Other', { fetchImpl: async () => ({ ok: true, json: async () => ({ name: 'Different', oracle_text: 'x' }) }) }), null);
});

test('burst: up to `parallel` open at once, ignores other eligible work, keeps billing guard, own cap, expires', () => {
  const burst = { until: new Date(NOW + 3600e3).toISOString(), parallel: 3, cap: 40 };
  const busy = status({ state: 'attempted' });
  const p = I.plan({ cards, manifest, requests: [], status: busy, now: NOW, burst });
  assert.equal(p.slots, 3);
  assert.equal(p.burst, true);
  const open = k => idleIssue(k, { state: 'open', assignees: [{ login: 'Copilot' }], created_at: new Date(NOW - 60e3).toISOString() });
  assert.equal(I.plan({ cards, manifest, requests: [open('a'), open('b')], status: busy, now: NOW, burst }).slots, 1);
  assert.match(I.plan({ cards, manifest, requests: [open('a'), open('b'), open('c')], status: busy, now: NOW, burst }).skip, /3\/3 idle suites already open/);
  assert.match(I.plan({ cards, manifest, requests: [], status: status({ state: 'attempted', billing: { level: 'slow' } }), now: NOW, burst }).skip, /billing/);
  const many = Array.from({ length: 39 }, (_, i) => idleIssue('m' + i, { created_at: new Date(NOW - 3600e3).toISOString() }));
  assert.equal(I.plan({ cards, manifest, requests: many, status: busy, now: NOW, burst }).slots, 1, 'cap limits slots');
  assert.match(I.plan({ cards, manifest, requests: [...many, idleIssue('m39', { created_at: new Date(NOW - 3600e3).toISOString() })], status: busy, now: NOW, burst }).skip, /daily cap reached \(40\/40\)/);
  const expired = { ...burst, until: new Date(NOW - 1).toISOString() };
  assert.match(I.plan({ cards, manifest, requests: [], status: busy, now: NOW, burst: expired }).skip, /eligible work/);
  assert.equal(I.activeBurst({ until: burst.until, parallel: 9, cap: 999 }, NOW).parallel, 3, 'never more than the 3-worker ceiling');
  assert.equal(I.activeBurst({ until: burst.until, parallel: 9, cap: 999 }, NOW).cap, 60);
});

test('normal mode is unchanged without a burst', () => {
  assert.equal(I.plan({ cards, manifest, requests: [], status: status(), now: NOW }).slots, 1);
});
