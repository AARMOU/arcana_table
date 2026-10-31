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

function fake({ issues = [queue(68, [A.ONE_SHOT_LABEL])], ledger = {}, ci = true, otherCiRuns = [], removeFails = false, freshAssignees = [], report81 = [],
  timeline = n => (issues.find(i => i.number === n)?.labels || []).some(l => l.name === A.ONE_SHOT_LABEL) ? [labeledEvent()] : [],
  editedAt = () => ago(60), freshAssociation = 'OWNER' } = {}) {
  const writes = [], dispatches = [];
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
      actions: { listWorkflowRunsForRepo: a => ci ? [{ head_sha: BASE_SHA, name: 'Arcana Development CI', status: 'completed', conclusion: 'success' }]
        : a.status === 'success' ? [] : otherCiRuns,
        createWorkflowDispatch: async a => { dispatches.push(a); } },
    },
  };
  return { github, writes, dispatches };
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

test('no CI run at all for the base head: the dispatcher starts Development CI once (never for an existing run)', async () => {
  const none = fake({ ci: false });
  const r = await runDispatch({ github: none.github, context: ctx, core: core(), env: env(), readBudget: async () => ({}) });
  assert.equal(r.state, 'ci-not-ready'); assert.equal(r.ciStarted, true);
  assert.equal(none.dispatches.length, 1);
  assert.equal(none.dispatches[0].workflow_id, 'arcana-development-ci.yml');
  assert.equal(none.dispatches[0].ref, env().ARCANA_INTEGRATION_BASE);
  for (const run of [{ status: 'in_progress', conclusion: null }, { status: 'completed', conclusion: 'failure' }, { status: 'completed', conclusion: 'action_required' }]) {
    const f = fake({ ci: false, otherCiRuns: [{ id: 9, head_sha: BASE_SHA, name: 'Arcana Development CI', ...run }] });
    const r2 = await runDispatch({ github: f.github, context: ctx, core: core(), env: env(), readBudget: async () => ({}) });
    assert.equal(r2.state, 'ci-not-ready'); assert.equal(f.dispatches.length, 0, JSON.stringify(run));
  }
  const dry = fake({ ci: false });
  await runDispatch({ github: dry.github, context: ctx, core: core(), env: env({ ARCANA_DISPATCH_MODE: 'dry-run', ARCANA_AUTOMATION_READY: '' }), readBudget: async () => ({}) });
  assert.equal(dry.dispatches.length, 0);
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

// Certification-suite requests opened by the backfill (certification/suite-requests.cjs).
const { suiteRequest } = require('../certification/suite-requests.cjs');
const ORACLE = '{"name":"Shield Sphere","oracle_text":"Defender\\nWhenever this creature blocks, put a -0/-1 counter on it."}';
const CREATED = '2026-10-30T19:00:00Z';
const suiteIssue = (n, body) => { const r = suiteRequest({ pr: 518, issue: 509, name: 'Shield Sphere', oracle: { line: ORACLE } });
  return { number: n, title: r.title, body: body ?? r.body, labels: [], assignees: [], state: 'open', created_at: CREATED,
    author_association: 'NONE', user: { login: 'github-actions[bot]', type: 'Bot' } }; };
const unedited = f => { f.github.graphql = async () => ({ repository: { issue: { createdAt: CREATED, lastEditedAt: null } } }); return f; };

test('normal mode assigns an unedited backfill suite request first, on main, with suite-only instructions', async () => {
  const f = unedited(fake({ issues: [queue(68), suiteIssue(600)] })); const calls = [];
  const r = await run(f, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk(calls) });
  assert.deepEqual(r, { state: 'attempted', issue: 600, result: 'confirmed' });
  const a = calls[0][1].agent_assignment;
  assert.equal(a.base_branch, 'main');
  assert.match(a.custom_instructions, /Do NOT open, fetch, diff or read the card pull request/);
  assert.match(a.custom_instructions, /Unit: #600/);
  assert.match(f.writes[0][2], /op:authorizing\nissue:600/);
});

test('a suite request still goes through admission: an unknown budget denies it', async () => {
  const f = unedited(fake({ issues: [suiteIssue(600)] })); const calls = [];
  const r = await runDispatch({ github: f.github, context: ctx, core: core(), env: env({ ARCANA_DISPATCH_MODE: 'normal' }),
    readBudget: async () => ({ snapshot: null }), fetchImpl: fetchOk(calls) });
  assert.equal(r.state, 'denied'); assert.match(r.reason, /^budget/);
  assert.equal(calls.length, 0); assert.deepEqual(f.writes, []);
});

test('an edited or person-written suite request is never assigned', async () => {
  const edited = fake({ issues: [suiteIssue(600)] });
  edited.github.graphql = async () => ({ repository: { issue: { createdAt: CREATED, lastEditedAt: '2026-10-30T19:05:00Z' } } });
  const calls = [];
  assert.equal((await run(edited, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk(calls) })).state, 'nothing-eligible');
  const forged = unedited(fake({ issues: [suiteIssue(601, 'Card unit: #509\nCard PR: #518\nSuite key: x\nBase: main')] }));
  assert.equal((await run(forged, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk(calls) })).state, 'nothing-eligible');
  assert.equal(calls.length, 0);
});

