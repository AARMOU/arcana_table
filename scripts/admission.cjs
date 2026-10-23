'use strict';
// Shared admission control for every paid Copilot operation (see
// docs/automation/ADMISSION-CONTROL-DESIGN.md). Pure functions only: callers fetch
// fresh GitHub/billing data inside the serialized `arcana-copilot-control` group.
const { decideBudget } = require('./usage-budget-policy.cjs');

const LEDGER = '<!-- arcana-wu-ledger-v1 -->';
const ONE_SHOT_LABEL = 'arcana:authorize-once';
const HALTED_LABEL = 'wu:halted';
const HARD_MAX_UNITS = 2;
const CEILING = { assign: 1, repair: 2 };
const PICKUP_TIMEOUT_MS = 30 * 60 * 1000;
const DISPATCH_MODES = ['off', 'dry-run', 'authorize-once', 'normal'];
const REPAIR_MODES = ['off', 'observe', 'request'];
const LEDGER_OPS = ['authorizing', 'attempt-result', 'repair-request', 'halt', 'observation'];
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
  return {
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
function deriveUnit(unit, now = Date.now()) {
  const ledger = unit.ledger || [];
  const sessions = unit.sessions || [];
  const results = new Set(ledger.filter(r => r.op === 'attempt-result').map(r => r.run));
  const interrupted = ledger.some(r => r.op === 'authorizing' && !results.has(r.run));
  const attempts = ledger.filter(r => r.op === 'attempt-result');
  // A Copilot assignment with no ledger record predates this system and counts as used.
  const assignments = attempts.length || (unit.assignedToCopilot ? 1 : 0);
  const repairs = ledger.filter(r => r.op === 'repair-request').length;
  const base = { number: unit.number, assignments, repairs, interrupted, blocksAll: false };
  if (interrupted) return { ...base, state: 'halted', reason: 'interrupted', holdsSlot: true, blocksAll: true };
  const started = latest(sessions, s => s.event === 'started');
  const finished = latest(sessions, s => s.event === 'finished');
  const repairAt = latest(ledger, r => r.op === 'repair-request');
  const confirmedAt = latest(attempts, r => r.result === 'confirmed');
  if (started > -Infinity && started > finished) return { ...base, state: 'working', holdsSlot: true };
  if (repairAt > -Infinity && !(finished > repairAt)) {
    const late = now - repairAt > PICKUP_TIMEOUT_MS && !(started > repairAt);
    return late ? { ...base, state: 'halted', reason: 'pickup-timeout', holdsSlot: true }
      : { ...base, state: 'repairing', holdsSlot: true };
  }
  const assigned = unit.assignedToCopilot || confirmedAt > -Infinity;
  if (assigned && !(started > -Infinity && started >= (confirmedAt > -Infinity ? confirmedAt : -Infinity))) {
    // Never picked up. A timed-out pickup keeps its slot until the owner reviews it.
    const late = confirmedAt > -Infinity ? now - confirmedAt > PICKUP_TIMEOUT_MS : true;
    return late ? { ...base, state: 'halted', reason: 'pickup-timeout', holdsSlot: true }
      : { ...base, state: 'pending-pickup', holdsSlot: true };
  }
  if (ledger.some(r => r.op === 'halt') || (unit.labels || []).includes(HALTED_LABEL))
    return { ...base, state: 'halted', reason: 'owner-review', holdsSlot: false };
  if (assigned) return { ...base, state: 'review', holdsSlot: false };
  return { ...base, state: 'queued', holdsSlot: false };
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
  if (op === 'assign') {
    if (ready.dispatch === 'authorize-once' && (labeledCount !== 1 || !unit.labeled))
      return deny('authorize-once requires exactly one labeled issue');
    if (unit.state !== 'queued') return deny('unit state ' + unit.state);
    if (unit.assignments >= CEILING.assign) return deny('assignment ceiling reached');
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

module.exports = { LEDGER, ONE_SHOT_LABEL, HALTED_LABEL, HARD_MAX_UNITS, CEILING, PICKUP_TIMEOUT_MS,
  readiness, formatLedger, parseLedger, deriveUnit, budgetWithReservations, admit, isBot };
