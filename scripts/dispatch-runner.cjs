'use strict';
// Card-worker dispatcher. Every paid operation goes through admission.cjs.
// Writes (ledger, labels, reports) use the workflow's GITHUB_TOKEN as github-actions[bot];
// only the Copilot assignment call uses the owner's dispatch token.
const A = require('./admission.cjs');
const { loadUnits, isCopilot, contentWrittenAt, approvalOf, commentersOf } = require('./units.cjs');
const F = require('./feasibility.cjs');

// A certification-suite request (certification/suite-requests.cjs): test-only work on main.
const isSuite = u => String(u?.raw?.title || '').startsWith(A.SUITE_TITLE);
const actionableOf = (title, body, base) => String(title || '').startsWith(A.SUITE_TITLE) ? A.suiteActionable(body) : A.unitActionable(body, base);
// Suites need no Cardkit verdict: the card itself already passed Cardkit in its own PR.
const SUITE_FEASIBLE = { ok: true, code: null, reason: null, cards: [] };

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
    const act = actionableOf(fresh.title, fresh.body, base);
    const reasons = [...p.reasons, ...(act.ok ? [] : ['not-actionable-' + act.missing.join('-')])];
    return { ok: reasons.length === 0, reasons, actor: raw.approval?.actor || null };
  } catch { return { ok: false, reasons: ['revalidation-unavailable'] }; }
}

const REPORT_ISSUE = 81;
const INTERRUPT_MARKER = '<!-- arcana-interrupted-attempt-v1 -->';

function suiteInstructions(n) {
  return [
    `Follow issue #${n}; it is your complete task: write one independent certification suite.`,
    'Write it strictly from the Oracle record in the issue. Do NOT open, fetch, diff or read the card pull request or its branch; the suite must be independent of that implementation.',
    'Add exactly two files and nothing else: the test class and the suite registration named in the issue, in the style of certification/CopperGnomesIndependentCertificationTest.kt and certification/suites/copper-gnomes.json.',
    'Cover every ability, cost, restriction and a negative case. No placeholders, no weakened or skipped assertions.',
    `Open one draft pull request against ${A.SUITE_BASE} with "Unit: #${n}" and "Fixes #${n}" in its body. If the card cannot be tested exactly, comment on issue #${n} and do NOT open a pull request.`,
    'Never merge, and never change workflows, scripts, production, release or signing configuration.',
  ].join(' ');
}

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

