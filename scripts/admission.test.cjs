'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const A = require('./admission.cjs');

const NOW = Date.parse('2026-10-24T12:00:00Z');
const iso = ms => new Date(ms).toISOString();
const MIN = 60 * 1000;
const env = (x = {}) => ({ ARCANA_AUTOMATION_READY: 'true', ARCANA_DISPATCH_MODE: 'normal',
  ARCANA_REPAIR_MODE: 'request', ...x });
const snap = (used = 300) => ({ used, allowance: 1500, measuredAt: iso(NOW - 5 * MIN) });
const bot = (body, at = NOW) => ({ body, created_at: iso(at), user: { type: 'Bot', login: 'github-actions[bot]' } });
const queued = n => A.deriveUnit({ number: n }, NOW);
const working = n => A.deriveUnit({ number: n, assignedToCopilot: true,
  sessions: [{ event: 'started', at: NOW - 10 * MIN }] }, NOW);
const review = (n, extra = {}) => A.deriveUnit({ number: n, assignedToCopilot: true,
  sessions: [{ event: 'started', at: NOW - 60 * MIN }, { event: 'finished', at: NOW - 30 * MIN }], ...extra }, NOW);

test('readiness defaults are off and malformed values fail closed', () => {
  const r = A.readiness({});
  assert.deepEqual([r.master, r.dispatch, r.repair, r.maxUnits, r.estSession, r.estRepair],
    [false, 'off', 'observe', 1, 60, 30]);
  for (const bad of [{ ARCANA_DISPATCH_MODE: 'on' }, { ARCANA_MAX_ACTIVE_UNITS: '3' },
    { ARCANA_MAX_ACTIVE_UNITS: '0' }, { ARCANA_EST_CREDITS_PER_SESSION: '-1' }, { ARCANA_REPAIR_MODE: 'yes' }]) {
    const ready = A.readiness(env(bad));
    assert.ok(ready.errors.length, JSON.stringify(bad));
    assert.equal(A.admit({ op: 'assign', unit: queued(68), units: [], ready, snapshot: snap(), now: NOW }).allowed, false);
  }
  assert.equal(A.readiness({ ARCANA_AUTOMATION_READY: 'TRUE' }).master, false);
});

test('master switch and per-workflow modes are independent', () => {
  const unit = queued(68);
  const off = A.admit({ op: 'assign', unit, ready: A.readiness(env({ ARCANA_AUTOMATION_READY: '' })), snapshot: snap(), now: NOW });
  assert.match(off.reason, /master/);
  // Dispatch on, repairs off: the controlled-test configuration.
  const ready = A.readiness(env({ ARCANA_REPAIR_MODE: 'off' }));
  assert.equal(A.admit({ op: 'assign', unit, ready, snapshot: snap(), now: NOW }).allowed, true);
  assert.match(A.admit({ op: 'repair', unit: review(67), ready, snapshot: snap(), now: NOW }).reason, /repair mode off/);
  const observe = A.readiness(env({ ARCANA_REPAIR_MODE: 'observe' }));
  assert.equal(A.admit({ op: 'repair', unit: review(67), ready: observe, snapshot: snap(), now: NOW }).allowed, false);
});

test('dry-run evaluates everything but never allows, even with master off', () => {
  const ready = A.readiness({ ARCANA_DISPATCH_MODE: 'dry-run' });
  const d = A.admit({ op: 'assign', unit: queued(68), units: [], ready, snapshot: snap(), now: NOW });
  assert.equal(d.allowed, false); assert.equal(d.wouldAllow, true);
  const full = A.admit({ op: 'assign', unit: queued(68), units: [working(1)], ready, snapshot: snap(), now: NOW });
  assert.equal(full.wouldAllow, undefined); assert.match(full.reason, /capacity/);
});

test('authorize-once requires exactly one labeled issue', () => {
  const ready = A.readiness(env({ ARCANA_DISPATCH_MODE: 'authorize-once' }));
  const unit = { ...queued(68), labeled: true };
  assert.equal(A.admit({ op: 'assign', unit, labeledCount: 1, ready, snapshot: snap(), now: NOW }).allowed, true);
  assert.equal(A.admit({ op: 'assign', unit, labeledCount: 2, ready, snapshot: snap(), now: NOW }).allowed, false);
  assert.equal(A.admit({ op: 'assign', unit: queued(66), labeledCount: 1, ready, snapshot: snap(), now: NOW }).allowed, false);
});

