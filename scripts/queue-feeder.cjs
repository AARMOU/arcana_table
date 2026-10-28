'use strict';
// Cardkit-backed Copilot queue feeder (issue #81).
// Produces suggestions; in owner's existing normal auto mode it may create ONE unedited
// Actions-bot issue. Paid assignment is still exclusively the separate guarded dispatcher.
const A = require('./admission.cjs');
const F = require('./feasibility.cjs');

const REPO_URL = 'https://github.com/AARMOU/arcana_table';
const MAX_URL = 7500;
// Only owner-authored Card Request issues may influence Copilot scheduling.
// A public request or a request from a contributor must never be interpreted as priority.
const OWNER = 'aarmou';
const trustedIssue = i => !i.pull_request && (A.TRUSTED_ASSOCIATIONS.includes(i.author_association) ||
  (i.user?.login === 'github-actions[bot]' && i.user?.type === 'Bot' &&
    String(i.body || '').startsWith(A.AUTO_QUEUE_MARKER + '\n')));

// Extract card names from an authenticated owner's Card Queue selection.
// Keep the order chosen in the UI, oldest request first; allow at most eight per issue.
function ownerSelections(openIssues = []) {
  const requests = openIssues.filter(i => !i.pull_request &&
    String(i.title || '').startsWith('[Card Request]') &&
    i.author_association === 'OWNER' && String(i.user?.login || '').toLowerCase() === OWNER &&
    i.user?.type === 'User').sort((a, b) =>
      String(a.created_at || '').localeCompare(String(b.created_at || '')) ||
      Number(a.number || 0) - Number(b.number || 0));
  const selected = [], seen = new Set();
  for (const issue of requests) {
    let inCards = false, count = 0;
    for (const line of String(issue.body || '').split(/\r?\n/)) {
      const heading = line.match(/^#{2,3}\s+(.+?)\s*$/);
      if (heading) { inCards = /^cards requested\s*$/i.test(heading[1]); continue; }
      if (!inCards || count >= 8) continue;
      const entry = line.match(/^\s*[-*]\s+(.+?)\s*$/);
      if (!entry) continue;
      const name = entry[1].split(/\s+—\s+(?:Not indexed|Double-face name review|Indexed|Manually entered)\b/i)[0].trim();
      const normalized = F.normName(name);
      if (!normalized || name.length > 141) continue;
      count++;
      if (seen.has(normalized)) continue;
      seen.add(normalized);selected.push({ name, issue: issue.number });
    }
  }
  return selected;
}

// Trust only the owner's Worker-written aggregate comments on tracker #321.
// A count is the current total for a card, NOT a new queue unit for each vote.
// Duplicate comments never double the total: the largest valid snapshot wins.
function demandCounts(comments = []) {
  const votes = new Map();
  for (const c of comments) {
    if (c.author_association !== 'OWNER' || String(c.user?.login || '').toLowerCase() !== OWNER ||
        c.user?.type !== 'User') continue;
    const body = String(c.body || '');
    if (!body.startsWith('<!-- arcana-card-request-v1 -->\n')) continue;
    const name = body.match(/^\*\*Requested card:\*\* ([^\r\n]+)$/m)?.[1];
    const raw = body.match(/^\*\*Opt-in requests:\*\* ([1-9][0-9]*)$/m)?.[1];
    if (!name || name.length > 141 || !raw) continue;
    const count = Number(raw), key = F.normName(name);
    if (!key || !Number.isSafeInteger(count) || count > 1000000) continue;
    votes.set(key, Math.max(votes.get(key) || 0, count));
  }
  return votes;
}

// Separate owner-authored GitHub request issues each contribute one vote per card.
// Repeated names in the same issue or a title alone cannot inflate demand.
function ownerDemandCounts(openIssues = []) {
  const votes = new Map();
  for (const issue of openIssues) for (const card of ownerSelections([issue])) {
    const key = F.normName(card.name);
    votes.set(key, (votes.get(key) || 0) + 1);
  }
  return votes;
}

// gaps: verified Cardkit JSON. requestComments: issue #321's read-only comments.
function candidates({ gaps, openIssues = [], requestComments = [], max = 5 }) {
  if (!gaps || !gaps.dispatch || gaps.dispatch.claimsVerified !== true || gaps.dispatch.dispatchable !== true)
    return { ok: false, reason: 'Cardkit could not verify open-PR claims; no suggestions (fail closed).', names: [], alreadyQueued: [] };
  const units = openIssues.filter(i => trustedIssue(i) && String(i.title || '').startsWith('[Arcana Queue]'));
  const queued = new Set(F.collectNames(units, 1000).map(F.normName));
  const titles = units.map(i => F.normName(i.title));
  const copilot = Array.isArray(gaps.dispatch.copilot) ? gaps.dispatch.copilot.filter(n => typeof n === 'string') : [];
  const alreadyQueued = copilot.filter(n => queued.has(F.normName(n)) || titles.some(t => t.includes(F.normName(n))));
  const available = copilot.filter(n => !alreadyQueued.includes(n));
  const ready = new Map(available.map(n => [F.normName(n), n]));
  const selected = ownerSelections(openIssues);
  const requested = selected.filter(r => ready.has(F.normName(r.name))).map(r => ({ name: ready.get(F.normName(r.name)), issue: r.issue }));
  const blockedRequests = selected.filter(r => !ready.has(F.normName(r.name))).slice(0, 30).map(r =>
    ({ ...r, reason: queued.has(F.normName(r.name)) || titles.some(t => t.includes(F.normName(r.name)))
      ? 'Already queued; duplicate Copilot work not permitted.'
      : 'Cardkit did not list this as unclaimed SUPPORTED / copilot (may be indexed, claimed or need engine work); do not dispatch.' }));
  // Request volume is the PRIMARY priority, across both the opt-in app and
  // owner-authored GitHub issues. Stable tie-breakers: owner selection, request
  // age (ownerSelections order), then the original Cardkit order.
  const optIn = demandCounts(requestComments), owner = ownerDemandCounts(openIssues);
  const ownerOrder = new Map(selected.map((r, i) => [F.normName(r.name), i]));
  const cardkitOrder = new Map(available.map((n, i) => [F.normName(n), i]));
  const score = n => (optIn.get(F.normName(n)) || 0) + (owner.get(F.normName(n)) || 0);
  const ordered = [...available].sort((a, b) => score(b) - score(a) ||
    Number(ownerOrder.has(F.normName(b))) - Number(ownerOrder.has(F.normName(a))) ||
    (ownerOrder.get(F.normName(a)) ?? Infinity) - (ownerOrder.get(F.normName(b)) ?? Infinity) ||
    cardkitOrder.get(F.normName(a)) - cardkitOrder.get(F.normName(b)));
  const names = ordered.slice(0, max);
  const demand = ordered.filter(n => score(n) > 0).slice(0, 100).map(n => ({
    name: n, totalRequests: score(n),
    optInRequests: optIn.get(F.normName(n)) || 0,
    ownerRequests: owner.get(F.normName(n)) || 0,
  }));
  return { ok: true, reason: null, names, requested, blockedRequests, alreadyQueued,
    eligibleNames: available, available: copilot.length, demand };
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
    const request = (result?.requested || []).find(r => F.normName(r.name) === F.normName(u.name));
    const demand = (result?.demand || []).find(r => F.normName(r.name) === F.normName(u.name));
    return { name: u.name, ready: !!body && act.ok, requestCount: demand?.totalRequests || 0,
      requestedIssue: Number.isSafeInteger(request?.issue) ? request.issue : null,
      missing: act.ok ? [] : act.missing, error: u.error ? String(u.error).slice(0, 200) : null, title,
      newIssueUrl: body && act.ok ? newIssueUrl(title, body) : null, body: body && act.ok ? body : null };
  });
  return { checkedAt: now.toISOString(), runId: runId ? String(runId) : null, base: base || null, baseSha: baseSha || null,
    ok: !!result?.ok, reason: result?.reason || null, available: result?.available ?? null,
    alreadyQueued: result?.alreadyQueued || [], blockedRequests: result?.blockedRequests || [], suggestions,
    demand: result?.demand || [],
    // This is the ENTIRE unclaimed Cardkit SUPPORTED/copilot allowlist for
    // owner-facing preflight. Never infer eligibility from the first 3 suggestions.
    // Above this bound, fail closed instead of publishing an incomplete allowlist.
    eligibilityComplete: !!result?.ok && Array.isArray(result?.eligibleNames) && result.eligibleNames.length <= 500,
    eligibleNames: result?.ok && Array.isArray(result?.eligibleNames) && result.eligibleNames.length <= 500
      ? result.eligibleNames : [],
    note: 'Cardkit routing is not gameplay certification. Normal auto mode may queue one card; the dispatcher independently enforces provenance, CI, capacity and billing before spending.' };
}

