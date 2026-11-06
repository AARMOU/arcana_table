'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {denverDay, identifiers, history} = require('../card_integration_history.cjs');
const options = {trackedRef: 'build', trackedCommit: 'head', trackedCommittedAt: '2026-11-05T23:00:00Z',
  checkedAt: '2026-11-06T12:00:00Z'};
const snapshot = (commit, cards, committedAt = '2026-11-03T18:10:15Z') =>
  ({commit, cards: new Set(cards), committedAt});
test('multi-card integrations count identifiers regardless of author or unrelated PR volume', () => {
  const result = history([snapshot('one', ['A', 'B', 'C'])], options);
  assert.equal(result.days.find(d => d.date === '2026-11-03').observedRegistrations, 3);
  assert.deepEqual(result.receipts[0].cardIdentifiers, ['A', 'B', 'C']);
  assert.equal(result.coverage.status, 'partial');
});
test('suite/no-op changes and removed then re-added registrations never count twice', () => {
  const result = history([snapshot('one', ['A']), snapshot('suite', ['A']),
    snapshot('remove', []), snapshot('restore', ['A', 'B'])], options);
  assert.deepEqual(result.receipts.map(r => r.cardIdentifiers), [['A'], ['B']]);
});
test('full historical baseline excludes old cards from seven-day window', () => {
  const result = history([snapshot('old', ['A'], '2026-10-01T12:00:00Z'),
    snapshot('new', ['A', 'B'])], options);
  assert.deepEqual(result.receipts[0].cardIdentifiers, ['B']);
  assert.equal(result.days.length, 7);
});
test('Denver boundary uses DST rather than fixed offset', () => {
  assert.equal(denverDay('2026-11-01T05:59:59Z'), '2026-10-31');
  assert.equal(denverDay('2026-11-01T06:00:00Z'), '2026-11-01');
  assert.equal(denverDay('2026-11-03T06:59:59Z'), '2026-11-02');
  assert.equal(denverDay('2026-11-03T07:00:00Z'), '2026-11-03');
});
test('comments and duplicate deck registrations do not create extra cards', () => {
  const cards = identifiers('val entries: List<Entry> = listOf(\n' +
    '// Entry(Fake, "SET", DECK),\n/* Entry(Other, "S", D), */\n' +
    'Entry(Real, "SET", D), Entry(Real, "SET", D))');
  assert.deepEqual([...cards], ['Real']);
});
test('unsupported/missing registry and invalid dates fail instead of reporting zero', () => {
  assert.throws(() => identifiers(''), /Unsupported/);
  assert.throws(() => identifiers('val entries: List<Entry> = listOf(Entry(dynamic(), "S", D))'), /Unsupported/);
  assert.throws(() => denverDay('invalid'), /Invalid/);
});
test('old tracked head cannot imply a zero on later dates', () => {
  const result = history([snapshot('one', ['A'])], options);
  const today = result.days.find(d => d.date === '2026-11-06');
  assert.equal(today.observedRegistrations, null);
  assert.equal(today.status, 'unavailable');
  assert.equal(result.days[0].status, 'partial');
});
