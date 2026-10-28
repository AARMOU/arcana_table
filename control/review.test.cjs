'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const R = require('./review.js');
const src = fs.readFileSync(path.join(__dirname, 'review.js'), 'utf8');
const SHA = 'a'.repeat(40);

test('all seven status categories have owner-facing labels', () => {
  assert.deepEqual(Object.values(R.LABELS).sort(), ['Awaiting Review', 'Changes Requested', 'Checks Failed', 'Checks Running',
    'Independently Certified', 'Integrated', 'Owner Approved'].sort());
});

test('only reviewable cards can be decided; approve needs green or certified', () => {
  const item = s => ({ pr: 360, headSha: SHA, state: s });
  assert.equal(R.canApprove(item('awaiting-review')), true);
  assert.equal(R.canApprove(item('certified')), true);
  for (const s of ['checks-failed', 'checks-running', 'approved', 'changes-requested']) {
    assert.equal(R.canApprove(item(s)), false, s);
    assert.equal(R.canDecide(item(s)), true, s);
  }
  assert.equal(R.canDecide(item('integrated')), false);
  assert.equal(R.canDecide({ pr: 360, headSha: 'short', state: 'awaiting-review' }), false);
});

test('decision outcomes: success, duplicate, auth failure, stale commit, failed checks', () => {
  assert.match(R.decisionMessage(200, { state: 'approved' }).text, /exact commit.*nothing was merged/i);
  assert.match(R.decisionMessage(200, { state: 'changes-requested' }).text, /only if budget, reserve and capacity allow/);
  assert.match(R.decisionMessage(200, { duplicate: true }).text, /Already recorded/);
  assert.deepEqual([R.decisionMessage(401, { error: 'sign-in-required' }).signIn, R.decisionMessage(401, {}).ok], [true, false]);
  const stale = R.decisionMessage(409, { error: 'stale-commit' });
  assert.equal(stale.stale, true); assert.match(stale.text, /Nothing was recorded/);
  assert.match(R.decisionMessage(422, { error: 'checks-not-green', checks: ['cards-and-android'] }).text, /cards-and-android/);
  assert.equal(R.decisionMessage(0, null).ok, false);
});

test('sign-in session arrives in the URL fragment only and is then stripped', () => {
  assert.equal(R.sessionFromHash('#review-session=abc.def'), 'abc.def');
  assert.equal(R.sessionFromHash('#other=1'), null);
  assert.match(src, /history\.replaceState\(null, '', location\.pathname \+ location\.search\)/);
});

test('no GitHub token, no raw HTML, one write endpoint, and it is the review Worker', () => {
  assert.doesNotThrow(() => new vm.Script(src));
  assert.ok(!/innerHTML|outerHTML|insertAdjacentHTML|document\.write/.test(src));
  assert.ok(!/ghp_|gho_|github_pat_|api\.github\.com\/repos\/[^'"]*\/(reviews|merge)/.test(src));
  const posts = src.match(/method: 'POST'/g) || [];
  assert.equal(posts.length, 1);
  assert.match(src, /fetch\(REVIEW_API \+ '\/v1\/decision', \{ method: 'POST'/);
  assert.match(src, /never a merge, build, signing or release|never merges, builds, signs or releases/);
});

test('dashboard loads it, the shell caches it and Pages publishes it', () => {
  const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
  assert.ok(html.includes('<script src="./review.js" defer></script>'));
  assert.ok(html.includes('window.ArcanaReview.render(list,r,reviewBadge)'));
  assert.ok(fs.readFileSync(path.join(__dirname, 'sw.js'), 'utf8').includes("'./review.js'"));
  assert.ok(fs.readFileSync(path.join(__dirname, '../.github/workflows/pages.yml'), 'utf8').includes('cp control/review.js _site/control/review.js'));
});
