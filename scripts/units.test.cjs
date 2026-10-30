'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { loadUnits, unitOf } = require('./units.cjs');

const T = m => new Date(Date.now() - m * 60000).toISOString();
// Mirrors the real #54 case: Copilot opened PR #59 and GitHub recorded a cross-reference,
// but the PR body never mentions #54.
function fake({ crossBy = 'Copilot', prBody = 'Refs #34 and #81', connected = true } = {}) {
  const timelines = {
    54: [{ event: 'assigned', created_at: T(600) },
      { event: 'cross-referenced', created_at: T(599), actor: { login: crossBy }, source: { issue: { number: 59, pull_request: {} } } },
      ...(connected ? [{ event: 'connected', created_at: T(599), subject: { type: 'pull_request', number: 59 } }] : [])],
    68: [{ event: 'cross-referenced', created_at: T(500), actor: { login: 'Copilot' }, source: { issue: { number: 59, pull_request: {} } } }],
    59: [{ event: 'copilot_work_started', created_at: T(300) }, { event: 'copilot_work_finished', created_at: T(240) }],
  };
  const data = {
    issues: [{ number: 54, title: '[Coding agent] Eldrazi', body: '', labels: [], assignees: [{ login: 'Copilot' }] },
      { number: 68, title: '[Arcana Queue] other', body: '', labels: [], assignees: [] }],
    prs: [{ number: 59, body: prBody }],
  };
  return { paginate: async (fn, a) => fn(a), rest: {
    issues: { listForRepo: () => data.issues, listComments: () => [], listEventsForTimeline: a => timelines[a.issue_number] || [] },
    pulls: { list: () => data.prs } } };
}

test('GitHub connected link assigns the PR to exactly one unit, even without a body reference', async () => {
  const d = await loadUnits({ github: fake(), owner: 'o', repo: 'r' });
  const u54 = d.units.find(u => u.raw.number === 54), u68 = d.units.find(u => u.raw.number === 68);
  assert.deepEqual(u54.prs.map(p => p.number), [59]);
  assert.deepEqual([u54.derived.state, u54.derived.holdsSlot], ['review', false]);
  // Mere cross-references (PR text mentioning another queue issue) do not link.
  assert.equal(u68.prs.length, 0);
});

test('without a connected link or reference, the stale assignment keeps its slot (safe direction)', async () => {
  const u = (await loadUnits({ github: fake({ connected: false }), owner: 'o', repo: 'r' })).units.find(x => x.raw.number === 54);
  assert.equal(u.prs.length, 0);
  assert.deepEqual([u.derived.state, u.derived.reason, u.derived.holdsSlot], ['halted', 'pickup-timeout', true]);
});

test('an explicit Unit line overrides the connected link', async () => {
  const d = await loadUnits({ github: fake({ prBody: 'Unit: #68' }), owner: 'o', repo: 'r' });
  assert.deepEqual(d.units.find(u => u.raw.number === 68).prs.map(p => p.number), [59]);
  assert.equal(d.units.find(u => u.raw.number === 54).prs.length, 0);
});

test('explicit Unit lines win over other references', () => {
  assert.equal(unitOf('Unit: #68\nsee #54', new Set([54, 68])), 68);
  assert.equal(unitOf('see #54 and #68', new Set([54, 68])), 54);
  assert.equal(unitOf('Unit: #12', new Set([54])), null);
});

test('a PR connected to two units is ambiguous: counted against both, flagged, unless a Unit line decides', async () => {
  const g = fake();
  const orig = g.rest.issues.listEventsForTimeline;
  g.rest.issues.listEventsForTimeline = a => a.issue_number === 68
    ? [{ event: 'connected', created_at: T(100), subject: { type: 'pull_request', number: 59 } }] : orig(a);
  const d = await loadUnits({ github: g, owner: 'o', repo: 'r' });
  for (const n of [54, 68]) {
    const u = d.units.find(x => x.raw.number === n);
    assert.deepEqual(u.prs.map(p => p.number), [59], '#' + n); assert.equal(u.derived.ambiguousLink, true, '#' + n);
  }
  const h = fake({ prBody: 'Unit: #54' }); h.rest.issues.listEventsForTimeline = g.rest.issues.listEventsForTimeline;
  const e = await loadUnits({ github: h, owner: 'o', repo: 'r' });
  assert.equal(e.units.find(x => x.raw.number === 54).derived.ambiguousLink, false);
  assert.equal(e.units.find(x => x.raw.number === 68).prs.length, 0);
});

test('a disconnected link no longer counts', async () => {
  const g = fake();
  const orig = g.rest.issues.listEventsForTimeline;
  g.rest.issues.listEventsForTimeline = a => a.issue_number === 54
    ? [...orig(a), { event: 'disconnected', created_at: T(50), subject: { type: 'pull_request', number: 59 } }] : orig(a);
  const u = (await loadUnits({ github: g, owner: 'o', repo: 'r' })).units.find(x => x.raw.number === 54);
  assert.equal(u.prs.length, 0);
});