test('an interrupted attempt older than an hour is resolved automatically, never retried', async () => {
  const old = new Date(Date.now() - 2 * 3600e3).toISOString();
  const f = fake({ issues: [queue(68)], ledger: { 68: [bot(A.formatLedger({ op: 'authorizing', issue: 68, run: '1', at: old }))] } });
  const calls = [];
  const r = await run(f, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk(calls) });
  assert.equal(r.state, 'interruption-resolved'); assert.equal(calls.length, 0);
  assert.match(f.writes[0][2], /op:resolution\nissue:68\nrun:1\nresult:not-assigned\nreason:owner-label by dispatcher-timeout/);
  assert.ok(!f.writes.some(w => w[0] === 'removeLabel'));
  const fresh = fake({ issues: [queue(68)], ledger: { 68: [bot(A.formatLedger({ op: 'authorizing', issue: 68, run: '1', at: new Date().toISOString() }))] } });
  assert.equal((await run(fresh, { ARCANA_DISPATCH_MODE: 'normal' }, { fetchImpl: fetchOk(calls) })).state, 'interrupted-attempt');
});

test('engine gap requires positive engineer evidence, not just zero delivery', () => {
  const { engineGapCandidates } = require('./dispatch-runner.cjs');
  const now = Date.now(), u = (over = {}) => ({ prs: [], derived: { state: 'review' }, raw: { title: '[Arcana Queue] X', labels: [],
    engineGapEvidence: { reason: 'Confirmed engine primitive missing', evidence: 'https://github.com/AARMOU/arcana_table/actions/runs/1', at: now - 2e6 },
    commenters: [{ login: 'Copilot', type: 'Bot' }], sessions: [{ event: 'started', at: now - 3e6 }, { event: 'finished', at: now - 2e6 }], ...over } });
  assert.equal(engineGapCandidates([u()], now).length, 1);
  assert.equal(engineGapCandidates([u({ engineGapEvidence: null })], now).length, 0, 'no engineer confirmation, even with Copilot comment');
  assert.equal(engineGapCandidates([{ ...u(), prs: [{ number: 1 }] }], now).length, 0, 'has a PR');
  assert.equal(engineGapCandidates([u({ labels: ['arcana:engine-gap'] })], now).length, 0, 'already labeled');
  assert.equal(engineGapCandidates([u({ sessions: [{ event: 'started', at: now - 60e3 }] })], now).length, 0, 'still working');
  assert.equal(engineGapCandidates([u({ title: '[Arcana Suite] X' })], now).length, 0, 'suites are not cards');
  assert.equal(engineGapCandidates([u({ engineGapEvidence: { at: now - 4e6 } })], now).length, 0, 'confirmation predates this attempt');
  assert.equal(engineGapCandidates([u({ engineGapEvidence: { at: now + 1 } })], now).length, 0, 'future confirmation');
});

