'use strict';
// Paid Copilot work may only follow instructions written by trusted people, and a one-shot
// approval covers only the content that existed when the owner gave it.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const A = require('./admission.cjs');
const { runDispatch } = require('./dispatch-runner.cjs');
const { createAuthorizer } = require('./failure-routing.cjs');

const BASE = 'gpt/arcana-integration-next';
const SHA = 'c'.repeat(40);
const ago = m => new Date(Date.now() - m * 60000).toISOString();
const BODY = ['## Cards', '- Eldrazi Monument', '', '## Acceptance',
  '- Eldrazi Monument: exact Oracle behavior with executable regression tests', '', 'Base: ' + BASE].join('\n');
const issue = (n, { association = 'OWNER', labels = [] } = {}) => ({ number: n, title: '[Arcana Queue] unit ' + n, body: BODY,
  labels: labels.map(name => ({ name })), assignees: [], state: 'open', author_association: association });
const comment = (association, login = 'someone', type = 'User') => ({ body: 'hi', created_at: ago(5), author_association: association, user: { login, type } });
const labeled = (m, actor = 'AARMOU') => ({ event: 'labeled', created_at: ago(m), label: { name: A.ONE_SHOT_LABEL }, actor: actor === null ? undefined : { login: actor } });
const renamed = m => ({ event: 'renamed', created_at: ago(m) });

// edited(call) → lastEditedAt for the nth GraphQL read (1 = admission, 2 = pre-assignment check).
// comments(n, call) likewise for listComments on the unit issue.
function fake({ issues, events = () => [labeled(10)], edited = () => null, comments = () => [], freshBody } = {}) {
  const writes = [], assigns = [];
  let gq = 0; const cc = {};
  const github = {
    graphql: async () => { gq++; const at = edited(gq); if (at instanceof Error) throw at;
      return { repository: { issue: { createdAt: ago(120), lastEditedAt: at } } }; },
    paginate: async (fn, a) => fn(a),
    rest: {
      issues: {
        listForRepo: () => issues,
        listComments: a => { cc[a.issue_number] = (cc[a.issue_number] || 0) + 1; return comments(a.issue_number, cc[a.issue_number]); },
        listEventsForTimeline: a => events(a.issue_number),
        createComment: async a => writes.push(['comment', a.issue_number, a.body]),
        removeLabel: async a => writes.push(['removeLabel', a.issue_number]),
        get: async a => ({ data: { ...issues.find(i => i.number === a.issue_number), assignees: [], ...(freshBody ? { body: freshBody } : {}) } }),
      },
      pulls: { list: () => [] },
      repos: { getBranch: async () => ({ data: { commit: { sha: SHA } } }) },
      actions: { listWorkflowRunsForRepo: () => [{ head_sha: SHA, name: 'Arcana Development CI', conclusion: 'success' }] },
    },
  };
  const fetchImpl = async u => { assigns.push(u); return { ok: true, json: async () => ({ assignees: [{ login: 'Copilot' }] }) }; };
  return { github, writes, assigns, fetchImpl };
}
const core = { info() {}, warning() {}, setFailed() {}, summary: { addHeading() { return this; }, addCodeBlock() { return this; }, async write() {} } };
const readBudget = async () => ({ snapshot: { used: 100, allowance: 1500, measuredAt: new Date().toISOString() } });
const dispatch = (f, mode, extraEnv = {}, coreImpl = core) => runDispatch({ github: f.github, context: { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 7 }, core: coreImpl,
  env: { ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: mode, ARCANA_INTEGRATION_BASE: BASE, COPILOT_DISPATCH_TOKEN: 't',
    ARCANA_FEASIBILITY: JSON.stringify({ schema: 1, baseSha: 'c'.repeat(40), cards: { 'eldrazi monument': { name: 'Eldrazi Monument', readiness: 'SUPPORTED', route: 'copilot' } } }), ...extraEnv },
  readBudget, fetchImpl: f.fetchImpl });

