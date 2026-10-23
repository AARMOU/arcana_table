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
const queued = n => ({ ...A.deriveUnit({ number: n }, NOW), actionable: { ok: true, missing: [] } });
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

// --- Accounting for failed/unknown outcomes and owner resolution (GPT review on #93) ---
const rec = (fields, at) => bot(A.formatLedger({ ...fields, at: iso(at) }), at);
const attempt = (result, at = NOW - 5 * MIN) => [rec({ op: 'authorizing', issue: 68, run: 1 }, at - MIN),
  rec({ op: 'attempt-result', issue: 68, run: 1, result }, at)];
const assignDenied = u => assert.equal(A.admit({ op: 'assign', unit: u, units: [u], ready: A.readiness(env({ ARCANA_MAX_ACTIVE_UNITS: '2' })), snapshot: snap(), now: NOW }).allowed, false);

test('error and not-confirmed outcomes are treated as possibly paid: slot held, no second assignment', () => {
  for (const result of ['error', 'not-confirmed', 'confirmed']) {
    const fresh = A.deriveUnit({ number: 68, ledger: A.parseLedger(attempt(result)) }, NOW);
    assert.deepEqual([fresh.state, fresh.holdsSlot, fresh.assignments], ['pending-pickup', true, 1], result);
    assignDenied(fresh);
    const late = A.deriveUnit({ number: 68, ledger: A.parseLedger(attempt(result, NOW - 90 * MIN)) }, NOW);
    assert.deepEqual([late.state, late.reason, late.holdsSlot], ['halted', 'pickup-timeout', true], result);
    assignDenied(late);
    const picked = A.deriveUnit({ number: 68, ledger: A.parseLedger(attempt(result, NOW - 90 * MIN)),
      sessions: [{ event: 'started', at: NOW - 80 * MIN }, { event: 'finished', at: NOW - 40 * MIN }] }, NOW);
    assert.equal(picked.state, 'review', result); assignDenied(picked);
  }
});

test('not-attempted consumes the authorization without holding a slot', () => {
  const u = A.deriveUnit({ number: 68, ledger: A.parseLedger(attempt('not-attempted')) }, NOW);
  assert.deepEqual([u.state, u.reason, u.holdsSlot, u.assignments], ['halted', 'assignment-consumed', false, 1]);
  assignDenied(u);
});

test('an unknown or missing result value never reopens the unit', () => {
  const u = A.deriveUnit({ number: 68, ledger: A.parseLedger(attempt('weird')) }, NOW);
  assert.equal(u.assignments, 1); assert.notEqual(u.state, 'queued'); assignDenied(u);
});

test('owner resolution is append-only, clears the block, and never authorizes a second assignment', () => {
  const auth = rec({ op: 'authorizing', issue: 68, run: 7 }, NOW - 10 * MIN);
  for (const [observed, assigned, state] of [['assigned', true, 'pending-pickup'], ['not-assigned', false, 'halted']]) {
    const res = rec({ op: 'resolution', issue: 68, run: 7, result: observed, reason: 'owner-label by AARMOU in run 9' }, NOW - 2 * MIN);
    const u = A.deriveUnit({ number: 68, assignedToCopilot: assigned, ledger: A.parseLedger([auth, res]) }, NOW);
    assert.equal(u.blocksAll, false); assert.equal(u.interrupted, false); assert.equal(u.assignments, 1);
    assert.equal(u.state, state, observed); assignDenied(u);
  }
  // Resolution "assigned" is treated as possibly paid even if the issue no longer shows the assignee.
  const r = A.deriveUnit({ number: 68, ledger: A.parseLedger([auth,
    rec({ op: 'resolution', issue: 68, run: 7, result: 'assigned' }, NOW - 2 * MIN)]) }, NOW);
  assert.deepEqual([r.state, r.holdsSlot], ['pending-pickup', true]);
});

test('resolution only closes its own run and must come from github-actions[bot]', () => {
  const ledger = [rec({ op: 'authorizing', issue: 68, run: 7 }, NOW - 9 * MIN), rec({ op: 'authorizing', issue: 68, run: 8 }, NOW - 8 * MIN),
    rec({ op: 'resolution', issue: 68, run: 7, result: 'not-assigned' }, NOW - 2 * MIN)];
  assert.equal(A.deriveUnit({ number: 68, ledger: A.parseLedger(ledger) }, NOW).blocksAll, true);
  const forged = { ...rec({ op: 'resolution', issue: 68, run: 8, result: 'not-assigned' }, NOW), user: { type: 'User', login: 'AARMOU' } };
  assert.equal(A.deriveUnit({ number: 68, ledger: A.parseLedger([...ledger, forged]) }, NOW).blocksAll, true);
  assert.equal(A.deriveUnit({ number: 68, ledger: A.parseLedger([...ledger, rec({ op: 'resolution', issue: 68, run: 8, result: 'not-assigned' }, NOW)]) }, NOW).blocksAll, false);
});

// --- Unit readiness: the dispatcher refuses vague work items (zero-diff #95 follow-up) ---
const BASE = 'gpt/arcana-integration-next';
const GOOD = ['## Cards', '- Eldrazi Monument', '- Oblivion Sower', '', '## Acceptance',
  '- Exact current Oracle behavior', '- Executable regression scenarios and deterministic export', '', 'Base: `' + BASE + '`'].join('\n');
