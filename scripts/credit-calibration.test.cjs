'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { calibrate, calibrateReady, copilotSessionsThisMonth, COPILOT_WORKFLOW_PATH } = require('./credit-calibration.cjs');
const A = require('./admission.cjs');

test('calibrate: measured credits per session with margin, clamped, and never from too few sessions', () => {
  assert.equal(calibrate({ reportedUsed: 1514.73, sessions: 98 }), 20);   // owner billing 2026-10-31: 15.5 x 1.25
  assert.equal(calibrate({ reportedUsed: 133.7, sessions: 22 }), 10);     // 6.1 x 1.25 -> floor 10
  assert.equal(calibrate({ reportedUsed: 9000, sessions: 50 }), 60);      // cap
  assert.equal(calibrate({ reportedUsed: 500, sessions: 9 }), null);      // too few sessions
  assert.equal(calibrate({ reportedUsed: NaN, sessions: 50 }), null);
});

const ready = () => A.readiness({ ARCANA_MASTER_SWITCH: 'on', ARCANA_DISPATCH_MODE: 'normal' });
const NOW = new Date('2026-10-31T15:00:00Z');
const snap = { reportedUsed: 1514.73, period: '2026-10', used: 1864, allowance: 3500, measuredAt: NOW.toISOString() };

test('calibrateReady uses the measurement only when the variable is unset and evidence is sufficient', async () => {
  const r = ready();
  assert.equal(r.estSession, 60);
  const cal = await calibrateReady({ env: {}, ready: r, snapshot: snap, now: NOW, count: async () => 98 });
  assert.equal(cal.source, 'calibrated'); assert.equal(cal.ready.estSession, 20); assert.equal(cal.ready.estRepair, 20);
  assert.equal(r.estSession, 60, 'input readiness is not mutated');
  const v = await calibrateReady({ env: { ARCANA_EST_CREDITS_PER_SESSION: '40' }, ready: r, snapshot: snap, now: NOW, count: async () => 98 });
  assert.equal(v.source, 'variable'); assert.equal(v.ready.estSession, 60);
  const rep = await calibrateReady({ env: { ARCANA_EST_CREDITS_PER_REPAIR: '30' }, ready: r, snapshot: snap, now: NOW, count: async () => 98 });
  assert.equal(rep.ready.estRepair, 30);
  for (const [why, args] of [
    ['few sessions', { count: async () => 3 }],
    ['api error', { count: async () => { throw Error('403'); } }],
    ['no snapshot', { snapshot: null }],
    ['other month', { snapshot: { ...snap, period: '2026-09' } }],
  ]) {
    const d = await calibrateReady({ env: {}, ready: r, snapshot: snap, now: NOW, count: async () => 98, ...args });
    assert.equal(d.source, 'default', why); assert.equal(d.ready.estSession, 60, why);
  }
});

test('copilotSessionsThisMonth counts runs of the dynamic Copilot workflow from the 1st of the month', async () => {
  let asked;
  const github = { rest: { actions: {
    listWorkflowRunsForRepo: async a => { assert.equal(a.event, 'dynamic'); return { data: { workflow_runs: [
      { workflow_id: 5, path: 'dynamic/other/thing' }, { workflow_id: 393690358, path: COPILOT_WORKFLOW_PATH }] } }; },
    listWorkflowRuns: async a => { asked = a; return { data: { total_count: 98 } }; } } } };
  assert.equal(await copilotSessionsThisMonth({ github, owner: 'o', repo: 'r', now: NOW }), 98);
  assert.equal(asked.workflow_id, 393690358);
  assert.equal(asked.created, '2026-10-01T00:00:00Z..2026-10-31T15:00:00Z');
  const none = { rest: { actions: { listWorkflowRunsForRepo: async () => ({ data: { workflow_runs: [] } }), listWorkflowRuns() { throw Error('unused'); } } } };
  assert.equal(await copilotSessionsThisMonth({ github: none, owner: 'o', repo: 'r', now: NOW }), null);
});

test('the calibrated estimate releases phantom reservations: 16 recent sessions no longer push warn into slow', () => {
  const r = ready();
  const args = { snapshot: { used: 1864, allowance: 3500, measuredAt: NOW.toISOString() }, active: 1, pendingRepairs: 0, recentFinished: 16, candidate: 'assign', now: NOW.getTime() };
  assert.equal(A.budgetWithReservations({ ...args, ready: r }).level, 'slow');
  assert.equal(A.budgetWithReservations({ ...args, ready: { ...r, estSession: 20 } }).level, 'warn');
});

test('public status hides credit liabilities once the estimate is calibrated', () => {
  const fn = require('./control-status.cjs').publicLiabilities;
  const l = { activeUnits: 20, pendingRepairs: 0, recentlyFinished: 320, candidate: 20 };
  assert.equal(fn(l).recentlyFinished, 320);
  const hidden = fn(l, 'calibrated');
  assert.ok(Object.values(hidden).every(v => typeof v === 'string'));
});