// #526 stayed open after #529 merged. A closing keyword is not completion proof;
// use the actual PR, merged registration, executed premerge and session timeline.
const NOW = Date.parse('2026-10-30T19:47:00Z');
const at = m => new Date(NOW - m * 60000).toISOString();
function mergedSuite({ closed = false } = {}) {
  const { SUITE_MARKER, formatLedger } = require('./admission.cjs');
  const issue = { number: 526, state: closed ? 'closed' : 'open', title: '[Arcana Suite] Shield Sphere',
    body: SUITE_MARKER + '\nCard unit: #509\nCard PR: #518\nSuite key: shield-sphere\nBase: main\n',
    labels: [], assignees: [{ login: 'Copilot' }], user: { login: 'github-actions[bot]', type: 'Bot' }, created_at: at(30) };
  const pr = { number: 529, state: 'closed', merged: true, merged_at: at(10), merge_commit_sha: 'a'.repeat(40),
    body: 'Unit: #526\nFixes #526', user: { login: 'Copilot' },
    head: { sha: 'b'.repeat(40), repo: { full_name: 'o/r' } }, base: { ref: 'main', repo: { full_name: 'o/r' } } };
  const source = 'certification/ShieldSphereIndependentCertificationTest.kt';
  const suite = { key: 'shield-sphere', issue: 509, source };
  const run = { id: 91, name: 'Arcana Card Certification Premerge', head_sha: pr.head.sha,
    head_repository: { full_name: 'o/r' }, event: 'pull_request', conclusion: 'success', updated_at: at(11) };
  const data = { issue, pr, suite, run,
    files: [{ filename: 'certification/suites/shield-sphere.json', status: 'added' }, { filename: source, status: 'added' }],
    jobs: [{ name: 'Execute trusted independent card scenarios (shield-sphere, 518, 509, Add Shield Sphere custom-car...', conclusion: 'success',
      steps: [{ name: 'Verify every registered independent test passed using the shared runner', conclusion: 'success' }] }],
    events: [{ event: 'connected', subject: { type: 'pull_request', number: 529 }, created_at: at(15) }],
    sessions: [{ event: 'copilot_work_started', created_at: at(15) }, { event: 'copilot_work_finished', created_at: at(12) }] };
  const calls = [];
  const comments = ['authorizing', 'attempt-result'].map(op => ({ user: { login: 'github-actions[bot]', type: 'Bot' },
    created_at: at(16), body: formatLedger({ op, issue: 526, run: 'original', ...(op === 'attempt-result' ? { result: 'confirmed' } : {}), at: at(16) }) }));
  const github = { paginate: async (fn, a) => { assert.equal(typeof fn, 'function'); calls.push(a); return fn(a); },
    graphql: async () => ({ repository: { issue: { createdAt: issue.created_at, lastEditedAt: null } } }),
    rest: {
      issues: { listForRepo: a => a.state === issue.state ? [issue] : [], listComments: () => comments,
        listEventsForTimeline: a => a.issue_number === 526 ? data.events : data.sessions },
      pulls: { list: () => [], get: async () => ({ data: data.pr }), listFiles: () => data.files },
      repos: { getContent: async a => { assert.equal(a.ref, pr.merge_commit_sha); return { data: { content: Buffer.from(JSON.stringify(data.suite)).toString('base64') } }; } },
      actions: { listWorkflowRuns: a => { assert.equal(a.workflow_id, 'arcana-card-certification-premerge.yml'); return [data.run]; }, listJobsForWorkflowRun: () => data.jobs },
    } };
  return { data, github, calls, comments, load: async (opts = {}) => (await loadUnits({ github, owner: 'o', repo: 'r', now: NOW, ...opts })).units[0] };
}

for (const closed of [false, true]) test(`merged suite with ${closed ? 'closed' : 'open'} issue releases capacity, keeps billing, cannot be repaired or reassigned`, async () => {
  const { load } = mergedSuite({ closed }); const u = await load();
  assert.equal(u.derived.state, 'completed'); assert.equal(u.derived.holdsSlot, false);
  assert.equal(u.derived.recentlyFinished, true); assert.equal(u.derived.assignments, 1);
  assert.equal(require('./control-status.cjs').summarizeUnits([u.derived]).completed, 1);
  assert.equal(u.raw.suiteCompletion.pr, 529); assert.equal(u.raw.suiteCompletion.run, 91);
  const A = require('./admission.cjs');
  const ready = A.readiness({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'normal', ARCANA_REPAIR_MODE: 'request' });
  const trusted = { ...u.derived, provenance: { ok: true } };
  for (const op of ['assign', 'repair']) assert.equal(A.admit({ op, unit: trusted, units: [trusted], ready }).reason, 'unit state completed');
  const b = A.budgetWithReservations({ snapshot: { used: 0, allowance: 1000, measuredAt: at(0) }, active: 0, pendingRepairs: 0,
    recentFinished: Number(u.derived.recentlyFinished), candidate: 'assign', ready, now: NOW });
  assert.equal(b.liabilities.recentlyFinished, 60); assert.equal(b.liabilities.activeUnits, 0);
});

