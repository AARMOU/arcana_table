'use strict';
// Machine-readable control-plane status for Arcana Control (control/index.html).
// Each writer owns ONE github-actions[bot] comment on issue #78, edited in place, so the public
// dashboard can show billing and dispatcher state without credentials. Status comments are
// display-only: admission never reads them, so a stale or forged status can never authorize spend.
// No @-mentions, tokens, response bodies or headers are ever written.
// PRIVACY: the repository is public, so status comments and job summaries never contain credit
// figures, the allowance, utilization percentages or anything derived from them (the reserve is 10%
// of the allowance). Only the threshold level (already public via #78 alerts), freshness, reasons
// and next steps are published. Exact figures stay in GitHub's private billing settings.
const PRIVATE_BILLING_URL = 'https://github.com/settings/billing';
const LEVEL_BANDS = { normal: 'below 50%', warn: '50-75%', slow: '75-90%', paused: '90% or more', unknown: 'unknown' };
const { billingAction } = require('./billing-monitor.cjs');

const STATUS_ISSUE = 78;
const MARKERS = {
  billing: '<!-- arcana-control-status-v1:billing -->',
  dispatcher: '<!-- arcana-control-status-v1:dispatcher -->',
  queue: '<!-- arcana-control-status-v1:queue -->',
  review: '<!-- arcana-control-status-v1:review -->',
};
const MAX_UNITS_LISTED = 40;
const isBot = c => c?.user?.type === 'Bot' && c?.user?.login === 'github-actions[bot]';
const round = x => Number.isFinite(x) ? Math.round(x * 100) / 100 : null;

// Strip anything that could notify people or break the fenced JSON block.
// Queue suggestions carry full issue bodies and prefilled links, so they get a larger limit.
function clean(value, max = 400) {
  if (typeof value === 'string') return value.replace(/@/g, '＠').replace(/```/g, "'''").slice(0, max);
  if (Array.isArray(value)) return value.map(v => clean(v, max));
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, clean(v, max)]));
  return value;
}

function formatStatus(kind, payload, summary = []) {
  if (!MARKERS[kind]) throw Error('invalid-status-kind');
  const max = kind === 'queue' ? 8000 : 400;
  const data = clean({ ...payload, kind, schema: 1 }, max);
  const lines = summary.map(l => '- ' + clean(String(l), max));
  return [MARKERS[kind],
    `**Arcana Control status — ${kind}** (maintained automatically; edited in place; read by https://aarmou.github.io/arcana_table/control/)`,
    ...lines, '', '```json', JSON.stringify(data, null, 1), '```'].join('\n');
}

function parseStatus(body) {
  const text = String(body || '');
  const kind = Object.keys(MARKERS).find(k => text.startsWith(MARKERS[k]));
  if (!kind) return null;
  const m = text.match(/```json\n([\s\S]*?)\n```/);
  if (!m) return null;
  try { const d = JSON.parse(m[1]); return d && d.kind === kind ? d : null; } catch { return null; }
}

async function findStatusComment({ github, owner, repo, kind }) {
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: STATUS_ISSUE, per_page: 100 });
  return [...comments].reverse().find(c => isBot(c) && String(c.body || '').startsWith(MARKERS[kind])) || null;
}

// Never throws: status publication must not change any admission outcome.
async function publishStatus({ github, owner, repo, kind, payload, summary }) {
  try {
    const body = formatStatus(kind, payload, summary);
    const existing = await findStatusComment({ github, owner, repo, kind });
    if (existing) {
      await github.rest.issues.updateComment({ owner, repo, comment_id: existing.id, body });
      return 'updated';
    }
    await github.rest.issues.createComment({ owner, repo, issue_number: STATUS_ISSUE, body });
    return 'created';
  } catch (e) {
    return 'failed:' + (Number.isInteger(e?.status) ? e.status : 'error');
  }
}

async function previousStatus({ github, owner, repo, kind }) {
  try { const c = await findStatusComment({ github, owner, repo, kind }); return c ? parseStatus(c.body) : null; }
  catch { return null; }
}

