'use strict';
// Regressions for the 2026-10-27 Copilot billing/dispatch repair (issue #81).
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const A = require('./admission.cjs');
const B = require('./billing-monitor.cjs');
const S = require('./control-status.cjs');
const W = require('./control-workflows.cjs');

const NOW = Date.parse('2026-10-27T20:00:00Z');
const MIN = 60000, HOUR = 60 * MIN;
const iso = ms => new Date(ms).toISOString();
const item = q => ({ product: 'Copilot AI Credits', sku: 'AI Credit', model: 'GPT-5', unitType: 'ai-credits', grossQuantity: q });
const report = items => ({ user: 'AARMOU', timePeriod: { year: 2026, month: 10 }, usageItems: items });
const okFetch = q => async () => ({ ok: true, json: async () => report([item(q)]) });
const env = (x = {}) => ({ COPILOT_BILLING_READ_TOKEN: 'dummy', ARCANA_AI_CREDIT_BUDGET: '1500', ...x });
const OK = { provenance: { ok: true, reasons: [] } };
const readyEnv = (x = {}) => A.readiness({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'normal', ...x });
const queued = n => ({ ...A.deriveUnit({ number: n }, NOW), actionable: { ok: true, missing: [] }, ...OK });
const snapAt = (used, measuredAt = NOW - 5 * MIN, reserved = 150, reportedUsed = used - reserved) =>
  ({ used, allowance: 1500, reserved, reportedUsed, measuredAt: iso(measuredAt) });

// ---- Billing retrieval -------------------------------------------------------------------------
test('successful billing retrieval: official AI-credit report, reserve added, figures preserved', async () => {
  const calls = [];
  const r = await B.readBudget(env(), { now: new Date(NOW), fetchImpl: async (url, init) => {
    calls.push([url, init.headers]); return { ok: true, json: async () => report([item(700.5), { product: 'Actions', sku: 'Linux', unitType: 'minutes', grossQuantity: 9999 }]) };
  } });
  assert.equal(r.error, null);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'https://api.github.com/users/AARMOU/settings/billing/ai_credit/usage?year=2026&month=10');
  assert.equal(calls[0][1]['X-GitHub-Api-Version'], '2026-03-10');
  assert.equal(r.snapshot.reportedUsed, 700.5);   // Actions minutes never mixed in
  assert.equal(r.snapshot.reserved, 150);         // agreed 10% reserve
  assert.equal(r.snapshot.used, 850.5);
  assert.equal(r.status.level, 'warn');
});

test('authentication failures fail closed with a specific owner action and no retry', async () => {
  for (const status of [401, 403, 404]) {
    let n = 0;
    const r = await B.readBudget(env(), { now: new Date(NOW), retryDelayMs: 0, fetchImpl: async () => { n++; return { ok: false, status }; } });
    assert.equal(r.error, 'billing-http-' + status);
    assert.equal(r.status.level, 'unknown');
    assert.equal(r.status.maxNewAssignments, 0);
    assert.equal(n, 1, 'definitive 4xx answers are not retried');
    const action = B.billingAction(r.error);
    assert.match(action, /Owner/);
    assert.doesNotMatch(action, /dummy/);
  }
  assert.match(B.billingAction('missing-token'), /COPILOT_BILLING_READ_TOKEN/);
  assert.match(B.billingAction('invalid-owner-budget', { ARCANA_PREMIUM_REQUEST_BUDGET: '300' }), /retired/);
});

test('transient billing errors are retried once, then fail closed', async () => {
  let n = 0;
  const flaky = async () => (++n === 1 ? { ok: false, status: 503 } : { ok: true, json: async () => report([item(100)]) });
  const r = await B.readBudget(env(), { now: new Date(NOW), retryDelayMs: 0, fetchImpl: flaky });
  assert.equal(r.error, null); assert.equal(n, 2);
  n = 0;
  const down = await B.readBudget(env(), { now: new Date(NOW), retryDelayMs: 0, fetchImpl: async () => { n++; return { ok: false, status: 502 }; } });
  assert.equal(down.error, 'billing-http-502'); assert.equal(n, 2); assert.equal(down.status.maxNewAssignments, 0);
  const timeout = await B.readBudget(env(), { now: new Date(NOW), retryDelayMs: 0,
    fetchImpl: async () => { throw Object.assign(Error('t'), { name: 'TimeoutError' }); } });
  assert.equal(timeout.error, 'billing-timeout');
  const garbled = await B.readBudget(env(), { now: new Date(NOW), fetchImpl: async () => ({ ok: true, json: async () => { throw SyntaxError('x'); } }) });
  assert.equal(garbled.error, 'invalid-billing-response');
});

