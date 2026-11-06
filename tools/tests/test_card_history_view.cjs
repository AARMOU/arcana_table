'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {cardHistoryView} = require('../card_history_view.cjs');
const now = '2026-11-06T15:00:00Z';
function feed(status = 'partial', count = 0) {
  return {schemaVersion: 1, checkedAt: now, trackedRef: 'delivered-build', trackedCommit: 'a'.repeat(40),
    coverage: {status, reason: 'Custom registry only'},
    days: Array.from({length: 7}, (_, i) => ({date: `2026-11-0${i + 1}`,
      status, observedRegistrations: count}))};
}
test('missing and partial feeds never become an authoritative zero', () => {
  assert.equal(cardHistoryView(null, {now}).status, 'unavailable');
  assert.ok(cardHistoryView(feed(), {now}).days.every(d => d.count === null && !d.verified));
});
test('positive partial observations retain honest label and tracked identity', () => {
  const result = cardHistoryView(feed('partial', 12), {now});
  assert.equal(result.label, 'Custom card registrations');
  assert.equal(result.trackedRef, 'delivered-build');
  assert.equal(result.days[0].count, 12);
  assert.equal(result.days[0].verified, false);
});
test('zero requires fresh complete coverage at feed and day levels', () => {
  const data = feed('complete');
  assert.equal(cardHistoryView(data, {now}).days[0].count, 0);
  data.days[0].status = 'partial';
  assert.equal(cardHistoryView(data, {now}).days[0].count, null);
  data.coverage.status = 'partial';
  assert.equal(cardHistoryView(data, {now}).days[1].count, null);
});
test('stale, future and invalid timestamps cannot publish counts', () => {
  const data = feed('complete', 3);
  const result = cardHistoryView(data, {now: '2026-11-06T18:00:01Z'});
  assert.equal(result.status, 'stale');
  assert.ok(result.days.every(d => d.count === null));
  assert.equal(cardHistoryView(data, {now: '2026-11-06T14:59:59Z'}).status, 'unavailable');
  data.checkedAt = 'invalid';
  assert.equal(cardHistoryView(data, {now}).status, 'unavailable');
});
test('malformed, duplicated or missing daily evidence fails closed', () => {
  for (const mutate of [d => d.days.pop(), d => d.days[1].date = d.days[0].date,
    d => d.days[0].observedRegistrations = -1, d => d.trackedCommit = 'unknown',
    d => d.days[0].status = 'invented']) {
    const data = feed(); mutate(data);
    assert.equal(cardHistoryView(data, {now}).status, 'unavailable');
  }
});
test('unavailable days stay unavailable even with complete aggregate coverage', () => {
  const data = feed('complete');
  data.days[0] = {date: data.days[0].date, status: 'unavailable', observedRegistrations: null};
  assert.equal(cardHistoryView(data, {now}).days[0].count, null);
});
