'use strict';
// Engine-feasibility gate and read-only queue feeder (issue #81, 2026-10-28).
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const F = require('./feasibility.cjs');
const Q = require('./queue-feeder.cjs');
const S = require('./control-status.cjs');
const W = require('./control-workflows.cjs');

const SHA = 'c'.repeat(40), BASE = 'gpt/arcana-integration-next';
// Real Cardkit verdicts at 9dd5fc9c (2026-10-28) for the cards involved, trimmed to the fields used.
const CLASSIFIED = [
  { name: 'The Swarmlord', readiness: 'NEEDS_REVIEW', route: 'gpt', reasons: ['1 line(s) without a close implemented match (best 0.63)'] },
  { name: "Ulamog's Nullifier", readiness: 'NEEDS_REVIEW', route: 'claude', reviewHint: 'possible-engine-gap', reasons: ['1 line(s) without a close implemented match (best 0.54)'] },
  { name: 'Darksteel Monolith', readiness: 'SUPPORTED', route: 'copilot', reasons: ['every rules line matches an implemented card at >= 0.88'] },
  { name: 'Ulalek, Fused Atrocity', readiness: 'SUPPORTED', route: 'copilot', reasons: [] },
  { name: 'Eldrazi Temple', readiness: 'ENGINE_BLOCKED', route: 'gpt', reasons: ['recorded gap'] },
];
const verdicts = F.buildVerdicts({ baseSha: SHA, classified: CLASSIFIED });
const body = (cards, extra = '') => ['## Cards', ...cards.map(c => '- ' + c), '', '## Acceptance', '- Exact Oracle behavior with executable regression tests', extra, '', 'Base: ' + BASE].join('\n');

test('card names are read from the Cards bullets in every format the queue uses', () => {
  assert.deepEqual(F.unitCards(['## Cards', "- Ulamog's Nullifier — original Eldrazi Incursion, one slot", '- **The Swarmlord** — the commander',
    '- Hideous Taskmaster (original deck): single-card repair', '- Psychomancer', '- Ulalek, Fused Atrocity', '- Blood for the Blood God!',
    '- Card name', '', '## Acceptance', '- Not A Card'].join('\n')),
  ["Ulamog's Nullifier", 'The Swarmlord', 'Hideous Taskmaster', 'Psychomancer', 'Ulalek, Fused Atrocity', 'Blood for the Blood God!']);
  assert.deepEqual(F.unitCards('## Cards — this unit\n- Darksteel Monolith'), ['Darksteel Monolith']);
  assert.deepEqual(F.unitCards('<!-- ## Cards\n- Hidden -->\n## Discovery\n- run `python3 x.py --json`'), []);
});

test('the two wasted paid runs (#127, #173) would have been refused', () => {
  for (const name of ['**The Swarmlord** — commander', "Ulamog's Nullifier — original"]) {
    const r = F.assess({ body: body([name]), verdicts, baseSha: SHA });
    assert.equal(r.ok, false);
    assert.match(r.reason, /^not Copilot-ready: .*NEEDS_REVIEW/);
  }
  assert.match(F.assess({ body: body(["Ulamog's Nullifier"]), verdicts, baseSha: SHA }).reason, /possible-engine-gap/);
});

test('only SUPPORTED + copilot route passes; every card in a unit must pass', () => {
  assert.deepEqual(F.assess({ body: body(['Darksteel Monolith', 'Ulalek, Fused Atrocity']), verdicts, baseSha: SHA }).ok, true);
  const mixed = F.assess({ body: body(['Darksteel Monolith', 'Eldrazi Temple']), verdicts, baseSha: SHA });
  assert.equal(mixed.ok, false); assert.match(mixed.reason, /Eldrazi Temple=ENGINE_BLOCKED route gpt/);
  assert.match(F.assess({ body: body(['Some Unknown Card']), verdicts, baseSha: SHA }).reason, /UNCLASSIFIED/);
  // Typographic apostrophes in issue text still match Cardkit names.
  assert.equal(F.assess({ body: body(['Ulalek, Fused Atrocity']), verdicts, baseSha: SHA }).ok, true);
  assert.equal(F.assess({ body: body(['Ulamog’s Nullifier']), verdicts, baseSha: SHA }).cards[0].readiness, 'NEEDS_REVIEW');
});