test('premium requests are never counted as AI credits', () => {
  assert.throws(() => B.usageQuantity(report([{ product: 'Copilot', sku: 'Copilot Premium Request', unitType: 'requests', grossQuantity: 5 }]), new Date(NOW)));
});

// ---- Stale data --------------------------------------------------------------------------------
test('stale or future billing snapshots deny admission', () => {
  const ready = readyEnv();
  for (const measuredAt of [NOW - 61 * MIN, NOW + 5 * MIN]) {
    const d = A.admit({ op: 'assign', unit: queued(70), units: [], ready, snapshot: snapAt(300, measuredAt), now: NOW });
    assert.equal(d.allowed, false); assert.match(d.reason, /^budget: unknown/);
  }
  const none = A.admit({ op: 'assign', unit: queued(70), units: [], ready, snapshot: null, now: NOW });
  assert.match(none.reason, /budget: unknown \(no verified usage snapshot\)/);
});

// ---- Pending consumption -----------------------------------------------------------------------
test('recently finished units are pending liabilities until the billing lag window passes', () => {
  const finished = (at) => A.deriveUnit({ number: 63, assignedToCopilot: true,
    sessions: [{ event: 'started', at: at - 40 * MIN }, { event: 'finished', at }] }, NOW, { lagMs: 24 * HOUR });
  const recent = finished(NOW - 2 * HOUR), old = finished(NOW - 30 * HOUR);
  assert.equal(recent.state, 'review'); assert.equal(recent.holdsSlot, false); assert.equal(recent.recentlyFinished, true);
  assert.equal(old.recentlyFinished, false);
  const ready = readyEnv();
  const d = A.admit({ op: 'assign', unit: queued(70), units: [recent, old], ready, snapshot: snapAt(450), now: NOW });
  assert.equal(d.allowed, true);
  assert.deepEqual(d.budget.liabilities, { activeUnits: 0, pendingRepairs: 0, recentlyFinished: 60, candidate: 60,
    fixedReserve: 150, total: 270, reportedCredits: 300, allowance: 1500 });
  // 450 + 120 = 570 of 1500 -> 38% -> normal; without the recent unit it would be 510.
  assert.equal(d.budget.level, 'normal');
});

test('pending liabilities can move the decision across a threshold (never under-count)', () => {
  const ready = readyEnv({ ARCANA_MAX_ACTIVE_UNITS: '2' });
  const recent = n => ({ ...A.deriveUnit({ number: n, assignedToCopilot: true,
    sessions: [{ event: 'started', at: NOW - 3 * HOUR }, { event: 'finished', at: NOW - 2 * HOUR }] }, NOW), ...OK });
  // 1260 reported+reserve. Candidate alone -> 1320 (88%, slow, allowed). Plus two recent units -> 1440 (96%, paused).
  assert.equal(A.admit({ op: 'assign', unit: queued(70), units: [], ready, snapshot: snapAt(1260), now: NOW }).allowed, true);
  const d = A.admit({ op: 'assign', unit: queued(70), units: [recent(63), recent(64)], ready, snapshot: snapAt(1260), now: NOW });
  assert.equal(d.allowed, false); assert.match(d.reason, /^budget: paused/);
  assert.equal(d.budget.liabilities.recentlyFinished, 120);
});

test('ARCANA_BILLING_LAG_HOURS is validated; 0 disables recent-finish liabilities', () => {
  assert.equal(A.readiness({}).billingLagMs, 24 * HOUR);
  assert.equal(A.readiness({ ARCANA_BILLING_LAG_HOURS: '0' }).billingLagMs, 0);
  assert.equal(A.readiness({ ARCANA_BILLING_LAG_HOURS: '6' }).billingLagMs, 6 * HOUR);
  for (const bad of ['-1', '73', '1.5', 'x']) assert.ok(A.readiness({ ARCANA_BILLING_LAG_HOURS: bad }).errors.length, bad);
  const u = A.deriveUnit({ number: 1, assignedToCopilot: true, sessions: [{ event: 'started', at: NOW - 2 * MIN }, { event: 'finished', at: NOW - MIN }] }, NOW, { lagMs: 0 });
  assert.equal(u.recentlyFinished, false);
});

