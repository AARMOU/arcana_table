'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { backfill, MAX_RERUNS } = require('./backfill.cjs');
const P = require('./policy.cjs');
const manifest = P.readManifest(__dirname + '/suites.json');
const sha = n => String(n).padStart(40, 'a');
const prFor = s => ({ number: s.issue + 1, title: 'Implement ' + s.name, body: 'Fixes #' + s.issue, state: 'open',
  user: { login: 'Copilot' }, base: { ref: manifest.base }, head: { sha: sha(s.issue) } });

function mock({ prs, checks = {}, runs = {} }) {
  const reruns = [];
  const github = {
    rest: { pulls: { list() {} }, checks: { listForRef() {} },
      actions: { listWorkflowRunsForRepo: async ({ head_sha }) => ({ data: { workflow_runs: runs[head_sha] || [] } }),
        reRunWorkflow: async ({ run_id }) => { reruns.push(run_id); },
        reRunWorkflowFailedJobs: async ({ run_id }) => { reruns.push('failed:' + run_id); } } },
    paginate: async (fn, args) => fn === github.rest.pulls.list ? prs : (checks[args.ref] || []),
  };
  return { github, reruns };
}
const run = (id, name, head_sha, conclusion = 'success', created_at = '2026-10-29T10:00:00Z', run_attempt = 1) =>
  ({ id, name, head_sha, conclusion, created_at, run_attempt });

test('re-runs the newest successful CI of an uncertified card PR, once', async () => {
  const s = manifest.suites[0], pr = prFor(s), h = pr.head.sha;
  const { github, reruns } = mock({ prs: [pr], runs: { [h]: [run(1, 'Arcana Cardkit', h, 'success', '2026-10-29T09:00:00Z'),
    run(2, 'Arcana Development CI', h, 'success', '2026-10-29T11:00:00Z'), run(3, 'Arcana Cardkit', h, 'failure', '2026-10-29T12:00:00Z'),
    run(4, 'Unrelated', h, 'success', '2026-10-29T13:00:00Z')] } });
  const r = await backfill({ github, owner: 'o', repo: 'r', root: __dirname + '/..' });
  assert.deepEqual(reruns, [2]);
  assert.equal(r[0].rerun, true);
});

test('skips certified heads, retries failed certification, never exceeds the cap', async () => {
  const [a, b] = manifest.suites, pa = prFor(a), pb = prFor(b);
  const cert = (conclusion, h) => [{ name: P.CHECK_NAME, head_sha: h, conclusion, app: { slug: 'github-actions' } }];
  const { github, reruns } = mock({ prs: [pa, pb],
    checks: { [pa.head.sha]: cert('success', pa.head.sha), [pb.head.sha]: cert('failure', pb.head.sha) },
    runs: { [pa.head.sha]: [run(10, 'Arcana Cardkit', pa.head.sha)], [pb.head.sha]: [run(20, 'Arcana Cardkit', pb.head.sha)] } });
  await backfill({ github, owner: 'o', repo: 'r', root: __dirname + '/..' });
  assert.deepEqual(reruns, [20]);
  assert.ok(MAX_RERUNS >= 1 && MAX_RERUNS <= 10);
});

test('a look-alike check or no successful CI never triggers a re-run', async () => {
  const s = manifest.suites[0], pr = prFor(s), h = pr.head.sha;
  const { github, reruns } = mock({ prs: [pr],
    checks: { [h]: [{ name: P.CHECK_NAME, head_sha: h, conclusion: 'success', app: { slug: 'evil-app' } }] },
    runs: { [h]: [run(5, 'Arcana Cardkit', h, 'failure', undefined, 2)] } });
  const r = await backfill({ github, owner: 'o', repo: 'r', root: __dirname + '/..' });
  assert.deepEqual(reruns, []);
  assert.equal(r[0].reason, 'CI failed after one retry');
});

test('failed CI with no green run is retried once, newest first', async () => {
  const s = manifest.suites[0], pr = prFor(s), h = pr.head.sha;
  const { github, reruns } = mock({ prs: [pr], runs: { [h]: [run(6, 'Arcana Cardkit', h, 'failure', '2026-10-29T09:00:00Z'),
    run(7, 'Arcana Development CI', h, 'timed_out', '2026-10-29T11:00:00Z'), run(8, 'Arcana Cardkit', h, 'cancelled', '2026-10-29T12:00:00Z')] } });
  const r = await backfill({ github, owner: 'o', repo: 'r', root: __dirname + '/..', scheduled: true });
  assert.deepEqual(reruns, ['failed:7']);
  assert.equal(r[0].retry, 'failed-jobs');
});

