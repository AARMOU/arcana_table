'use strict';
const { calibrateReady } = require('./credit-calibration.cjs');
const A = require('./admission.cjs');
const { loadUnits, unitOf } = require('./units.cjs');
const { createCopilotDelivery } = require('./copilot-delivery.cjs');
const MARKER = '<!-- arcana-ci-repair-router-v2 -->';
const OLD_MARKER = '<!-- arcana-ci-repair-router -->';
const OBSERVED = '<!-- arcana-ci-failure-observed-v2 -->';
const WORKFLOWS = new Set(['Arcana Development CI', 'Arcana Cardkit', 'Arcana Card Certification Premerge',
  'Argentum six-precon isolated worker regression',
  'Isolated B131 shared-mechanics compatibility',
  'Six-precon worker integration — full custom card regressions']);
const isOurs = c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot';
const observeOnly = async () => ({ allowed: false, reason: 'observation only' });
const DISPATCHER = 'Arcana Card Worker Dispatcher';
// Independent certification: a red suite run usually means the CARD is wrong, but the suite PR's worker may not
// edit the card, and the card PR's own checks are green, so nothing ever repaired it (2026-10-31: #609 Thunderhawk
// Gunship, #552 Ancient Stone Idol stuck all day). Once the suite PR has had its own repair and the worker answered,
// later failures of that suite go to the CARD PR as a bounded repair, with the card unit's admission and ceiling.
// When the card PR then changes, the failed certification jobs are re-run once for the new card head; the suite
// premerge only triggers on suite PR changes, so without this a fixed card would never be re-certified.
const CERT = 'Arcana Card Certification Premerge';
const CERT_JOB = /^Execute trusted independent card scenarios \(([a-z0-9]+(?:-[a-z0-9]+)*), (\d+), (\d+), /;
const ESCALATED = '<!-- arcana-cert-card-repair-v1 -->';
const CERT_WORKFLOW_FILE = 'arcana-card-certification-premerge.yml';
const CERT_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
const RERUN = '<!-- arcana-cert-rerun-v1 -->';
// The return path (2026-11-01: #611 Thunderhawk, #732 Arch of Orazca, #739 Assault Suit all stuck). When the card
// worker has answered a certification repair and certification still fails for the card's current head, the card
// worker usually said the SUITE is wrong (Crew is a special action, not activatedAbilities[0]; nine Sol Rings can pay
// {5}; control change is projected). Nothing sent that back, so the unit waited forever. The suite worker now gets
// its turn with the card worker's answer, once per (run, card head), charged to the suite unit's own repair ceiling.
// If the suite worker also answers without changing the suite, the disagreement is posted once for the owner/engine
// lane instead of looping.
const CONTEST = '<!-- arcana-cert-suite-contest-v1 -->';
const DEADLOCK = '<!-- arcana-cert-deadlock-v1 -->';
// An idle-time suite for an integrated card (card PR 0: certified on the build branch) has no card PR to repair.
// Once its own worker has had a repair turn and it still fails, it is a likely regression in the shipped card:
// one note on the suite PR and one on #81 for the card lane, then nothing more.
const REGRESSION = '<!-- arcana-cert-build-regression-v1 -->';
const isCopilotUser = u => ['copilot', 'copilot-swe-agent[bot]'].includes(String(u?.login || '').toLowerCase());
function suiteHadItsTurn(comments, repairs) {
  const first = Math.min(...repairs.map(c => Date.parse(c.created_at)).filter(Number.isFinite));
  return Number.isFinite(first) && comments.some(c => isCopilotUser(c.user) && Date.parse(c.created_at) > first);
}

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
  const rerun = authorize === observeOnly ? null : createRerun({ github, ...context.repo, token: env.COPILOT_DISPATCH_TOKEN });
  if (context.eventName === 'schedule' || context.eventName === 'workflow_dispatch' ||
      (context.payload?.workflow_run?.name === DISPATCHER &&
       context.payload.workflow_run.conclusion === 'success'))
    return reconcileFailures({ github, context, authorize, delivery, rerun });
  return routeFailure({ github, context, authorize, delivery, rerun });
}