// ---- Thresholds --------------------------------------------------------------------------------
test('50/75/90 thresholds and the 10% reserve are unchanged', async () => {
  const level = async q => (await B.readBudget(env(), { now: new Date(NOW), fetchImpl: okFetch(q) })).status;
  assert.equal((await level(599)).level, 'normal');   // 749/1500 = 49.9%
  assert.equal((await level(600)).level, 'warn');     // 750 = 50%
  assert.equal((await level(975)).level, 'slow');     // 1125 = 75%
  const paused = await level(1200);                    // 1350 = 90%
  assert.equal(paused.level, 'paused'); assert.equal(paused.maxNewAssignments, 0);
  // Slow: one operation at a time.
  const ready = readyEnv({ ARCANA_MAX_ACTIVE_UNITS: '2' });
  const working = { ...A.deriveUnit({ number: 5, assignedToCopilot: true, sessions: [{ event: 'started', at: NOW - 5 * MIN }] }, NOW), ...OK };
  const d = A.admit({ op: 'assign', unit: queued(70), units: [working], ready, snapshot: snapAt(1020), now: NOW });
  assert.equal(d.budget.level, 'slow'); // 1020 + 60 running + 60 candidate = 76%
  assert.equal(d.allowed, false); assert.match(d.reason, /slow allows one operation/);
});

// ---- Worker completion and stalled work --------------------------------------------------------
test('a failed Copilot session ends the session instead of holding a slot forever', () => {
  const { deriveUnit } = A;
  // units.cjs maps copilot_work_finished_failure -> finished; emulate the mapped session list.
  const src = fs.readFileSync(require.resolve('./units.cjs'), 'utf8');
  assert.match(src, /copilot_work_finished_failure: 'finished'/);
  const u = deriveUnit({ number: 9, assignedToCopilot: true, sessions: [{ event: 'started', at: NOW - 50 * MIN }, { event: 'finished', at: NOW - 10 * MIN }] }, NOW);
  assert.equal(u.state, 'review'); assert.equal(u.holdsSlot, false);
});

test('a session with no finish event becomes session-stale after 3h but keeps its slot', () => {
  const s = at => A.deriveUnit({ number: 9, assignedToCopilot: true, sessions: [{ event: 'started', at }] }, NOW);
  assert.equal(s(NOW - 2 * HOUR).state, 'working');
  const stale = s(NOW - 4 * HOUR);
  assert.deepEqual([stale.state, stale.reason, stale.holdsSlot], ['halted', 'session-stale', true]);
});

test('owner wu:halted label releases a timed-out slot; a later real session takes it back', () => {
  const stuck = labels => A.deriveUnit({ number: 9, assignedToCopilot: true, labels,
    ledger: [{ op: 'authorizing', run: '1', at: NOW - 2 * HOUR }, { op: 'attempt-result', run: '1', result: 'error', at: NOW - 2 * HOUR }] }, NOW);
  assert.deepEqual([stuck([]).reason, stuck([]).holdsSlot], ['pickup-timeout', true]);
  const released = stuck([A.HALTED_LABEL]);
  assert.deepEqual([released.state, released.reason, released.releasedFrom, released.holdsSlot], ['halted', 'owner-released', 'pickup-timeout', false]);
  const ready = readyEnv();
  assert.equal(A.admit({ op: 'assign', unit: queued(70), units: [{ ...stuck([]), ...OK }], ready, snapshot: snapAt(300), now: NOW }).allowed, false);
  assert.equal(A.admit({ op: 'assign', unit: queued(70), units: [{ ...released, ...OK }], ready, snapshot: snapAt(300), now: NOW }).allowed, true);
  // The label never releases a genuinely running session.
  const running = A.deriveUnit({ number: 9, assignedToCopilot: true, labels: [A.HALTED_LABEL],
    ledger: [{ op: 'authorizing', run: '1', at: NOW - 2 * HOUR }, { op: 'attempt-result', run: '1', result: 'error', at: NOW - 2 * HOUR }],
    sessions: [{ event: 'started', at: NOW - 10 * MIN }] }, NOW);
  assert.deepEqual([running.state, running.holdsSlot], ['working', true]);
  // Released units are never re-dispatched: the single assignment stays consumed.
  assert.equal(released.assignments, 1);
});

