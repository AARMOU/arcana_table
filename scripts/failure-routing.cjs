'use strict';
const A = require('./admission.cjs');
const { loadUnits, unitOf } = require('./units.cjs');
const MARKER = '<!-- arcana-ci-repair-router-v2 -->';
const OLD_MARKER = '<!-- arcana-ci-repair-router -->';
const OBSERVED = '<!-- arcana-ci-failure-observed-v2 -->';
const WORKFLOWS = new Set(['Arcana Development CI',
  'Argentum six-precon isolated worker regression',
  'Isolated B131 shared-mechanics compatibility',
  'Six-precon worker integration — full custom card regressions']);
const isOurs = c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot';
const observeOnly = async () => ({ allowed: false, reason: 'observation only' });

// Shared admission for repair requests: readiness, work unit, capacity, ceilings, budget.
function createAuthorizer({ github, owner, repo, env, readBudget, now = Date.now() }) {
  return async ({ pr, run }) => {
    const ready = A.readiness(env);
    if (ready.repair !== 'request' || !ready.master) return { allowed: false, reason: 'readiness: repair mode ' + ready.repair };
    const data = await loadUnits({ github, owner, repo, now });
    const n = unitOf(pr.body, data.numbers);
    const unit = data.units.find(u => u.raw.number === n);
    if (!unit) return { allowed: false, reason: 'no work unit linked to PR' };
    const { snapshot } = await readBudget(env);
    // Evaluate after the billing read: a fresh measurement must not look like it came from the future.
    const d = A.admit({ op: 'repair', unit: unit.derived, units: data.units.map(u => u.derived), ready, snapshot, now: Math.max(now, Date.now()) });
    if (!d.allowed) return { allowed: false, reason: d.reason };
    return { allowed: true, reason: d.reason, attempt: unit.derived.repairs + 1, unit: n,
      // Reserve before posting: a failed post over-counts (safe), never under-counts.
      record: () => github.rest.issues.createComment({ owner, repo, issue_number: n,
        body: A.formatLedger({ op: 'repair-request', issue: n, run: String(run.id), sha: run.head_sha, at: new Date().toISOString() }) }) };
  };
}

// Trusted default-branch workflow calls this module. Never loads failed-branch code.
async function routeFailure({ github, context, authorize = observeOnly }) {
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
  const previous = ours.find(c => c.body?.startsWith(OBSERVED) && c.body.includes(fingerprint));
  const decision = await authorize({ pr, run });
  const allowed = decision.allowed === true;
  // A withheld observation is not a repair attempt and can later be upgraded.
  if (!allowed && previous) return {state:'already-observed', reason: decision.reason};
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun,
    {owner, repo, run_id:run.id, per_page:100});
  const details = jobs.filter(j=>j.conclusion==='failure').slice(0,8).map(j => {
    const step = j.steps?.find(s=>s.conclusion==='failure');
    // Workflow/job names may contain mentions; do not relay arbitrary @ handles.
    return '- '+`${j.name}${step ? ' / '+step.name : ''}`.replace(/@/g,'＠')+
      ` (job ${j.id})`;
  }).join('\n') || '- No failed job details available; inspect linked run.';
  const reason = String(decision.reason || '').replace(/[@\r\n]/g, ' ');
  const body = [allowed ? MARKER : OBSERVED, `run-id:${run.id}`, fingerprint,
    allowed ? '@copilot Reproduce and repair this exact-head CI failure on this PR branch.' :
      `CI failure observed. Automatic agent request withheld (${reason}).`,
    `Run: https://github.com/${owner}/${repo}/actions/runs/${run.id}`,
    `Commit: \`${run.head_sha}\``, details,
    allowed ? `Repair request ${decision.attempt} of ${A.CEILING.repair} for unit #${decision.unit}. Preserve all test assertions and Oracle behavior. Report exact commit, executed commands and run IDs. Never merge, release, change main/production or signing.` :
      'No worker launched and no retry requested. This observation does not consume a repair attempt.'
  ].join('\n\n');
  // Narrow the race with pushes while the jobs/comments were being fetched.
  const current = await github.rest.pulls.get({owner,repo,pull_number:pr.number});
  if (!current.data.draft || current.data.state !== 'open' || current.data.head.sha !== run.head_sha)
    return {state:'stale-or-unmatched'};
  try {
    if (allowed) await decision.record();
    if (previous) await github.rest.issues.updateComment({owner,repo,comment_id:previous.id,body});
    else await github.rest.issues.createComment({owner,repo,issue_number:pr.number,body});
  } catch (e) {
    if (e.status === 403) return {state:'permission-blocked', required:'pull-requests: write', pr:pr.number};
    throw e;
  }
  return {state:allowed?'repair-requested':'observed-only',pr:pr.number, reason: decision.reason};
}

// Scheduled backfill: records failures whose router run was dropped from the queue.
// Observation only; never requests a repair.
async function reconcileFailures({ github, context, now = Date.now(), windowMs = 6 * 60 * 60 * 1000 }) {
  const { owner, repo } = context.repo;
  const since = new Date(now - windowMs).toISOString();
  const runs = await github.paginate(github.rest.actions.listWorkflowRunsForRepo,
    { owner, repo, status: 'failure', created: '>=' + since, per_page: 100 });
  const results = [];
  for (const run of runs.filter(r => WORKFLOWS.has(r.name))) {
    const ctx = { repo: context.repo, payload: { repository: context.payload.repository, workflow_run: run } };
    const r = await routeFailure({ github, context: ctx, authorize: observeOnly });
    results.push({ run: run.id, state: r.state });
  }
  return results;
}

module.exports = {routeFailure, reconcileFailures, createAuthorizer, MARKER, OBSERVED, WORKFLOWS};
