'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { runDispatch, INTERRUPT_MARKER } = require('./dispatch-runner.cjs');
const A = require('./admission.cjs');

const BASE_SHA = 'c'.repeat(40);
const core = () => { const log = []; return { log, info: m => log.push(m), warning: m => log.push('W:' + m),
  setFailed: m => log.push('F:' + m), summary: { addHeading() { return this; }, addCodeBlock() { return this; }, async write() {} } }; };
const bot = body => ({ body, created_at: new Date().toISOString(), user: { type: 'Bot', login: 'github-actions[bot]' } });
const BASE = 'gpt/arcana-integration-next';
const BODY = ['## Cards', '- Eldrazi Monument', '', '## Acceptance', '- Exact Oracle behavior with executable regression tests', '', 'Base: ' + BASE].join('\n');
const queue = (n, labels = [], body = BODY, association = 'OWNER') => ({ number: n, title: '[Arcana Queue] deck ' + n, body, labels: labels.map(name => ({ name })), assignees: [], state: 'open', author_association: association });
const ago = m => new Date(Date.now() - m * 60000).toISOString();
// Default history: issue written 60 min ago, one-shot label applied by the owner 10 min ago.
const labeledEvent = (m = 10) => ({ event: 'labeled', created_at: ago(m), label: { name: A.ONE_SHOT_LABEL }, actor: { login: 'AARMOU' } });

function fake({ issues = [queue(68, [A.ONE_SHOT_LABEL])], ledger = {}, ci = true, removeFails = false, freshAssignees = [], report81 = [],
  timeline = n => (issues.find(i => i.number === n)?.labels || []).some(l => l.name === A.ONE_SHOT_LABEL) ? [labeledEvent()] : [],
  editedAt = () => ago(60), freshAssociation = 'OWNER' } = {}) {
  const writes = [];
  const comments = n => n === 81 ? report81 : (ledger[n] || []);
  let graphqlCalls = 0;
  const github = {
    graphql: async () => { graphqlCalls++; const at = editedAt(graphqlCalls); if (at instanceof Error) throw at; return { repository: { issue: { createdAt: ago(120), lastEditedAt: at } } }; },
    paginate: async (fn, args) => fn(args),
    rest: {
      issues: {
        listForRepo: () => issues, listComments: a => comments(a.issue_number), listEventsForTimeline: a => timeline(a.issue_number),
        createComment: async a => { writes.push(['comment', a.issue_number, a.body]); },
        removeLabel: async a => { if (removeFails) throw { status: 500 }; writes.push(['removeLabel', a.issue_number, a.name]); },
        get: async a => ({ data: { ...issues.find(i => i.number === a.issue_number), state: 'open', assignees: freshAssignees, number: a.issue_number, author_association: freshAssociation } }),
      },
      pulls: { list: () => [] },
      repos: { getBranch: async () => ({ data: { commit: { sha: BASE_SHA } } }) },
      actions: { listWorkflowRunsForRepo: () => ci ? [{ head_sha: BASE_SHA, name: 'Arcana Development CI', conclusion: 'success' }] : [] },
    },
  };
  return { github, writes };
}
const ctx = { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 555 };
const env = x => ({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'authorize-once', ARCANA_REPAIR_MODE: 'off', ARCANA_INTEGRATION_BASE: BASE, COPILOT_DISPATCH_TOKEN: 't',
  // Cardkit verdict for the test card on BASE_SHA (feasibility gate).
  ARCANA_FEASIBILITY: JSON.stringify({ schema: 1, baseSha: 'c'.repeat(40), cards: { 'eldrazi monument': { name: 'Eldrazi Monument', readiness: 'SUPPORTED', route: 'copilot' } } }), ...x });
const readBudget = async () => ({ snapshot: { used: 300, allowance: 1500, measuredAt: new Date().toISOString() } });
const fetchOk = calls => async (url, init) => { calls.push([url, JSON.parse(init.body)]); return { ok: true, json: async () => ({ assignees: [{ login: 'Copilot' }] }) }; };
const run = (f, e, extra = {}) => runDispatch({ github: f.github, context: ctx, core: core(), env: env(e), readBudget, ...extra });

test('dispatch off and master off make no GitHub calls', async () => {
  const throwing = { paginate: () => { throw Error('no calls allowed'); }, rest: {} };
  for (const e of [{ ARCANA_DISPATCH_MODE: '' }, { ARCANA_DISPATCH_MODE: 'off' }, { ARCANA_AUTOMATION_READY: '' }])
    assert.ok(['dispatch-off', 'readiness-off'].includes((await runDispatch({ github: throwing, context: ctx, core: core(), env: env(e), readBudget })).state));
});