test('provenance: author must be owner, member or collaborator', () => {
  for (const a of ['OWNER', 'MEMBER', 'COLLABORATOR']) assert.ok(A.provenance({ authorAssociation: a, commenters: [] }).ok, a);
  for (const a of ['NONE', 'CONTRIBUTOR', 'FIRST_TIMER', 'FIRST_TIME_CONTRIBUTOR', 'MANNEQUIN', undefined])
    assert.deepEqual(A.provenance({ authorAssociation: a, commenters: [] }).reasons, ['author-untrusted'], String(a));
});

test('provenance: any untrusted comment blocks; our bots and Copilot are trusted; unknown comments fail closed', () => {
  const p = c => A.provenance({ authorAssociation: 'OWNER', commenters: c });
  assert.ok(p([{ association: 'COLLABORATOR' }, { association: 'NONE', login: 'github-actions[bot]', type: 'Bot' },
    { association: 'NONE', login: 'Copilot', type: 'Bot' }]).ok);
  assert.deepEqual(p([{ association: 'NONE', login: 'stranger', type: 'User' }]).reasons, ['untrusted-comments']);
  // A user account named like a bot is not the bot.
  assert.deepEqual(p([{ association: 'NONE', login: 'Copilot', type: 'User' }]).reasons, ['untrusted-comments']);
  assert.deepEqual(A.provenance({ authorAssociation: 'OWNER' }).reasons, ['comments-unavailable']);
});

test('provenance: one-shot approval must be newer than the last edit and rename; missing history fails closed', () => {
  const t = Date.now();
  const p = approval => A.provenance({ authorAssociation: 'OWNER', commenters: [], approval: approval && { actor: 'AARMOU', ...approval } },
    { needApproval: true, approvers: ['AARMOU'] }).reasons;
  assert.deepEqual(p({ labeledAt: t, contentAt: t - 1000, renamedAfter: false }), []);
  assert.deepEqual(p(null), ['approval-missing']);
  assert.deepEqual(p({ labeledAt: t, contentAt: t + 1000, renamedAfter: false }), ['edited-after-approval']);
  assert.deepEqual(p({ labeledAt: t, contentAt: t, renamedAfter: false }), ['edited-after-approval']);
  assert.deepEqual(p({ labeledAt: t, contentAt: NaN, renamedAfter: false }), ['edit-history-unavailable']);
  assert.deepEqual(p({ labeledAt: t, contentAt: t - 1000, renamedAfter: true }), ['renamed-after-approval']);
  assert.deepEqual(p({ labeledAt: t, contentAt: t - 1000 }), ['rename-history-unavailable']);
});

test('admit refuses an unchecked unit on every paid path, and dry-run reports why', () => {
  const ready = A.readiness({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'normal', ARCANA_REPAIR_MODE: 'request' });
  const snapshot = { used: 100, allowance: 1500, measuredAt: new Date().toISOString() };
  const q = { number: 1, state: 'queued', assignments: 0, repairs: 0, actionable: { ok: true } };
  assert.match(A.admit({ op: 'assign', unit: q, ready, snapshot }).reason, /untrusted-origin: unchecked/);
  assert.match(A.admit({ op: 'repair', unit: { ...q, state: 'review' }, ready, snapshot }).reason, /untrusted-origin: unchecked/);
  const dry = A.admit({ op: 'assign', unit: { ...q, provenance: { ok: false, reasons: ['author-untrusted'] } },
    ready: A.readiness({ ARCANA_DISPATCH_MODE: 'dry-run' }), snapshot });
  assert.equal(dry.allowed, false); assert.match(dry.reason, /author-untrusted/);
});