test('capacity denials name the units holding the slots', () => {
  const stuck = { ...A.deriveUnit({ number: 245, assignedToCopilot: true }, NOW), ...OK };
  const d = A.admit({ op: 'assign', unit: queued(70), units: [stuck], ready: readyEnv(), snapshot: snapAt(300), now: NOW });
  assert.equal(d.reason, 'capacity: 1 of 1 active (#245 halted:pickup-timeout)');
});

// ---- Status publishing (display only) ----------------------------------------------------------
function statusGithub(existing = []) {
  const writes = [];
  return { writes, github: {
    paginate: async (fn, a) => fn(a),
    rest: { issues: {
      listComments: () => existing,
      createComment: async a => { writes.push(['create', a.issue_number, a.body]); },
      updateComment: async a => { writes.push(['update', a.comment_id, a.body]); },
    } } } };
}
const botC = (id, body) => ({ id, body, user: { login: 'github-actions[bot]', type: 'Bot' } });

test('billing status: verified figures, freshness, reserve and exclusions; no mentions or secrets', async () => {
  const r = await B.readBudget(env({ COPILOT_BILLING_READ_TOKEN: 'ghp_SECRET' }), { now: new Date(NOW), fetchImpl: okFetch(810) });
  const p = S.billingPayload(r, { now: new Date(NOW) });
  assert.equal(p.ok, true);
  assert.deepEqual([p.current.reportedCredits, p.current.allowanceCredits, p.current.reserveCredits, p.current.effectiveCredits,
    p.current.remainingVerifiedCredits, p.current.remainingAfterReserveCredits], [810, 1500, 150, 960, 690, 540]);
  assert.equal(p.lastSuccessAt, iso(NOW));
  assert.ok(p.excludes.includes('ChatGPT usage'));
  const body = S.formatStatus('billing', p, S.billingSummary(p));
  assert.ok(body.startsWith(S.MARKERS.billing));
  assert.doesNotMatch(body, /SECRET|@AARMOU|Bearer/);
  assert.deepEqual(S.parseStatus(body).current, p.current);
});

test('failed billing check keeps the last successful reading for display and states the block', () => {
  const prev = { ok: true, current: { measuredAt: iso(NOW - 4 * HOUR), reportedCredits: 800, allowanceCredits: 1500 } };
  const p = S.billingPayload({ snapshot: null, status: { level: 'unknown' }, error: 'billing-http-401' }, { previous: prev, now: new Date(NOW) });
  assert.equal(p.ok, false); assert.equal(p.level, 'unknown'); assert.equal(p.blocksDispatch, true);
  assert.equal(p.lastSuccessAt, iso(NOW - 4 * HOUR));
  assert.match(p.action, /invalid or expired/);
  assert.equal(p.current, undefined, 'stale figures are never presented as current');
});

test('status comment is edited in place, only our own bot comment is touched, failures never throw', async () => {
  const forged = { id: 1, body: S.MARKERS.billing + '\n```json\n{"kind":"billing"}\n```', user: { login: 'mallory', type: 'User' } };
  const mine = botC(2, S.MARKERS.billing + '\nold');
  const a = statusGithub([forged, mine]);
  assert.equal(await S.publishStatus({ ...a, owner: 'AARMOU', repo: 'arcana_table', kind: 'billing', payload: { ok: true } }), 'updated');
  assert.deepEqual(a.writes.map(w => w.slice(0, 2)), [['update', 2]]);
  const b = statusGithub([forged]);
  assert.equal(await S.publishStatus({ ...b, owner: 'AARMOU', repo: 'arcana_table', kind: 'billing', payload: {} }), 'created');
  assert.deepEqual(b.writes[0].slice(0, 2), ['create', 78]);
  const broken = { paginate: async () => { throw Object.assign(Error('x'), { status: 403 }); }, rest: { issues: { listComments() {} } } };
  assert.equal(await S.publishStatus({ github: broken, owner: 'AARMOU', repo: 'arcana_table', kind: 'billing', payload: {} }), 'failed:403');
  assert.throws(() => S.formatStatus('admission', {}));
  assert.equal(S.parseStatus('<!-- arcana-control-status-v1:billing -->\n```json\n{"kind":"dispatcher"}\n```'), null);
});

