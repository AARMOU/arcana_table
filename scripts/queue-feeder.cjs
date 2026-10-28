'use strict';
// Read-only Copilot queue feeder (issue #81, 2026-10-28).
//
// Suggests [Arcana Queue] units for cards Cardkit routes to Copilot (SUPPORTED) that no open PR claims
// and no open queue issue already names. It never creates issues, labels, assignments or comments
// other than its own status comment: the dispatcher only trusts queue issues written by the owner, so
// each suggestion carries a prefilled "new issue" link the owner reviews and submits.
const A = require('./admission.cjs');
const F = require('./feasibility.cjs');

const REPO_URL = 'https://github.com/AARMOU/arcana_table';
const MAX_URL = 7500;

// gaps: Cardkit `gaps --claims-from-github` JSON. openIssues: open issues (REST shape).
function candidates({ gaps, openIssues = [], max = 5 }) {
  if (!gaps || !gaps.dispatch || gaps.dispatch.claimsVerified !== true || gaps.dispatch.dispatchable !== true)
    return { ok: false, reason: 'Cardkit could not verify open-PR claims; no suggestions (fail closed).', names: [], alreadyQueued: [] };
  const queued = new Set(F.collectNames(openIssues, 1000).map(F.normName));
  const titles = openIssues.filter(i => !i.pull_request).map(i => F.normName(i.title));
  const copilot = Array.isArray(gaps.dispatch.copilot) ? gaps.dispatch.copilot.filter(n => typeof n === 'string') : [];
  const alreadyQueued = copilot.filter(n => queued.has(F.normName(n)) || titles.some(t => t.includes(F.normName(n))));
  const names = copilot.filter(n => !alreadyQueued.includes(n)).slice(0, max);
  return { ok: true, reason: null, names, alreadyQueued, available: copilot.length };
}

function newIssueUrl(title, body) {
  const url = `${REPO_URL}/issues/new?title=${encodeURIComponent(title)}&body=${encodeURIComponent(body)}`;
  return url.length <= MAX_URL ? url : null;
}

// units: [{name, body|null, error|null}] from `cardkit unit NAME` (fails closed per card).
function queuePayload({ result, units = [], base, baseSha, now = new Date(), runId }) {
  const suggestions = units.map(u => {
    const body = typeof u.body === 'string' ? u.body.trim() + '\n' : null;
    const act = body ? A.unitActionable(body, base) : { ok: false, missing: ['no-body'] };
    const title = `[Arcana Queue] ${u.name} — Cardkit SUPPORTED, unclaimed`;
    return { name: u.name, ready: !!body && act.ok, missing: act.ok ? [] : act.missing,
      error: u.error ? String(u.error).slice(0, 200) : null, title,
      newIssueUrl: body && act.ok ? newIssueUrl(title, body) : null, body: body && act.ok ? body : null };
  });
  return { checkedAt: now.toISOString(), runId: runId ? String(runId) : null, base: base || null, baseSha: baseSha || null,
    ok: !!result?.ok, reason: result?.reason || null, available: result?.available ?? null,
    alreadyQueued: result?.alreadyQueued || [], suggestions,
    note: 'Suggestions only. Review, then open the prefilled issue as the owner; the dispatcher still applies every admission, billing, capacity and feasibility gate. Cardkit readiness is routing evidence, not a gameplay certification.' };
}

function queueSummary(p) {
  if (!p.ok) return [`Queue feeder: no suggestions — ${p.reason}`];
  const ready = p.suggestions.filter(s => s.ready);
  return [`Queue feeder: ${ready.length} Copilot-ready card(s) suggested of ${p.available} SUPPORTED and unclaimed on ${String(p.baseSha || '').slice(0, 7)}` +
    (p.alreadyQueued.length ? `; already queued: ${p.alreadyQueued.join(', ')}` : '') + '.',
  ...ready.map(s => `${s.name}: ${s.newIssueUrl ? '[open prefilled queue issue](' + s.newIssueUrl + ')' : 'body in JSON below'}`),
  'Opening an issue does not dispatch anything by itself; normal-mode dispatch then picks the lowest-numbered eligible unit.'];
}

module.exports = { candidates, newIssueUrl, queuePayload, queueSummary };
