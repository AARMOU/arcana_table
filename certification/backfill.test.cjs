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
        reRunWorkflow: async ({ run_id }) => { reruns.push(run_id); } } },
    paginate: async (fn, args) => fn === github.rest.pulls.list ? prs : (checks[args.ref] || []),
  };
  return { github, reruns };
}
const run = (id, name, head_sha, conclusion = 'success', created_at = '2026-10-29T10:00:00Z') =>
  ({ id, name, head_sha, conclusion, created_at });

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
    runs: { [h]: [run(5, 'Arcana Cardkit', h, 'failure')] } });
  const r = await backfill({ github, owner: 'o', repo: 'r', root: __dirname + '/..' });
  assert.deepEqual(reruns, []);
  assert.equal(r[0].reason, 'no successful CI run on head');
});