test('normal mode: a stranger\'s well-formed issue is never a candidate and cannot block trusted work', async () => {
  const solo = fake({ issues: [issue(50, { association: 'NONE' })], events: () => [] });
  assert.equal((await dispatch(solo, 'normal')).state, 'nothing-eligible');
  assert.equal(solo.assigns.length, 0); assert.equal(solo.writes.length, 0);
  const mixed = fake({ issues: [issue(50, { association: 'NONE' }), issue(68)], events: () => [] });
  const r = await dispatch(mixed, 'normal');
  assert.deepEqual([r.state, r.issue, r.result], ['attempted', 68, 'confirmed']);
  assert.equal(mixed.assigns.length, 1);
});

test('authorize-once: a stranger\'s issue is refused even after the owner labels it', async () => {
  const f = fake({ issues: [issue(50, { association: 'NONE', labels: [A.ONE_SHOT_LABEL] })] });
  const r = await dispatch(f, 'authorize-once');
  assert.equal(r.state, 'denied'); assert.match(r.reason, /author-untrusted/);
  assert.equal(f.writes.length, 0); assert.equal(f.assigns.length, 0);
});

test('authorize-once: edits or renames after approval, or missing edit history, are refused before anything is written', async () => {
  const cases = [
    [{ edited: () => ago(2) }, /edited-after-approval/],
    [{ events: () => [labeled(10), renamed(3)] }, /renamed-after-approval/],
    [{ edited: () => Error('graphql down') }, /edit-history-unavailable/],
    [{ events: () => [] }, /approval-missing/],
  ];
  for (const [opts, reason] of cases) {
    const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], ...opts });
    const r = await dispatch(f, 'authorize-once');
    assert.equal(r.state, 'denied', String(reason)); assert.match(r.reason, reason);
    assert.equal(f.writes.length, 0); assert.equal(f.assigns.length, 0);
  }
});

test('authorize-once: re-applying the label after an edit is a fresh, valid approval', async () => {
  const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], edited: () => ago(20), events: () => [labeled(30), labeled(5)] });
  const r = await dispatch(f, 'authorize-once');
  assert.deepEqual([r.state, r.result], ['attempted', 'confirmed']);
  assert.equal(f.assigns.length, 1);
});

test('an edit made after admission but before the paid call stops it; the attempt is recorded, never retried', async () => {
  const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], edited: call => call === 1 ? null : new Date().toISOString() });
  const r = await dispatch(f, 'authorize-once');
  assert.deepEqual([r.state, r.result], ['attempted', 'not-attempted']); assert.match(r.reason, /edited-after-approval/);
  assert.equal(f.assigns.length, 0);
  assert.deepEqual(f.writes.map(w => w[0]), ['comment', 'removeLabel', 'comment']);
  assert.match(f.writes[2][2], /result:not-attempted\nreason:revalidation edited-after-approval/);
});

test('an untrusted comment posted after admission but before the paid call stops it', async () => {
  const f = fake({ issues: [issue(68)], events: () => [], comments: (n, call) => call === 1 ? [] : [comment('NONE', 'stranger')] });
  const r = await dispatch(f, 'normal');
  assert.deepEqual([r.result], ['not-attempted']); assert.match(r.reason, /untrusted-comments/);
  assert.equal(f.assigns.length, 0);
});

// Repair path: the unit, the PR and the PR conversation must all come from trusted people.
function repairGithub({ unitAssociation = 'OWNER', prComments = [] } = {}) {
  const now = Date.now();
  const timeline = [{ event: 'copilot_work_started', created_at: new Date(now - 3600e3).toISOString() },
    { event: 'copilot_work_finished', created_at: new Date(now - 1800e3).toISOString() }];
  const github = { paginate: async (fn, a) => fn(a), rest: {
    issues: { listForRepo: () => [{ number: 67, title: '[Arcana Queue] x', labels: [], assignees: [{ login: 'Copilot' }], author_association: unitAssociation }],
      listComments: a => a.issue_number === 85 ? prComments : [], listEventsForTimeline: () => timeline, createComment: async () => {} },
    pulls: { list: () => [{ number: 85, body: 'Unit: #67' }], listReviews: () => [], listReviewComments: () => [] } } };
  return github;
}
const repairEnv = { ARCANA_AUTOMATION_READY: 'true', ARCANA_REPAIR_MODE: 'request', ARCANA_DISPATCH_MODE: 'off' };
const copilotPr = { number: 85, body: 'Unit: #67', author_association: 'NONE', user: { login: 'Copilot', type: 'Bot' } };
const authorize = (github, pr = copilotPr) => createAuthorizer({ github, owner: 'o', repo: 'r', env: repairEnv, readBudget })({ pr, run: { id: 1, head_sha: SHA } });