// result: readBudget() output. previous: last published billing status (display carry-forward only).
function billingPayload(result, { env = {}, previous = null, now = new Date(), source = 'billing-watch' } = {}) {
  const s = result?.snapshot;
  const ok = !!s && !result.error;
  const checkedAt = now.toISOString();
  const legacy = env.ARCANA_PREMIUM_REQUEST_BUDGET !== undefined && env.ARCANA_PREMIUM_REQUEST_BUDGET !== null &&
    env.ARCANA_PREMIUM_REQUEST_BUDGET !== '';
  const notes = [];
  if (legacy) notes.push('ARCANA_PREMIUM_REQUEST_BUDGET is set but retired and ignored; AI credits use ARCANA_AI_CREDIT_BUDGET.');
  const level = result?.status?.level || 'unknown';
  if (!ok) {
    const last = previous?.lastSuccess || (previous?.ok ? previous.current : null) || null;
    return { ok: false, source, checkedAt, level: 'unknown', reason: result?.error || 'billing-unavailable',
      action: billingAction(result?.error || 'billing-unavailable', env),
      blocksDispatch: true, lastSuccess: last ? { measuredAt: last.measuredAt, period: last.period, metric: last.metric, level: last.level, band: last.band } : null,
      lastSuccessAt: last?.measuredAt || null, notes, privateFigures: PRIVATE_BILLING_URL,
      unit: 'ai-credits', excludes: ['premium requests', 'GitHub Actions minutes', 'ChatGPT usage'] };
  }
  // Level and freshness only: no credit counts, allowance or percentages (see PRIVACY above).
  const current = { measuredAt: s.measuredAt, period: s.period, metric: s.metric, level, band: LEVEL_BANDS[level] || 'unknown' };
  return { ok: true, source, checkedAt, level, reason: null,
    action: level === 'paused' ? 'Wait for the monthly reset or an owner budget decision; new paid work is paused at 90%.' : null,
    blocksDispatch: level === 'paused', current, lastSuccess: current, lastSuccessAt: s.measuredAt, notes,
    thresholds: { warn: 0.5, slow: 0.75, paused: 0.9, reserve: '10% of allowance unless ARCANA_AI_CREDIT_RESERVE is set' },
    privateFigures: PRIVATE_BILLING_URL, unit: 'ai-credits', excludes: ['premium requests', 'GitHub Actions minutes', 'ChatGPT usage'] };
}

function billingSummary(p) {
  if (!p.ok) return [`Billing: UNAVAILABLE (${p.reason}). New paid work is blocked.`, p.action ? 'Next step: ' + p.action : '',
    p.lastSuccessAt ? 'Last successful check: ' + p.lastSuccessAt : 'No successful check recorded yet.'].filter(Boolean);
  const c = p.current;
  return [`Billing level: ${p.level} (${c.band} of the allowance, reserve included). Exact figures are private: ${PRIVATE_BILLING_URL}`,
    `Checked: ${c.measuredAt}.`];
}

// Owner-facing explanation for each dispatcher outcome.
function dispatcherAction(state, reason = '') {
  const r = String(reason || '');
  switch (state) {
    case 'configuration-error': return 'Owner: fix the invalid repository variables listed in errors.';
    case 'dispatch-off': return 'Owner decision: ARCANA_DISPATCH_MODE is off/unset. Set it to authorize-once (one labeled issue) or normal when ready.';
    case 'readiness-off': return 'Owner decision: ARCANA_AUTOMATION_READY is not true. Paid dispatch stays off until the owner sets it.';
    case 'interrupted-attempt': return 'Owner: review the interrupted attempt reported in #81, then add the wu:resolve-interrupted label to that issue.';
    case 'interruption-resolved': return 'None: resolution recorded; the next run can dispatch.';
    case 'base-not-configured': return 'Owner: set ARCANA_INTEGRATION_BASE to the integration branch name.';
    case 'ci-not-ready': return 'Wait: the integration base head has no successful "Arcana Development CI" run yet.';
    case 'authorize-once-needs-exactly-one-label': return 'Owner: in authorize-once mode, add the arcana:authorize-once label to exactly one [Arcana Queue] issue.';
    case 'nothing-eligible': return 'Queue empty: no queued, trusted, complete, Copilot-ready [Arcana Queue] issue without an open PR. See skipped units, and the Queue Feeder suggestions on #78 for cards ready to queue.';
    case 'authorized-unit-not-eligible': return 'None: the approved card can never be assigned as written (see reason), so the approval was withdrawn. Normal queue order resumes on the next run.';
    case 'authorization-clear-failed': return 'Owner: an approved card can never be assigned (see reason) and its arcana:authorize-once label could not be removed. Remove it on GitHub.';
    case 'label-removal-failed': return 'Owner: the one-shot label could not be removed; review the attempt in #81.';
    case 'dry-run': return 'None: dry run only. The admission decision is shown; nothing was assigned.';
    case 'attempted': return null;
    case 'denied':
      if (r.startsWith('feasibility: feasibility-unavailable') || r.startsWith('feasibility: feasibility-stale'))
        return 'Wait/inspect: the Cardkit feasibility job did not produce verdicts for this base SHA. Check the dispatcher run\'s feasibility job.';
      if (r.startsWith('feasibility')) return 'This card is not Copilot-ready per Cardkit; do not assign it. Route engine gaps to GPT/Claude. The arcana:feasibility-reviewed exception is available only for separately owner-created queue units, never automatic queue units.';
      if (r.startsWith('budget')) return 'Wait: billing headroom (including reserve and pending liabilities) does not allow new work. See billing.';
      if (r.startsWith('capacity')) return 'Wait for active units to finish. A unit stuck in pickup-timeout or session-stale can be released by the owner with the wu:halted label.';
      if (r.startsWith('untrusted-origin')) return 'Owner: the issue has content from untrusted accounts; edit/remove it or open a clean issue.';
      if (r.startsWith('unit-not-actionable')) return 'Owner: complete the issue sections (Cards/Discovery, Acceptance, Base) per the queue template.';
      if (r.startsWith('interrupted')) return 'Owner: resolve the interrupted attempt first.';
      return 'See reason.';
    default: return null;
  }
}