test('a repeated/delayed observation is idempotent and the configured billing lag expires', async () => {
  const { load, data } = mergedSuite({ closed: true });
  data.events.push(...data.events); // duplicate connected event
  const first = await load(), second = await load();
  assert.deepEqual(first.raw.suiteCompletion, second.raw.suiteCompletion);
  assert.deepEqual(first.derived, second.derived); assert.equal(second.prs.length, 1);
  assert.equal((await load({ lagMs: 10 * 60000 })).derived.recentlyFinished, false);
  assert.equal((await load({ now: NOW + 25 * 3600000 })).derived.recentlyFinished, false);
});

test('merged metadata never fabricates session completion; missing events keep the reserved slot', async () => {
  for (const events of [[], [{ event: 'copilot_work_started', created_at: at(15) }], [{ event: 'copilot_work_finished', created_at: at(12) }]]) {
    const { data, load } = mergedSuite(); data.sessions = events;
    const u = await load(); assert.equal(u.derived.holdsSlot, true); assert.equal(u.derived.recentlyFinished, false);
  }
});

test('late post-merge sessions and pending repairs reclaim the slot, including on old closed issues', async () => {
  for (const closed of [false, true]) {
    const f = mergedSuite({ closed }); f.data.issue.created_at = at(10000);
    f.data.sessions.push({ event: 'copilot_work_started', created_at: at(1) });
    let u = await f.load(); assert.equal(u.derived.state, 'working'); assert.equal(u.derived.holdsSlot, true);
    f.data.sessions.push({ event: 'copilot_work_finished', created_at: at(0) });
    u = await f.load(); assert.equal(u.derived.state, 'completed'); assert.equal(u.derived.recentlyFinished, true);
    f.comments.push({ user: { login: 'github-actions[bot]', type: 'Bot' }, created_at: at(0),
      body: require('./admission.cjs').formatLedger({ op: 'repair-request', issue: 526, run: 'late', at: at(0) }) });
    assert.equal((await f.load()).derived.holdsSlot, true);
  }
});

test('closed units with no merge proof stay in accounting without becoming assignment or repair candidates', async () => {
  const f = mergedSuite({ closed: true }); f.data.pr.merged = false;
  const u = await f.load(); assert.equal(u.derived.state, 'halted'); assert.equal(u.derived.reason, 'closed-unit');
  assert.equal(u.derived.recentlyFinished, true);
});

test('unrelated references, disconnected links, and forged completion comments do not release slots', async () => {
  for (const disconnected of [false, true]) {
    const f = mergedSuite(); f.comments.push({ user: { login: 'someone', type: 'User' }, created_at: at(0),
      body: '<!-- arcana-suite-completion-v1 -->\nmerged:true\npr:529' });
    f.data.events = disconnected ? [...f.data.events, { event: 'disconnected', subject: { type: 'pull_request', number: 529 } }]
      : [{ event: 'cross-referenced', source: { issue: { number: 529, pull_request: {} } } }];
    const u = await f.load(); assert.equal(u.prs.length, 0); assert.equal(u.derived.holdsSlot, true); assert.equal(u.raw.suiteCompletion, null);
  }
});

test('wrong suite/head/job/files/merge never creates a completed unit', async () => {
  const variants = [d => d.suite.key = 'wrong', d => d.suite.issue = 1, d => d.files.push({ filename: 'scripts/evil.cjs', status: 'added' }),
    d => d.files[0].status = 'modified', d => d.pr.merged = false, d => d.pr.merged_at = at(-1), d => d.pr.merge_commit_sha = 'bad',
    d => d.run.head_sha = 'c'.repeat(40), d => d.run.conclusion = 'failure', d => d.run.updated_at = at(-1),
    d => d.jobs[0].conclusion = 'skipped', d => d.jobs[0].steps[0].conclusion = 'skipped', d => d.jobs[0].name = 'Execute trusted independent card scenarios (wrong, 518)'];
  for (const change of variants) { const f = mergedSuite(); change(f.data); const u = await f.load();
    assert.equal(u.raw.suiteCompletion, null); assert.notEqual(u.derived.state, 'completed'); }
});

test('unavailable GitHub session or merge evidence fails the entire admission read closed', async () => {
  const variants = [g => g.rest.pulls.get = async () => { throw Error('403'); },
    g => g.rest.issues.listEventsForTimeline = () => { throw Error('503'); },
    g => g.rest.repos.getContent = async () => { throw Error('404'); },
    g => g.rest.actions.listWorkflowRuns = () => { throw Error('403'); },
    g => g.rest.issues.listForRepo = a => { if (a.state === 'closed') throw Error('403'); return []; }];
  for (const change of variants) { const f = mergedSuite(); change(f.github); await assert.rejects(f.load); }
  for (const change of [d => d.pr.user.login = 'outsider', d => d.pr.head.repo.full_name = 'fork/r', d => d.pr.base.ref = 'other']) {
    const f = mergedSuite(); change(f.data); await assert.rejects(f.load, /Unverified connected suite PR/);
  }
});
