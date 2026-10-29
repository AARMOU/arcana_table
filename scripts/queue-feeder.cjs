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

// A separate trusted owner-authored [Card Request] issue is another recorded request.
// Repeated lines within a single issue count once; public Worker demand never enters here.
function ownerRequestCounts(openIssues = []) {
  const counts = new Map();
  for (const issue of openIssues) for (const card of ownerSelections([issue])) {
    const key = F.normName(card.name);
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  return counts;
}

// Cardkit decklist (ManaBox text) of the owner's selected cards. The default `gaps` run only covers
// the six precon decks, so without this second classification no card outside those decks could
// ever become Copilot-eligible or be routed anywhere else.
function requestedDeck(openIssues = [], demand = []) {
  const names = [...ownerSelections(openIssues).map(r => r.name), ...demand.slice(0, MAX_DEMAND_CLASSIFIED).map(d => d.name)];
  const seen = new Set();
  return names.filter(n => { const k = F.normName(n); if (seen.has(k)) return false; seen.add(k); return true; })
    .map(n => '1 ' + n.replace(/[\r\n]+/g, ' ') + '\n').join('');
}

// ---- Public app demand (owner decision 2026-10-28: "public demand only") --------------------------
// The app's "Request missing cards" button goes to the card-request Worker, which keeps one comment per
// card on #321 (written with the owner's token, so the comments are OWNER-authored) carrying an opt-in
// request count. Any player can press that button, so demand is a VOTE ONLY: it reorders cards Cardkit
// already says Copilot can do. It never creates a [Card Work] issue, never routes to GPT, never jumps
// ahead of the owner's own requests and never bypasses any dispatcher gate.
const DEMAND_ISSUE = 321;
const DEMAND_MARKER = '<!-- arcana-card-request-v1 -->';
const MAX_DEMAND_CLASSIFIED = 50;

function publicDemand(comments = []) {
  const votes = new Map();
  for (const c of comments) {
    // Only the Worker's own comments: OWNER-authored, marker first. A stranger's lookalike is ignored.
    if (c?.author_association !== 'OWNER' || c?.user?.type !== 'User' || String(c?.user?.login || '').toLowerCase() !== OWNER) continue;
    const body = String(c.body || '');
    if (!body.startsWith(DEMAND_MARKER + '\n')) continue;
    const name = (body.match(/^\*\*Requested card:\*\* (.+?)\s*$/m) || [])[1];
    const count = Number((body.match(/^\*\*Opt-in requests:\*\* (\d{1,6})\s*$/m) || [])[1]);
    if (!name || name.length > 141 || /[\r\n<>`@]/.test(name) || !Number.isSafeInteger(count) || count < 1) continue;
    const k = F.normName(name);
    const prev = votes.get(k);
    if (!prev || count > prev.votes) votes.set(k, { name, votes: count });
  }
  return [...votes.values()].sort((a, b) => b.votes - a.votes || a.name.localeCompare(b.name));
}

const verifiedGaps = g => !!(g && g.dispatch && g.dispatch.claimsVerified === true && g.dispatch.dispatchable === true);
const stringList = v => Array.isArray(v) ? v.filter(n => typeof n === 'string') : [];

function classificationCounts(...runs) {
  const cards = new Map();
  const counts = { supportedCopilotUnclaimed: 0, needsReview: 0, engineBlocked: 0,
    implementedAndTested: 0, claimed: 0, unclassified: 0 };
  for (const g of runs.filter(Boolean)) {
    if (!verifiedGaps(g) || !Array.isArray(g.cards) || !g.dispatch.claimed ||
        typeof g.dispatch.claimed !== 'object' || Array.isArray(g.dispatch.claimed)) return null;
    const claimed = new Map(Object.entries(g.dispatch.claimed).map(([name, by]) => [F.normName(name), by]));
    for (const c of g.cards) {
      if (!c || typeof c.name !== 'string' || !F.normName(c.name)) return null;
      const key = F.normName(c.name);
      const claimedBy = claimed.get(key);
      const category = c.readiness === 'IMPLEMENTED_AND_TESTED' ? 'implementedAndTested' :
        claimedBy ? 'claimed' :
        c.readiness === 'ENGINE_BLOCKED' || c.route === 'claude' || c.reviewHint === 'possible-engine-gap' ? 'engineBlocked' :
        c.readiness === 'NEEDS_REVIEW' ? 'needsReview' :
        c.readiness === 'SUPPORTED' && c.route === 'copilot' ? 'supportedCopilotUnclaimed' : 'unclassified';
      const previous = cards.get(key);
      cards.set(key, previous === undefined || previous === category ? category :
        previous === 'claimed' || category === 'claimed' ? 'claimed' : 'unclassified');
    }
  }
  for (const category of cards.values()) counts[category]++;
  return { distinctNames: cards.size, counts };
}

// Per-card Cardkit verdicts from one or more verified `gaps` runs: normName -> compact verdict.
function verdictMap(...runs) {
  const out = new Map();
  for (const g of runs) {
    if (!verifiedGaps(g)) continue;
    const claimed = g.dispatch.claimed && typeof g.dispatch.claimed === 'object' ? g.dispatch.claimed : {};
    for (const c of Array.isArray(g.cards) ? g.cards : []) {
      if (!c || typeof c.name !== 'string' || typeof c.readiness !== 'string') continue;
      out.set(F.normName(c.name), { name: c.name, readiness: c.readiness,
        route: typeof c.route === 'string' ? c.route : null,
        hint: typeof c.reviewHint === 'string' ? c.reviewHint : null,
        claimedBy: typeof claimed[c.name] === 'string' ? claimed[c.name].slice(0, 120) : null,
        reasons: stringList(c.reasons).slice(0, 2).map(r => r.slice(0, 200)) });
    }
  }
  return out;
}

// What happens to one owner-requested card. Only Cardkit verdicts decide; unknown stays unknown.
const OUTCOME_TEXT = {
  implemented: 'Already in the game (Cardkit: implemented). No work needed.',
  claimed: 'Already being worked on',
  queued: 'Already queued; duplicate Copilot work not permitted.',
  'engine-work': 'Needs engine work before Copilot can implement it; routed to GPT (no Copilot credits).',
  'needs-review': 'Needs rules review before Copilot can implement it; routed to GPT (no Copilot credits).',
  unclassified: 'Cardkit could not classify this card in this pass; do not dispatch.',
};
function outcomeOf(v) {
  if (!v) return 'unclassified';
  if (v.readiness === 'IMPLEMENTED_AND_TESTED') return 'implemented';
  if (v.claimedBy) return 'claimed';
  if (v.readiness === 'ENGINE_BLOCKED' || v.route === 'claude' || v.hint === 'possible-engine-gap') return 'engine-work';
  if (v.readiness === 'NEEDS_REVIEW') return 'needs-review';
  return 'unclassified';
}

// gaps: Cardkit `gaps --claims-from-github` JSON for the six precons. requestedGaps: the same for the
// owner's selected cards (optional). openIssues: open issues (REST shape).
function candidates({ gaps, requestedGaps = null, openIssues = [], demand = [], max = 5 }) {
  if (!verifiedGaps(gaps))
    return { ok: false, reason: 'Cardkit could not verify open-PR claims; no suggestions (fail closed).', names: [], alreadyQueued: [] };
  const units = openIssues.filter(i => trustedIssue(i) && String(i.title || '').startsWith('[Arcana Queue]'));
  const queued = new Set(F.collectNames(units, 1000).map(F.normName));
  const titles = units.map(i => F.normName(i.title));
  const isQueued = n => queued.has(F.normName(n)) || titles.some(t => t.includes(F.normName(n)));
  const requestedVerified = verifiedGaps(requestedGaps);
  const classifications = classificationCounts(gaps, requestedVerified ? requestedGaps : null);
  // Copilot-ready = free SUPPORTED/copilot in either verified run (precons + owner requests).
  const copilot = [...new Set([...stringList(gaps.dispatch.copilot),
    ...(requestedVerified ? stringList(requestedGaps.dispatch.copilot) : [])])];
  const alreadyQueued = copilot.filter(isQueued);
  const available = copilot.filter(n => !alreadyQueued.includes(n));
  const ready = new Map(available.map(n => [F.normName(n), n]));
  const verdicts = verdictMap(gaps, requestedVerified ? requestedGaps : null);
  const selected = ownerSelections(openIssues);
  const ownerCounts = ownerRequestCounts(openIssues);
  const requested = selected.filter(r => ready.has(F.normName(r.name))).map(r => ({ name: ready.get(F.normName(r.name)), issue: r.issue }));
  const blockedRequests = selected.filter(r => !ready.has(F.normName(r.name))).slice(0, 30).map(r => {
    const v = verdicts.get(F.normName(r.name));
    const outcome = isQueued(r.name) ? 'queued' : outcomeOf(v);
    const reason = outcome === 'claimed' ? OUTCOME_TEXT.claimed + ' in ' + v.claimedBy + '.' : OUTCOME_TEXT[outcome];
    return { ...r, outcome, reason, readiness: v?.readiness || null, route: v?.route || null, hint: v?.hint || null,
      cardkitReasons: v?.reasons || [] };
  });
  // Cards that need GPT before Copilot can take them. Only from a verified classification.
  const workRequests = blockedRequests.filter(r => r.outcome === 'engine-work' || r.outcome === 'needs-review');
  // Owner-requested cards that no longer need GPT (now Copilot-ready, queued, claimed or implemented).
  const resolvedRequests = selected.filter(r => ready.has(F.normName(r.name)) ||
    ['implemented', 'claimed', 'queued'].includes(blockedRequests.find(b => b.name === r.name && b.issue === r.issue)?.outcome))
    .map(r => r.name);
  // Cards in open queue issues that Cardkit positively says Copilot cannot take now (claimed,
  // implemented, blocked or needing review). The dispatcher will skip such an issue forever, so it
  // must not hold the single "waiting" slot of the automatic queue (see chooseAutoQueue).
  const notReady = F.collectNames(units, 1000).filter(n => {
    const v = verdicts.get(F.normName(n));
    return v && !ready.has(F.normName(n)) && !alreadyQueued.some(q => F.normName(q) === F.normName(n)) &&
      (v.claimedBy || v.readiness !== 'SUPPORTED' || v.route !== 'copilot');
  });
  // Keep owner-authorized priorities ahead of public demand. Within that owner's
  // ready cards, repeated genuine request issues increase priority; equal counts
  // retain oldest selection order. Public demand is never an owner authorization.
  const ownerOrdered = requested.map((r, i) => ({ ...r, i })).sort((a, b) =>
    (ownerCounts.get(F.normName(b.name)) || 0) - (ownerCounts.get(F.normName(a.name)) || 0) || a.i - b.i);
  const ownerDemand = ownerOrdered.slice(0, 30).map(r => ({
    name: r.name, issue: r.issue, requests: ownerCounts.get(F.normName(r.name)) || 0,
  }));
  // Public demand reorders only what is already Copilot-ready; ties keep Cardkit's order.
  const votesOf = new Map(demand.map(d => [F.normName(d.name), d.votes]));
  const byDemand = available.map((n, i) => ({ n, i, v: votesOf.get(F.normName(n)) || 0 }))
    .sort((a, b) => b.v - a.v || a.i - b.i).map(x => x.n);
  const names = [...new Set([...ownerOrdered.map(r => r.name), ...byDemand])].slice(0, max);
  const isReady = n => ready.has(F.normName(n));
  const publicRequests = demand.slice(0, 30).map(d => ({ name: d.name, votes: d.votes,
    status: isReady(d.name) ? 'copilot-ready' : isQueued(d.name) ? 'queued' : 'not-copilot-ready' }));
  return { ok: true, reason: null, names, requested, blockedRequests, workRequests, resolvedRequests, notReady, publicRequests,
    ownerDemand, requestedClassified: requestedVerified, alreadyQueued, eligibleNames: available, available: copilot.length,
    classificationCounts: classifications };
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
    return { name: u.name, ready: !!body && act.ok, requestedIssue: Number.isSafeInteger(request?.issue) ? request.issue : null,
      missing: act.ok ? [] : act.missing, error: u.error ? String(u.error).slice(0, 200) : null, title,
      newIssueUrl: body && act.ok ? newIssueUrl(title, body) : null, body: body && act.ok ? body : null };
  });
  return { checkedAt: now.toISOString(), runId: runId ? String(runId) : null, base: base || null, baseSha: baseSha || null,
    ok: !!result?.ok, reason: result?.reason || null, available: result?.available ?? null,
    classificationCounts: result?.classificationCounts || null,
    alreadyQueued: result?.alreadyQueued || [], blockedRequests: result?.blockedRequests || [], suggestions,
    workRequests: result?.workRequests || [], resolvedRequests: result?.resolvedRequests || [],
    notReady: result?.notReady || [], requestedClassified: !!result?.requestedClassified,
    publicRequests: result?.publicRequests || [], ownerDemand: result?.ownerDemand || [],
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
  const work = p.cardWork || {};
  const classifications = p.classificationCounts;
  const classificationSummary = classifications
    ? `Cardkit classifications (${classifications.distinctNames} distinct names in verified feeder inputs; not an index-gap or rules-playability count): ` +
      `SUPPORTED/copilot/unclaimed ${classifications.counts.supportedCopilotUnclaimed}, NEEDS_REVIEW ${classifications.counts.needsReview}, ` +
      `ENGINE_BLOCKED ${classifications.counts.engineBlocked}, IMPLEMENTED_AND_TESTED ${classifications.counts.implementedAndTested}, ` +
      `claimed ${classifications.counts.claimed}, unclassified ${classifications.counts.unclassified}. Source ${p.baseSha || 'unknown'}; checked ${p.checkedAt || 'unknown'}.`
    : `Cardkit classification breakdown unavailable (incomplete card or claim evidence). Source ${p.baseSha || 'unknown'}; checked ${p.checkedAt || 'unknown'}.`;
  return [`Queue feeder: ${ready.length} Copilot-ready card(s) suggested of ${p.available} SUPPORTED and unclaimed on ${String(p.baseSha || '').slice(0, 7)}` +
    (p.alreadyQueued.length ? `; already queued: ${p.alreadyQueued.join(', ')}` : '') + '.',
  classificationSummary,
  ...ready.map(s => `${s.name}${s.requestedIssue ? ' (owner selected #' + s.requestedIssue + ')' : ''}: ${s.newIssueUrl ? '[open prefilled queue issue](' + s.newIssueUrl + ')' : 'body in JSON below'}`),
  ...(p.blockedRequests?.length ? ['Owner-selected cards not Cardkit Copilot-ready (NO assignment): ' +
    p.blockedRequests.map(r => r.name + ' (#' + r.issue + ', ' + (r.outcome || 'unclassified') + ')').join(', ').slice(0, 900)] : []),
  ...((p.ownerDemand || []).length ? ['Owner-requested priorities (multiple separate trusted request issues count separately): ' +
    p.ownerDemand.slice(0, 10).map(d => d.name + ' ×' + d.requests).join(', ').slice(0, 400)] : []),
  ...((p.publicRequests || []).length ? ['Public app demand (#321, votes reorder Copilot-ready cards only): ' +
    p.publicRequests.slice(0, 12).map(d => d.name + ' ×' + d.votes + (d.status === 'copilot-ready' ? ' (ready)' : '')).join(', ').slice(0, 600)] : []),
  ...(work.created ? ['Card work for GPT opened: #' + work.created.issue + ' (' + work.created.name + ').'] : []),
  ...((work.closed || []).length ? ['Card work handed back to the Copilot queue: ' + work.closed.map(c => '#' + c.issue + ' (' + c.name + ')').join(', ') + '.'] : []),
  ...((p.autoQueue?.stuck || []).length ? ['Queue issues Copilot cannot take now (dispatcher will skip them; owner review): ' +
    p.autoQueue.stuck.map(x => '#' + x.issue + ' (' + x.cards.join(', ') + ')').join(', ').slice(0, 600)] : []),
  ...((p.autoQueue?.held || []).length ? ['Not re-queued automatically because an earlier queue issue for the card was closed without it landing: ' +
    p.autoQueue.held.map(x => x.name + ' (#' + x.issue + ')').join(', ').slice(0, 600)] : []),
  ...(p.autoQueue?.created ? ['Auto-queued #' + p.autoQueue.issue + ' (' + p.autoQueue.name + '); guarded dispatcher requested.'] :
    ['Auto-queue: ' + (p.autoQueue?.reason || 'not attempted') +
      (p.autoQueue?.pending ? ' (#' + p.autoQueue.issue + ' queued; guarded dispatcher requested)' : '') + '.'])];
}

const isCopilotAssigned = i => (i.assignees || []).some(a => ['copilot', 'copilot-swe-agent[bot]'].includes(String(a.login || '').toLowerCase()));

// Queue at most one new issue per pass, and never when a complete unassigned
// queue issue already awaits the normal dispatcher. No billable operation here.
// closedIssues: closed issues (REST shape); a card whose earlier trusted queue issue was closed while
// the card is still Copilot-eligible did not land, so it is held for the owner instead of being paid
// for again automatically.
function chooseAutoQueue(payload, openIssues = [], closedIssues = []) {
  if (!payload?.ok || !/^[a-f0-9]{40}$/.test(String(payload.baseSha || '')) ||
      !/^[A-Za-z0-9._/-]+$/.test(String(payload.base || ''))) return { reason: 'invalid-cardkit-result' };
  const safeIssues = openIssues.filter(trustedIssue);
  const queued = new Set(F.collectNames(safeIssues, 1000).map(F.normName));
  const notReady = new Set((payload.notReady || []).map(F.normName));
  // An outsider cannot park a fake "ready" issue and indefinitely block the feed.
  const waiting = safeIssues.filter(i => String(i.title || '').startsWith('[Arcana Queue]') && !isCopilotAssigned(i) &&
    A.unitActionable(i.body || '', payload.base).ok);
  // A waiting issue whose card Cardkit says Copilot cannot take is skipped by the dispatcher forever;
  // it must not freeze the automatic queue. Reported, never closed or edited here.
  const stuck = waiting.map(i => ({ issue: i.number, cards: F.unitCards(i.body).filter(n => notReady.has(F.normName(n))) }))
    .filter(x => x.cards.length);
  const pending = waiting.find(i => !stuck.some(x => x.issue === i.number));
  if (pending) return { pendingIssue: pending.number, reason: 'eligible-queue-item-already-waiting', stuck };
  const attempted = new Map();
  for (const i of closedIssues.filter(i => trustedIssue(i) && String(i.title || '').startsWith('[Arcana Queue]')))
    for (const n of F.unitCards(i.body)) if (!attempted.has(F.normName(n))) attempted.set(F.normName(n), i.number);
  const held = [];
  const s = (payload.suggestions || []).find(x => {
    if (!(x.ready && x.body && A.unitActionable(x.body, payload.base).ok && !queued.has(F.normName(x.name)) &&
      !safeIssues.some(i => String(i.title || '') === x.title))) return false;
    if (attempted.has(F.normName(x.name))) { held.push({ name: x.name, issue: attempted.get(F.normName(x.name)) }); return false; }
    return true;
  });
  return s ? { suggestion: s, stuck, held } : { reason: 'no-unclaimed-ready-card', stuck, held };
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
  const closed = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'closed', per_page: 100 });
  const pick = chooseAutoQueue(payload, open, closed);
  const notes = { stuck: pick.stuck || [], held: pick.held || [] };
  if (!pick.suggestion) return { reason: pick.reason, ...notes, ...(pick.pendingIssue ? { pending: true, issue: pick.pendingIssue } : {}) };
  const s = pick.suggestion;
  const body = [A.AUTO_QUEUE_MARKER,
    '<!-- source: trusted-main-queue-feeder; run:' + runId + '; base-sha:' + payload.baseSha + ' -->',
    ...(s.requestedIssue ? ['<!-- owner-selected-card-request: #' + s.requestedIssue + ' -->'] : []),
    s.body.trim(), ''].join('\n');
  const issue = (await github.rest.issues.create({ owner, repo, title: s.title, body })).data;
  if (issue.user?.login !== 'github-actions[bot]' || issue.user?.type !== 'Bot')
    throw Error('auto-queue-issuer-not-github-actions-bot; no dispatch');
  return { created: true, issue: issue.number, name: s.name, url: issue.html_url, ...notes };
}

// ---- Blocked owner requests -> GPT card work -> back to the Copilot queue -------------------------
// A card Copilot cannot implement yet (engine gap or rules review) gets ONE bot-authored
// "[Card Work]" issue for GPT. It is never assigned to Copilot, never labeled for the dispatcher and
// never spends credits. When Cardkit later classifies the card Copilot-ready (or it lands some other
// way), the issue is closed automatically; the owner's [Card Request] stays open, so the normal
// feeder path then queues the card for Copilot under every existing gate.
const CARD_WORK_MARKER = '<!-- arcana-card-work-v1 -->';
const CARD_WORK_PREFIX = '[Card Work] ';
const MAX_OPEN_CARD_WORK = 10;
const MAX_CLOSE_PER_PASS = 3;

const isCardWork = i => !i.pull_request && i.user?.login === 'github-actions[bot]' && i.user?.type === 'Bot' &&
  String(i.title || '').startsWith(CARD_WORK_PREFIX) && String(i.body || '').startsWith(CARD_WORK_MARKER + '\n');
const cardWorkName = i => String(i.title || '').slice(CARD_WORK_PREFIX.length).split(' — ')[0].trim();

function cardWorkTitle(r) {
  return CARD_WORK_PREFIX + r.name + ' — ' + (r.outcome === 'engine-work' ? 'needs engine work' : 'needs rules review');
}

function cardWorkBody(r, { runId, baseSha, base }) {
  const oracleMissing = (r.cardkitReasons || []).some(x => /no cached oracle record/i.test(x));
  return [CARD_WORK_MARKER,
    '<!-- source: trusted-main-queue-feeder; run:' + runId + '; base-sha:' + baseSha + '; owner-request:#' + r.issue + ' -->',
    '## Card', '- ' + r.name, '',
    '## Why Copilot cannot take it yet',
    'Cardkit on `' + base + '` @ `' + baseSha + '`: **' + (r.readiness || 'UNKNOWN') + '**' +
      (r.route ? ', suggested route `' + r.route + '`' : '') + (r.hint ? ', hint `' + r.hint + '`' : '') + '.',
    ...(r.cardkitReasons || []).map(x => '- ' + x), '',
    '## Owner request', 'Selected by the owner in #' + r.issue + '. That request stays open; do not close or edit it.', '',
    '## Task (GPT)',
    oracleMissing
      ? '1. Cardkit has no Oracle record for this card. Refresh the Oracle cache on an isolated branch (`tools/cardkit/cardkit oracle fetch`) and re-classify.'
      : '1. Reproduce the Cardkit verdict on the current integration base (`tools/cardkit/cardkit classify --verbose -- "' + r.name + '"`).',
    r.outcome === 'engine-work'
      ? '2. Identify the exact missing engine primitive(s). Implement them in the engine (never as app-side workarounds), with positive and negative scenario tests, or record a verified blocker (`cardkit blocker`) and explain on #81 why it cannot be done now.'
      : '2. Review each unmatched rules line. Either show existing primitives cover it (with real scenario tests) or identify the missing engine primitive and treat it as engine work.',
    '3. Group the work: other requested cards blocked by the same primitive should be unlocked by the same change.',
    '4. Open a draft PR only. The owner approves before anything is merged into the integration base.', '',
    '## Hand-back to Copilot',
    'Done when Cardkit classifies this card `SUPPORTED` with route `copilot` on the integration base (or the card is implemented). The queue feeder then closes this issue and queues the card for Copilot automatically. Implementing the card itself here is optional, not required.', '',
    '## Rules', 'No Copilot assignment, no paid agents, no merges, releases, signing or deployment from this issue. Coordinate on #81.', ''].join('\n');
}

// Pure decision: which card-work issue to open and which to close in this pass.
function planCardWork(payload, botIssues = []) {
  if (!payload?.ok || !payload.requestedClassified) return { create: null, close: [], reason: 'requested-cards-not-classified' };
  const existing = botIssues.filter(isCardWork);
  const openWork = existing.filter(i => i.state === 'open');
  const resolved = new Set((payload.resolvedRequests || []).map(F.normName));
  const close = openWork.filter(i => resolved.has(F.normName(cardWorkName(i)))).slice(0, MAX_CLOSE_PER_PASS)
    .map(i => ({ issue: i.number, name: cardWorkName(i) }));
  // One issue per card, ever: an existing open OR closed card-work issue means GPT already had it.
  const seen = new Set(existing.map(i => F.normName(cardWorkName(i))));
  const create = openWork.length - close.length >= MAX_OPEN_CARD_WORK ? null :
    (payload.workRequests || []).find(r => !seen.has(F.normName(r.name)) && Number.isSafeInteger(r.issue)) || null;
  return { create, close, reason: create ? null : openWork.length >= MAX_OPEN_CARD_WORK ? 'card-work-limit-reached' : 'no-new-card-work' };
}

async function syncCardWork({ github, owner, repo, payload, runId, enabled }) {
  if (!enabled) return { reason: 'normal-auto-dispatch-not-enabled' };
  if (!/^[0-9]+$/.test(String(runId))) return { reason: 'invalid-run-id' };
  if (!payload?.ok || !/^[a-f0-9]{40}$/.test(String(payload.baseSha || ''))) return { reason: 'cardkit-suggestions-unavailable' };
  const botIssues = await github.paginate(github.rest.issues.listForRepo,
    { owner, repo, state: 'all', creator: 'github-actions[bot]', per_page: 100 });
  const plan = planCardWork(payload, botIssues);
  const out = { reason: plan.reason, closed: [] };
  for (const c of plan.close) {
    await github.rest.issues.createComment({ owner, repo, issue_number: c.issue, body: [CARD_WORK_MARKER,
      'Cardkit no longer blocks **' + c.name + '** on `' + payload.baseSha + '` (run ' + runId + '). ' +
      'Handing it back to the automatic Copilot queue; every dispatcher gate (Cardkit, CI, billing reserve, capacity) still applies.'].join('\n') });
    await github.rest.issues.update({ owner, repo, issue_number: c.issue, state: 'closed', state_reason: 'completed' });
    out.closed.push(c);
  }
  if (plan.create) {
    const r = plan.create;
    const issue = (await github.rest.issues.create({ owner, repo, title: cardWorkTitle(r),
      body: cardWorkBody(r, { runId, baseSha: payload.baseSha, base: payload.base }) })).data;
    out.created = { issue: issue.number, name: r.name, outcome: r.outcome };
  }
  return out;
}

// The control site's card data is built from the integration base at Pages deploy time. When the base
// moves, the deployed data no longer matches the feeder snapshot until Pages is redeployed.
function needsSiteRefresh(previous, payload) {
  return !!(payload?.ok && /^[a-f0-9]{40}$/.test(String(payload.baseSha || '')) &&
    previous && /^[a-f0-9]{40}$/.test(String(previous.baseSha || '')) && previous.baseSha !== payload.baseSha);
}

module.exports = { candidates, ownerSelections, ownerRequestCounts, requestedDeck, publicDemand, DEMAND_ISSUE, outcomeOf, classificationCounts, newIssueUrl, queuePayload, queueSummary,
  chooseAutoQueue, createNextAutoQueue, planCardWork, syncCardWork, cardWorkTitle, cardWorkBody, needsSiteRefresh,
  CARD_WORK_MARKER };