test('authorize-once: record, consume label, assign once, close record — in that order', async () => {
  const f = fake(); const calls = [];
  const r = await run(f, {}, { fetchImpl: fetchOk(calls) });
  assert.deepEqual(r, { state: 'attempted', issue: 68, result: 'confirmed' });
  assert.equal(calls.length, 1); const ins = calls[0][1].agent_assignment.custom_instructions;
  assert.match(ins, /Unit: #68/); assert.match(ins, /issue #68/); assert.match(ins, /do NOT open an empty pull request/);
  assert.ok(!/#81/.test(ins)); assert.equal(calls[0][1].agent_assignment.base_branch, BASE);
  assert.deepEqual(f.writes.map(w => w[0]), ['comment', 'removeLabel', 'comment']);
  assert.match(f.writes[0][2], /op:authorizing\nissue:68\nrun:555/);
  assert.match(f.writes[2][2], /op:attempt-result\nissue:68\nrun:555\nresult:confirmed/);
});

test('authorize-once refuses zero or several labeled issues; scheduled runs included', async () => {
  for (const issues of [[queue(68)], [queue(66, [A.ONE_SHOT_LABEL]), queue(68, [A.ONE_SHOT_LABEL])]]) {
    const f = fake({ issues }); const calls = [];
    assert.equal((await run(f, {}, { fetchImpl: fetchOk(calls) })).state, 'authorize-once-needs-exactly-one-label');
    assert.equal(calls.length, 0); assert.equal(f.writes.length, 0);
  }
});

test('label removal failure stops before any assignment and leaves an interrupted attempt', async () => {
  const f = fake({ removeFails: true }); const calls = [];
  assert.equal((await run(f, {}, { fetchImpl: fetchOk(calls) })).state, 'label-removal-failed');
  assert.equal(calls.length, 0);
  assert.deepEqual(f.writes.map(w => w[0]), ['comment']); // authorizing only → interrupted
});

test('interrupted attempt is reported once to #81 and blocks dispatch without retry', async () => {
  const ledger = { 68: [bot(A.formatLedger({ op: 'authorizing', issue: 68, run: 554 }))] };
  const f = fake({ ledger }); const calls = [];
  assert.equal((await run(f, {}, { fetchImpl: fetchOk(calls) })).state, 'interrupted-attempt');
  assert.equal(calls.length, 0); assert.equal(f.writes.length, 1); assert.equal(f.writes[0][1], 81);
  assert.ok(f.writes[0][2].startsWith(INTERRUPT_MARKER)); assert.ok(!f.writes[0][2].includes('@'));
  const again = fake({ ledger, report81: [bot(f.writes[0][2])] });
  await run(again, {}, { fetchImpl: fetchOk(calls) }); assert.equal(again.writes.length, 0);
});

test('dry-run plans with master off but writes nothing and never assigns', async () => {
  const f = fake({ issues: [queue(66), queue(68)] }); const calls = [];
  const r = await run(f, { ARCANA_AUTOMATION_READY: '', ARCANA_DISPATCH_MODE: 'dry-run' }, { fetchImpl: fetchOk(calls) });
  assert.deepEqual(r, { state: 'dry-run', issue: 66, reason: 'dry-run' });
  assert.equal(calls.length, 0); assert.equal(f.writes.length, 0);
});

test('missing exact-base CI blocks before any budget read or write', async () => {
  const f = fake({ ci: false }); let budgetReads = 0;
  const r = await runDispatch({ github: f.github, context: ctx, core: core(), env: env(), readBudget: async () => { budgetReads++; return {}; } });
  assert.equal(r.state, 'ci-not-ready'); assert.equal(budgetReads, 0); assert.equal(f.writes.length, 0);
});

test('claim changed after authorization is recorded as not attempted', async () => {
  const f = fake({ freshAssignees: [{ login: 'someone' }] }); const calls = [];
  assert.equal((await run(f, {}, { fetchImpl: fetchOk(calls) })).result, 'not-attempted');
  assert.equal(calls.length, 0); assert.match(f.writes[2][2], /result:not-attempted/);
});

test('assignment failure is recorded once and never retried', async () => {
  const f = fake(); let n = 0;
  const r = await run(f, {}, { fetchImpl: async () => { n++; return { ok: false }; } });
  assert.equal(r.result, 'error'); assert.equal(n, 1); assert.match(f.writes[2][2], /result:error/);
});

test('unknown budget denies without writes', async () => {
  const f = fake(); const calls = [];
  const r = await runDispatch({ github: f.github, context: ctx, core: core(), env: env(), readBudget: async () => ({ snapshot: null }), fetchImpl: fetchOk(calls) });
  assert.equal(r.state, 'denied'); assert.match(r.reason, /budget/); assert.equal(f.writes.length, 0); assert.equal(calls.length, 0);
});

test('owner resolution label appends a resolution record, keeps originals, and dispatches nothing that run', async () => {
  const ledger = { 68: [bot(A.formatLedger({ op: 'authorizing', issue: 68, run: 554 }))] };
  const f = fake({ issues: [queue(68, [A.RESOLVE_LABEL])], ledger }); const calls = [];
  f.github.rest.issues.listEventsForTimeline = () => [{ event: 'labeled', label: { name: A.RESOLVE_LABEL }, actor: { login: 'AARMOU' } }];
  const r = await run(f, {}, { fetchImpl: fetchOk(calls) });
  assert.equal(r.state, 'interruption-resolved'); assert.equal(calls.length, 0);
  assert.deepEqual(f.writes.map(w => [w[0], w[1]]), [['comment', 68], ['removeLabel', 68], ['comment', 81]]);
  assert.match(f.writes[0][2], /op:resolution\nissue:68\nrun:554\nresult:not-assigned\nreason:owner-label by AARMOU in run 555/);
  assert.ok(!f.writes.some(w => w[0] === 'deleteComment'));
  assert.ok(!f.writes[2][2].includes('@'));
});

test('resolution label is ignored in dry-run (no writes)', async () => {
  const ledger = { 68: [bot(A.formatLedger({ op: 'authorizing', issue: 68, run: 554 }))] };
  const f = fake({ issues: [queue(68, [A.RESOLVE_LABEL])], ledger });
  const r = await run(f, { ARCANA_DISPATCH_MODE: 'dry-run' });
  assert.equal(r.state, 'interrupted-attempt'); assert.equal(f.writes.length, 0);
});

test('after resolution the unit stays non-assignable and dispatch moves on to other work', async () => {
  const ledger = { 68: [bot(A.formatLedger({ op: 'authorizing', issue: 68, run: 554 })),
    bot(A.formatLedger({ op: 'resolution', issue: 68, run: 554, result: 'not-assigned' }))] };
  const f = fake({ issues: [queue(66), queue(68)], ledger }); const calls = [];
  const r = await run(f, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk(calls) });
  assert.equal(r.issue, 66); assert.equal(calls.length, 1);
});

test('integration base must be configured explicitly (no stale default)', async () => {
  for (const b of ['', undefined, 'bad branch;rm']) {
    const f = fake(); const calls = [];
    assert.equal((await run(f, { ARCANA_INTEGRATION_BASE: b }, { fetchImpl: fetchOk(calls) })).state, 'base-not-configured');
    assert.equal(calls.length, 0); assert.equal(f.writes.length, 0);
  }
});

test('a vague unit is denied before any write or assignment, in every mode', async () => {
  for (const mode of ['authorize-once', 'normal', 'dry-run']) {
    const f = fake({ issues: [queue(68, [A.ONE_SHOT_LABEL], 'Please do the next batch.')] }); const calls = [];
    const r = await run(f, { ARCANA_DISPATCH_MODE: mode }, { fetchImpl: fetchOk(calls) });
    // A specifically authorized normal-mode unit fails visibly instead of silently
    // selecting another card. Dry-run continues to skip incomplete ordinary candidates.
    // An approved unit that can never be assigned as written has its approval withdrawn instead
    // of pinning the queue; nothing else is written, assigned or billed.
    if (mode === 'dry-run') { assert.equal(r.state, 'nothing-eligible', mode); assert.equal(f.writes.length, 0); }
    else {
      assert.equal(r.state, 'authorized-unit-not-eligible', mode); assert.match(r.reason, /approval withdrawn: not-actionable/);
      assert.deepEqual(f.writes.map(w => w[0]), ['removeLabel', 'comment'], mode);
      assert.match(f.writes[1][2], /op:observation[\s\S]*reason:owner approval withdrawn, not assignable: not-actionable/);
    }
    assert.equal(calls.length, 0);
  }
});

test('a unit whose Base line names another branch is denied', async () => {
  const f = fake({ issues: [queue(68, [A.ONE_SHOT_LABEL], BODY.replace(BASE, 'gpt/six-precon-worker-integration-20261022'))] });
  assert.match((await run(f, {})).reason, /base-mismatch/);
});

test('every queued unit carries the dispatcher verdict even when another unit was selected', async () => {
  const report = {};
  const f = fake({ issues: [queue(68), queue(70, [], 'Please do the next batch.')] });
  await run(f, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk([]), report });
  const byN = Object.fromEntries(report.units.map(u => [u.number, u]));
  assert.equal(byN[70].eligible, false); assert.match(byN[70].skip, /^not-actionable/);
  assert.ok('eligible' in byN[68]);
});
