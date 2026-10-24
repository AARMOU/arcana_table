'use strict';
// Shared admission control for every paid Copilot operation (see
// docs/automation/ADMISSION-CONTROL-DESIGN.md). Pure functions only: callers fetch
// fresh GitHub/billing data inside the serialized `arcana-copilot-control` group.
const { decideBudget } = require('./usage-budget-policy.cjs');

const LEDGER = '<!-- arcana-wu-ledger-v1 -->';
const ONE_SHOT_LABEL = 'arcana:authorize-once';
const HALTED_LABEL = 'wu:halted';
const RESOLVE_LABEL = 'wu:resolve-interrupted';
const HARD_MAX_UNITS = 2;
const CEILING = { assign: 1, repair: 2 };
const PICKUP_TIMEOUT_MS = 30 * 60 * 1000;
const DISPATCH_MODES = ['off', 'dry-run', 'authorize-once', 'normal'];
const REPAIR_MODES = ['off', 'observe', 'request'];
const LEDGER_OPS = ['authorizing', 'attempt-result', 'resolution', 'repair-request', 'halt', 'observation'];
const isBot = c => c?.user?.type === 'Bot' && c?.user?.login === 'github-actions[bot]';

function positiveInt(raw, fallback) {
  if (raw === undefined || raw === null || raw === '') return fallback;
  return /^[1-9]\d*$/.test(String(raw)) && Number.isSafeInteger(Number(raw)) ? Number(raw) : null;
}

// Owner-set repository variables. Anything malformed fails closed.
function readiness(env = {}) {
  const errors = [];
  const mode = (name, allowed, fallback) => {
    const raw = env[name];
    if (raw === undefined || raw === null || raw === '') return fallback;
    if (allowed.includes(raw)) return raw;
    errors.push(name + ' invalid');
    return fallback === 'observe' ? 'observe' : 'off';
  };
  const maxUnits = positiveInt(env.ARCANA_MAX_ACTIVE_UNITS, 1);
  if (maxUnits === null || maxUnits > HARD_MAX_UNITS) errors.push('ARCANA_MAX_ACTIVE_UNITS invalid');
  const estSession = positiveInt(env.ARCANA_EST_CREDITS_PER_SESSION, 60);
  const estRepair = positiveInt(env.ARCANA_EST_CREDITS_PER_REPAIR, 30);
  if (estSession === null) errors.push('ARCANA_EST_CREDITS_PER_SESSION invalid');
  if (estRepair === null) errors.push('ARCANA_EST_CREDITS_PER_REPAIR invalid');
  // Who may approve one-shot spending, as GitHub logins. Unset = the repository owner only
  // (the caller supplies it). Anything malformed fails closed.
  let approvers = null;
  const rawApprovers = env.ARCANA_SPEND_APPROVERS;
  if (rawApprovers !== undefined && rawApprovers !== null && rawApprovers !== '') {
    const list = String(rawApprovers).split(',').map(s => s.trim());
    if (list.every(s => /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/.test(s))) approvers = list;
    else errors.push('ARCANA_SPEND_APPROVERS invalid');
  }
  return {
    approvers,
    master: env.ARCANA_AUTOMATION_READY === 'true',
    dispatch: mode('ARCANA_DISPATCH_MODE', DISPATCH_MODES, 'off'),
    repair: mode('ARCANA_REPAIR_MODE', REPAIR_MODES, 'observe'),
    maxUnits: maxUnits !== null && maxUnits <= HARD_MAX_UNITS ? maxUnits : null,
    estSession, estRepair, errors,
  };
}

function formatLedger(fields) {
  if (!LEDGER_OPS.includes(fields.op)) throw Error('invalid-ledger-op');
  const lines = [LEDGER];
  for (const key of ['op', 'issue', 'run', 'result', 'reason', 'sha', 'at']) {
    if (fields[key] === undefined) continue;
    const value = String(fields[key]);
    if (/[\r\n@]/.test(value)) throw Error('invalid-ledger-value');
    lines.push(key + ':' + value);
  }
  return lines.join('\n');
}

// Only github-actions[bot] ledger comments are trusted; people and other tokens can't forge state.
function parseLedger(comments = []) {
  const out = [];
  for (const c of comments) {
    if (!isBot(c) || !c.body?.startsWith(LEDGER)) continue;
    const r = {};
    for (const line of c.body.split('\n').slice(1)) {
      const i = line.indexOf(':');
      if (i > 0) r[line.slice(0, i)] = line.slice(i + 1);
    }
    if (!LEDGER_OPS.includes(r.op)) continue;
    r.at = Date.parse(r.at || c.created_at);
    out.push(r);
  }
  return out.sort((a, b) => a.at - b.at);
}

