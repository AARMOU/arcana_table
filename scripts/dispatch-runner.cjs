'use strict';
// Card-worker dispatcher. Every paid operation goes through admission.cjs.
// Writes (ledger, labels, reports) use the workflow's GITHUB_TOKEN as github-actions[bot];
// only the Copilot assignment call uses the owner's dispatch token.
const A = require('./admission.cjs');
const { loadUnits, isCopilot, contentWrittenAt, approvalOf, commentersOf } = require('./units.cjs');
const F = require('./feasibility.cjs');

// Re-reads the issue right before the paid call so an edit, rename, new or edited comment, or a
// body that no longer passes the readiness rules stops the assignment, even if it happened after
// admission. Returns provenance() for the fresh state, plus the fresh readiness result.
async function revalidate({ github, owner, repo, n, fresh, needApproval, approvers, base }) {
  try {
    const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: n, per_page: 100 });
    const raw = { authorAssociation: fresh.author_association, authorLogin: fresh.user?.login,
      authorType: fresh.user?.type, title: fresh.title, body: fresh.body,
      commenters: commentersOf(comments) };
    if (raw.authorLogin === 'github-actions[bot]' && raw.authorType === 'Bot') {
      const writtenAt = await contentWrittenAt(github, owner, repo, n);
      raw.autoQueueUnedited = Number.isFinite(writtenAt) && writtenAt === Date.parse(fresh.created_at);
    }
    if (needApproval) {
      const events = await github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number: n, per_page: 100 });
      raw.approval = approvalOf(events, await contentWrittenAt(github, owner, repo, n));
    }
    const p = A.provenance(raw, { needApproval, approvers });
    const act = A.unitActionable(fresh.body, base);
    const reasons = [...p.reasons, ...(act.ok ? [] : ['not-actionable-' + act.missing.join('-')])];
    return { ok: reasons.length === 0, reasons, actor: raw.approval?.actor || null };
  } catch { return { ok: false, reasons: ['revalidation-unavailable'] }; }
}

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

// One selected owner authorization ALWAYS wins over the ordinary lowest-numbered normal
// candidate. Without this, a dashboard tap on #379 in normal mode could dispatch #100.
// Multiple labels fail closed. Feasibility/provenance/billing are checked AFTER selection.
function selectCandidate(allUnits, mode, base, feasible, actionable = A.unitActionable) {
  const labeled = allUnits.filter(u => u.raw.labels.includes(A.ONE_SHOT_LABEL));
  if (mode === 'authorize-once') {
    return labeled.length === 1
      ? { candidate: labeled[0], labeled, error: null }
      : { candidate: null, labeled, error: 'authorize-once-needs-exactly-one-label' };
  }
  // Explicit priority is active in NORMAL mode. Dry-run continues to model ordinary
  // automatic eligibility without treating a pending owner label as a paid assignment.
  if (mode === 'normal') {
    if (labeled.length > 1) return { candidate: null, labeled, error: 'multiple-owner-authorizations' };
    if (labeled.length === 1) return { candidate: labeled[0], labeled, error: null };
  }
  const candidate = allUnits.filter(u => u.derived.state === 'queued' && u.raw.title.startsWith('[Arcana Queue]') &&
    !u.prs.length && u.derived.provenance?.ok && actionable(u.raw.body, base).ok && feasible(u).ok)
    .sort((a, b) => a.raw.number - b.raw.number)[0];
  return { candidate, labeled, error: null };
}

