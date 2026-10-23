'use strict';
const { repairAllowed } = require('./usage-gate.cjs');
const MARKER = '<!-- arcana-ci-repair-router-v2 -->';
const OLD_MARKER = '<!-- arcana-ci-repair-router -->';
const OBSERVED = '<!-- arcana-ci-failure-observed-v2 -->';
const WORKFLOWS = new Set(['Arcana Development CI',
  'Argentum six-precon isolated worker regression',
  'Isolated B131 shared-mechanics compatibility',
  'Six-precon worker integration — full custom card regressions']);
const isOurs = c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot';

// Trusted default-branch workflow calls this module. Never loads failed-branch code.
async function routeFailure({ github, context, budget }) {
  const run = context.payload.workflow_run;
  const repoId = context.payload.repository.id;
  if (!run || run.conclusion !== 'failure' || run.event === 'workflow_run' ||
      !WORKFLOWS.has(run.name) || run.head_repository?.id !== repoId ||
      !/^(gpt\/|copilot\/)/.test(run.head_branch || '') ||
      !/^[a-f0-9]{40}$/.test(run.head_sha || '')) return {state:'ignored'};
  const {owner, repo} = context.repo;
  const prs = await github.paginate(github.rest.pulls.list, {owner, repo, state:'open', per_page:100});
  const pr = prs.find(p => p.draft && p.head.repo?.id === repoId &&
    p.head.ref === run.head_branch && p.head.sha === run.head_sha);
  if (!pr) return {state:'stale-or-unmatched'};
  const comments = await github.paginate(github.rest.issues.listComments,
    {owner, repo, issue_number:pr.number, per_page:100});
  const ours = comments.filter(isOurs);
  const fingerprint = `workflow-id:${run.workflow_id}\nhead-sha:${run.head_sha}`;
  const repairs = ours.filter(c => c.body?.startsWith(MARKER) || c.body?.startsWith(OLD_MARKER));
  if (repairs.some(c => c.body.includes(fingerprint) ||
      new RegExp(`(?:^|\\n)run-id:${run.id}(?:\\n|$)`).test(c.body))) return {state:'already-routed'};
  const allowed = repairAllowed(budget, repairs.length);
  const marker = allowed ? MARKER : OBSERVED;
  // A withheld observation is not a repair attempt and can later be upgraded.
  const previous = ours.find(c => c.body?.startsWith(OBSERVED) && c.body.includes(fingerprint));
  if (!allowed && previous) return {state:'already-observed'};
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun,
    {owner, repo, run_id:run.id, per_page:100});
  const details = jobs.filter(j=>j.conclusion==='failure').slice(0,8).map(j => {
    const step = j.steps?.find(s=>s.conclusion==='failure');
    // Workflow/job names may contain mentions; do not relay arbitrary @ handles.
    return '- '+`${j.name}${step ? ' / '+step.name : ''}`.replace(/@/g,'＠')+
      ` (job ${j.id})`;
  }).join('\n') || '- No failed job details available; inspect linked run.';
  const body = [marker, `run-id:${run.id}`, fingerprint,
    allowed ? '@copilot Reproduce and repair this exact-head CI failure on this PR branch.' :
      'CI failure observed. Automatic agent request withheld: budget unknown/paused or retry limit reached.',
    `Run: https://github.com/${owner}/${repo}/actions/runs/${run.id}`,
    `Commit: \`${run.head_sha}\``, details,
    allowed ? `Repair request ${repairs.length+1} of 3. Preserve all test assertions and Oracle behavior. Report exact commit, executed commands and run IDs. Never merge, release, change main/production or signing.` :
      'No worker launched and no retry requested. This observation does not consume a repair attempt.'
  ].join('\n\n');
  // Narrow the race with pushes while the jobs/comments were being fetched.
  const current = await github.rest.pulls.get({owner,repo,pull_number:pr.number});
  if (!current.data.draft || current.data.state !== 'open' || current.data.head.sha !== run.head_sha)
    return {state:'stale-or-unmatched'};
  try {
    if (previous) await github.rest.issues.updateComment({owner,repo,comment_id:previous.id,body});
    else await github.rest.issues.createComment({owner,repo,issue_number:pr.number,body});
  } catch (e) {
    if (e.status === 403) return {state:'permission-blocked', required:'pull-requests: write', pr:pr.number};
    throw e;
  }
  return {state:allowed?'repair-requested':'observed-only',pr:pr.number};
}
module.exports = {routeFailure, MARKER, OBSERVED};
