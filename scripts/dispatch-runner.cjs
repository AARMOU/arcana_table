'use strict';
// Card-worker dispatcher. Every paid operation goes through admission.cjs.
// Writes (ledger, labels, reports) use the workflow's GITHUB_TOKEN as github-actions[bot];
// only the Copilot assignment call uses the owner's dispatch token.
const A = require('./admission.cjs');
const { loadUnits, isCopilot } = require('./units.cjs');

const REPORT_ISSUE = 81;
const INTERRUPT_MARKER = '<!-- arcana-interrupted-attempt-v1 -->';

function unitInstructions(n, base) {
  return [
    `Follow the procedure and acceptance criteria in issue #${n}; it is your complete task.`,
    'Before editing, confirm each card is not already implemented or claimed in open work, then claim the specific cards on the issue.',
    'Implement exact current Oracle behavior with executable regression scenarios, registration and deterministic asset export. No placeholders or weakened tests.',
    `If no card can be identified or implemented exactly, comment on issue #${n} explaining why and do NOT open an empty pull request.`,
    `Open one isolated draft pull request against ${base} with "Unit: #${n}" in its body.`,
    'Never merge, and never change main, production, release or signing configuration.',
  ].join(' ');
}

async function assignCopilot({ token, owner, repo, issue_number, base, fetchImpl = fetch }) {
  if (!token) return 'error';
  const res = await fetchImpl(`https://api.github.com/repos/${owner}/${repo}/issues/${issue_number}/assignees`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ assignees: ['copilot-swe-agent[bot]'], agent_assignment: {
      target_repo: owner + '/' + repo, base_branch: base,
      custom_instructions: unitInstructions(issue_number, base),
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
    `To resolve (owner): confirm what happened, then add the label \`${A.RESOLVE_LABEL}\` to #${unit.raw.number}. The next dispatcher run in \`authorize-once\` or \`normal\` mode appends a resolution record; all original records are kept. The unit's single assignment stays used either way, so resolving can never authorize a second assignment. To retry the card, open a new queue issue.`].join('\n\n') });
  return 'reported';
}

// Owner-authorized, append-only resolution. Records who applied the label and what is observed
// now; never deletes or edits earlier ledger records, never retries the assignment.
async function resolveInterrupted({ github, owner, repo, unit, runId }) {
  const n = unit.raw.number;
  const events = await github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number: n, per_page: 100 });
  const labeled = events.filter(e => e.event === 'labeled' && e.label?.name === A.RESOLVE_LABEL).pop();
  const actor = String(labeled?.actor?.login || 'unknown').replace(/[^A-Za-z0-9_.\-[\]]/g, '');
  const closed = new Set(unit.raw.ledger.filter(r => r.op === 'attempt-result' || r.op === 'resolution').map(r => r.run));
  const open = [...new Set(unit.raw.ledger.filter(r => r.op === 'authorizing' && !closed.has(r.run)).map(r => r.run))];
  const observed = unit.raw.assignedToCopilot ? 'assigned' : 'not-assigned';
  for (const run of open) {
    await github.rest.issues.createComment({ owner, repo, issue_number: n, body: A.formatLedger({ op: 'resolution',
      issue: n, run, result: observed, reason: `owner-label by ${actor} in run ${runId}`, at: new Date().toISOString() }) });
  }
  await github.rest.issues.removeLabel({ owner, repo, issue_number: n, name: A.RESOLVE_LABEL });
  await github.rest.issues.createComment({ owner, repo, issue_number: REPORT_ISSUE, body: [INTERRUPT_MARKER,
    `resolved issue:${n} runs:${open.join(',')}`,
    `**Interrupted attempt on #${n} resolved** (label applied by ${actor}). Observed: Copilot ${observed === 'assigned' ? 'IS' : 'is NOT'} assigned.`,
    'Original ledger records are preserved. The unit\'s assignment remains used; no retry was made.'].join('\n\n') });
  return { issue: n, runs: open, observed, actor };
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
    const resolved = [];
    for (const u of interrupted) {
      if (!dry && u.raw.labels.includes(A.RESOLVE_LABEL)) {
        resolved.push(await resolveInterrupted({ github, owner, repo, unit: u, runId: String(context.runId) }));
      } else await reportInterrupted({ github, owner, repo, unit: u, dry });
    }
    // Never dispatch in the same run that resolved or reported an interruption.
    return done(resolved.length ? 'interruption-resolved' : 'interrupted-attempt',
      { issues: interrupted.map(u => u.raw.number), resolved });
  }

  const base = env.ARCANA_INTEGRATION_BASE;
  if (!base || !/^[A-Za-z0-9._\/-]+$/.test(base)) return done('base-not-configured');
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
  const decision = A.admit({ op: 'assign', unit: { ...candidate.derived, labeled: labeled.includes(candidate),
    actionable: A.unitActionable(candidate.raw.body, base) },
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

module.exports = { runDispatch, assignCopilot, unitInstructions, reportInterrupted, resolveInterrupted, INTERRUPT_MARKER };
