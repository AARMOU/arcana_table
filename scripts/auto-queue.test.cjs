'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const A = require('./admission.cjs');
const Q = require('./queue-feeder.cjs');
const BASE = 'gpt/arcana-integration-next';
const SHA = '9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f';
const CARD = 'Blood for the Blood God!';
const BODY = [
  '## Cards', '- ' + CARD, '',
  '## Acceptance',
  '- Blood for the Blood God!: reduce generic cost for each creature that died this turn; discard your hand, draw eight and deal eight to each opponent; exile the spell',
  '- Exact current Oracle behavior (cost, types, every ability, targeting and timing), checked against authoritative card records',
  '- Executable regression scenarios for positive and negative behavior, registration and deterministic asset export',
  'Base: ' + BASE, '',
].join('\n');
const title = '[Arcana Queue] ' + CARD + ' — Cardkit SUPPORTED, unclaimed';
const payload = () => Q.queuePayload({
  result: { ok: true, available: 12, alreadyQueued: [] },
  units: [{ name: CARD, body: BODY }], base: BASE, baseSha: SHA, runId: 555,
});

function fakeGitHub({ open = [], head = SHA, issuer = { login: 'github-actions[bot]', type: 'Bot' } } = {}) {
  const calls = [];
  const github = {
    rest: {
      repos: { getBranch: async args => { calls.push(['branch', args]); return { data: { commit: { sha: head } } }; } },
      issues: {
        listForRepo: () => {},
        create: async args => { calls.push(['create', args]); return { data: { number: 777, user: issuer, html_url: 'https://github.com/AARMOU/arcana_table/issues/777' } }; },
      },
    },
    paginate: async (fn, args) => { calls.push(['list', args]); return open; },
  };
  return { github, calls };
}
const run = async (opts = {}, p = payload()) => {
  const { github, calls } = fakeGitHub(opts);
  const result = await Q.createNextAutoQueue({ github, owner: 'AARMOU', repo: 'arcana_table', payload: p, runId: 123456, enabled: true });
  return { result, calls };
};

test('unit generator produces only specific, actionable complete card entries', () => {
  const p = payload();
  assert.equal(p.suggestions.length, 1);
  assert.equal(p.suggestions[0].ready, true);
  assert.equal(A.unitActionable(p.suggestions[0].body, BASE).ok, true);
  assert.equal(Q.chooseAutoQueue(p, []).suggestion.name, CARD);
});
test('invalid, incomplete, stale or duplicate candidates cannot be enqueued', async () => {
  for (const bad of [
    { ...payload(), ok: false },
    { ...payload(), baseSha: 'malformed' },
    { ...payload(), suggestions: [{ ...payload().suggestions[0], body: 'TBD', ready: true }] },
  ]) {
    const a = Q.chooseAutoQueue(bad, []);
    assert.equal(!!a.suggestion, false);
  }
  const duplicate = [{ title, body: BODY, assignees: [], author_association: 'OWNER' }];
  assert.equal(Q.chooseAutoQueue(payload(), duplicate).pendingIssue, undefined);
  // A pending actionable queue unit must be known by number to wake dispatch.
  duplicate[0].number = 222;
  assert.equal(Q.chooseAutoQueue(payload(), duplicate).pendingIssue, 222);
  assert.equal((await run({ head: 'a'.repeat(40) })).result.reason, 'integration-base-moved');
});
test('disabled auto mode never calls GitHub or queues anything', async () => {
  const { github, calls } = fakeGitHub();
  const result = await Q.createNextAutoQueue({ github, owner: 'AARMOU', repo: 'arcana_table', payload: payload(), runId: 1, enabled: false });
  assert.equal(result.created, undefined);
  assert.equal(calls.length, 0);
});
test('one trusted bot issue is created with immutable provenance and exact unit requirements', async () => {
  const { result, calls } = await run();
  assert.equal(result.created, true); assert.equal(result.issue, 777);
  assert.deepEqual(calls.map(([kind]) => kind), ['branch', 'list', 'create']);
  const body = calls[2][1].body;
  assert.equal(calls[2][1].title, title);
  assert.ok(body.startsWith(A.AUTO_QUEUE_MARKER + '\n'));
  assert.match(body, /run:123456; base-sha:9dd5fc9/);
  assert.equal(A.unitActionable(body, BASE).ok, true);
  assert.equal(A.provenance({ authorAssociation: 'NONE', authorLogin: 'github-actions[bot]',
    authorType: 'Bot', body, title, autoQueueUnedited: true, commenters: [] }).ok, true);
});
test('existing complete unassigned issue is woken without opening another issue', async () => {
  const existing = [{ number: 99, title: '[Arcana Queue] Another Card', body: BODY, assignees: [], author_association: 'OWNER' }];
  const { result, calls } = await run({ open: existing });
  assert.deepEqual([result.created, result.pending, result.issue], [undefined, true, 99]);
  assert.equal(calls.some(([kind]) => kind === 'create'), false);
});
test('untrusted public ready-looking issues cannot monopolize the pending queue slot', () => {
  const p = payload();
  const outsider = [{ number: 99, title: '[Arcana Queue] Fake Card', body: BODY, assignees: [], author_association: 'NONE',
    user: { login: 'stranger', type: 'User' } }];
  assert.equal(Q.chooseAutoQueue(p, outsider).suggestion.name, CARD);
  const gaps = { dispatch: { claimsVerified: true, dispatchable: true, copilot: [CARD] } };
  assert.deepEqual(Q.candidates({ gaps, openIssues: outsider }).names, [CARD]);
});
test('bot provenance rejects missing marker, wrong issuer, edits and untrusted comments', () => {
  const base = { authorAssociation: 'NONE', authorLogin: 'github-actions[bot]', authorType: 'Bot',
    body: A.AUTO_QUEUE_MARKER + '\n' + BODY, title, autoQueueUnedited: true, commenters: [] };
  assert.equal(A.provenance(base).ok, true);
  for (const spoof of [
    { authorLogin: 'attacker' }, { authorType: 'User' },
    { body: BODY }, { title: 'Unrelated issue' }, { autoQueueUnedited: false },
    { commenters: [{ association: 'NONE', type: 'User', login: 'stranger' }] },
  ]) assert.equal(A.provenance({ ...base, ...spoof }).ok, false, JSON.stringify(spoof));
  // Trusted human-authored issues keep their original behavior.
  assert.equal(A.provenance({ ...base, authorAssociation: 'OWNER', body: BODY, autoQueueUnedited: false }).ok, true);
});
test('unexpected token issuer halts, never silently assumes issue provenance', async () => {
  await assert.rejects(() => run({ issuer: { login: 'other-app[bot]', type: 'Bot' } }), /issuer-not-github-actions/);
});