test('repairs: trusted unit, Copilot PR and clean conversation are admitted', async () => {
  assert.equal((await authorize(repairGithub())).allowed, true);
});

test('repairs: an untrusted unit author, PR author or PR comment refuses the paid repair', async () => {
  assert.match((await authorize(repairGithub({ unitAssociation: 'NONE' }))).reason, /author-untrusted/);
  assert.match((await authorize(repairGithub(), { ...copilotPr, user: { login: 'stranger', type: 'User' } })).reason, /pr-author-untrusted/);
  assert.match((await authorize(repairGithub({ prComments: [comment('NONE', 'stranger')] }))).reason, /untrusted-pr-comments/);
  assert.equal((await authorize(repairGithub({ prComments: [comment('OWNER', 'AARMOU')] }))).allowed, true);
});

// ---- Review of #103 (GPT): approver identity, comments after approval, fresh readiness, preflight ----
const ledgerComment = m => ({ body: A.formatLedger({ op: 'authorizing', issue: 68, run: 1, at: ago(m) }), created_at: ago(m), updated_at: ago(m),
  author_association: 'NONE', user: { login: 'github-actions[bot]', type: 'Bot' } });
const trustedComment = (createdMin, updatedMin = createdMin) => ({ body: 'also do X', created_at: ago(createdMin), updated_at: ago(updatedMin),
  author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' } });

test('approver: only listed logins approve one-shot spending; missing actor or empty list fails closed', () => {
  const t = Date.now();
  const p = (actor, approvers) => A.provenance({ authorAssociation: 'OWNER', commenters: [],
    approval: { labeledAt: t, contentAt: t - 1000, renamedAfter: false, actor } }, { needApproval: true, approvers }).reasons;
  assert.deepEqual(p('AARMOU', ['AARMOU']), []);
  assert.deepEqual(p('aarmou', ['AARMOU']), [], 'logins compare case-insensitively');
  assert.deepEqual(p('helper', ['AARMOU']), ['approval-actor-unauthorized']);
  assert.deepEqual(p(null, ['AARMOU']), ['approval-actor-missing']);
  assert.deepEqual(p('AARMOU', []), ['approvers-unconfigured']);
  assert.deepEqual(p('AARMOU', undefined), ['approvers-unconfigured']);
});

test('ARCANA_SPEND_APPROVERS parses a login list; anything malformed is a configuration error', () => {
  assert.deepEqual(A.readiness({ ARCANA_SPEND_APPROVERS: 'AARMOU, helper-2' }).approvers, ['AARMOU', 'helper-2']);
  assert.equal(A.readiness({}).approvers, null);
  for (const bad of ['AARMOU;rm', 'a b', ',', '@AARMOU']) assert.ok(A.readiness({ ARCANA_SPEND_APPROVERS: bad }).errors.length, bad);
});

test('comments: a trusted comment created or edited after the label voids the approval; only our ledger is exempt', () => {
  const { commentersOf } = require('./units.cjs');
  const t = Date.now() - 10 * 60000; // label time
  const p = comments => A.provenance({ authorAssociation: 'OWNER', commenters: commentersOf(comments),
    approval: { labeledAt: t, contentAt: t - 60000, renamedAfter: false, actor: 'AARMOU' } }, { needApproval: true, approvers: ['AARMOU'] }).reasons;
  assert.deepEqual(p([trustedComment(30)]), []);
  assert.deepEqual(p([trustedComment(5)]), ['comment-after-approval'], 'new comment');
  assert.deepEqual(p([trustedComment(30, 5)]), ['comment-after-approval'], 'old comment edited later');
  assert.deepEqual(p([ledgerComment(5)]), [], 'dispatcher ledger record');
  const botChat = { ...ledgerComment(5), body: 'not a ledger record' };
  assert.deepEqual(p([botChat]), ['comment-after-approval'], 'other bot content is not exempt');
  assert.deepEqual(p([{ ...trustedComment(30), created_at: 'garbage' }]), ['comment-history-unavailable']);
});

test('dispatch: a label applied by someone not on the approver list is refused; adding them to the list allows it', async () => {
  const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], events: () => [labeled(10, 'helper')] });
  const r = await dispatch(f, 'authorize-once');
  assert.equal(r.state, 'denied'); assert.match(r.reason, /approval-actor-unauthorized/);
  assert.equal(f.writes.length, 0); assert.equal(f.assigns.length, 0);
  const g = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], events: () => [labeled(10, 'helper')] });
  assert.equal((await dispatch(g, 'authorize-once', { ARCANA_SPEND_APPROVERS: 'AARMOU,helper' })).result, 'confirmed');
  const h = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], events: () => [labeled(10, null)] });
  assert.match((await dispatch(h, 'authorize-once')).reason, /approval-actor-missing/);
});