test('a complete unit body is actionable; discovery with a script path also qualifies', () => {
  assert.deepEqual(A.unitActionable(GOOD, BASE), { ok: true, missing: [] });
  const discovery = GOOD.replace('## Cards\n- Eldrazi Monument\n- Oblivion Sower', '## Discovery\nRun `investigation/argentum-six-precons/coverage-audit.py --json-output`');
  assert.equal(A.unitActionable(discovery, BASE).ok, true);
});
test('each missing section is reported and blocks assignment', () => {
  const cases = [[GOOD.replace(/## Cards[\s\S]*?\n\n/, ''), 'cards-or-discovery'],
    [GOOD.replace('## Discovery', '').replace(/- Eldrazi Monument\n- Oblivion Sower/, 'TBD'), 'cards-or-discovery'],
    [GOOD.replace(/## Acceptance[\s\S]*?\n\n/, ''), 'acceptance'],
    [GOOD.replace('Exact current Oracle behavior', 'Make it work'), 'acceptance'],
    [GOOD.replace(/Base:.*$/m, ''), 'base-missing'],
    [GOOD, 'base-mismatch', 'gpt/six-precon-worker-integration-20261022'],
    ['', 'cards-or-discovery']];
  for (const [body, reason, base = BASE] of cases) {
    const a = A.unitActionable(body, base);
    assert.equal(a.ok, false, reason); assert.ok(a.missing.includes(reason), reason + ' got ' + a.missing);
    const unit = { ...A.deriveUnit({ number: 68 }, NOW), actionable: a };
    assert.match(A.admit({ op: 'assign', unit, ready: A.readiness(env()), snapshot: snap(), now: NOW }).reason, /unit-not-actionable/);
  }
});
test('an unchecked unit is refused (fail closed) and dry-run reports the reason', () => {
  const unit = A.deriveUnit({ number: 68 }, NOW);
  assert.match(A.admit({ op: 'assign', unit, ready: A.readiness(env()), snapshot: snap(), now: NOW }).reason, /unit-not-actionable: unchecked/);
  const d = A.admit({ op: 'assign', unit: { ...unit, actionable: A.unitActionable('vague', BASE) }, ready: A.readiness({ ARCANA_DISPATCH_MODE: 'dry-run' }), snapshot: snap(), now: NOW });
  assert.equal(d.wouldAllow, undefined); assert.match(d.reason, /unit-not-actionable/);
});

// --- GPT review on #99: placeholders, comments and boilerplate must not pass ---
const fs = require('node:fs'); const path = require('node:path');
const TEMPLATE = fs.readFileSync(path.join(__dirname, '..', '.github', 'ISSUE_TEMPLATE', 'arcana-queue-unit.md'), 'utf8')
  .replace(/^---[\s\S]*?---\n/, '');
const fill = (t, cards, specific) => t.replace('- Card name', cards).replace('- <Card>: specific behaviors and edge cases to verify', specific);
test('the untouched issue template is refused', () => {
  const a = A.unitActionable(TEMPLATE, BASE);
  assert.equal(a.ok, false); assert.ok(a.missing.includes('cards-or-discovery')); assert.ok(a.missing.includes('acceptance'));
});
test('cards filled in but only boilerplate acceptance is refused', () => {
  const a = A.unitActionable(fill(TEMPLATE, '- Eldrazi Monument', '- <Card>: specific behaviors and edge cases to verify'), BASE);
  assert.deepEqual(a.missing, ['acceptance']);
});
test('a fully filled template is actionable', () => {
  const a = A.unitActionable(fill(TEMPLATE, '- Eldrazi Monument', '- Eldrazi Monument: upkeep sacrifice choice with several creatures; sacrifice, not destroy'), BASE);
  assert.deepEqual(a, { ok: true, missing: [] });
});
test('placeholder card bullets are refused', () => {
  for (const ph of ['- TBD', '- Card name', '- <card>', '- ...', '- ?', '- todo'])
    assert.ok(A.unitActionable(GOOD.replace('- Eldrazi Monument\n- Oblivion Sower', ph), BASE).missing.includes('cards-or-discovery'), ph);
});
test('content inside HTML comments is ignored', () => {
  const commentedCards = GOOD.replace('- Eldrazi Monument\n- Oblivion Sower', '<!-- - Eldrazi Monument -->');
  assert.ok(A.unitActionable(commentedCards, BASE).missing.includes('cards-or-discovery'));
  const commentedBase = GOOD.replace(/^Base:.*$/m, '<!-- Base: ' + BASE + ' -->');
  assert.ok(A.unitActionable(commentedBase, BASE).missing.includes('base-missing'));
  const unclosed = GOOD.replace('## Acceptance', '<!-- unfinished note\n## Acceptance');
  assert.equal(A.unitActionable(unclosed, BASE).ok, false);
});
test('discovery needs a runnable command, not just a filename', () => {
  const withDiscovery = d => GOOD.replace('## Cards\n- Eldrazi Monument\n- Oblivion Sower', '## Discovery\n' + d);
  assert.ok(A.unitActionable(withDiscovery('See coverage-audit.py for details.'), BASE).missing.includes('cards-or-discovery'));
  assert.ok(A.unitActionable(withDiscovery('<!-- run `python3 coverage-audit.py --json-output` -->'), BASE).missing.includes('cards-or-discovery'));
  assert.equal(A.unitActionable(withDiscovery('Run `python3 investigation/argentum-six-precons/coverage-audit.py --json-output`.'), BASE).ok, true);
  assert.equal(A.unitActionable(withDiscovery('python3 investigation/argentum-six-precons/coverage-audit.py --json-output'), BASE).ok, true);
});
test('ambiguous PR links block admission for the unit', () => {
  const unit = { ...queued(68), ambiguousLink: true };
  assert.match(A.admit({ op: 'assign', unit, ready: A.readiness(env()), snapshot: snap(), now: NOW }).reason, /ambiguous/);
  const rep = { ...review(67), ambiguousLink: true };
  assert.match(A.admit({ op: 'repair', unit: rep, ready: A.readiness(env()), snapshot: snap(), now: NOW }).reason, /ambiguous/);
});