test('missing, malformed or stale verdicts fail closed with a reason', () => {
  assert.match(F.assess({ body: body(['Darksteel Monolith']), verdicts: null, baseSha: SHA }).reason, /^feasibility-unavailable/);
  assert.match(F.assess({ body: body(['Darksteel Monolith']), verdicts, baseSha: 'd'.repeat(40) }).reason, /^feasibility-stale/);
  assert.match(F.assess({ body: '## Discovery\n- `python3 tools/x.py --all`\n\nBase: ' + BASE, verdicts, baseSha: SHA }).reason, /no named cards/);
  for (const raw of ['', 'not json', '{"schema":2}', JSON.stringify({ schema: 1, baseSha: 'short', cards: {} })]) assert.equal(F.parseVerdicts(raw), null, raw);
  assert.equal(F.buildVerdicts({ baseSha: SHA, classified: [{ name: 'x' }] }), null);
  assert.equal(F.buildVerdicts({ baseSha: 'nope', classified: [] }), null);
  assert.deepEqual(F.parseVerdicts(JSON.stringify(verdicts)), verdicts);
});

test('owner override label counts only when an approver applied it', () => {
  const events = actor => [{ event: 'labeled', label: { name: F.FEASIBILITY_LABEL }, actor: { login: actor }, created_at: '2026-10-28T06:00:00Z' }];
  const owner = F.overrideOf(events('AARMOU'), [F.FEASIBILITY_LABEL]);
  assert.equal(F.assess({ body: body(['The Swarmlord']), override: owner, approvers: ['AARMOU'], verdicts, baseSha: SHA }).ok, true);
  const stranger = F.overrideOf(events('someone'), [F.FEASIBILITY_LABEL]);
  assert.match(F.assess({ body: body(['The Swarmlord']), override: stranger, approvers: ['AARMOU'], verdicts, baseSha: SHA }).reason, /not applied by an approver/);
  assert.equal(F.overrideOf(events('AARMOU'), []), null, 'label removed -> no override');
});

// ---- Dispatcher integration --------------------------------------------------------------------
function dispatchGithub(issues) {
  const writes = [];
  return { writes, github: {
    graphql: async () => ({ repository: { issue: { createdAt: new Date(Date.now() - 7200e3).toISOString() } } }),
    paginate: async (fn, a) => fn(a),
    rest: {
      issues: { listForRepo: () => issues, listComments: () => [], listEventsForTimeline: () => [],
        createComment: async a => { writes.push(['comment', a.issue_number, a.body]); }, updateComment: async a => { writes.push(['update', a.comment_id]); },
        removeLabel: async a => { writes.push(['removeLabel', a.issue_number]); },
        get: async a => ({ data: { ...issues.find(i => i.number === a.issue_number), assignees: [] } }) },
      pulls: { list: () => [] },
      repos: { getBranch: async () => ({ data: { commit: { sha: SHA } } }) },
      actions: { listWorkflowRunsForRepo: () => [{ head_sha: SHA, name: 'Arcana Development CI', conclusion: 'success' }] },
    } } };
}
const issue = (n, cards) => ({ number: n, title: '[Arcana Queue] ' + n, body: body(cards), labels: [], assignees: [], state: 'open', author_association: 'OWNER' });
const core = () => ({ info() {}, warning() {}, setFailed() {}, summary: { addHeading() { return this; }, addCodeBlock() { return this; }, addRaw() { return this; }, async write() {} } });
const budget = async () => ({ snapshot: { account: 'AARMOU', metric: 'ai_credits', period: '2026-10', used: 450, reportedUsed: 300, reserved: 150, allowance: 1500, measuredAt: new Date().toISOString() }, status: { level: 'normal' }, error: null });
const env = x => ({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'normal', ARCANA_INTEGRATION_BASE: BASE, COPILOT_DISPATCH_TOKEN: 't',
  ARCANA_FEASIBILITY: JSON.stringify(verdicts), ...x });
