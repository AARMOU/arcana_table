'use strict';
const { decideBudget } = require('./usage-budget-policy.cjs');

// Parses a premium-request snapshot ({used, allowance, measuredAt}) supplied by
// the COPILOT_USAGE_SNAPSHOT repository variable. Anything unparsable is "unknown".
function parseSnapshot(raw) {
  try {
    const s = JSON.parse(raw);
    return { used: s.used, allowance: s.allowance, measuredAt: Date.parse(s.measuredAt) };
  } catch {
    return {};
  }
}

function budgetFromEnv(raw, now = Date.now()) {
  return decideBudget({ ...parseSnapshot(raw), now });
}

// Dispatcher gate. test_issue gets no bypass: it is still one new assignment.
function dispatchLimit(budget, { testIssue = false, requested = 2 } = {}) {
  const cap = Math.min(budget.maxNewAssignments, testIssue ? 1 : requested);
  return Math.max(0, cap);
}

// Repair gate: normal/warn allow all 3 retries, slow only the first, paused/unknown none.
function repairAllowed(budget, priorAttempts) {
  if (budget.level === 'slow') return priorAttempts === 0;
  return budget.maxNewAssignments > 0;
}

function describe(budget) {
  const pct = budget.ratio === undefined ? 'n/a' : (budget.ratio * 100).toFixed(1) + '%';
  return 'Copilot premium-request budget: ' + budget.level + ' (' + pct + ')' +
    (budget.reason ? ' - ' + budget.reason : '');
}

module.exports = { parseSnapshot, budgetFromEnv, dispatchLimit, repairAllowed, describe };
