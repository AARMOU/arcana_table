const test = require('node:test');
const assert = require('node:assert/strict');
const { billingPayload } = require('./control-status.cjs');
const snap = { measuredAt: '2026-11-02T14:00:00Z', period: '2026-11', metric: 'ai_credits' };
test('a daily-limit pause is published as the daily limit, not "90% of the month"', () => {
  const p = billingPayload({ snapshot: snap, status: { level: 'paused', reason: 'daily-credit-limit' }, error: null });
  assert.equal(p.reason, 'daily-credit-limit');
  assert.match(p.current.band, /daily credit limit/);
  assert.match(p.action, /resumes on its own/);
  assert.equal(p.blocksDispatch, true);
});
test('a monthly pause keeps the 90% wording', () => {
  const p = billingPayload({ snapshot: snap, status: { level: 'paused' }, error: null });
  assert.equal(p.reason, null);
  assert.equal(p.current.band, '90% or more');
});