function queueSummary(p) {
  if (!p.ok) return [`Queue feeder: no suggestions — ${p.reason}`];
  const ready = p.suggestions.filter(s => s.ready);
  return [`Queue feeder: ${ready.length} Copilot-ready card(s) suggested of ${p.available} SUPPORTED and unclaimed on ${String(p.baseSha || '').slice(0, 7)}` +
    (p.alreadyQueued.length ? `; already queued: ${p.alreadyQueued.join(', ')}` : '') + '.',
  ...ready.map(s => `${s.name} (${s.requestCount} recorded request${s.requestCount === 1 ? '' : 's'})${s.requestedIssue ? ' (owner selected #' + s.requestedIssue + ')' : ''}: ${s.newIssueUrl ? '[open prefilled queue issue](' + s.newIssueUrl + ')' : 'body in JSON below'}`),
  ...(p.blockedRequests?.length ? ['Owner-selected cards not Cardkit Copilot-ready (NO assignment): ' +
    p.blockedRequests.map(r => r.name + ' (#' + r.issue + ')').join(', ').slice(0, 900)] : []),
  ...(p.autoQueue?.created ? ['Auto-queued #' + p.autoQueue.issue + ' (' + p.autoQueue.name + '); guarded dispatcher requested.'] :
    ['Auto-queue: ' + (p.autoQueue?.reason || 'not attempted') +
      (p.autoQueue?.pending ? ' (#' + p.autoQueue.issue + ' queued; guarded dispatcher requested)' : '') + '.'])];
}


