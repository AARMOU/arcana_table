'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const g = require('./usage-gate.cjs');
const now = Date.UTC(2026, 9, 23, 16);
const snap = (used, at = new Date(now).toISOString()) =>
  JSON.stringify({ used, allowance: 1500, measuredAt: at });
const b = (used) => g.budgetFromEnv(snap(used), now);

test('missing or garbage snapshot fails closed', () => {
  for (const raw of [undefined, '', 'nope', '{}']) {
    const x = g.budgetFromEnv(raw, now);
    assert.equal(x.level, 'unknown');
    assert.equal(g.dispatchLimit(x), 0);
    assert.equal(g.repairAllowed(x, 0), false);
  }
});
test('stale snapshot fails closed', () =>
  assert.equal(g.budgetFromEnv(snap(1, new Date(now - 7200000).toISOString()), now).level, 'unknown'));
test('dispatch limits by level', () => {
  assert.equal(g.dispatchLimit(b(100)), 2);
  assert.equal(g.dispatchLimit(b(750)), 2);
  assert.equal(g.dispatchLimit(b(1125)), 1);
  assert.equal(g.dispatchLimit(b(1350)), 0);
});
test('test_issue never bypasses the gate', () => {
  assert.equal(g.dispatchLimit(b(100), { testIssue: true }), 1);
  assert.equal(g.dispatchLimit(b(1350), { testIssue: true }), 0);
  assert.equal(g.dispatchLimit(g.budgetFromEnv(undefined, now), { testIssue: true }), 0);
});
test('repair retries respect levels', () => {
  assert.equal(g.repairAllowed(b(100), 2), true);
  assert.equal(g.repairAllowed(b(1125), 0), true);
  assert.equal(g.repairAllowed(b(1125), 1), false);
  assert.equal(g.repairAllowed(b(1350), 0), false);
});
test('describe reports level', () => assert.match(g.describe(b(750)), /warn.*50\.0%/));
