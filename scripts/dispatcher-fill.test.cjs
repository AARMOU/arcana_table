'use strict';
// Owner 2026-10-31: fill free worker slots right away, not one assignment per 30-minute tick.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { continueFilling } = require('./control-workflows.cjs');

test('only a confirmed real assignment starts the next dispatcher run', () => {
  assert.equal(continueFilling({ result: { state: 'attempted', result: 'confirmed' } }), true);
  for (const result of [{ state: 'attempted', result: 'error' }, { state: 'attempted', result: 'not-attempted' },
    { state: 'attempted', result: 'not-confirmed' }, { state: 'nothing-eligible' }, { state: 'denied', reason: 'capacity: 2 of 2 active' },
    { state: 'dry-run' }, { state: 'error' }, null])
    assert.equal(continueFilling({ result }), false, JSON.stringify(result));
  assert.equal(continueFilling({ result: { state: 'attempted', result: 'confirmed' }, forcedDry: true }), false);
  assert.equal(continueFilling({ result: { state: 'attempted', result: 'confirmed' }, failure: Error('x') }), false);
});