// Queue at most one new issue per pass, and never when a complete unassigned
// queue issue already awaits the normal dispatcher. No billable operation here.
function chooseAutoQueue(payload, openIssues = []) {
  if (!payload?.ok || !/^[a-f0-9]{40}$/.test(String(payload.baseSha || '')) ||
      !/^[A-Za-z0-9._/-]+$/.test(String(payload.base || ''))) return { reason: 'invalid-cardkit-result' };
  const safeIssues = openIssues.filter(trustedIssue);
  const queued = new Set(F.collectNames(safeIssues, 1000).map(F.normName));
  // An outsider cannot park a fake "ready" issue and indefinitely block the feed.
  const pending = safeIssues.find(i => String(i.title || '').startsWith('[Arcana Queue]') &&
    !(i.assignees || []).some(a => ['copilot', 'copilot-swe-agent[bot]'].includes(String(a.login || '').toLowerCase())) &&
    A.unitActionable(i.body || '', payload.base).ok);
  if (pending) return { pendingIssue: pending.number, reason: 'eligible-queue-item-already-waiting' };
  const s = (payload.suggestions || []).find(x => x.ready && x.body &&
    A.unitActionable(x.body, payload.base).ok && !queued.has(F.normName(x.name)) &&
    !safeIssues.some(i => String(i.title || '') === x.title));
  return s ? { suggestion: s } : { reason: 'no-unclaimed-ready-card' };
}

async function createNextAutoQueue({ github, owner, repo, payload, runId, enabled }) {
  if (!enabled) return { reason: 'normal-auto-dispatch-not-enabled' };
  if (!payload?.ok) return { reason: 'cardkit-suggestions-unavailable' };
  if (!/^[0-9]+$/.test(String(runId))) return { reason: 'invalid-run-id' };
  if (!/^[a-f0-9]{40}$/.test(String(payload.baseSha || '')) ||
      !/^[A-Za-z0-9._/-]+$/.test(String(payload.base || ''))) return { reason: 'invalid-base-provenance' };
  const head = (await github.rest.repos.getBranch({ owner, repo, branch: payload.base })).data.commit.sha;
  if (head !== payload.baseSha) return { reason: 'integration-base-moved' };
  const open = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', per_page: 100 });
  const pick = chooseAutoQueue(payload, open);
  if (!pick.suggestion) return { reason: pick.reason, ...(pick.pendingIssue ? { pending: true, issue: pick.pendingIssue } : {}) };
  const s = pick.suggestion;
  const body = [A.AUTO_QUEUE_MARKER,
    '<!-- source: trusted-main-queue-feeder; run:' + runId + '; base-sha:' + payload.baseSha + ' -->',
    ...(s.requestedIssue ? ['<!-- owner-selected-card-request: #' + s.requestedIssue + ' -->'] : []),
    s.body.trim(), ''].join('\n');
  const issue = (await github.rest.issues.create({ owner, repo, title: s.title, body })).data;
  if (issue.user?.login !== 'github-actions[bot]' || issue.user?.type !== 'Bot')
    throw Error('auto-queue-issuer-not-github-actions-bot; no dispatch');
  return { created: true, issue: issue.number, name: s.name, url: issue.html_url };
}

module.exports = { candidates, ownerSelections, demandCounts, ownerDemandCounts, newIssueUrl, queuePayload, queueSummary, chooseAutoQueue, createNextAutoQueue };