// ---- Dispatcher workflow entry point: dry run, concurrency, status --------------------------------
const BASE = 'gpt/arcana-integration-next', BASE_SHA = 'c'.repeat(40);
const BODY = ['## Cards', '- Eldrazi Monument', '', '## Acceptance', '- Exact Oracle behavior with executable regression tests', '', 'Base: ' + BASE].join('\n');
const issue = (n, extra = {}) => ({ number: n, title: '[Arcana Queue] deck ' + n, body: BODY, labels: [], assignees: [], state: 'open', author_association: 'OWNER', ...extra });
function dispatchGithub({ issues = [issue(70)], prs = [], comments = {} } = {}) {
  const writes = [];
  const github = {
    graphql: async () => ({ repository: { issue: { createdAt: iso(Date.now() - 2 * HOUR) } } }),
    paginate: async (fn, a) => fn(a),
    rest: {
      issues: {
        listForRepo: () => issues, listComments: a => comments[a.issue_number] || [], listEventsForTimeline: () => [],
        createComment: async a => { writes.push(['comment', a.issue_number, a.body]); },
        updateComment: async a => { writes.push(['update', a.comment_id, a.body]); },
        removeLabel: async a => { writes.push(['removeLabel', a.issue_number]); },
        get: async a => ({ data: { ...issues.find(i => i.number === a.issue_number), assignees: [] } }),
      },
      pulls: { list: () => prs },
      repos: { getBranch: async () => ({ data: { commit: { sha: BASE_SHA } } }) },
      actions: { listWorkflowRunsForRepo: () => [{ head_sha: BASE_SHA, name: 'Arcana Development CI', conclusion: 'success' }] },
    },
  };
  return { github, writes };
}
const coreStub = () => ({ log: [], info() {}, warning() {}, setFailed() {}, summary: { addHeading() { return this; }, addCodeBlock() { return this; }, addRaw() { return this; }, async write() {} } });
const wfEnv = (x = {}) => ({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'normal', ARCANA_INTEGRATION_BASE: BASE,
  COPILOT_DISPATCH_TOKEN: 't', ...x });
const goodBudget = async () => ({ snapshot: { account: 'AARMOU', metric: 'ai_credits', period: '2026-10', used: 450, reportedUsed: 300, reserved: 150,
  allowance: 1500, measuredAt: new Date().toISOString() }, status: { level: 'normal' }, error: null });

test('manual dry_run input forces a non-billable dry run even when normal dispatch is enabled', async () => {
  const f = dispatchGithub(); const calls = [];
  const r = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 1, eventName: 'workflow_dispatch',
    payload: { inputs: { dry_run: 'true' } } }, core: coreStub(), env: wfEnv(), readBudget: goodBudget,
    fetchImpl: async () => { calls.push(1); return { ok: true, json: async () => ({}) }; } });
  assert.equal(r.result.state, 'dry-run');
  assert.equal(calls.length, 0, 'no assignment call');
  assert.ok(f.writes.every(w => w[0] !== 'removeLabel'));
  assert.ok(f.writes.filter(w => w[0] === 'comment').every(w => w[1] === 78), 'only the status comment is written');
  assert.equal(r.payload.forcedDryRun, true);
  assert.equal(r.payload.decision.wouldAllow, true);
  assert.equal(r.payload.liabilities.candidate, 60);
});

test('dispatcher publishes an actionable status when dispatch is off, without loading units', async () => {
  const f = dispatchGithub();
  const r = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 2, eventName: 'schedule', payload: {} },
    core: coreStub(), env: wfEnv({ ARCANA_AUTOMATION_READY: '' }), readBudget: goodBudget });
  assert.equal(r.result.state, 'readiness-off');
  assert.match(r.payload.action, /ARCANA_AUTOMATION_READY/);
  assert.equal(r.payload.dispatchReady, false);
  assert.equal(r.payload.billing.ok, true, 'billing freshness still reported');
  assert.equal(r.published, 'created');
});

