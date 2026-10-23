'use strict';
// Card-worker dispatcher. Every paid operation goes through admission.cjs.
// Writes (ledger, labels, reports) use the workflow's GITHUB_TOKEN as github-actions[bot];
// only the Copilot assignment call uses the owner's dispatch token.
const A = require('./admission.cjs');
const { loadUnits, isCopilot } = require('./units.cjs');

const REPORT_ISSUE = 81;
const INTERRUPT_MARKER = '<!-- arcana-interrupted-attempt-v1 -->';
const DEFAULT_BASE = 'gpt/six-precon-worker-integration-20261022';

async function assignCopilot({ token, owner, repo, issue_number, base, fetchImpl = fetch }) {
  if (!token) return 'error';
  const res = await fetchImpl(`https://api.github.com/repos/${owner}/${repo}/issues/${issue_number}/assignees`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ assignees: ['copilot-swe-agent[bot]'], agent_assignment: {
      target_repo: owner + '/' + repo, base_branch: base,
      custom_instructions: 'Read issue #81. Claim distinct cards before coding. Exact Oracle behavior and executable regressions required. Isolated draft PR only. Put "Unit: #' + issue_number + '" in the PR body. No merge/main/production/release/signing changes.',
      custom_agent: '', model: '' } }),
    signal: AbortSignal.timeout(20000),
  });
  if (!res.ok) return 'error';
  const data = await res.json();
  return (data.assignees || []).some(isCopilot) ? 'confirmed' : 'not-confirmed';
}

async function reportInterrupted({ github, owner, repo, unit, dry }) {
  const runs = unit.raw.ledger.filter(r => r.op === 'authorizing').map(r => r.run);
  const key = `issue:${unit.raw.number} runs:${runs.join(',')}`;
  if (dry) return 'dry-run';
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: REPORT_ISSUE, per_page: 100 });
  if (comments.some(c => A.isBot(c) && c.body?.startsWith(INTERRUPT_MARKER) && c.body.includes(key))) return 'already-reported';
  await github.rest.issues.createComment({ owner, repo, issue_number: REPORT_ISSUE, body: [INTERRUPT_MARKER, key,
    `**Interrupted dispatch attempt on #${unit.raw.number}.** All dispatch is refused until the owner reviews it.`,
    `Observed now: Copilot ${unit.raw.assignedToCopilot ? 'IS' : 'is NOT'} assigned (observation only; never retried automatically).`,
    `To clear (owner): confirm what happened, then delete the \`authorizing\` ledger comment on #${unit.raw.number}. This report stays as the audit record. A Copilot assignment that did happen keeps counting as the unit's one assignment.`].join('\n\n') });
  return 'reported';
}

async function runDispatch({ github, context, core, env, readBudget, now = Date.now(), fetchImpl }) {
  const { owner, repo } = context.repo;
  const ready = A.readiness(env);
  const done = (state, extra = {}) => { const r = { state, ...extra }; core.info(JSON.stringify(r)); return r; };
  if (ready.errors.length) return done('configuration-error', { errors: ready.errors });
  if (ready.dispatch === 'off') return done('dispatch-off');
  const dry = ready.dispatch === 'dry-run';
  if (!dry && !ready.master) return done('readiness-off');

  const data = await loadUnits({ github, owner, repo, now });
  const units = data.units.map(u => u.derived);
  const interrupted = data.units.filter(u => u.derived.blocksAll);
  if (interrupted.length) {
    for (const u of interrupted) await reportInterrupted({ github, owner, repo, unit: u, dry });
    return done('interrupted-attempt', { issues: interrupted.map(u => u.raw.number) });
  }

  const base = env.ARCANA_INTEGRATION_BASE || DEFAULT_BASE;
  const baseSha = (await github.rest.repos.getBranch({ owner, repo, branch: base })).data.commit.sha;
  const runs = await github.paginate(github.rest.actions.listWorkflowRunsForRepo,
    { owner, repo, head_sha: baseSha, status: 'success', per_page: 100 });
  if (!runs.some(r => r.head_sha === baseSha && r.name === 'Arcana Development CI' && r.conclusion === 'success'))
    return done('ci-not-ready', { base, baseSha });

  const labeled = data.units.filter(u => u.raw.labels.includes(A.ONE_SHOT_LABEL));
  let candidate;
  if (ready.dispatch === 'authorize-once') {
    if (labeled.length !== 1) return done('authorize-once-needs-exactly-one-label', { labeled: labeled.map(u => u.raw.number) });
    candidate = labeled[0];
  } else {
    candidate = data.units.filter(u => u.derived.state === 'queued' && u.raw.title.startsWith('[Arcana Queue]') &&
      !u.prs.length).sort((a, b) => a.raw.number - b.raw.number)[0];
  }
  if (!candidate) return done('nothing-eligible');

  const { snapshot } = await readBudget(env);
  const n = candidate.raw.number;
  const decision = A.admit({ op: 'assign', unit: { ...candidate.derived, labeled: labeled.includes(candidate) },
    units, labeledCount: labeled.length, ready, snapshot, now: Math.max(now, Date.now()) });
  await core.summary.addHeading('Admission decision').addCodeBlock(JSON.stringify({ issue: n, ...decision })).write();
  if (!decision.allowed) return done(decision.dryRun ? 'dry-run' : 'denied', { issue: n, reason: decision.reason });

  const runId = String(context.runId);
  const at = () => new Date().toISOString();
  // 1. Record before spending anything. If this fails, the label is still present and nothing ran.
  await github.rest.issues.createComment({ owner, repo, issue_number: n,
    body: A.formatLedger({ op: 'authorizing', issue: n, run: runId, at: at() }) });
  // 2. Consume the one-shot authorization. Failure leaves an interrupted attempt for owner review.
  if (ready.dispatch === 'authorize-once') {
    try { await github.rest.issues.removeLabel({ owner, repo, issue_number: n, name: A.ONE_SHOT_LABEL }); }
    catch { core.setFailed('Could not consume one-shot label; attempt left for owner review.'); return done('label-removal-failed', { issue: n }); }
  }
  // 3. Re-check the claim, then attempt exactly once.
  const fresh = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
  let result;
  if (fresh.state !== 'open' || (fresh.assignees || []).length) result = 'not-attempted';
  else {
    try { result = await assignCopilot({ token: env.COPILOT_DISPATCH_TOKEN, owner, repo, issue_number: n, base, fetchImpl }); }
    catch { result = 'error'; }
  }
  // 4. Close the record. Missing this step is what the interrupted-attempt check catches.
  await github.rest.issues.createComment({ owner, repo, issue_number: n,
    body: A.formatLedger({ op: 'attempt-result', issue: n, run: runId, result, at: at() }) });
  if (result !== 'confirmed') core.warning(`Assignment ${result} for #${n}; no retry.`);
  return done('attempted', { issue: n, result });
}

module.exports = { runDispatch, assignCopilot, reportInterrupted, INTERRUPT_MARKER, DEFAULT_BASE };