test('dispatch: the authorization ledger records who approved', async () => {
  const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })] });
  await dispatch(f, 'authorize-once');
  assert.match(f.writes[0][2], /op:authorizing\nissue:68\nrun:7\nreason:approved-by AARMOU/);
});

test('dispatch: a trusted comment added or edited after approval is refused before anything is written', async () => {
  for (const c of [trustedComment(5), trustedComment(30, 5)]) {
    const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], comments: () => [c] });
    const r = await dispatch(f, 'authorize-once');
    assert.equal(r.state, 'denied'); assert.match(r.reason, /comment-after-approval/);
    assert.equal(f.writes.length, 0); assert.equal(f.assigns.length, 0);
  }
});

test('dispatch: a trusted comment added between admission and the paid call stops it, with zero paid calls', async () => {
  const f = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })], comments: (n, call) => call === 1 ? [] : [trustedComment(0)] });
  const r = await dispatch(f, 'authorize-once');
  assert.deepEqual([r.result], ['not-attempted']); assert.match(r.reason, /comment-after-approval/);
  assert.equal(f.assigns.length, 0);
});

test('dispatch: a body that no longer passes the readiness rules at the paid call is not assigned (normal mode)', async () => {
  const f = fake({ issues: [issue(68)], events: () => [], freshBody: 'please do whatever seems best' });
  const r = await dispatch(f, 'normal');
  assert.deepEqual([r.result], ['not-attempted']); assert.match(r.reason, /not-actionable/);
  assert.equal(f.assigns.length, 0);
  const g = fake({ issues: [issue(68)], events: () => [], freshBody: BODY.replace('Base: ' + BASE, 'Base: main') });
  assert.match((await dispatch(g, 'normal')).reason, /base-mismatch/);
});

test('dry-run reports the edit-history preflight and spends nothing', async () => {
  const log = [];
  const c = { ...core, info: m => log.push(m) };
  const ok = fake({ issues: [issue(68, { labels: [A.ONE_SHOT_LABEL] })] });
  await dispatch(ok, 'dry-run', {}, c);
  assert.ok(log.some(m => /"editHistoryPreflight":"ok"/.test(m)));
  assert.equal(ok.writes.length, 0); assert.equal(ok.assigns.length, 0);
  log.length = 0;
  const down = fake({ issues: [issue(68)], events: () => [], edited: () => Error('graphql down') });
  await dispatch(down, 'dry-run', {}, c);
  assert.ok(log.some(m => /"editHistoryPreflight":"unavailable"/.test(m)));
  assert.equal(down.writes.length, 0); assert.equal(down.assigns.length, 0);
});