async function assignCopilot({ token, owner, repo, issue_number, base, suite = false, fetchImpl = fetch }) {
  if (!token) return 'error';
  const res = await fetchImpl(`https://api.github.com/repos/${owner}/${repo}/issues/${issue_number}/assignees`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ assignees: ['copilot-swe-agent[bot]'], agent_assignment: {
      target_repo: owner + '/' + repo, base_branch: suite ? A.SUITE_BASE : base,
      custom_instructions: suite ? suiteInstructions(issue_number) : unitInstructions(issue_number, base),
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
async function resolveInterrupted({ github, owner, repo, unit, runId, auto = false }) {
  const n = unit.raw.number;
  const events = await github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number: n, per_page: 100 });
  const labeled = events.filter(e => e.event === 'labeled' && e.label?.name === A.RESOLVE_LABEL).pop();
  const actor = auto ? 'dispatcher-timeout' : String(labeled?.actor?.login || 'unknown').replace(/[^A-Za-z0-9_.\-[\]]/g, '');
  const closed = new Set(unit.raw.ledger.filter(r => r.op === 'attempt-result' || r.op === 'resolution').map(r => r.run));
  const open = [...new Set(unit.raw.ledger.filter(r => r.op === 'authorizing' && !closed.has(r.run)).map(r => r.run))];
  const observed = unit.raw.assignedToCopilot ? 'assigned' : 'not-assigned';
  for (const run of open) {
    await github.rest.issues.createComment({ owner, repo, issue_number: n, body: A.formatLedger({ op: 'resolution',
      issue: n, run, result: observed, reason: `owner-label by ${actor} in run ${runId}`, at: new Date().toISOString() }) });
  }
  if (unit.raw.labels.includes(A.RESOLVE_LABEL)) await github.rest.issues.removeLabel({ owner, repo, issue_number: n, name: A.RESOLVE_LABEL });
  await github.rest.issues.createComment({ owner, repo, issue_number: REPORT_ISSUE, body: [INTERRUPT_MARKER,
    `resolved issue:${n} runs:${open.join(',')}`,
    `**Interrupted attempt on #${n} resolved** (label applied by ${actor}). Observed: Copilot ${observed === 'assigned' ? 'IS' : 'is NOT'} assigned.`,
    'Original ledger records are preserved. The unit\'s assignment remains used; no retry was made.'].join('\n\n') });
  return { issue: n, runs: open, observed, actor };
}

// A card unit whose Copilot session finished, that has no PR and on which Copilot commented, is a card
// Copilot reported it cannot implement exactly (unitInstructions tells it to comment instead of opening a
// PR). Label it arcana:engine-gap so it leaves the suite path and enters the GPT engine lane, which the #78
// stall report times. Never spends; a ledger observation records why.
const ENGINE_GAP_LABEL = 'arcana:engine-gap';
const ENGINE_GAP_GRACE_MS = 30 * 60 * 1000;
function engineGapCandidates(units, now = Date.now()) {
  return units.filter(u => String(u.raw.title).startsWith('[Arcana Queue]') && u.derived.state === 'review' && !u.prs.length &&
    !u.raw.labels.includes(ENGINE_GAP_LABEL) && (u.raw.commenters || []).some(c => isCopilot(c)) && (() => {
      const at = s => Math.max(-Infinity, ...(u.raw.sessions || []).filter(x => x.event === s).map(x => x.at));
      const fin = at('finished');
      return fin > at('started') && now - fin > ENGINE_GAP_GRACE_MS;
    })());
}
async function labelEngineGaps({ github, owner, repo, units, runId, now }) {
  const out = [];
  for (const u of engineGapCandidates(units, now).slice(0, 5)) {
    try {
      await github.rest.issues.addLabels({ owner, repo, issue_number: u.raw.number, labels: [ENGINE_GAP_LABEL] });
      await github.rest.issues.createComment({ owner, repo, issue_number: u.raw.number, body: A.formatLedger({ op: 'observation',
        issue: u.raw.number, run: runId, reason: 'labeled arcana:engine-gap: Copilot finished, commented, and opened no PR', at: new Date().toISOString() }) });
      out.push(u.raw.number);
    } catch { /* retried on the next run */ }
  }
  return out;
}

// Owner-authorized cards have priority over the ordinary lowest-numbered normal
// candidate. Multiple approvals queue deterministically, one per dispatcher run.
// Feasibility/provenance/billing/capacity are still checked AFTER selection.
// The dispatcher's own verdict on a queued unit, from structured checks (never from reason text):
//   eligible  - assignable now (still subject to CI, capacity and billing admission)
//   invalid   - the issue itself can never be assigned as written (not a unit, incomplete, no cards)
//   claimed   - an open PR is already linked to this unit
//   blocked   - Cardkit says not Copilot-ready yet (may change after engine work)
//   transient - a check was unavailable or stale this run
//   provenance- untrusted content or approval problem (owner must look)
// `reason` is the human-readable text published to Arcana Control.
const FEAS_CLASS = { 'no-cards': 'invalid', 'auto-override-prohibited': 'invalid', 'not-ready': 'blocked',
  'override-unauthorized': 'blocked', unavailable: 'transient', stale: 'transient' };
function verdictOf(u, base, feasible, actionable = A.unitActionable) {
  const suite = isSuite(u);
  if (!suite && !u.raw.title.startsWith('[Arcana Queue]')) return { cls: 'invalid', reason: 'not-a-queue-issue' };
  if (u.prs.length) return { cls: 'claimed', reason: 'has-open-pr ' + u.prs.map(p => '#' + p.number).join(',') };
  if (!u.derived.provenance?.ok) return { cls: 'provenance', reason: 'untrusted-origin: ' + (u.derived.provenance?.reasons || []).join(',') };
  const act = suite ? A.suiteActionable(u.raw.body) : actionable(u.raw.body, base);
  if (!act.ok) return { cls: 'invalid', reason: 'not-actionable: ' + (act.missing || []).join(',') };
  const feas = suite ? SUITE_FEASIBLE : feasible(u);
  // An unknown/missing code is treated as blocked: the approval is kept, never discarded.
  if (!feas.ok) return { cls: FEAS_CLASS[feas.code] || 'blocked', reason: 'feasibility: ' + (feas.reason || 'not Copilot-ready') };
  return { cls: 'eligible', reason: null };
}
const skipReason = (u, base, feasible, actionable) => verdictOf(u, base, feasible, actionable).reason;
// Only these withdraw a pending one-shot approval: the approved issue can never be assigned, or the
// work already exists. Blocked, transient and provenance verdicts keep the owner's approval.
const WITHDRAW = new Set(['invalid', 'claimed']);

function selectCandidate(allUnits, mode, base, feasible, actionable = A.unitActionable) {
  const labeled = allUnits.filter(u => u.raw.labels.includes(A.ONE_SHOT_LABEL));
  const verdict = u => u.derived.state === 'queued' ? verdictOf(u, base, feasible, actionable) : { cls: 'other', reason: null };
  const withdraw = u => ({ candidate: null, labeled: [], clear: { unit: u, reason: verdict(u).reason }, error: 'authorized-unit-not-eligible' });
  if (mode === 'authorize-once') {
    if (labeled.length !== 1) return { candidate: null, labeled, error: 'authorize-once-needs-exactly-one-label' };
    // Withdrawal dispatches nothing else in the same run; blocked/transient deny and keep the label.
    return WITHDRAW.has(verdict(labeled[0]).cls) ? withdraw(labeled[0]) : { candidate: labeled[0], labeled, error: null };
  }
  // In normal mode, each owner's authorization is a queued approval, not a demand
  // for exclusive dispatch. Pick at most one per run and leave all other labels intact.
  // Use issue number for deterministic priority, regardless of API enumeration order.
  if (mode === 'normal' && labeled.length) {
    const ordered = [...labeled].sort((a, b) => a.raw.number - b.raw.number);
    // Withdraw one permanently ineligible authorization without spending. Others remain
    // approved for the next run (including if removing this label fails).
    const obsolete = ordered.find(u => WITHDRAW.has(verdict(u).cls));
    if (obsolete) return withdraw(obsolete);
    // A temporarily blocked card must not hold up another authorized, eligible card.
    const eligible = ordered.find(u => verdict(u).cls === 'eligible');
    if (eligible) return { candidate: eligible, labeled, error: null };
    // Preserve fail-closed behavior for unknown/provenance/transient states; only
    // Cardkit-blocked cards fall back to the ordinary normal-mode queue.
    const unverified = ordered.find(u => verdict(u).cls !== 'blocked');
    if (unverified) return { candidate: unverified, labeled, error: null };
  }
  // Suite requests go first: each one unblocks a finished card PR that is waiting for certification.
  const candidate = allUnits.filter(u => u.derived.state === 'queued' && verdictOf(u, base, feasible, actionable).cls === 'eligible')
    .sort((a, b) => isSuite(b) - isSuite(a) || a.raw.number - b.raw.number)[0];
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
  if (!dry) report.engineGaps = await labelEngineGaps({ github, owner, repo, units: data.units, runId: String(context.runId), now: Math.max(now, Date.now()) });
  const interrupted = data.units.filter(u => u.derived.blocksAll);
  if (interrupted.length) {
    const resolved = [];
    for (const u of interrupted) {
      // Resolved by the owner's label, or automatically once the attempt is an hour old. Resolution only
      // records what is observed; it never retries, so the unit's one assignment stays used either way.
      const since = Math.max(...u.raw.ledger.filter(r => r.op === 'authorizing').map(r => r.at));
      const auto = Number.isFinite(since) && Math.max(now, Date.now()) - since > A.INTERRUPT_AUTO_RESOLVE_MS;
      if (!dry && (u.raw.labels.includes(A.RESOLVE_LABEL) || auto)) {
        resolved.push(await resolveInterrupted({ github, owner, repo, unit: u, runId: String(context.runId), auto: !u.raw.labels.includes(A.RESOLVE_LABEL) }));
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
  // Every run publishes the dispatcher's own reason for each queued unit (null = eligible), so
  // Arcana Control never has to re-derive eligibility. Pure: no extra API calls.
  for (const u of data.units) if (u.derived.state === 'queued') {
    const v = verdictOf(u, base, feasible);
    u.derived.skip = v.reason; u.derived.verdict = v.cls; u.derived.eligible = v.cls === 'eligible';
  }
  const selection = selectCandidate(data.units, ready.dispatch, base, feasible);
  const { candidate, labeled } = selection;
  if (selection.clear) {
    // The owner's approval names a unit no later run could assign. Withdraw it (the existing
    // one-shot label) so it cannot pin the queue or block the next approval. Never spends.
    const c = selection.clear;
    report.clearedAuthorization = { issue: c.unit.raw.number, reason: c.reason };
    try { await github.rest.issues.removeLabel({ owner, repo, issue_number: c.unit.raw.number, name: A.ONE_SHOT_LABEL }); }
    catch { return done('authorization-clear-failed', { issue: c.unit.raw.number, reason: c.reason }); }
    // Durable notice on the issue itself. A ledger observation is exempt from provenance and never
    // changes the unit's state, so re-approving later stays a clean one-shot approval.
    try {
      await github.rest.issues.createComment({ owner, repo, issue_number: c.unit.raw.number,
        body: A.formatLedger({ op: 'observation', issue: c.unit.raw.number, run: String(context.runId),
          reason: ('owner approval withdrawn, not assignable: ' + c.reason).replace(/[\r\n@]/g, ' ').slice(0, 300),
          at: new Date().toISOString() }) });
    } catch { core.warning('Approval withdrawn but the notice comment could not be written.'); }
    return done(selection.error, { issue: c.unit.raw.number, reason: 'approval withdrawn: ' + c.reason });
  }
  if (selection.error) return done(selection.error, { labeled: labeled.map(u => u.raw.number) });
  if (!candidate) return done('nothing-eligible');

  const n = candidate.raw.number;
  // authorize-once names its unit explicitly, so the gate denies instead of skipping.
  const suite = isSuite(candidate);
  const feas = suite ? SUITE_FEASIBLE : feasible(candidate);
  const actionable = actionableOf(candidate.raw.title, candidate.raw.body, base);
  report.candidateFeasibility = { issue: n, ok: feas.ok, reason: feas.reason, cards: feas.cards.map(c => ({ name: c.name, readiness: c.readiness, route: c.route, hint: c.hint || null })) };
  // An incomplete unit is denied by admission with its own, more specific reason.
  if (!feas.ok && actionable.ok) {
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
    actionable },
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
    try { result = await assignCopilot({ token: env.COPILOT_DISPATCH_TOKEN, owner, repo, issue_number: n, base, suite, fetchImpl }); }
    catch { result = 'error'; }
  }
  // 4. Close the record. Missing this step is what the interrupted-attempt check catches.
  await github.rest.issues.createComment({ owner, repo, issue_number: n,
    body: A.formatLedger({ op: 'attempt-result', issue: n, run: runId, result, reason, at: at() }) });
  if (result !== 'confirmed') core.warning(`Assignment ${result} for #${n}${reason ? ' (' + reason + ')' : ''}; no retry.`);
  return done('attempted', { issue: n, result, ...(reason ? { reason } : {}) });
}

module.exports = { engineGapCandidates, labelEngineGaps, ENGINE_GAP_LABEL, runDispatch, selectCandidate, verdictOf, skipReason, assignCopilot, unitInstructions, suiteInstructions, reportInterrupted, resolveInterrupted, revalidate, INTERRUPT_MARKER };