const latest = (xs, pred) => xs.filter(pred).reduce((m, x) => Math.max(m, x.at), -Infinity);

// unit: {number, labels:[], assignedToCopilot, ledger:[parsed], sessions:[{event:'started'|'finished', at}]}
// Accounting rules (conservative):
// - Every `authorizing` record consumes the unit's single assignment, whatever happened next.
// - An authorization with neither an `attempt-result` nor an owner `resolution` is interrupted.
// - `confirmed`, `error` and `not-confirmed` results may have started paid work, so they are
//   treated as an assignment that is pending pickup (and keep their slot after a timeout).
function deriveUnit(unit, now = Date.now()) {
  const ledger = unit.ledger || [];
  const sessions = unit.sessions || [];
  const authorized = new Set(ledger.filter(r => r.op === 'authorizing').map(r => r.run));
  const closed = new Set(ledger.filter(r => r.op === 'attempt-result' || r.op === 'resolution').map(r => r.run));
  const interrupted = [...authorized].some(run => !closed.has(run));
  // A Copilot assignment with no ledger record predates this system and counts as used.
  const assignments = Math.max(authorized.size, unit.assignedToCopilot ? 1 : 0);
  const repairs = ledger.filter(r => r.op === 'repair-request').length;
  const base = { number: unit.number, assignments, repairs, interrupted, blocksAll: false };
  if (interrupted) return { ...base, state: 'halted', reason: 'interrupted', holdsSlot: true, blocksAll: true };
  const started = latest(sessions, s => s.event === 'started');
  const finished = latest(sessions, s => s.event === 'finished');
  const repairAt = latest(ledger, r => r.op === 'repair-request');
  if (started > -Infinity && started > finished) return { ...base, state: 'working', holdsSlot: true };
  if (repairAt > -Infinity && !(finished > repairAt)) {
    const late = now - repairAt > PICKUP_TIMEOUT_MS && !(started > repairAt);
    return late ? { ...base, state: 'halted', reason: 'pickup-timeout', holdsSlot: true }
      : { ...base, state: 'repairing', holdsSlot: true };
  }
  const maybePaid = ['confirmed', 'error', 'not-confirmed'];
  const paidAt = Math.max(latest(ledger, r => r.op === 'attempt-result' && maybePaid.includes(r.result)),
    latest(ledger, r => r.op === 'resolution' && r.result === 'assigned'));
  const assigned = unit.assignedToCopilot || paidAt > -Infinity;
  if (assigned && !(started > -Infinity && (paidAt === -Infinity || started >= paidAt))) {
    // Never picked up. A timed-out pickup keeps its slot until the owner reviews it.
    const late = paidAt > -Infinity ? now - paidAt > PICKUP_TIMEOUT_MS : true;
    return late ? { ...base, state: 'halted', reason: 'pickup-timeout', holdsSlot: true }
      : { ...base, state: 'pending-pickup', holdsSlot: true };
  }
  if (ledger.some(r => r.op === 'halt') || (unit.labels || []).includes(HALTED_LABEL))
    return { ...base, state: 'halted', reason: 'owner-review', holdsSlot: false };
  if (assigned) return { ...base, state: 'review', holdsSlot: false };
  // Authorization used but no assignment happened (claim changed, or resolved as not assigned).
  if (assignments) return { ...base, state: 'halted', reason: 'assignment-consumed', holdsSlot: false };
  return { ...base, state: 'queued', holdsSlot: false };
}