const ctx = (extra = {}) => ({ repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 1, eventName: 'schedule', payload: {}, ...extra });

test('normal mode skips engine-gap units and assigns the next Copilot-ready one', async () => {
  const f = dispatchGithub([issue(127, ['The Swarmlord']), issue(173, ["Ulamog's Nullifier"]), issue(400, ['Darksteel Monolith'])]);
  const calls = [];
  const r = await W.dispatcher({ github: f.github, context: ctx(), core: core(), env: env(), readBudget: budget,
    fetchImpl: async (url, init) => { calls.push(JSON.parse(init.body)); return { ok: true, json: async () => ({ assignees: [{ login: 'Copilot' }] }) }; } });
  assert.equal(r.result.state, 'attempted'); assert.equal(r.result.issue, 400);
  assert.equal(calls.length, 1);
  assert.deepEqual(r.payload.candidateFeasibility.cards.map(c => c.readiness), ['SUPPORTED']);
});

test('nothing Copilot-ready: no assignment, skip reasons name the gap, action points to override or engine lane', async () => {
  const f = dispatchGithub([issue(127, ['The Swarmlord']), issue(173, ["Ulamog's Nullifier"])]);
  const r = await W.dispatcher({ github: f.github, context: ctx(), core: core(), env: env(), readBudget: budget,
    fetchImpl: async () => { throw Error('must not assign'); } });
  assert.equal(r.result.state, 'nothing-eligible');
  const skip = Object.fromEntries(r.payload.workers.list.map(u => [u.n, u.skip]));
  assert.match(skip[127], /^feasibility: not Copilot-ready: The Swarmlord=NEEDS_REVIEW/);
  assert.match(skip[173], /possible-engine-gap/);
  assert.match(r.payload.action, /Queue Feeder/);
  assert.ok(f.writes.every(w => w[1] === 78), 'only the status comment');
});

test('authorize-once on an engine-gap unit is denied before billing or any ledger write', async () => {
  const one = { ...issue(127, ['The Swarmlord']), labels: [{ name: 'arcana:authorize-once' }] };
  const f = dispatchGithub([one]);
  f.github.rest.issues.listEventsForTimeline = () => [{ event: 'labeled', label: { name: 'arcana:authorize-once' }, actor: { login: 'AARMOU' }, created_at: new Date().toISOString() }];
  let billingReads = 0;
  const r = await W.dispatcher({ github: f.github, context: ctx(), core: core(), env: env({ ARCANA_DISPATCH_MODE: 'authorize-once' }),
    readBudget: async () => { billingReads++; return budget(); }, fetchImpl: async () => { throw Error('must not assign'); } });
  assert.equal(r.result.state, 'denied'); assert.match(r.result.reason, /^feasibility: not Copilot-ready/);
  assert.match(r.payload.action, /arcana:feasibility-reviewed/);
  assert.ok(f.writes.every(w => w[1] === 78)); assert.ok(f.writes.every(w => w[0] !== 'removeLabel'), 'one-shot label not consumed');
  assert.equal(billingReads, 1, 'only the display read after the run');
});

test('missing feasibility verdicts block every candidate (fail closed)', async () => {
  const f = dispatchGithub([issue(400, ['Darksteel Monolith'])]);
  const r = await W.dispatcher({ github: f.github, context: ctx(), core: core(), env: env({ ARCANA_FEASIBILITY: '' }), readBudget: budget,
    fetchImpl: async () => { throw Error('must not assign'); } });
  assert.equal(r.result.state, 'nothing-eligible');
  assert.match(r.payload.workers.list[0].skip, /feasibility-unavailable/);
  assert.deepEqual(r.payload.feasibility, { unavailable: true });
});