// Shared admission for repair requests: readiness, work unit, capacity, ceilings, budget.
// A repair worker reads the whole PR conversation: issue comments, review bodies and inline review
// comments. Every author must be trusted; any read failure fails closed. Returns a reason or null.
async function untrustedConversation({ github, owner, repo, pr }) {
  if (!A.TRUSTED_ASSOCIATIONS.includes(pr.author_association) && !A.trustedCommenter({ login: pr.user?.login, type: pr.user?.type }))
    return 'untrusted-origin: pr-author-untrusted';
  const author = x => ({ association: x.author_association, login: x.user?.login, type: x.user?.type });
  let comments, reviews, reviewComments;
  try {
    [comments, reviews, reviewComments] = await Promise.all([
      github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: pr.number, per_page: 100 }),
      github.paginate(github.rest.pulls.listReviews, { owner, repo, pull_number: pr.number, per_page: 100 }),
      github.paginate(github.rest.pulls.listReviewComments, { owner, repo, pull_number: pr.number, per_page: 100 })]);
  } catch { return 'untrusted-origin: pr-conversation-unavailable'; }
  if (![comments, reviews, reviewComments].every(Array.isArray)) return 'untrusted-origin: pr-conversation-unavailable';
  if (comments.some(c => !A.trustedCommenter(author(c)))) return 'untrusted-origin: untrusted-pr-comments';
  // A review with no body adds no text, but its inline comments are checked below.
  if (reviews.some(r => String(r.body || '').trim() && !A.trustedCommenter(author(r)))) return 'untrusted-origin: untrusted-pr-reviews';
  if (reviewComments.some(c => !A.trustedCommenter(author(c)))) return 'untrusted-origin: untrusted-pr-review-comments';
  return null;
}

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
    const untrusted = await untrustedConversation({ github, owner, repo, pr });
    if (untrusted) return { allowed: false, reason: untrusted };
    const { snapshot } = await readBudget(env);
    const cal = await calibrateReady({ github, owner, repo, env, ready, snapshot });
    // Evaluate after the billing read: a fresh measurement must not look like it came from the future.
    const d = A.admit({ op: 'repair', unit: unit.derived, units: data.units.map(u => u.derived), ready: cal.ready, snapshot, now: Math.max(now, Date.now()) });
    if (!d.allowed) return { allowed: false, reason: d.reason };
    return { allowed: true, reason: d.reason, attempt: unit.derived.repairs + 1, unit: n,
      // Re-run right before the ledger write and the Copilot trigger: text added after admission counts.
      recheck: () => untrustedConversation({ github, owner, repo, pr }),
      // Reserve before posting: a failed post over-counts (safe), never under-counts.
      record: () => github.rest.issues.createComment({ owner, repo, issue_number: n,
        body: A.formatLedger({ op: 'repair-request', issue: n, run: String(run.id), sha: run.head_sha, at: new Date().toISOString() }) }) };
  };
}