// A unit must tell the worker exactly what to do before credits are spent on it.
// Required sections (see .github/ISSUE_TEMPLATE/arcana-queue-unit.md):
//   "## Cards" with at least one bullet, or "## Discovery" naming a script path;
//   "## Acceptance" mentioning Oracle behavior and executable tests;
//   a "Base: <branch>" line equal to the dispatcher's integration base.
// Template defaults that must be replaced before a unit is actionable. Kept in sync with
// .github/ISSUE_TEMPLATE/arcana-queue-unit.md (a test asserts the untouched template fails).
const TEMPLATE_DEFAULTS = new Set([
  'card name',
  'exact current oracle behavior (cost, types, every ability, targeting and timing), checked against authoritative card records',
  'executable regression scenarios for positive and negative behavior, registration and deterministic asset export',
  'no placeholders, approximations or weakened tests; report exact commands and run ids',
]);
const PLACEHOLDER = /^(?:tbd|todo|tba|n\/a|none|\.{2,}|card(?: name)?|name|\?+|-+)$/i;
const stripComments = body => String(body || '').replace(/<!--[\s\S]*?(?:-->|$)/g, '');
const bulletText = l => (l.match(/^\s*[-*]\s+(.*?)\s*$/) || [])[1];
const norm = t => t.replace(/[`*_]/g, '').replace(/\s+/g, ' ').trim().toLowerCase();
const isPlaceholder = t => !t || PLACEHOLDER.test(norm(t)) || TEMPLATE_DEFAULTS.has(norm(t)) || /<[^>]*>/.test(t);

function sections(body) {
  const out = {};
  let cur = null;
  for (const line of stripComments(body).split(/\r?\n/)) {
    const h = line.match(/^#{2,3}\s+(.+?)\s*$/);
    if (h) { cur = h[1].toLowerCase(); out[cur] = out[cur] || []; continue; }
    if (cur) out[cur].push(line);
  }
  return out;
}

// A discovery step must be a runnable command naming a script, not just a filename.
const SCRIPT = /[\w./-]+\.(?:py|cjs|mjs|js|sh|kts?)\b/;
const isCommand = l => [...l.matchAll(/`([^`]+)`/g)].some(m => SCRIPT.test(m[1]) && /\s/.test(m[1].trim())) ||
  /^\s*(?:\$\s*)?(?:python3?|node|bash|sh|\.\/)\S*\s+\S*/.test(l) && SCRIPT.test(l);

function unitActionable(body, base) {
  const sec = sections(body);
  const missing = [];
  const cards = (sec.cards || []).map(bulletText).filter(t => t !== undefined && !isPlaceholder(t));
  const discovery = (sec.discovery || []).some(isCommand);
  if (!cards.length && !discovery) missing.push('cards-or-discovery');
  const acc = (sec.acceptance || []).map(l => l.trim()).filter(Boolean);
  const accText = acc.join('\n');
  // Generic criteria alone don't describe the work: at least one line must be unit-specific.
  const specific = acc.some(l => bulletText(l) !== undefined && !isPlaceholder(bulletText(l)));
  if (!/oracle/i.test(accText) || !/\b(tests?|regressions?|scenarios?)\b/i.test(accText) || !specific) missing.push('acceptance');
  const line = stripComments(body).match(/^Base:\s*`?([^`\s]+)`?\s*$/m);
  if (!line) missing.push('base-missing');
  else if (!base || line[1] !== base) missing.push('base-mismatch');
  return { ok: missing.length === 0, missing };
}

// Who may direct paid work. author_association is computed by GitHub from live repository
// permissions and cannot be set by issue text. The bots are the dispatcher's own ledger writer
// and Copilot itself.
const TRUSTED_ASSOCIATIONS = ['OWNER', 'MEMBER', 'COLLABORATOR'];
const TRUSTED_BOTS = ['github-actions[bot]', 'copilot', 'copilot-swe-agent[bot]'];
const trustedCommenter = c => TRUSTED_ASSOCIATIONS.includes(c?.association) ||
  (c?.type === 'Bot' && TRUSTED_BOTS.includes(String(c?.login || '').toLowerCase()));

// raw: { authorAssociation,
//        commenters: [{association, login, type, ledger, writtenAt}],
//        approval: { labeledAt, actor, contentAt, renamedAfter } }  (approval only for one-shot)
// contentAt = when the issue body was last written (GitHub lastEditedAt, else createdAt).
// writtenAt = when a comment was created or last edited. ledger = the dispatcher's own
// append-only record (github-actions[bot] and the ledger marker), the only exempt content.
// The Copilot worker reads the issue body and its comments, so all of it must come from
// trusted people, and a one-shot approval covers only the content that existed when the
// named approver gave it.
function provenance(raw = {}, { needApproval = false, approvers } = {}) {
  const reasons = [];
  if (!TRUSTED_ASSOCIATIONS.includes(raw.authorAssociation)) reasons.push('author-untrusted');
  if (!Array.isArray(raw.commenters)) reasons.push('comments-unavailable');
  else if (raw.commenters.some(c => !trustedCommenter(c))) reasons.push('untrusted-comments');
  if (needApproval) {
    const a = raw.approval;
    const allowed = (Array.isArray(approvers) ? approvers : []).map(s => String(s).toLowerCase());
    if (!a || !Number.isFinite(a.labeledAt)) reasons.push('approval-missing');
    else {
      if (!allowed.length) reasons.push('approvers-unconfigured');
      else if (!a.actor) reasons.push('approval-actor-missing');
      else if (!allowed.includes(String(a.actor).toLowerCase())) reasons.push('approval-actor-unauthorized');
      if (!Number.isFinite(a.contentAt)) reasons.push('edit-history-unavailable');
      else if (a.contentAt >= a.labeledAt) reasons.push('edited-after-approval');
      if (Array.isArray(raw.commenters)) {
        const human = raw.commenters.filter(c => !c.ledger);
        if (human.some(c => !Number.isFinite(c.writtenAt))) reasons.push('comment-history-unavailable');
        else if (human.some(c => c.writtenAt >= a.labeledAt)) reasons.push('comment-after-approval');
      }
    }
    if (a && a.renamedAfter !== false) reasons.push(a.renamedAfter ? 'renamed-after-approval' : 'rename-history-unavailable');
  }
  return { ok: reasons.length === 0, reasons };
}

function budgetWithReservations({ snapshot, active, pendingRepairs, candidate, ready, now = Date.now() }) {
  if (!snapshot || !Number.isFinite(snapshot.used) || ready.estSession === null || ready.estRepair === null)
    return { level: 'unknown', maxNewAssignments: 0, reason: 'no verified usage snapshot' };
  const extra = (active + (candidate === 'assign' ? 1 : 0)) * ready.estSession +
    (pendingRepairs + (candidate === 'repair' ? 1 : 0)) * ready.estRepair;
  return { ...decideBudget({ used: snapshot.used + extra, allowance: snapshot.allowance,
    measuredAt: Date.parse(snapshot.measuredAt), now }), reserved: extra };
}

// op: 'assign' | 'repair'. units: deriveUnit() results for every known unit.
function admit({ op, unit, units = [], labeledCount = 0, ready, snapshot, now = Date.now() }) {
  const deny = reason => ({ allowed: false, reason });
  if (!ready || ready.errors.length) return deny('configuration: ' + (ready?.errors.join(', ') || 'missing'));
  if (op !== 'assign' && op !== 'repair') return deny('unknown operation');
  const dryRun = op === 'assign' && ready.dispatch === 'dry-run';
  if (!dryRun && !ready.master) return deny('readiness: master switch off');
  if (op === 'assign' && !['dry-run', 'authorize-once', 'normal'].includes(ready.dispatch))
    return deny('readiness: dispatch mode ' + ready.dispatch);
  if (op === 'repair' && ready.repair !== 'request') return deny('readiness: repair mode ' + ready.repair);
  if (units.some(u => u.blocksAll)) return deny('interrupted attempt needs owner review');
  if (!unit) return deny('no work unit');
  // Every paid path, every mode: the unit must come from trusted people. Unchecked = denied.
  if (!unit.provenance?.ok) return deny('untrusted-origin: ' + (unit.provenance?.reasons || ['unchecked']).join(', '));
  if (unit.ambiguousLink) return deny('ambiguous PR link: a PR is connected to more than one unit');
  if (op === 'assign') {
    if (ready.dispatch === 'authorize-once' && (labeledCount !== 1 || !unit.labeled))
      return deny('authorize-once requires exactly one labeled issue');
    if (unit.state !== 'queued') return deny('unit state ' + unit.state);
    if (unit.assignments >= CEILING.assign) return deny('assignment ceiling reached');
    if (!unit.actionable?.ok) return deny('unit-not-actionable: ' + (unit.actionable?.missing || ['unchecked']).join(', '));
  } else {
    if (unit.state !== 'review') return deny('unit state ' + unit.state);
    if (unit.repairs >= CEILING.repair) return deny('repair ceiling reached');
  }
  const active = units.filter(u => u.holdsSlot).length;
  if (ready.maxUnits === null || active >= ready.maxUnits) return deny(`capacity: ${active} of ${ready.maxUnits} active`);
  const pendingRepairs = units.filter(u => u.state === 'repairing').length;
  const budget = budgetWithReservations({ snapshot, active, pendingRepairs, candidate: op, ready, now });
  if (!budget.maxNewAssignments) return deny('budget: ' + budget.level);
  if (budget.level === 'slow' && active > 0) return deny('budget: slow allows one operation at a time');
  return dryRun ? { allowed: false, dryRun: true, wouldAllow: true, reason: 'dry-run', budget }
    : { allowed: true, reason: 'admitted', budget };
}

module.exports = { LEDGER, ONE_SHOT_LABEL, HALTED_LABEL, RESOLVE_LABEL, HARD_MAX_UNITS, CEILING, PICKUP_TIMEOUT_MS,
  readiness, formatLedger, parseLedger, deriveUnit, unitActionable, budgetWithReservations, admit, isBot,
  provenance, trustedCommenter, TRUSTED_ASSOCIATIONS };
