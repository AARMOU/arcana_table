'use strict';
// Shadow admission gate for AI-credit liability. NOT yet wired to paid operations.
// A trusted caller must load a complete, bot-authenticated append-only ledger and
// derive expectedOperationIds from *all* potentially paid attempts, including legacy.
const { reconcile } = require('./credit-reservations.cjs');
const { decideBudget } = require('./usage-budget-policy.cjs');
const ID = /^[A-Za-z0-9:_-]{1,120}$/;
const PERIOD = /^\d{4}-\d{2}$/;
const deny = reason => ({ allowed: false, reason });

function evaluateCreditAdmission({ events, expectedOperationIds, ledgerComplete, snapshot,
  candidateCredits, now = Date.now() } = {}) {
  if (ledgerComplete !== true || !Array.isArray(events) || !Array.isArray(expectedOperationIds))
    return deny('unverified-reservation-ledger');
  if (expectedOperationIds.some(id => typeof id !== 'string' || !ID.test(id)) ||
      new Set(expectedOperationIds).size !== expectedOperationIds.length)
    return deny('invalid-operation-inventory');
  if (!snapshot || snapshot.metric !== 'ai_credits' || !PERIOD.test(snapshot.period || '') ||
      !snapshot.account || !snapshot.sku || !Number.isFinite(snapshot.used) || snapshot.used < 0 ||
      !Number.isFinite(snapshot.allowance) || snapshot.allowance <= 0)
    return deny('unverified-ai-credit-snapshot');
  if (!Number.isSafeInteger(candidateCredits) || candidateCredits <= 0)
    return deny('invalid-candidate-estimate');
  // A boolean flag alone is not proof of settlement: require a stable external
  // evidence identifier. The loader must authenticate the event author as well.
  if (events.some(e => (e?.op === 'settle' || e?.op === 'cancel') &&
      (typeof e.evidence !== 'string' || !/^(?:billing|owner):[A-Za-z0-9:_-]{1,120}$/.test(e.evidence))))
    return deny('settlement-evidence-missing');
  const reservations = reconcile(events, { period: snapshot.period });
  if (!reservations.ok) return deny('reservation-' + reservations.reason);
  const reserved = new Set(events.filter(e => e.op === 'reserve').map(e => e.id));
  if (expectedOperationIds.some(id => !reserved.has(id)))
    return deny('unreserved-paid-operation');
  const budget = decideBudget({ used: snapshot.used + reservations.outstanding + candidateCredits,
    allowance: snapshot.allowance, measuredAt: Date.parse(snapshot.measuredAt), now });
  if (!budget.maxNewAssignments) return { ...deny('budget-' + budget.level), budget,
    outstanding: reservations.outstanding };
  return { allowed: true, reason: 'shadow-budget-eligible', budget,
    outstanding: reservations.outstanding, committed: candidateCredits };
}
module.exports = { evaluateCreditAdmission };