test('scheduled runs never re-run green CI', async () => {
  const s = manifest.suites[0], pr = prFor(s), h = pr.head.sha;
  const { github, reruns } = mock({ prs: [pr], runs: { [h]: [run(9, 'Arcana Cardkit', h)] } });
  await backfill({ github, owner: 'o', repo: 'r', root: __dirname + '/..', scheduled: true });
  assert.deepEqual(reruns, []);
});

const { missingSuites, reportMissingSuites, missingBody, MARKER } = require('./backfill.cjs');
const cardFile = 'android/custom-cards/src/main/kotlin/x/cards/Card.kt';
function suiteMock({ files = {}, comments = [], link = '' } = {}) {
  const posted = [];
  const github = {
    rest: { pulls: { listFiles() {} }, issues: {
      get: async ({ issue_number }) => ({ data: { number: issue_number, labels: issue_number === 9006 ? [{ name: 'arcana:engine-gap' }] : [] } }),
      listComments: async ({ page }) => ({ headers: { link }, data: page === 1 && link ? [] : comments }),
      createComment: async ({ body }) => { posted.push(body); } } },
    paginate: async (fn, args) => files[args.pull_number] || [],
  };
  return { github, posted };
}
const copilotPr = (number, issue, extra = {}) => ({ number, title: 'Add Card ' + number, body: 'Fixes #' + issue,
  state: 'open', user: { login: 'Copilot' }, base: { ref: manifest.base }, ...extra });

test('lists Copilot card PRs with card code but no registered suite', async () => {
  const covered = manifest.suites[0].issue;
  const prs = [copilotPr(901, 9001), copilotPr(902, covered), copilotPr(903, 9003),
    copilotPr(904, 9004, { user: { login: 'AARMOU' } }), copilotPr(905, 9005, { base: { ref: 'other' } })];
  const { github } = suiteMock({ files: { 901: [{ filename: cardFile }], 902: [{ filename: cardFile }],
    903: [{ filename: 'README.md' }], 904: [{ filename: cardFile }], 905: [{ filename: cardFile }] } });
  const list = await missingSuites({ github, owner: 'o', repo: 'r', manifest, prs });
  assert.deepEqual(list.map(m => m.pr), [901]);
});

test('posts the missing-suite note only when its list changes', async () => {
  const list = [{ pr: 901, issue: 9001, title: 'Add Card 901' }];
  const same = suiteMock({ comments: [{ user: { login: 'github-actions[bot]' }, body: missingBody(list) }] });
  assert.equal((await reportMissingSuites({ github: same.github, owner: 'o', repo: 'r', list })).posted, false);
  // A look-alike from another account never suppresses the note.
  const fake = suiteMock({ comments: [{ user: { login: 'someone' }, body: missingBody(list) }], link: '<x?page=9>; rel="last"' });
  await reportMissingSuites({ github: fake.github, owner: 'o', repo: 'r', list });
  assert.equal(fake.posted.length, 1);
  assert.ok(fake.posted[0].startsWith(MARKER) && fake.posted[0].includes('#901'));
  const none = suiteMock();
  assert.equal((await reportMissingSuites({ github: none.github, owner: 'o', repo: 'r', list: [] })).posted, false);
});

test('engine-gap cards are listed apart and never asked for a suite', async () => {
  const { github } = suiteMock({ files: { 901: [{ filename: cardFile }], 906: [{ filename: cardFile }] } });
  const list = await missingSuites({ github, owner: 'o', repo: 'r', manifest, prs: [copilotPr(901, 9001), copilotPr(906, 9006)] });
  assert.deepEqual(list.map(m => [m.pr, !!m.engineGap]), [[901, false], [906, true]]);
  const body = missingBody(list);
  assert.ok(body.indexOf('#901') < body.indexOf('Blocked on engine work') && body.indexOf('Blocked on engine work') < body.indexOf('#906'));
});