// `report` is an optional sink the workflow publishes to the Arcana Control status comment. It never
// feeds back into admission.
async function runDispatch({ github, context, core, env, readBudget, now = Date.now(), fetchImpl, report = {} }) {
  const { owner, repo } = context.repo;
  const ready = A.readiness(env);
  report.ready = ready;
  const done = (state, extra = {}) => { const r = { state, ...extra }; core.info(JSON.stringify(r)); return r; };
  if (ready.errors.length) return done('configuration-error', { errors: ready.errors });
  if (ready.dispatch === 'off') return done('dispatch-off');
  const dry = ready.dispatch === 'dry-run';
  if (!dry && !ready.master) return done('readiness-off');

  // One-shot approvers: ARCANA_SPEND_APPROVERS, else the repository owner alone.
  const approvers = ready.approvers || [owner];
  const data = await loadUnits({ github, owner, repo, now, approvers, lagMs: ready.billingLagMs });
  const units = data.units.map(u => u.derived);
  report.units = units;
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
  report.base = { branch: base, sha: baseSha };
  const runs = await github.paginate(github.rest.actions.listWorkflowRunsForRepo,
    { owner, repo, head_sha: baseSha, status: 'success', per_page: 100 });
  if (!runs.some(r => r.head_sha === baseSha && r.name === 'Arcana Development CI' && r.conclusion === 'success')) {
    // Say what GitHub actually has for this commit (e.g. a run waiting for approval), not just "no".
    let ciRuns = [];
    try {
      const all = await github.paginate(github.rest.actions.listWorkflowRunsForRepo, { owner, repo, head_sha: baseSha, per_page: 100 });
      ciRuns = all.filter(r => r.head_sha === baseSha && r.name === 'Arcana Development CI')
        .slice(0, 5).map(r => ({ id: r.id, event: r.event, status: r.status, conclusion: r.conclusion }));
    } catch { ciRuns = null; }
    report.ciRuns = ciRuns;
    return done('ci-not-ready', { base, baseSha });
  }

  // Engine feasibility (Cardkit on this exact base SHA). Missing or stale verdicts fail closed.
  const verdicts = F.parseVerdicts(env.ARCANA_FEASIBILITY);
  const feasible = u => F.assess({ body: u.raw.body, override: u.raw.feasibilityOverride, approvers, verdicts, baseSha });
  report.feasibility = verdicts ? { baseSha: verdicts.baseSha, classified: Object.keys(verdicts.cards).length } : { unavailable: true };
  const selection = selectCandidate(data.units, ready.dispatch, base, feasible);
  const { candidate, labeled } = selection;
  if (selection.error) return done(selection.error, { labeled: labeled.map(u => u.raw.number) });
  if (!candidate) {
    // Explain why every queued unit was passed over instead of staying silently blocked.
    for (const u of data.units) {
      if (u.derived.state !== 'queued') continue;
      u.derived.skip = !u.raw.title.startsWith('[Arcana Queue]') ? 'not-a-queue-issue'
        : u.prs.length ? 'has-open-pr ' + u.prs.map(p => '#' + p.number).join(',')
          : !u.derived.provenance?.ok ? 'untrusted-origin: ' + (u.derived.provenance?.reasons || []).join(',')
            : !A.unitActionable(u.raw.body, base).ok ? 'not-actionable: ' + A.unitActionable(u.raw.body, base).missing.join(',')
              : !feasible(u).ok ? 'feasibility: ' + feasible(u).reason : 'not-labeled';
    }
    return done('nothing-eligible');
  }

  const n = candidate.raw.number;
  // authorize-once names its unit explicitly, so the gate denies instead of skipping.
  const feas = feasible(candidate);
  report.candidateFeasibility = { issue: n, ok: feas.ok, reason: feas.reason, cards: feas.cards.map(c => ({ name: c.name, readiness: c.readiness, route: c.route, hint: c.hint || null })) };
  // An incomplete unit is denied by admission with its own, more specific reason.
  if (!feas.ok && A.unitActionable(candidate.raw.body, base).ok) {
    await core.summary.addHeading('Feasibility gate').addCodeBlock(JSON.stringify(report.candidateFeasibility)).write();
    return done('denied', { issue: n, reason: 'feasibility: ' + feas.reason });
  }
  // Non-spending preflight: dry-run proves the edit-history lookup works from Actions before any
  // one-shot relies on it (a failure there would refuse every one-shot unit).
  if (dry) {
    const edit = await contentWrittenAt(github, owner, repo, n);
    await core.summary.addHeading('Edit-history preflight').addCodeBlock(JSON.stringify({ issue: n, available: Number.isFinite(edit) })).write();
    core.info(JSON.stringify({ editHistoryPreflight: Number.isFinite(edit) ? 'ok' : 'unavailable', issue: n }));
  }
  const billing = await readBudget(env);
  report.billing = billing;
  const { snapshot } = billing;
  const decision = A.admit({ op: 'assign', unit: { ...candidate.derived, labeled: labeled.includes(candidate),
    actionable: A.unitActionable(candidate.raw.body, base) },
    units, labeledCount: labeled.length, ready, snapshot, now: Math.max(now, Date.now()) });
  report.decision = { issue: n, ...decision };
  // Job summaries of a public repository are public: no usage ratio or credit totals.
  await core.summary.addHeading('Admission decision').addCodeBlock(JSON.stringify({ issue: n, allowed: !!decision.allowed,
    dryRun: !!decision.dryRun, wouldAllow: !!decision.wouldAllow, reason: decision.reason, level: decision.budget?.level || null })).write();
  if (!decision.allowed) return done(decision.dryRun ? 'dry-run' : 'denied', { issue: n, reason: decision.reason });

  const runId = String(context.runId);
  const at = () => new Date().toISOString();
  // 1. Record before spending anything. If this fails, the label is still present and nothing ran.
  await github.rest.issues.createComment({ owner, repo, issue_number: n,
    body: A.formatLedger({ op: 'authorizing', issue: n, run: runId,
      ...(labeled.includes(candidate) ? { reason: 'approved-by ' + String(candidate.raw.approval?.actor || 'unknown').replace(/[^A-Za-z0-9-]/g, '') } : {}),
      at: at() }) });
  // 2. Consume the one-shot authorization. Failure leaves an interrupted attempt for owner review.
  if (labeled.includes(candidate)) {
    try { await github.rest.issues.removeLabel({ owner, repo, issue_number: n, name: A.ONE_SHOT_LABEL }); }
    catch { core.setFailed('Could not consume one-shot label; attempt left for owner review.'); return done('label-removal-failed', { issue: n }); }
  }
  // 3. Re-check the claim and the content's origin and approval, then attempt exactly once.
  const fresh = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
  let result, reason;
  const claimed = fresh.state !== 'open' || (fresh.assignees || []).length > 0;
  const origin = claimed ? null : await revalidate({ github, owner, repo, n, fresh, needApproval: labeled.includes(candidate), approvers, base });
  if (claimed) result = 'not-attempted';
  else if (!origin.ok) { result = 'not-attempted'; reason = 'revalidation ' + origin.reasons.join(' '); }
  else {
    try { result = await assignCopilot({ token: env.COPILOT_DISPATCH_TOKEN, owner, repo, issue_number: n, base, fetchImpl }); }
    catch { result = 'error'; }
  }
  // 4. Close the record. Missing this step is what the interrupted-attempt check catches.
  await github.rest.issues.createComment({ owner, repo, issue_number: n,
    body: A.formatLedger({ op: 'attempt-result', issue: n, run: runId, result, reason, at: at() }) });
  if (result !== 'confirmed') core.warning(`Assignment ${result} for #${n}${reason ? ' (' + reason + ')' : ''}; no retry.`);
  return done('attempted', { issue: n, result, ...(reason ? { reason } : {}) });
}

module.exports = { runDispatch, selectCandidate, assignCopilot, unitInstructions, reportInterrupted, resolveInterrupted, revalidate, INTERRUPT_MARKER };