test('dispatcher workflow runs Cardkit in a separate job with no secrets and read-only permissions', () => {
  const y = fs.readFileSync('.github/workflows/arcana-card-dispatcher.yml', 'utf8');
  const feas = y.slice(y.indexOf('  feasibility:'), y.indexOf('  dispatch:'));
  assert.match(feas, /permissions:\n      contents: read\n      issues: read\n/);
  assert.doesNotMatch(feas, /secrets\.|COPILOT_|GH_TOKEN|github\.token/);
  assert.match(feas, /persist-credentials: false[\s\S]*persist-credentials: false/);
  assert.match(feas, /classify --json -- "\$\{names\[@\]\}"/);
  const dispatch = y.slice(y.indexOf('  dispatch:'));
  assert.match(dispatch, /needs: feasibility/);
  assert.match(dispatch, /!cancelled\(\)/);
  assert.match(dispatch, /ARCANA_FEASIBILITY: \$\{\{ needs\.feasibility\.outputs\.verdicts \}\}/);
});

// ---- Queue feeder ------------------------------------------------------------------------------
const gaps = { dispatch: { copilot: ['Darksteel Monolith', 'Abstruse Archaic', 'Poxwalkers'], claimsVerified: true, dispatchable: true } };
const unitBody = name => body([name], '- ' + name + ': Indestructible; exact Oracle line (reference implementations: X)');

test('feeder suggests only Copilot-routed, unclaimed, not-yet-queued cards, and fails closed without verified claims', () => {
  const r = Q.candidates({ gaps, openIssues: [issue(500, ['Darksteel Monolith'])], max: 5 });
  assert.deepEqual([r.ok, r.names, r.alreadyQueued], [true, ['Abstruse Archaic', 'Poxwalkers'], ['Darksteel Monolith']]);
  assert.deepEqual(Q.candidates({ gaps, openIssues: [], max: 1 }).names, ['Darksteel Monolith']);
  for (const bad of [null, { dispatch: { copilot: ['X'], claimsVerified: false, dispatchable: false } }, { dispatch: { copilot: ['X'] } }])
    assert.equal(Q.candidates({ gaps: bad, openIssues: [] }).ok, false);
});

test('feeder output: actionable bodies with repo-only prefilled links; refused cards are reported, not suggested', () => {
  const p = Q.queuePayload({ result: Q.candidates({ gaps, openIssues: [] }), base: BASE, baseSha: SHA,
    units: [{ name: 'Darksteel Monolith', body: unitBody('Darksteel Monolith') }, { name: 'Poxwalkers', body: null, error: 'refusing Poxwalkers: claimed by PR #9' }] });
  const [ok, refused] = p.suggestions;
  assert.equal(ok.ready, true);
  assert.ok(ok.newIssueUrl.startsWith('https://github.com/AARMOU/arcana_table/issues/new?title=%5BArcana%20Queue%5D%20Darksteel%20Monolith'));
  assert.equal(decodeURIComponent(ok.newIssueUrl.split('&body=')[1]), ok.body);
  assert.deepEqual([refused.ready, refused.newIssueUrl, refused.body], [false, null, null]);
  // A suggested body is exactly what the dispatcher accepts: actionable, and feasible for the same SHA.
  assert.equal(F.assess({ body: ok.body, verdicts, baseSha: SHA }).ok, true);
  const comment = S.formatStatus('queue', p, Q.queueSummary(p));
  assert.ok(comment.length < 60000);
  assert.equal(S.parseStatus(comment).suggestions[0].body, ok.body, 'bodies survive the status comment intact');
  assert.doesNotMatch(comment, /@AARMOU/);
});

test('feeder workflow never writes issues from the job that runs base-branch code', () => {
  const y = fs.readFileSync('.github/workflows/arcana-queue-feeder.yml', 'utf8');
  const suggest = y.slice(y.indexOf('  suggest:'), y.indexOf('  publish:'));
  assert.match(suggest, /permissions:\n      contents: read\n      issues: read\n      pull-requests: read\n/);
  assert.doesNotMatch(suggest, /\$\{\{ secrets\.|createComment|addLabels|assignees/);
  const publish = y.slice(y.indexOf('  publish:'));
  assert.doesNotMatch(publish, /path: base|cardkit\.py/);
  assert.doesNotMatch(y, /issues\.create\(|addAssignees|COPILOT_DISPATCH_TOKEN|ARCANA_AUTOMATION_READY/);
});
