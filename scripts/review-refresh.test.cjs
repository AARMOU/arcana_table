'use strict';
const test = require('node:test');
const assert = require('node:assert');
const R = require('./review-refresh.cjs');
const A = require('./admission.cjs');

const BASE = 'b'.repeat(40);
const ORACLE = '    {"name": "Armory Automaton", "oracle_text": "Whenever this enters or attacks, attach Equipment."}';
const itemBody = (priority = 0) => [A.CARD_WORK_MARKER, '', '## Card', '', '- Armory Automaton', '', '## Offline Oracle record', '', ORACLE, '',
  `<!-- source: trusted-main-queue-feeder; base-sha:${BASE}; priority:${priority} -->`].join('\n');
const DATA = 'tools/cardkit/data/';
function rec(n, over = {}) {
  return {
    pr: { number: n, mergeable_state: 'clean' },
    issue: { number: 500 + n, state: 'open', user: { login: 'github-actions[bot]', type: 'Bot' }, title: A.CARD_WORK_TITLE + ' Armory Automaton', body: itemBody() },
    files: [DATA + 'verified-support.json', DATA + 'oracle-cache.jsonl'],
    run: { id: 9, status: 'completed', conclusion: 'success', run_attempt: 1 }, behind: false, refreshedOnto: null, ...over };
}

test('sanity: the fixture item is an actionable card-work item', () => {
  assert.ok(A.reviewActionable(itemBody()).ok, JSON.stringify(A.reviewActionable(itemBody())));
});

test('a conflicted, behind or never-checked review is rebuilt', () => {
  for (const over of [{ pr: { number: 1, mergeable_state: 'dirty' } }, { behind: true }, { run: null }]) {
    const p = R.plan([rec(1, over)], BASE);
    assert.equal(p.action, 'rebuild', JSON.stringify(over));
    assert.equal(p.pr, 1);
  }
});

test('a current, green review of a reopened item is re-run once, then left alone', () => {
  assert.equal(R.plan([rec(1)], BASE).action, 'rerun');
  const p = R.plan([rec(1, { run: { id: 9, status: 'completed', conclusion: 'success', run_attempt: 2 } })], BASE);
  assert.equal(p.action, null);
  assert.match(p.skipped[0].reason, /green-but-not-merged/);
});

test('a review is never rebuilt twice on the same base', () => {
  const waiting = R.plan([rec(1, { behind: false, run: null, refreshedOnto: BASE })], BASE);
  assert.equal(waiting.action, null);
  assert.match(waiting.skipped[0].reason, /waiting-for-cardkit/);
  const red = R.plan([rec(1, { refreshedOnto: BASE, run: { status: 'completed', conclusion: 'failure', run_attempt: 1 } })], BASE);
  assert.equal(red.action, null);
  // The base moved on: a new rebuild is due.
  assert.equal(R.plan([rec(1, { refreshedOnto: 'c'.repeat(40), behind: true })], BASE).action, 'rebuild');
});

test('only open bot card-work items with a record, and only pure review PRs, are touched', () => {
  const cases = [
    { issue: { ...rec(1).issue, state: 'closed' } },
    { issue: { ...rec(1).issue, user: { login: 'someone', type: 'User' } } },
    { issue: { ...rec(1).issue, title: '[Arcana Queue] Armory Automaton' } },
    { files: [DATA + 'verified-support.json', 'android/custom-cards/X.kt'] },
    { files: [] },
  ];
  for (const over of cases) assert.equal(R.plan([rec(1, { behind: true, ...over })], BASE).action, null, JSON.stringify(over));
});

test('running Cardkit is waited for; a red Cardkit on the current base goes to the repair router', () => {
  assert.equal(R.plan([rec(1, { behind: true, run: { status: 'in_progress' } })], BASE).action, null);
  const p = R.plan([rec(1, { run: { status: 'completed', conclusion: 'failure' } })], BASE);
  assert.equal(p.action, null);
  assert.match(p.skipped[0].reason, /repair router/);
});

test('one PR per pass: reruns first, then highest priority, then oldest', () => {
  const hi = rec(7, { behind: true, issue: { ...rec(7).issue, body: itemBody(80) } });
  const p = R.plan([rec(3, { behind: true }), hi, rec(5, { behind: true })], BASE);
  assert.deepEqual([p.action, p.pr, p.waiting], ['rebuild', 7, 2]);
  assert.deepEqual([R.plan([rec(3, { behind: true }), rec(9)], BASE).action, R.plan([rec(3, { behind: true }), rec(9)], BASE).pr], ['rerun', 9]);
});

test('refreshedOnto trusts only a trailer that names a real parent', () => {
  const msg = `Refresh\n\n${R.TRAILER} base=${BASE}\n`;
  assert.equal(R.refreshedOnto({ commit: { message: msg }, parents: [{ sha: 'a'.repeat(40) }, { sha: BASE }] }), BASE);
  assert.equal(R.refreshedOnto({ commit: { message: msg }, parents: [{ sha: 'a'.repeat(40) }] }), null);
  assert.equal(R.refreshedOnto({ commit: { message: 'plain' }, parents: [{ sha: BASE }] }), null);
});