test('nothing-eligible explains every skipped queued unit (no silent block)', async () => {
  const pr = { number: 300, body: 'Unit: #70', user: { login: 'AARMOU' } };
  const f = dispatchGithub({ issues: [issue(70), issue(71, { author_association: 'NONE' })], prs: [pr] });
  const r = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 3, eventName: 'schedule', payload: {} },
    core: coreStub(), env: wfEnv(), readBudget: goodBudget });
  assert.equal(r.result.state, 'nothing-eligible');
  const skip = Object.fromEntries(r.payload.workers.list.map(u => [u.n, u.skip]));
  assert.equal(skip[70], 'has-open-pr #300');
  assert.match(skip[71], /^untrusted-origin: author-untrusted/);
});

test('budget denial status carries the billing owner action', async () => {
  const f = dispatchGithub();
  const r = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 4, eventName: 'schedule', payload: {} },
    core: coreStub(), env: wfEnv(), readBudget: async () => ({ snapshot: null, status: { level: 'unknown' }, error: 'missing-token' }) });
  assert.equal(r.result.state, 'denied');
  assert.match(r.result.reason, /^budget: unknown/);
  assert.match(r.payload.action, /COPILOT_BILLING_READ_TOKEN/);
  assert.ok(f.writes.every(w => w[1] === 78), 'no ledger writes on denial');
});

test('concurrent dispatch: every paid workflow shares one serialized group; status never feeds admission', () => {
  for (const f of ['arcana-card-dispatcher', 'arcana-ci-failure-router']) {
    const y = fs.readFileSync(`.github/workflows/${f}.yml`, 'utf8');
    assert.match(y, /concurrency:\n  group: arcana-copilot-control\n  cancel-in-progress: false/, f);
  }
  for (const f of ['admission.cjs', 'dispatch-runner.cjs', 'failure-routing.cjs', 'units.cjs'])
    assert.doesNotMatch(fs.readFileSync(require.resolve('./' + f), 'utf8'), /control-status|parseStatus/, f);
});

