'use strict';
const A = require('./admission.cjs');
const { loadUnits, unitOf } = require('./units.cjs');
const { createCopilotDelivery } = require('./copilot-delivery.cjs');
const MARKER = '<!-- arcana-ci-repair-router-v2 -->';
const OLD_MARKER = '<!-- arcana-ci-repair-router -->';
const OBSERVED = '<!-- arcana-ci-failure-observed-v2 -->';
const WORKFLOWS = new Set(['Arcana Development CI', 'Arcana Cardkit',
  'Argentum six-precon isolated worker regression',
  'Isolated B131 shared-mechanics compatibility',
  'Six-precon worker integration — full custom card regressions']);
const isOurs = c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot';
const observeOnly = async () => ({ allowed: false, reason: 'observation only' });
const DISPATCHER = 'Arcana Card Worker Dispatcher';

// CI can finish before the builder's session-finished event is visible. Revisit
// withheld failures after dispatcher ticks, even when its builder queue is empty.
// This runs under the same serialized control group as dispatch and repair.
async function runRouter({ github, context, env, readBudget, delivery }) {
  const forcedDry = context.eventName === 'workflow_dispatch' &&
    String(context.payload?.inputs?.dry_run) !== 'false';
  const ready = A.readiness(env);
  if (ready.repair === 'off') return { state: 'repair-off' };
  const authorize = !forcedDry && ready.repair === 'request' ?
    createAuthorizer({ github, ...context.repo, env, readBudget }) : observeOnly;
  if (context.eventName === 'schedule' || context.eventName === 'workflow_dispatch' ||
      (context.payload?.workflow_run?.name === DISPATCHER &&
       context.payload.workflow_run.conclusion === 'success'))
    return reconcileFailures({ github, context, authorize, delivery });
  return routeFailure({ github, context, authorize, delivery });
}

// Shared admission for repair requests: readiness, work unit, capacity, ceilings, budget.
function createAuthorizer({ github, owner, repo, env, readBudget, now = Date.now() }) {
  // One router invocation reconciles many failures; loadUnits costs ~100 API calls against the
  // shared hourly quota. Units are read once per invocation: after any admitted repair the
  // router stops authorizing (one paid request per tick), so the snapshot cannot go stale.
  let units;
  return async ({ pr, run }) => {
    const ready = A.readiness(env);
    if (ready.repair !== 'request' || !ready.master) return { allowed: false, reason: 'readiness: repair mode ' + ready.repair };
    units ??= loadUnits({ github, owner, repo, now, lagMs: ready.billingLagMs }).catch(e => { units = undefined; throw e; });
    const data = await units;
    const n = unitOf(pr.body, data.numbers);
    const unit = data.units.find(u => u.raw.number === n);
    if (!unit) return { allowed: false, reason: 'no work unit linked to PR' };
    // A repair worker also reads the PR conversation: it must come from trusted people too.
    let prComments;
    try { prComments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: pr.number, per_page: 100 }); }
    catch { return { allowed: false, reason: 'untrusted-origin: pr-comments-unavailable' }; }
    if (!A.TRUSTED_ASSOCIATIONS.includes(pr.author_association) && !A.trustedCommenter({ login: pr.user?.login, type: pr.user?.type }))
      return { allowed: false, reason: 'untrusted-origin: pr-author-untrusted' };
    if (prComments.some(c => !A.trustedCommenter({ association: c.author_association, login: c.user?.login, type: c.user?.type })))
      return { allowed: false, reason: 'untrusted-origin: untrusted-pr-comments' };
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
async function routeFailure({ github, context, authorize = observeOnly, openPrs, delivery }) {
  const run = context.payload.workflow_run;
  const repoId = context.payload.repository.id;
  if (!run || run.conclusion !== 'failure' || run.event === 'workflow_run' ||
      !WORKFLOWS.has(run.name) || run.head_repository?.id !== repoId ||
      !/^(gpt\/|copilot\/)/.test(run.head_branch || '') ||
      !/^[a-f0-9]{40}$/.test(run.head_sha || '')) return {state:'ignored'};
  const {owner, repo} = context.repo;
  const prs = openPrs || await github.paginate(github.rest.pulls.list, {owner, repo, state:'open', per_page:100});
  const pr = prs.find(p => p.draft && p.head.repo?.id === repoId &&
    p.head.ref === run.head_branch && p.head.sha === run.head_sha);
  if (!pr) return {state:'stale-or-unmatched'};
  const comments = await github.paginate(github.rest.issues.listComments,
    {owner, repo, issue_number:pr.number, per_page:100});
  const ours = comments.filter(isOurs);
  const fingerprint = `workflow-id:${run.workflow_id}\nhead-sha:${run.head_sha}`;
  // Owner-delivered comments actually trigger Copilot. Legacy bot requests
  // remain deduped: never spend again simply to repeat an uncertain handoff.
  const ownerComment = c => String(c.user?.login || '').toLowerCase() === owner.toLowerCase() &&
    A.TRUSTED_ASSOCIATIONS.includes(c.author_association);
  const repairs = comments.filter(c => (isOurs(c) || ownerComment(c)) &&
    (c.body?.startsWith(MARKER) || c.body?.startsWith(OLD_MARKER)));
  if (repairs.some(c => c.body.includes(fingerprint) ||
      new RegExp(`(?:^|\\n)run-id:${run.id}(?:\\n|$)`).test(c.body))) return {state:'already-routed'};
  const previous = ours.find(c => c.body?.startsWith(OBSERVED) && c.body.includes(fingerprint));
  // Fail closed before admission/ledger writes if the existing dispatch
  // credential cannot prove it belongs to the owner.
  const sender = delivery || createCopilotDelivery({owner,repo,token:null});
  const proof = authorize === observeOnly ? {ok:true} : await sender.preflight();
  const decision = proof.ok ? await authorize({pr,run}) :
    {allowed:false,reason:proof.reason};
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
    if (allowed) {
      // Reserve before invoking Copilot. If an owner-token POST is uncertain,
      // the attempt remains consumed, preventing silent repeated spending.
      await decision.record();
      const delivered = await sender.post({issue_number:pr.number,body});
      if (!delivered.ok)
        return {state:'delivery-uncertain',pr:pr.number,reason:delivered.reason};
    } else if (previous) {
      await github.rest.issues.updateComment({owner,repo,comment_id:previous.id,body});
    } else {
      await github.rest.issues.createComment({owner,repo,issue_number:pr.number,body});
    }
  } catch (e) {
    if (e.status === 403) return {state:'permission-blocked', required:'pull-requests: write', pr:pr.number};
    // A failed observation POST cannot have invoked Copilot. Keep reconciling
    // other PRs; never retry the uncertain write in this tick.
    if (!allowed && e.status >= 500)
      return {state:'observation-unavailable',pr:pr.number,reason:'github-http-' + e.status};
    throw e;
  }
  return {state:allowed?'repair-requested':'observed-only',pr:pr.number, reason: decision.reason};
}