test('zero-delivery #458 shape is diagnosis work, never an inferred engine gap or paid retry', async () => {
  const {zeroDeliveryCandidates,labelDiagnosis,engineGapCandidates,DIAGNOSIS_LABEL} = require('./dispatch-runner.cjs');
  const now = Date.now();
  const u = {raw:{number:458,title:'[Arcana Queue] Kenrith',labels:[],ledger:[],sessions:[{event:'started',at:now-4e6},{event:'finished',at:now-3e6}]},derived:{state:'review'},prs:[]};
  assert.equal(zeroDeliveryCandidates([u],now).length,1);
  assert.deepEqual(engineGapCandidates([u],now),[]);
  const writes=[];
  const github={rest:{issues:{addLabels:async a=>writes.push(a),removeLabel:async a=>writes.push(a)}}};
  await labelDiagnosis({github,owner:'AARMOU',repo:'arcana_table',units:[u],now});
  assert.deepEqual(writes[0].labels,[DIAGNOSIS_LABEL]);
  u.raw.labels=[DIAGNOSIS_LABEL];
  await labelDiagnosis({github,owner:'AARMOU',repo:'arcana_table',units:[u],now});
  assert.equal(writes.length,1,'repeat tick adds no label or assignment');
  u.prs=[{number:540}];
  await labelDiagnosis({github,owner:'AARMOU',repo:'arcana_table',units:[u],now});
  assert.equal(writes[1].name,DIAGNOSIS_LABEL,'an actual PR resolves diagnosis supply');
});

test('ciEquivalent: a base that only changed non-build paths since its last green CI counts as green', async () => {
  const { ciEquivalent } = require('./dispatch-runner.cjs');
  const GREEN = 'a'.repeat(40), HEAD = 'b'.repeat(40);
  const gh = ({ files, status = 'ahead', runs } = {}) => ({ rest: {
    actions: { listWorkflowRuns: async () => ({ data: { workflow_runs: runs ?? [
      { id: 7, name: 'Arcana Development CI', status: 'completed', conclusion: 'success', head_sha: GREEN, created_at: '2026-10-31T15:00:00Z' }] } }) },
    repos: { compareCommitsWithBasehead: async a => { assert.equal(a.basehead, GREEN + '...' + HEAD);
      return { data: { status, ahead_by: 1, files: files.map(filename => ({ filename })) } }; } } } });
  const args = { owner: 'o', repo: 'r', base: 'b', baseSha: HEAD };
  const ok = await ciEquivalent({ ...args, github: gh({ files: ['.github/workflows/x.yml', 'scripts/a.cjs', 'tools/cardkit/data/verified-support.json', 'README.md'] }) });
  assert.deepEqual(ok, { greenSha: GREEN, run: 7, files: 4 });
  for (const [why, g] of [
    ['android change', gh({ files: ['scripts/a.cjs', 'android/app/src/main/X.kt'] })],
    ['engine patch', gh({ files: ['argentum-patches/0001-a.patch'] })],
    ['cardkit code', gh({ files: ['tools/cardkit/cardkit.py'] })],
    ['certification', gh({ files: ['certification/suites/x.json'] })],
    ['diverged', gh({ files: ['scripts/a.cjs'], status: 'diverged' })],
    ['no green run', gh({ files: ['scripts/a.cjs'], runs: [] })],
  ]) assert.equal(await ciEquivalent({ ...args, github: g }), null, why);
  assert.equal(await ciEquivalent({ ...args, github: { rest: { actions: { listWorkflowRuns: async () => { throw Error('403'); } } } } }), null);
});

test('dispatcher proceeds past the CI gate when the base change is non-build only', async () => {
  const f = fake({ ci: false });
  f.github.rest.actions.listWorkflowRuns = async () => ({ data: { workflow_runs: [
    { id: 7, name: 'Arcana Development CI', status: 'completed', conclusion: 'success', head_sha: 'e'.repeat(40), created_at: '2026-10-31T15:00:00Z' }] } });
  f.github.rest.repos.compareCommitsWithBasehead = async () => ({ data: { status: 'ahead', ahead_by: 1, files: [{ filename: '.github/workflows/x.yml' }] } });
  const r = await runDispatch({ github: f.github, context: ctx, core: core(), env: env(), readBudget: async () => ({ snapshot: null }) });
  assert.notEqual(r.state, 'ci-not-ready');
  assert.equal(f.dispatches.length, 0);
});

test('the exact-head CI check filters by conclusion itself (the API status filter lags)', async () => {
  const f = fake();
  const orig = f.github.rest.actions.listWorkflowRunsForRepo;
  f.github.rest.actions.listWorkflowRunsForRepo = a => (a.status === 'success' ? [] : orig(a));
  const r = await runDispatch({ github: f.github, context: ctx, core: core(), env: env(), readBudget: async () => ({ snapshot: null }) });
  assert.notEqual(r.state, 'ci-not-ready');
});