test('capacity counts work units once, including pending pickups and repairs', () => {
  const ready = A.readiness(env());
  const pending = A.deriveUnit({ number: 2, ledger: A.parseLedger([
    bot(A.formatLedger({ op: 'authorizing', issue: 2, run: 9, at: iso(NOW - 5 * MIN) })),
    bot(A.formatLedger({ op: 'attempt-result', issue: 2, run: 9, result: 'confirmed', at: iso(NOW - 4 * MIN) }))]) }, NOW);
  assert.equal(pending.state, 'pending-pickup'); assert.equal(pending.holdsSlot, true);
  assert.match(A.admit({ op: 'assign', unit: queued(68), units: [pending], ready, snapshot: snap(), now: NOW }).reason, /capacity: 1 of 1/);
  // Finished sessions (issue + its PR) are one unit in review, holding no slot.
  assert.equal(A.admit({ op: 'assign', unit: queued(68), units: [review(63), review(64)], ready, snapshot: snap(), now: NOW }).allowed, true);
  const two = A.readiness(env({ ARCANA_MAX_ACTIVE_UNITS: '2' }));
  assert.equal(A.admit({ op: 'assign', unit: queued(68), units: [working(1)], ready: two, snapshot: snap(), now: NOW }).allowed, true);
});

test('legacy assignment without a session holds a slot as an unreviewed pickup timeout', () => {
  const u = A.deriveUnit({ number: 63, assignedToCopilot: true }, NOW);
  assert.deepEqual([u.state, u.reason, u.holdsSlot, u.assignments], ['halted', 'pickup-timeout', true, 1]);
});

test('per-unit ceilings: one assignment and two repair requests', () => {
  const ready = A.readiness(env());
  assert.match(A.admit({ op: 'assign', unit: review(63), ready, snapshot: snap(), now: NOW }).reason, /state review/);
  const repairs = n => A.parseLedger(Array.from({ length: n }, (_, i) =>
    bot(A.formatLedger({ op: 'repair-request', issue: 67, run: 100 + i, at: iso(NOW - (90 - i) * MIN) }))));
  assert.equal(A.admit({ op: 'repair', unit: review(67), ready, snapshot: snap(), now: NOW }).allowed, true);
  const two = review(67, { ledger: repairs(2) });
  assert.equal(two.state, 'review');
  assert.match(A.admit({ op: 'repair', unit: two, ready, snapshot: snap(), now: NOW }).reason, /repair ceiling/);
});

test('an outstanding repair request holds a slot until the session finishes', () => {
  const u = review(67, { ledger: A.parseLedger([bot(A.formatLedger({ op: 'repair-request', issue: 67, run: 5, at: iso(NOW - 5 * MIN) }))]) });
  assert.deepEqual([u.state, u.holdsSlot], ['repairing', true]);
});

test('interrupted attempts halt the unit and block every operation', () => {
  const ledger = A.parseLedger([bot(A.formatLedger({ op: 'authorizing', issue: 68, run: 77, at: iso(NOW - MIN) }))]);
  const u = A.deriveUnit({ number: 68, ledger }, NOW);
  assert.deepEqual([u.state, u.reason, u.blocksAll], ['halted', 'interrupted', true]);
  const ready = A.readiness(env());
  for (const op of ['assign', 'repair'])
    assert.match(A.admit({ op, unit: op === 'assign' ? queued(66) : review(67), units: [u], ready, snapshot: snap(), now: NOW }).reason, /interrupted/);
});

test('ledger trusts only github-actions[bot] and rejects injected values', () => {
  const body = A.formatLedger({ op: 'authorizing', issue: 68, run: 1 });
  assert.equal(A.parseLedger([{ ...bot(body), user: { type: 'User', login: 'AARMOU' } }]).length, 0);
  assert.equal(A.parseLedger([bot(body)]).length, 1);
  assert.throws(() => A.formatLedger({ op: 'halt', reason: 'x\nop:attempt-result' }));
  assert.throws(() => A.formatLedger({ op: 'halt', reason: 'ping @copilot' }));
  assert.throws(() => A.formatLedger({ op: 'merge' }));
});

test('budget includes in-flight reservations and the candidate itself', () => {
  const ready = A.readiness(env({ ARCANA_MAX_ACTIVE_UNITS: '2' }));
  // 1300 reported + 60 active + 60 candidate = 1420 / 1500 → paused.
  assert.match(A.admit({ op: 'assign', unit: queued(68), units: [working(1)], ready, snapshot: snap(1300), now: NOW }).reason, /paused/);
  // 1000 + 60 = 1060 → slow, allowed only with nothing active.
  assert.equal(A.admit({ op: 'assign', unit: queued(68), units: [], ready, snapshot: snap(1000), now: NOW }).allowed, true);
  assert.match(A.admit({ op: 'assign', unit: queued(68), units: [working(1)], ready, snapshot: snap(1060), now: NOW }).reason, /slow allows one/);
  const stale = { used: 100, allowance: 1500, measuredAt: iso(NOW - 2 * 60 * MIN) };
  assert.match(A.admit({ op: 'assign', unit: queued(68), ready, snapshot: stale, now: NOW }).reason, /unknown/);
  assert.match(A.admit({ op: 'assign', unit: queued(68), ready, snapshot: null, now: NOW }).reason, /unknown/);
});