// Trusted default-branch workflow calls this module. Never loads failed-branch code.
async function routeFailure({ github, context, authorize = observeOnly, openPrs, delivery, rerun = null }) {
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
  if (run.name === CERT && pr.head.sha === run.head_sha && suiteHadItsTurn(comments, repairs))
    return escalateToCard({ github, owner, repo, repoId, run, suitePr: pr, suiteComments: comments, authorize, delivery, rerun });
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
      // Last trust check, immediately before anything is spent. Nothing is recorded or sent on refusal;
      // the next reconcile pass observes the failure instead.
      const late = typeof decision.recheck === 'function' ? await decision.recheck() : 'untrusted-origin: recheck-missing';
      if (late) return {state:'withheld-at-trigger',pr:pr.number,reason:late};
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

// Owner token first (its re-run completion triggers Arcana Suite Auto-merge); else the job token.
function createRerun({ github, owner, repo, token, fetchImpl = fetch }) {
  return async runId => {
    if (token) {
      try {
        const r = await fetchImpl(`https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}/rerun-failed-jobs`, {
          method: 'POST', headers: { Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json' } });
        if (r.ok) return { ok: true, via: 'owner-token' };
      } catch { /* fall through */ }
    }
    try { await github.rest.actions.reRunWorkflowFailedJobs({ owner, repo, run_id: runId }); return { ok: true, via: 'job-token' }; }
    catch (e) { return { ok: false, reason: 'rerun-http-' + (e?.status || 'error') }; }
  };
}

const at = c => Date.parse(c?.created_at || '');
// The card worker's latest answer after the latest certification-repair request for this run, or null.
function cardAnswer(cardComments, escalations) {
  const last = Math.max(...escalations.map(at).filter(Number.isFinite));
  if (!Number.isFinite(last)) return null;
  return cardComments.filter(c => isCopilotUser(c.user) && at(c) > last).sort((a, b) => at(b) - at(a))[0] || null;
}
const quote = text => String(text || '').replace(/^> .*$/gm, '').replace(/@/g, '＠').replace(/<!--[\s\S]*?-->/g, '')
  .replace(/\n{3,}/g, '\n\n').trim().slice(0, 1800).split('\n').map(l => '> ' + l).join('\n');

// Give the suite worker its turn (see CONTEST). Returns a router result, or null to continue with the card path.
async function contestToSuite({ github, owner, repo, run, suitePr, suiteComments, card, answer, authorize, delivery, waitIfContested }) {
  const key = `contest:run-${run.id}:card-${card.head.sha}`;
  const ownerComment = c => String(c.user?.login || '').toLowerCase() === owner.toLowerCase() && A.TRUSTED_ASSOCIATIONS.includes(c.author_association);
  const contested = suiteComments.filter(c => (isOurs(c) || ownerComment(c)) && String(c.body || '').startsWith(CONTEST) && String(c.body).includes(key));
  if (contested.length) {
    const reply = suiteComments.some(c => isCopilotUser(c.user) && at(c) > Math.max(...contested.map(at).filter(Number.isFinite)));
    if (!reply) return { state: 'cert-contested-waiting-suite', pr: suitePr.number, card: card.number };
    if (!waitIfContested) return null; // the card changed and the suite answered: the card's turn again (its ceiling bounds it)
    // Same run means the suite head did not change: both workers answered and certification still fails.
    if (!suiteComments.some(c => isOurs(c) && String(c.body || '').startsWith(DEADLOCK) && String(c.body).includes(key)))
      await github.rest.issues.createComment({ owner, repo, issue_number: suitePr.number, body: [DEADLOCK, key,
        `Certification is deadlocked: the card worker on #${card.number} and the suite worker here both answered, neither changed their side, and run ${run.id} still fails for card head \`${card.head.sha}\`. No further automatic requests. Needs an owner or engine-lane decision (a contested assertion, or an engine gap).`].join('\n\n') });
    return { state: 'cert-deadlock', pr: suitePr.number, card: card.number };
  }
  const sender = delivery || createCopilotDelivery({ owner, repo, token: null });
  const proof = authorize === observeOnly ? { ok: true } : await sender.preflight();
  const decision = proof.ok ? await authorize({ pr: suitePr, run }) : { allowed: false, reason: proof.reason };
  if (!decision.allowed) return { state: 'cert-contest-withheld', pr: suitePr.number, reason: String(decision.reason || '').replace(/[@\r\n]/g, ' ') };
  const body = [CONTEST, key,
    `@copilot Independent certification run ${run.id} still fails for card PR #${card.number} at head \`${card.head.sha}\`. The card's worker answered the repair request and says the problem is in this suite. Their answer:`,
    quote(answer.body),
    'Check every assertion they cite against the exact Oracle text and the engine/test-driver behaviour. Where they are right, fix the suite on this branch so it tests the same rule correctly (replace a wrong assertion or setup with a correct one; never drop coverage of Oracle behaviour, never touch the card). Where the card is wrong, change nothing and say exactly which assertion is correct and why. If a rule cannot be tested because the engine lacks it, keep the assertion and say so: that is engine work, not a suite fix.',
    `Repair request ${decision.attempt} of ${A.CEILING.repair} for unit #${decision.unit}. Report the exact commit and commands. Never merge, release, change main/production or signing.`].join('\n\n');
  const current = (await github.rest.pulls.get({ owner, repo, pull_number: suitePr.number })).data;
  if (current.head.sha !== run.head_sha || current.state !== 'open') return { state: 'stale-or-unmatched', pr: suitePr.number };
  const late = typeof decision.recheck === 'function' ? await decision.recheck() : 'untrusted-origin: recheck-missing';
  if (late) return { state: 'withheld-at-trigger', pr: suitePr.number, reason: late };
  await decision.record();
  const delivered = await sender.post({ issue_number: suitePr.number, body });
  if (!delivered.ok) return { state: 'delivery-uncertain', pr: suitePr.number, reason: delivered.reason };
  return { state: 'repair-requested', pr: suitePr.number, reason: 'card worker contested the suite; routed to suite', card: card.number };
}

async function escalateToCard({ github, owner, repo, repoId, run, suitePr, suiteComments, authorize, delivery, rerun }) {
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, { owner, repo, run_id: run.id, per_page: 100 });
  // The job name comes from the trusted premerge matrix: (suite key, card PR, card unit, title).
  const failed = jobs.filter(j => j.conclusion === 'failure').map(j => ({ j, m: String(j.name || '').match(CERT_JOB) })).filter(x => x.m);
  if (failed.length !== 1) return { state: 'cert-unattributed', pr: suitePr.number };
  const [, key, cardN, unitN] = failed[0].m;
  if (Number(cardN) === 0) {
    const fp = `suite-head:${suitePr.head.sha}`;
    if (suiteComments.some(c => isOurs(c) && String(c.body || '').startsWith(REGRESSION) && String(c.body).includes(fp)))
      return { state: 'cert-build-regression-reported', pr: suitePr.number };
    const job = failed[0].j;
    const note = [REGRESSION, fp,
      `Independent certification for **${key}** (an integrated card, run on the build branch) still fails after this suite's own repair turn: run https://github.com/${owner}/${repo}/actions/runs/${run.id}, job ${job.id}.`,
      'Likely a regression in the shipped card (or an engine gap). Card lane: check the failing assertions against the Oracle text. No further automatic requests for this suite head.'].join('\n\n');
    await github.rest.issues.createComment({ owner, repo, issue_number: suitePr.number, body: note });
    try { await github.rest.issues.createComment({ owner, repo, issue_number: 81, body: note + `\n\nSuite PR: #${suitePr.number}` }); } catch { /* the suite PR note stands */ }
    return { state: 'cert-build-regression', pr: suitePr.number };
  }
  let card;
  try { card = (await github.rest.pulls.get({ owner, repo, pull_number: Number(cardN) })).data; }
  catch { return { state: 'cert-card-unavailable', pr: Number(cardN) }; }
  if (card.number !== Number(cardN) || card.state !== 'open' || !card.draft || card.head?.repo?.id !== repoId ||
      !isCopilotUser(card.user) || !/^(gpt\/|copilot\/)/.test(card.head?.ref || '') || !/^[a-f0-9]{40}$/.test(card.head?.sha || ''))
    return { state: 'cert-card-not-repairable', pr: card.number };
  const cardComments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: card.number, per_page: 100 });
  const ownerComment = c => String(c.user?.login || '').toLowerCase() === owner.toLowerCase() && A.TRUSTED_ASSOCIATIONS.includes(c.author_association);
  const escalations = cardComments.filter(c => (isOurs(c) || ownerComment(c)) && String(c.body || '').startsWith(ESCALATED));
  const heads = escalations.flatMap(c => [...String(c.body).matchAll(new RegExp(`^cert-fingerprint:run-${run.id}:card-([a-f0-9]{40})$`, 'gm'))].map(m => m[1]));
  if (heads.includes(card.head.sha)) {
    // Already sent to the card for this head. If certification was re-run for it and failed again, the next repair
    // is due; otherwise wait for the card worker (or the ceiling, which admission enforces).
    const reran = suiteComments.filter(c => isOurs(c) && String(c.body || '').startsWith(RERUN) &&
      String(c.body).includes(`card-head:${card.head.sha}`) && String(c.body).includes(`run:${run.id}`));
    const answer = cardAnswer(cardComments, escalations.filter(c => String(c.body).includes(`card-${card.head.sha}`)));
    if (answer) {
      const r = await contestToSuite({ github, owner, repo, run, suitePr, suiteComments, card, answer, authorize, delivery, waitIfContested: true });
      if (r) return r;
    }
    return { state: reran.length ? 'cert-failed-again-waiting' : 'cert-already-escalated', pr: card.number };
  }
  if (heads.length) {
    // The card changed since its certification repair request: re-run the failed certification job once per card head.
    const marker = `run:${run.id} card-head:${card.head.sha}`;
    const done = suiteComments.find(c => isOurs(c) && String(c.body || '').startsWith(RERUN) && String(c.body).includes(marker));
    if (done) {
      const attempt = Number((String(done.body).match(/attempt:(\d+)/) || [])[1]);
      // Re-run finished and still failed on the new card head: fall through to the next bounded repair.
      if (!(Number(run.run_attempt) > attempt)) return { state: 'cert-rerun-pending', pr: suitePr.number };
    } else {
      if (!rerun) return { state: 'cert-rerun-withheld', pr: suitePr.number, reason: 'observation only' };
      await github.rest.issues.createComment({ owner, repo, issue_number: suitePr.number, body: [RERUN, marker + ` attempt:${Number(run.run_attempt) || 1}`,
        `Card PR #${card.number} changed after its certification repair request. Re-running the failed independent certification job for card head \`${card.head.sha}\`.`].join('\n\n') });
      const r = await rerun(run.id);
      return { state: r.ok ? 'cert-rerun-requested' : 'cert-rerun-failed', pr: suitePr.number, card: card.number, reason: r.via || r.reason };
    }
  }
  // A card that changed but still fails: if its worker answered, the suite gets its turn first (once per head).
  const answer = heads.length ? cardAnswer(cardComments, escalations) : null;
  if (answer) {
    const r = await contestToSuite({ github, owner, repo, run, suitePr, suiteComments, card, answer, authorize, delivery, waitIfContested: false });
    if (r) return r;
  }
  const sender = delivery || createCopilotDelivery({ owner, repo, token: null });
  const proof = authorize === observeOnly ? { ok: true } : await sender.preflight();
  const decision = proof.ok ? await authorize({ pr: card, run }) : { allowed: false, reason: proof.reason };
  if (!decision.allowed) return { state: 'cert-escalation-withheld', pr: card.number, reason: String(decision.reason || '').replace(/[@\r\n]/g, ' ') };
  if (decision.unit !== Number(unitN)) return { state: 'cert-unit-mismatch', pr: card.number, reason: `job names unit #${unitN}, PR links #${decision.unit}` };
  const job = failed[0].j;
  const step = (job.steps || []).find(s => s.conclusion === 'failure');
  const body = [ESCALATED, `cert-fingerprint:run-${run.id}:card-${card.head.sha}`,
    `@copilot The independent certification suite for this card failed against this PR's head \`${card.head.sha}\`. Repair the CARD on this branch so it implements its exact Oracle text.`,
    `- Suite: \`certification/suites/${key}.json\` on suite PR #${suitePr.number} (its worker already checked the suite and could not fix this without changing the card; see that PR's conversation for the failing assertions).`,
    `- Failing job: ${String(job.name).replace(/@/g, '＠')}${step ? ' / ' + String(step.name).replace(/@/g, '＠') : ''} (job ${job.id}).`,
    `- Run: https://github.com/${owner}/${repo}/actions/runs/${run.id}`,
    'The suite assertions are the specification: do NOT edit, weaken or skip the suite or any existing scenario test. Keep this PR card-only (card, its scenario test, registry, generated assets). If you are certain the suite contradicts the Oracle text, say exactly which assertion and why in a comment and change nothing.',
    `Repair request ${decision.attempt} of ${A.CEILING.repair} for unit #${decision.unit}. Report the exact commit and commands. Never merge, release, change main/production or signing.`].join('\n\n');
  const current = (await github.rest.pulls.get({ owner, repo, pull_number: card.number })).data;
  if (current.head.sha !== card.head.sha || current.state !== 'open') return { state: 'stale-or-unmatched', pr: card.number };
  const late = typeof decision.recheck === 'function' ? await decision.recheck() : 'untrusted-origin: recheck-missing';
  if (late) return { state: 'withheld-at-trigger', pr: card.number, reason: late };
  await decision.record();
  const delivered = await sender.post({ issue_number: card.number, body });
  if (!delivered.ok) return { state: 'delivery-uncertain', pr: card.number, reason: delivered.reason };
  return { state: 'repair-requested', pr: card.number, reason: 'certification failure routed to card', suite: suitePr.number };
}

// Scheduled backfill also revisits capacity/billing-denied failures when repairs are enabled.
// Never more than one paid request per tick, with the same exact-head/admission guards.
// Default remains observation-only for callers that do not supply an authorizer.
async function reconcileFailures({ github, context, authorize = observeOnly, delivery, rerun = null,
  now = Date.now(), windowMs = 24 * 60 * 60 * 1000 }) {
  const { owner, repo } = context.repo;
  const since = new Date(now - windowMs).toISOString();
  const runs = await github.paginate(github.rest.actions.listWorkflowRunsForRepo,
    { owner, repo, created: '>=' + since, per_page: 100 });
  // A red independent-certification run waits on capacity/budget for the CARD repair; while the suite PR head is
  // unchanged it stays the current evidence, so it must not age out after 24 h (2026-11-01: #559's 06:05Z run was
  // about to). Certification premerge runs only, 7 days, one extra listing.
  try {
    const certSince = new Date(now - CERT_WINDOW_MS).toISOString();
    const older = await github.paginate(github.rest.actions.listWorkflowRuns,
      { owner, repo, workflow_id: CERT_WORKFLOW_FILE, created: '>=' + certSince, per_page: 100 });
    const seenIds = new Set(runs.map(r => r.id));
    for (const r of older) if (!seenIds.has(r.id)) runs.push(r);
  } catch { /* the 24 h window still applies */ }
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
    const r = await routeFailure({ github, context: ctx, openPrs, delivery, rerun,
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

module.exports = { escalateToCard, contestToSuite, createRerun, CERT, ESCALATED, RERUN, CONTEST, DEADLOCK, REGRESSION, untrustedConversation,runRouter, routeFailure, reconcileFailures, createAuthorizer, MARKER, OBSERVED, WORKFLOWS};

