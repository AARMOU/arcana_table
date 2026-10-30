'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const S = require('./suite-requests.cjs');
const A = require('../scripts/admission.cjs');

const RECORD = '{"name":"Shield Sphere","mana_cost":"{0}","oracle_text":"Defender\\nWhenever this creature blocks, put a -0/-1 counter on it."}';
const UNIT = ['## Cards', '', '- Shield Sphere', '', '## Acceptance', '', '- x', '', 'Base: gpt/arcana-integration-next', '',
  '## Offline Oracle record (trusted feeder snapshot)', '', '    ' + RECORD, ''].join('\n');
const entry = (over = {}) => { const m = { pr: 518, issue: 509, title: 'Add Shield Sphere custom-card behavior', ...over };
  Object.defineProperty(m, "unitBody", { value: over.unitBody ?? UNIT }); return m; };
function gh(existing = []) {
  const created = [];
  const github = { paginate: async fn => fn(), rest: { issues: { listForRepo: () => existing,
    create: async a => { const d = { number: 700 + created.length, state: 'open', ...a }; created.push(d); return { data: d }; } } } };
  return { github, created };
}

test('names, keys and the single-card / Oracle extraction', () => {
  assert.equal(S.suiteKey('Shield Sphere'), 'shield-sphere');
  assert.equal(S.pascal("Whisperer of the Wilds"), 'WhispererOfTheWilds');
  assert.equal(S.suiteKey('Lim-Dûl’s Vault'), 'lim-duls-vault');
  assert.equal(S.cardOf(UNIT), 'Shield Sphere');
  assert.equal(S.cardOf('## Cards\n- A\n- B\n'), null);
  assert.equal(S.oracleRecord(UNIT, 'Shield Sphere').record.mana_cost, '{0}');
  assert.equal(S.oracleRecord(UNIT, 'Other Card'), null);
});

test('the request body is actionable for the dispatcher and trusted only as an unedited bot issue', () => {
  const r = S.suiteRequest({ pr: 518, issue: 509, name: 'Shield Sphere', oracle: S.oracleRecord(UNIT, 'Shield Sphere') });
  assert.deepEqual(A.suiteActionable(r.body), { ok: true, missing: [] });
  assert.ok(r.title.startsWith(A.SUITE_TITLE));
  assert.match(r.body, /certification\/ShieldSphereIndependentCertificationTest\.kt/);
  assert.match(r.body, /certification\/suites\/shield-sphere\.json/);
  assert.match(r.body, /Do \*\*not\*\* open, fetch, diff or read card PR #518/);
  const raw = { authorLogin: 'github-actions[bot]', authorType: 'Bot', authorAssociation: 'NONE', title: r.title, body: r.body, commenters: [] };
  assert.equal(A.provenance({ ...raw, autoQueueUnedited: true }).ok, true);
  assert.equal(A.provenance({ ...raw, autoQueueUnedited: false }).ok, false);
  assert.equal(A.provenance({ ...raw, autoQueueUnedited: true, title: '[Arcana Other] x' }).ok, false);
  assert.equal(A.suiteActionable(r.body.replace('Base: main', 'Base: gpt/arcana-integration-next')).ok, false);
});

test('opens one request per card, idempotently, and skips engine gaps and inexact units', async () => {
  const { github, created } = gh();
  const list = [entry(), entry({ pr: 519, issue: 510, engineGap: true }), entry({ pr: 520, issue: 511, unitBody: '## Cards\n- A\n- B\n' }),
    entry({ pr: 521, issue: 512, unitBody: '## Cards\n- Copper Gnomes\n' })];
  await S.requestSuites({ github, owner: 'o', repo: 'r', list });
  assert.equal(created.length, 1);
  assert.deepEqual(list[0].request, { issue: 700, state: 'open', created: true });
  assert.equal(list[1].request, undefined);
  assert.match(list[2].request.skipped, /exactly one card/);
  assert.match(list[3].request.skipped, /no Oracle record/);
  const again = gh([{ ...created[0], user: { login: 'github-actions[bot]' } }]);
  const l2 = [entry()];
  await S.requestSuites({ github: again.github, owner: 'o', repo: 'r', list: l2 });
  assert.equal(again.created.length, 0);
  assert.equal(l2[0].request.issue, 700);
});

test('a closed request is never reopened or duplicated', async () => {
  const r = S.suiteRequest({ pr: 518, issue: 509, name: 'Shield Sphere', oracle: { line: RECORD } });
  const { github, created } = gh([{ number: 650, state: 'closed', title: r.title, body: r.body }]);
  const list = [entry()];
  await S.requestSuites({ github, owner: 'o', repo: 'r', list });
  assert.equal(created.length, 0);
  assert.deepEqual(list[0].request, { issue: 650, state: 'closed' });
});