test('label-event trigger is limited to control labels on main and keeps the readiness gate', () => {
  const y = fs.readFileSync('.github/workflows/arcana-card-dispatcher.yml', 'utf8');
  assert.match(y, /issues:\n    types: \[labeled\]/);
  assert.match(y, /github\.ref == 'refs\/heads\/main'/);
  assert.match(y, /\["arcana:authorize-once","wu:resolve-interrupted","wu:halted"\]/);
  assert.match(y, /dry_run:/);
  assert.doesNotMatch(y, /ARCANA_AUTOMATION_READY: ['"]?true/);
  assert.match(y, /ARCANA_AUTOMATION_READY: \$\{\{ vars\.ARCANA_AUTOMATION_READY \}\}/);
});

// ---- Retry behaviour ---------------------------------------------------------------------------
test('assignment errors are never retried by a later run; repairs stop at the per-unit ceiling', async () => {
  const errored = { ...A.deriveUnit({ number: 70, ledger: [{ op: 'authorizing', run: '9', at: NOW - 5 * MIN },
    { op: 'attempt-result', run: '9', result: 'error', at: NOW - 5 * MIN }] }, NOW), actionable: { ok: true, missing: [] }, ...OK };
  const d = A.admit({ op: 'assign', unit: errored, units: [errored], ready: readyEnv({ ARCANA_MAX_ACTIVE_UNITS: '2' }), snapshot: snapAt(300), now: NOW });
  assert.equal(d.allowed, false); assert.match(d.reason, /unit state pending-pickup/);
  const repaired = { ...A.deriveUnit({ number: 71, assignedToCopilot: true,
    ledger: [{ op: 'repair-request', run: '1', at: NOW - 5 * HOUR }, { op: 'repair-request', run: '2', at: NOW - 4 * HOUR }],
    sessions: [{ event: 'started', at: NOW - 3 * HOUR }, { event: 'finished', at: NOW - 2 * HOUR }] }, NOW), ...OK };
  const r = A.admit({ op: 'repair', unit: repaired, units: [repaired], ready: readyEnv({ ARCANA_REPAIR_MODE: 'request' }), snapshot: snapAt(300), now: NOW });
  assert.equal(r.allowed, false); assert.equal(r.reason, 'repair ceiling reached');
});

test('billing watch publishes status and includes the reason and next step in unknown alerts', async () => {
  const comments = []; const writes = [];
  const github = { paginate: async (fn, a) => fn(a), rest: { issues: {
    listComments: () => comments, createComment: async a => { writes.push(a.body); }, updateComment: async () => {} } } };
  const r = await W.billingWatch({ github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' } }, core: coreStub(), env: { ARCANA_AI_CREDIT_BUDGET: '1500' },
    readBudget: B.readBudget, now: () => new Date(NOW) });
  assert.equal(r.result.error, 'missing-token');
  assert.equal(r.published, 'created');
  const alert = writes.find(b => b.startsWith(B.MARKER));
  assert.match(alert, /Reason: `missing-token`/); assert.match(alert, /COPILOT_BILLING_READ_TOKEN/);
  assert.ok(writes.some(b => b.startsWith(S.MARKERS.billing)));
});

test('ci-not-ready names the run waiting for approval instead of staying silently blocked', async () => {
  const f = dispatchGithub();
  f.github.rest.actions.listWorkflowRunsForRepo = a => a.status === 'success' ? [] :
    [{ id: 40359905589, head_sha: BASE_SHA, name: 'Arcana Development CI', event: 'pull_request', status: 'completed', conclusion: 'action_required' }];
  const r = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 5, eventName: 'schedule', payload: {} },
    core: coreStub(), env: wfEnv(), readBudget: goodBudget });
  assert.equal(r.result.state, 'ci-not-ready');
  assert.match(r.payload.action, /40359905589 .* waiting for approval/);
  assert.equal(r.payload.dispatchReady, false);
  f.github.rest.actions.listWorkflowRunsForRepo = () => [];
  const none = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 6, eventName: 'schedule', payload: {} },
    core: coreStub(), env: wfEnv(), readBudget: goodBudget });
  assert.match(none.payload.action, /no Arcana Development CI run exists .* GITHUB_TOKEN/);
});

test('a qualified "Acceptance — ..." heading is still the Acceptance section; boilerplate still fails', () => {
  const body = ['## Cards', '- The Swarmlord', '', '## Acceptance — complete rules behavior, not registration-only',
    '- **Oracle:** 5/5 with counters per command-zone cast', '- **Executable scenarios:** positive and negative tests', '', 'Base: ' + BASE].join('\n');
  assert.deepEqual(A.unitActionable(body, BASE), { ok: true, missing: [] });
  assert.equal(A.unitActionable(body.replace('## Acceptance —', '## Acceptance:'), BASE).ok, true);
  assert.equal(A.unitActionable(body.replace('## Acceptance — complete rules behavior, not registration-only', '## Acceptability'), BASE).ok, false);
  const generic = body.replace(/- \*\*Oracle[\s\S]*?tests/, '- Exact current Oracle behavior (cost, types, every ability, targeting and timing), checked against authoritative card records');
  assert.equal(A.unitActionable(generic, BASE).ok, false);
});

test('normal mode skips an incomplete queue issue instead of letting it block every later unit', async () => {
  const f = dispatchGithub({ issues: [issue(70, { body: '## Cards\n- X\n\nBase: ' + BASE }), issue(71)] });
  const r = await W.dispatcher({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 7, eventName: 'workflow_dispatch',
    payload: { inputs: { dry_run: true } } }, core: coreStub(), env: wfEnv(), readBudget: goodBudget });
  assert.equal(r.result.state, 'dry-run');
  assert.equal(r.result.issue, 71);
  const none = dispatchGithub({ issues: [issue(70, { body: '## Cards\n- X\n\nBase: ' + BASE })] });
  const r2 = await W.dispatcher({ github: none.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 8, eventName: 'schedule', payload: {} },
    core: coreStub(), env: wfEnv(), readBudget: goodBudget });
  assert.equal(r2.result.state, 'nothing-eligible');
  assert.equal(r2.payload.workers.list[0].skip, 'not-actionable: acceptance');
});