// ci-not-ready with evidence: name the waiting run so the owner can approve it, or run CI by hand.
function ciAction(state, result, report) {
  if (state !== 'ci-not-ready') return null;
  const base = result?.base || report?.base?.branch || 'the integration base';
  const sha = String(result?.baseSha || report?.base?.sha || '').slice(0, 7);
  const runs = Array.isArray(report?.ciRuns) ? report.ciRuns : [];
  const waiting = runs.find(r => r.conclusion === 'action_required');
  const running = runs.find(r => r.status !== 'completed');
  const failed = runs.find(r => r.status === 'completed' && r.conclusion && r.conclusion !== 'success' && r.conclusion !== 'action_required');
  const manual = `run "Arcana Development CI" manually (Run workflow) on ${base}`;
  if (running) return `Wait: Arcana Development CI run ${running.id} for ${base}@${sha} is still ${running.status}.`;
  if (waiting) return `Owner: Arcana Development CI run ${waiting.id} for ${base}@${sha} is waiting for approval (action_required, started by an automation token). Approve it, or ${manual}.`;
  if (failed) return `Fix CI: Arcana Development CI run ${failed.id} for ${base}@${sha} concluded ${failed.conclusion}.`;
  return `Owner: no Arcana Development CI run exists for ${base}@${sha} (pushes made with GITHUB_TOKEN do not start CI). ${manual[0].toUpperCase() + manual.slice(1)}.`;
}

// Admission decision without usage ratio or credit totals.
function publicDecision(d) {
  if (!d) return null;
  return { issue: d.issue ?? null, allowed: !!d.allowed, dryRun: !!d.dryRun, wouldAllow: !!d.wouldAllow, reason: d.reason || null,
    level: d.budget?.level || null };
}
// Estimated reservations are configuration x unit counts; the fixed reserve, totals, reported credits
// and allowance would reveal private usage, so they are dropped.
function publicLiabilities(l) {
  if (!l) return null;
  return { activeUnits: l.activeUnits, pendingRepairs: l.pendingRepairs, recentlyFinished: l.recentlyFinished, candidate: l.candidate,
    fixedReserve: 'private (10% of allowance)' };
}

function summarizeUnits(units = []) {
  const counts = {};
  for (const u of units) counts[u.state] = (counts[u.state] || 0) + 1;
  return {
    counts,
    running: (counts.working || 0) + (counts.repairing || 0),
    pendingPickup: counts['pending-pickup'] || 0,
    queued: counts.queued || 0,
    completed: counts.review || 0,
    blocked: counts.halted || 0,
    holdingSlots: units.filter(u => u.holdsSlot).map(u => u.number),
    list: units.slice(0, MAX_UNITS_LISTED).map(u => ({ n: u.number, state: u.state, reason: u.reason || null, holdsSlot: !!u.holdsSlot,
      recentlyFinished: !!u.recentlyFinished, skip: u.skip || null, eligible: u.eligible === true })),
  };
}

function dispatcherPayload({ result, report = {}, ready, now = new Date(), runId, event }) {
  const state = result?.state || 'unknown';
  const reason = result?.reason || (result?.errors || []).join(', ') || null;
  const dispatchReady = ['attempted', 'dry-run'].includes(state) ||
    (state === 'nothing-eligible' && !!ready?.master && ['normal', 'authorize-once'].includes(ready?.dispatch));
  return {
    checkedAt: now.toISOString(), runId: runId ? String(runId) : null, event: event || null,
    readiness: ready ? { master: ready.master, dispatch: ready.dispatch, repair: ready.repair, maxUnits: ready.maxUnits,
      errors: ready.errors, billingLagHours: ready.billingLagMs !== undefined ? ready.billingLagMs / 3600000 : null } : null,
    state, reason, action: ciAction(state, result, report) || dispatcherAction(state, reason), issue: result?.issue || null, result: result?.result || null,
    dispatchReady, workers: report.units ? summarizeUnits(report.units) : null,
    decision: publicDecision(report.decision), liabilities: publicLiabilities(report.decision?.budget?.liabilities),
    base: report.base || null, ciRuns: report.ciRuns || null,
    feasibility: report.feasibility || null, candidateFeasibility: report.candidateFeasibility || null,
  };
}

function dispatcherSummary(p) {
  const w = p.workers;
  return [`Dispatcher: ${p.state}${p.reason ? ' — ' + p.reason : ''}.`,
    p.action ? 'Next step: ' + p.action : '',
    w ? `Units: ${w.running} running, ${w.pendingPickup} pending pickup, ${w.queued} queued, ${w.completed} completed (review), ${w.blocked} blocked.` : '',
    `Checked: ${p.checkedAt}.`].filter(Boolean);
}

module.exports = { STATUS_ISSUE, MARKERS, PRIVATE_BILLING_URL, publicDecision, publicLiabilities, formatStatus, parseStatus, publishStatus, previousStatus, billingPayload,
  billingSummary, dispatcherAction, dispatcherPayload, dispatcherSummary, summarizeUnits, clean };
