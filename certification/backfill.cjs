'use strict';
// See .github/workflows/arcana-certification-backfill.yml. Re-runs CI only; certification
// itself still happens in the trusted independent certification workflow.
const P = require('./policy.cjs');
const { cardPr } = require('./premerge-plan.cjs');

const TRIGGERS = ['Arcana Cardkit', 'Arcana Development CI'];
const MAX_RERUNS = 6;

async function backfill({ github, owner, repo, root = '.' }) {
  const manifest = P.readManifest(root + '/certification/suites.json');
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  const out = [];
  for (const suite of manifest.suites) {
    if (out.filter(r => r.rerun).length >= MAX_RERUNS) break;
    const pr = cardPr(manifest, suite, prs);
    if (!pr) continue;
    const sha = pr.head.sha;
    const checks = await github.paginate(github.rest.checks.listForRef, { owner, repo, ref: sha, per_page: 100 });
    // A failed certification is retried: the merged change may be the corrected suite.
    if (checks.some(c => c.name === P.CHECK_NAME && c.head_sha === sha && c.app?.slug === 'github-actions' &&
        c.conclusion === 'success')) continue;
    const runs = (await github.rest.actions.listWorkflowRunsForRepo({ owner, repo, head_sha: sha, event: 'pull_request', per_page: 100 }))
      .data.workflow_runs || [];
    const run = runs.filter(r => TRIGGERS.includes(r.name) && r.head_sha === sha && r.conclusion === 'success')
      .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0];
    if (!run) { out.push({ suite: suite.key, pr: pr.number, rerun: false, reason: 'no successful CI run on head' }); continue; }
    await github.rest.actions.reRunWorkflow({ owner, repo, run_id: run.id });
    out.push({ suite: suite.key, pr: pr.number, rerun: true, run: run.id, workflow: run.name });
  }
  return out;
}

module.exports = { backfill, MAX_RERUNS };
