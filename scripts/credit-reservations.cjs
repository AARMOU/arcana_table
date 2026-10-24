'use strict';
// Pure, conservative financial-liability accounting. Not wired into paid admission yet.
// Never infer settlement from worker completion or a newer aggregate billing snapshot.
const OPS = new Set(['reserve', 'settle', 'cancel']);
function reconcile(events, { period } = {}) {
  if (!Array.isArray(events)) return { ok: false, reason: 'events-unavailable', outstanding: null };
  const records = new Map();
  for (const e of events) {
    if (!e || !OPS.has(e.op) || typeof e.id !== 'string' || !/^[A-Za-z0-9:_-]{1,120}$/.test(e.id))
      return { ok: false, reason: 'invalid-event', outstanding: null };
    if (e.op === 'reserve') {
      if (records.has(e.id) || !Number.isSafeInteger(e.credits) || e.credits <= 0 ||
          typeof e.period !== 'string' || !/^\d{4}-\d{2}$/.test(e.period))
        return { ok: false, reason: 'invalid-reservation', outstanding: null };
      records.set(e.id, { period: e.period, credits: e.credits, remaining: e.credits, closed: false });
    } else {
      const r = records.get(e.id);
      if (!r || r.closed || e.authorized !== true || !Number.isSafeInteger(e.credits) ||
          e.credits < 0 || e.credits > r.remaining)
        return { ok: false, reason: 'unverified-settlement', outstanding: null };
      // Explicit authorized settlement, never a presumed billing delta.
      r.remaining -= e.credits;
      if (e.op === 'cancel' && r.remaining !== 0) return { ok: false, reason: 'partial-cancellation', outstanding: null };
      if (r.remaining === 0) r.closed = true;
    }
  }
  if (period !== undefined && (!/^\d{4}-\d{2}$/.test(period) ||
      [...records.values()].some(r => r.period !== period && r.remaining > 0)))
    return { ok: false, reason: 'unsettled-other-period', outstanding: null };
  const outstanding = [...records.values()].reduce((n, r) => n + r.remaining, 0);
  if (!Number.isSafeInteger(outstanding)) return { ok: false, reason: 'overflow', outstanding: null };
  return { ok: true, outstanding, open: [...records.values()].filter(r => r.remaining > 0).length };
}
module.exports = { reconcile };
