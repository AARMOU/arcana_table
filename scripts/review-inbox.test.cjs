'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const R = require('./review-inbox.cjs');
const S = require('./control-status.cjs');

const pr = (extra = {}) => ({ number: 360, title: 'Implement Celestine, the Living Saint', body: '- Fixes #358',
  head: { sha: 'abc' }, base: { repo: { full_name: 'AARMOU/arcana_table' } }, user: { login: 'Copilot' }, ...extra });
const run = (name, conclusion, status = 'completed', head_sha = 'abc') => ({ name, status, conclusion, head_sha });
const review = (state, commit_id = 'abc', login = 'AARMOU') => ({ state, commit_id, user: { login } });

test('states: running, failed, awaiting review, approved and changes requested', () => {
  assert.equal(R.classify(pr(), []).state, 'checks-running');
  assert.equal(R.classify(pr(), [run('toolkit', null, 'in_progress')]).state, 'checks-running');
  assert.equal(R.classify(pr(), [run('toolkit', 'success'), run('cards-and-android', 'failure')]).state, 'checks-failed');
  const green = [run('toolkit', 'success'), run('cards-and-android', 'success'), run('publish-oracle-cache', 'skipped')];
  const row = R.classify(pr(), green);
  assert.deepEqual([row.state, row.card, row.issue, row.url], ['awaiting-review', 'Celestine, the Living Saint', 358,
    'https://github.com/AARMOU/arcana_table/pull/360/files']);
  assert.equal(R.classify(pr(), green, [review('APPROVED')]).state, 'approved');
  assert.equal(R.classify(pr(), green, [review('APPROVED'), review('CHANGES_REQUESTED')]).state, 'changes-requested');
});

test('only the owner\'s review of the CURRENT head counts', () => {
  const green = [run('toolkit', 'success')];
  assert.equal(R.classify(pr(), green, [review('APPROVED', 'old-sha')]).state, 'awaiting-review');
  assert.equal(R.classify(pr(), green, [review('APPROVED', 'abc', 'stranger')]).state, 'awaiting-review');
  // Check runs from an older head are ignored.
  assert.equal(R.classify(pr(), [run('toolkit', 'failure', 'completed', 'old')]).state, 'checks-running');
});

test('payload puts cards needing the owner first and counts the badge', () => {
  const p = R.inboxPayload([
    { pr: 1, state: 'checks-running' }, { pr: 2, state: 'awaiting-review' }, { pr: 3, state: 'approved' }, { pr: 4, state: 'awaiting-review' },
  ], new Date('2026-10-28T17:00:00Z'));
  assert.equal(p.awaitingReview, 2);
  assert.deepEqual(p.items.map(i => i.pr), [2, 4, 1, 3]);
  assert.match(R.inboxSummary({ ...p, items: p.items.map(i => ({ ...i, card: 'X' })) })[0], /2 awaiting owner review of 4/);
});

test('published as the public #78 review status the app can read without a token', () => {
  assert.ok(S.MARKERS.review);
  const body = S.formatStatus('review', { ...R.inboxPayload([]), kind: 'review' }, R.inboxSummary(R.inboxPayload([])));
  assert.ok(body.startsWith(S.MARKERS.review));
  assert.equal(S.parseStatus(body).kind, 'review');
});

test('buildReviewInbox reads only Copilot PRs and never writes', async () => {
  const calls = [];
  const github = {
    paginate: async (fn, args) => { calls.push(fn.name || 'paginate'); return fn === github.rest.pulls.list
      ? [pr(), pr({ number: 9, user: { login: 'AARMOU' } }), pr({ number: 330, body: 'Fixes #173' }), pr({ number: 331, body: 'no link' })]
      : [review('APPROVED')]; },
    rest: {
      pulls: { list: function list() {}, listReviews: function listReviews() {} },
      checks: { listForRef: async () => ({ data: { check_runs: [run('toolkit', 'success')] } }) },
      issues: { get: async ({ issue_number }) => ({ data: issue_number === 358
        ? { state: 'open', title: '[Arcana Queue] Celestine, the Living Saint — Cardkit SUPPORTED, unclaimed' }
        : { state: 'open', title: 'Some other issue' } }) },
    },
  };
  const p = await R.buildReviewInbox({ github, owner: 'AARMOU', repo: 'arcana_table' });
  assert.deepEqual(p.items.map(i => [i.pr, i.state]), [[360, 'approved']]);
});