// Scheduled backfill also revisits capacity/billing-denied failures when repairs are enabled.
// Never more than one paid request per tick, with the same exact-head/admission guards.
// Default remains observation-only for callers that do not supply an authorizer.
async function reconcileFailures({ github, context, authorize = observeOnly, delivery,
  now = Date.now(), windowMs = 24 * 60 * 60 * 1000 }) {
  const { owner, repo } = context.repo;
  const since = new Date(now - windowMs).toISOString();
  const runs = await github.paginate(github.rest.actions.listWorkflowRunsForRepo,
    { owner, repo, created: '>=' + since, per_page: 100 });
  // Pre-filter stale commits and duplicate runs before potentially expensive admission.
  const openPrs = await github.paginate(github.rest.pulls.list,
    { owner, repo, state:'open', per_page:100 });
  const repoId = context.payload.repository.id;
  const heads = new Set(openPrs.filter(p => p.draft && p.head.repo?.id === repoId &&
    /^(gpt\/|copilot\/)/.test(p.head.ref || '')).map(p => p.head.ref + ':' + p.head.sha));
  const results = [], seen = new Set();
  let requested = 0, admissionBlocked = false;
  // Read all outcomes: a newer successful/in-progress run supersedes a failure
  // on the same workflow and head. Never repair an already-cleared failure.
  const latestRuns = [...runs].sort((a, b) =>
    (Date.parse(b.run_started_at || b.created_at) || 0) -
    (Date.parse(a.run_started_at || a.created_at) || 0));
  for (const run of latestRuns.filter(r => WORKFLOWS.has(r.name) &&
    r.head_repository?.id === repoId && heads.has(r.head_branch + ':' + r.head_sha))) {
    const fingerprint = run.workflow_id + ':' + run.head_sha;
    if (seen.has(fingerprint)) continue;
    seen.add(fingerprint);
    if (run.conclusion !== 'failure') continue;
    const ctx = { repo: context.repo, payload: { repository: context.payload.repository, workflow_run: run } };
    const r = await routeFailure({ github, context: ctx, openPrs, delivery,
      authorize: requested < 1 && !admissionBlocked ? authorize : observeOnly });
    if (r.state === 'repair-requested' || r.state === 'delivery-uncertain') requested++;
    // Capacity and billing are shared gates: don't re-fetch the same admission
    // state for every other PR when no repair could be admitted this tick.
    if (/^(capacity|budget|readiness|configuration):/.test(String(r.reason || '')))
      admissionBlocked = true;
    results.push({ run: run.id, state: r.state });
  }
  return results;
}

module.exports = {runRouter, routeFailure, reconcileFailures, createAuthorizer, MARKER, OBSERVED, WORKFLOWS};

