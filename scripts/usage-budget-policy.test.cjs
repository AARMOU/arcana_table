'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { decideBudget } = require('./usage-budget-policy.cjs');
const now = Date.UTC(2026, 9, 23, 16);
const check = (used, allowance = 1500, measuredAt = now) =>
  decideBudget({ used, allowance, measuredAt, now });
test('normal below half', () => assert.equal(check(338).level, 'normal'));
test('warn at 50 percent', () => assert.equal(check(750).level, 'warn'));
test('slow at 75 percent', () => assert.equal(check(1125).maxNewAssignments, 1));
test('pause at 90 percent', () => assert.equal(check(1350).maxNewAssignments, 0));
test('pause above allowance', () => assert.equal(check(1600).level, 'paused'));
test('fail closed when usage is missing', () => assert.equal(check(undefined).maxNewAssignments, 0));
test('fail closed when allowance is zero', () => assert.equal(check(1, 0).maxNewAssignments, 0));
test('fail closed on stale readings', () => assert.equal(check(10, 1500, now - 3600001).maxNewAssignments, 0));
test('fail closed on future timestamps', () => assert.equal(check(10, 1500, now + 1).maxNewAssignments, 0));
