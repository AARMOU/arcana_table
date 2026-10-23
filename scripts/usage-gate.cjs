'use strict';
const { decideBudget } = require('./usage-budget-policy.cjs');

// Parses a premium-request snapshot ({used, allowance, measuredAt}) supplied by
// the COPILOT_USAGE_SNAPSHOT repository variable. Anything unparsable is "unknown".
function parseSnapshot(raw, now = Date.now()) {
  try {
    const s = JSON.parse(raw);
    // This provenance is supplied by a trusted collector, never a worker/PR input.
    // A JSON "verified" flag alone is not evidence of authorization or measurement.
    const suffix = { premium_requests: 'premium_request', ai_credits: 'ai_credit' }[s.metric];
    if (s.account !== 'AARMOU' || s.source !== 'github-billing-api' || !suffix ||
        s.sourceUrl !== `https://api.github.com/users/AARMOU/settings/billing/${suffix}/usage` ||
        s.period !== new Date(now).toISOString().slice(0, 7) ||
        s.allowanceSource !== 'owner-configured-budget') return {};
    return { used: s.used, allowance: s.allowance, measuredAt: Date.parse(s.measuredAt) };
  } catch {
    return {};
  }
}

function budgetFromEnv(raw, now = Date.now()) {
  return decideBudget({ ...parseSnapshot(raw, now), now });
}

// Dispatcher gate. test_issue gets no bypass: it is still one new assignment.
function dispatchLimit(budget, { testIssue = false, requested = 2 } = {}) {
  if (!Number.isInteger(requested) || requested < 0 ||
      !Number.isInteger(budget.maxNewAssignments)) return 0;
  const cap = Math.min(budget.maxNewAssignments, testIssue ? 1 : requested);
  return Math.max(0, cap);
}

// Repair gate: normal/warn allow all 3 retries, slow only the first, paused/unknown none.
function repairAllowed(budget, priorAttempts) {
  if (!Number.isInteger(priorAttempts) || priorAttempts < 0 || priorAttempts >= 3) return false;
  if (budget.level === 'slow') return priorAttempts === 0;
  return budget.maxNewAssignments > 0;
}

function describe(budget) {
  const pct = budget.ratio === undefined ? 'n/a' : (budget.ratio * 100).toFixed(1) + '%';
  return 'Copilot premium-request budget: ' + budget.level + ' (' + pct + ')' +
    (budget.reason ? ' - ' + budget.reason : '');
}

module.exports = { parseSnapshot, budgetFromEnv, dispatchLimit, repairAllowed, describe };
