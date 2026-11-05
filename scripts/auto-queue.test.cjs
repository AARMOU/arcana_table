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
const BOT = { login: 'github-actions[bot]', type: 'Bot' };
const payload = () => Q.queuePayload({
  result: { ok: true, available: 12, alreadyQueued: [] },
  units: [{ name: CARD, body: BODY }], base: BASE, baseSha: SHA, runId: 555,
});

function fakeGitHub({ open = [], closed = [], comments = {}, events = {}, head = SHA, issuer = BOT } = {}) {
  const calls = [];
  const github = {
    rest: {
      repos: { getBranch: async args => { calls.push(['branch', args]); return { data: { commit: { sha: head } } }; } },
      issues: {
        listForRepo: () => {},
        listComments: () => {},
        listEventsForTimeline: () => {},
        removeLabel: async ({ issue_number, name }) => {
          const issue = closed.find(i => i.number === issue_number);
          if (issue) issue.labels = (issue.labels || []).filter(l => (typeof l === 'string' ? l : l.name) !== name);
        },
        create: async args => { calls.push(['create', args]); return { data: { number: 777, user: issuer, html_url: 'https://github.com/AARMOU/arcana_table/issues/777' } }; },
      },
    },
    paginate: async (fn, args) => {
      calls.push([fn === github.rest.issues.listComments ? 'comments' : 'list', args]);
      if (fn === github.rest.issues.listComments) return comments[args.issue_number] || [];
      if (fn === github.rest.issues.listEventsForTimeline) return events[args.issue_number] || [];
      return args.state === 'closed' ? closed : open;
    },
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
  assert.deepEqual(calls.map(([kind]) => kind), ['branch', 'list', 'list', 'create']);
  const body = calls[3][1].body;
  assert.equal(calls[3][1].title, title);
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

const queueIssue = (number, name = CARD, labels = []) => ({ number, state: 'closed', labels, user: BOT,
  author_association: 'NONE', title: '[Arcana Queue] ' + name, body: A.AUTO_QUEUE_MARKER + '\n' + BODY.replaceAll(CARD, name) });
const ledgerComment = (issue, fields) => ({ user: BOT, body: A.formatLedger({ issue, at: '2026-11-04T10:00:00Z', ...fields }) });
const paidPair = (issue, n) => [
  ledgerComment(issue, { op: 'authorizing', run: n }),
  ledgerComment(issue, { op: 'attempt-result', run: n, result: 'confirmed' }),
  ledgerComment(issue, { op: 'repair-request', run: n + 1000 }),
];

test('B15 counts assignment plus repair across re-queues and hands off at the card-level cap', async () => {
  const firstQueue = queueIssue(901);
  const { result } = await run({ closed: [firstQueue], comments: { 901: paidPair(901, 1) } });
  assert.equal(result.created, undefined);
  assert.deepEqual(result.held, [{ name: CARD, issue: 901 }]);
});

test('B15 counts first assignments on two queue issues as two paid attempts', async () => {
  const a = queueIssue(901), b = queueIssue(902);
  const { result } = await run({ closed: [a, b], comments: {
    901: [ledgerComment(901, { op: 'authorizing', run: 1 }), ledgerComment(901, { op: 'attempt-result', run: 1, result: 'confirmed' })],
    902: [ledgerComment(902, { op: 'authorizing', run: 2 }), ledgerComment(902, { op: 'attempt-result', run: 2, result: 'confirmed' })],
  } });
  assert.equal(result.created, undefined);
  assert.deepEqual(result.held, [{ name: CARD, issue: 902 }]);
});

test('B15 holds exhausted card but keeps an unrelated eligible card moving', async () => {
  const other = 'Unrelated Test Card';
  const p = Q.queuePayload({ result: { ok: true, available: 2 }, units: [
    { name: CARD, body: BODY }, { name: other, body: BODY.replaceAll(CARD, other) },
  ], base: BASE, baseSha: SHA });
  const { result } = await run({ closed: [queueIssue(901)], comments: { 901: paidPair(901, 1) } }, p);
  assert.equal(result.created, true);
  assert.equal(result.name, other);
  assert.deepEqual(result.held, [{ name: CARD, issue: 901 }]);
});

test('B15 consumes one explicit cap-lift and does not authorize another queue next pass', async () => {
  const prior = queueIssue(901, CARD, [{ name: Q.RETRY_CAP_LIFT_LABEL }]);
  const options = { closed: [prior], comments: { 901: paidPair(901, 1) }, events: { 901: [
    { event: 'labeled', actor: { login: 'AARMOU' }, label: { name: Q.RETRY_CAP_LIFT_LABEL } },
  ] } };
  const first = await run(options);
  assert.equal(first.result.created, true);
  assert.ok(!prior.labels.some(l => l.name === Q.RETRY_CAP_LIFT_LABEL));
  const second = await run(options);
  assert.equal(second.result.created, undefined);
  assert.deepEqual(second.result.held, [{ name: CARD, issue: 901 }]);
});

test('B15 treats malformed trusted attempt history as unknown and blocks auto-queue', () => {
  const history = Q.paidAttemptHistory([{ user: BOT, body: A.LEDGER + '\nop:unknown\nrun:7' }]);
  assert.equal(history.complete, false);
});

test('owner-selected supported card is prioritized over automatic suggestions', () => {
  const requested = { number: 501, title: '[Card Request] ' + CARD + ', Ulalek, Fused Atrocity',
    author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' },
    created_at: '2026-10-28T14:20:00Z',
    body: '## Cards requested\n- ' + CARD + ' — Not indexed — Personal: Rocco\n- Ulalek, Fused Atrocity — Not indexed\n\n## Source and triage' };
  const gaps = { dispatch: { claimsVerified: true, dispatchable: true, copilot: ['Celestine, the Living Saint', CARD] } };
  const r = Q.candidates({ gaps, openIssues: [requested], max: 2 });
  assert.deepEqual(r.names, [CARD, 'Celestine, the Living Saint']);
  assert.deepEqual(r.requested, [{ name: CARD, issue: 501 }]);
  assert.deepEqual(r.blockedRequests.map(x => x.name), ['Ulalek, Fused Atrocity']);
  const generated = Q.queuePayload({ result: r, units: [{ name: CARD, body: BODY }], base: BASE, baseSha: SHA });
  assert.equal(generated.suggestions[0].requestedIssue, 501);
  assert.match(Q.queueSummary(generated).join('\n'), /NO assignment/);
  assert.equal(generated.suggestions[0].ready, true);
});

test('unsupported selections are never sent to Cardkit unit generation or queued', () => {
  const request = { number: 502, title: '[Card Request] Impossible Engine Card',
    author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' },
    body: '## Cards requested\n- Impossible Engine Card — Manually entered\n\n## Source and triage' };
  const gaps = { dispatch: { claimsVerified: true, dispatchable: true, copilot: [] } };
  const r = Q.candidates({ gaps, openIssues: [request], max: 3 });
  assert.deepEqual(r.names, []);
  assert.equal(r.blockedRequests[0].issue, 502);
  assert.equal(Q.chooseAutoQueue(Q.queuePayload({ result: r, units: [], base: BASE, baseSha: SHA }), [request]).suggestion, undefined);
});

test('outsider requests and unrelated owner issue sections cannot affect priority', () => {
  const outsider = { number: 503, title: '[Card Request] ' + CARD,
    author_association: 'NONE', user: { login: 'outsider', type: 'User' },
    body: '## Cards requested\n- ' + CARD + '\n' };
  const badBody = { number: 504, title: '[Card Request] ' + CARD,
    author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' },
    body: '## Other section\n- ' + CARD + '\n' };
  assert.deepEqual(Q.ownerSelections([outsider, badBody]), []);
  const gaps = { dispatch: { claimsVerified: true, dispatchable: true, copilot: ['Celestine, the Living Saint', CARD] } };
  assert.deepEqual(Q.candidates({ gaps, openIssues: [outsider, badBody] }).names,
    ['Celestine, the Living Saint', CARD]);
});

test('owner selections never fake an actionable queue unit or suppress eligibility', () => {
  const request = { number: 505, title: '[Card Request] ' + CARD,
    author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' },
    body: '## Cards requested\n- ' + CARD + ' — Not indexed\n' };
  const gaps = { dispatch: { claimsVerified: true, dispatchable: true, copilot: [CARD] } };
  const r = Q.candidates({ gaps, openIssues: [request] });
  assert.deepEqual(r.names, [CARD]);
  assert.equal(r.alreadyQueued.length, 0);
  const p = Q.queuePayload({ result: r, units: [{ name: CARD, body: BODY }], base: BASE, baseSha: SHA });
  assert.equal(Q.chooseAutoQueue(p, [request]).suggestion.name, CARD);
});

test('Copilot PR wakeups are restricted to trusted main code and the configured integration base', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/arcana-queue-feeder.yml'), 'utf8');
  assert.ok(workflow.includes('  pull_request_target:\n    # synchronize fires repeatedly while a session is still working and does not free a slot.\n    # review_requested is Copilot\'s completion signal; the bounded schedule is the fallback.\n    types: [opened, ready_for_review, review_requested]'));
  // These checks must be present on BOTH read-only classification and write-enabled publishing.
  for (const gate of [
    "github.event_name != 'pull_request_target' && github.ref == 'refs/heads/main'",
    "github.event_name == 'pull_request_target' &&\n      github.event.pull_request.user.login",
    "github.event.pull_request.user.login == 'Copilot'",
    'github.event.pull_request.base.ref == vars.ARCANA_INTEGRATION_BASE',
    'github.event.pull_request.head.repo.full_name == github.repository',
    'github.event.pull_request.head.repo.fork == false',
  ]) assert.equal(workflow.split(gate).length - 1, 2, 'both jobs must check: ' + gate);
  assert.equal(workflow.split("ref: ${{ github.event_name == 'pull_request_target' && 'main' || github.workflow_sha }}").length - 1, 2,
    'both checkout steps must use trusted main rather than the PR head');
  assert.ok(workflow.includes("ARCANA_AUTOMATION_READY==='true' && process.env.ARCANA_DISPATCH_MODE==='normal'"),
    'queue creation remains opt-in to normal readiness');
  assert.ok(workflow.includes("createWorkflowDispatch({...context.repo,workflow_id:'arcana-card-dispatcher.yml'"),
    'new work must still go through the guarded dispatcher, not a browser or Copilot direct call');
  assert.ok(workflow.includes("if(auto.created||auto.pending)await github.rest.actions.createWorkflowDispatch("),
    'a waiting unit re-wakes the dispatcher on every feeder pass');
  assert.ok(workflow.includes('types: [opened, ready_for_review, review_requested]'),
    'Copilot completion and ready-state changes wake the feeder without code-only synchronize events');
  assert.ok(!workflow.includes('  workflow_run:'),
    'review_requested is the single Copilot completion wake; the 30-minute timer is the fallback');
});

test('a card already on the destination build is never re-queued; an unreadable destination index fails closed', async () => {
  const index = names => ({ data: { encoding: 'base64', content: Buffer.from(names.map(n => n + '\tcard').join('\n')).toString('base64') } });
  const mk = getContent => { const g = fakeGitHub(); g.github.rest.repos.getContent = getContent; return g; };
  let g = mk(async ({ ref, path }) => { assert.match(ref, /^gpt\/b\d+-candidate-/); assert.match(path, /custom-cards\.index\.tsv$/); return index([CARD]); });
  let r = await Q.createNextAutoQueue({ github: g.github, owner: 'AARMOU', repo: 'arcana_table', payload: payload(), runId: 1, enabled: true });
  assert.equal(r.created, undefined); assert.deepEqual(r.skippedOnDestination, [CARD]);
  assert.equal(g.calls.some(c => c[0] === 'create'), false, 'no paid queue item for a card already in the build');
  g = mk(async () => index(['Some Other Card']));
  r = await Q.createNextAutoQueue({ github: g.github, owner: 'AARMOU', repo: 'arcana_table', payload: payload(), runId: 1, enabled: true });
  assert.equal(r.created, true, 'cards not on the destination still queue normally');
  g = mk(async () => { throw Error('rate limited'); });
  r = await Q.createNextAutoQueue({ github: g.github, owner: 'AARMOU', repo: 'arcana_table', payload: payload(), runId: 1, enabled: true });
  assert.equal(r.reason, 'destination-index-unavailable'); assert.equal(g.calls.some(c => c[0] === 'create'), false);
});
