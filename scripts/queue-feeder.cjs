'use strict';
// Cardkit-backed Copilot queue feeder (issue #81).
// Produces suggestions; in owner's existing normal auto mode it may create ONE unedited
// Actions-bot issue. Paid assignment is still exclusively the separate guarded dispatcher.
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
    note: 'Cardkit routing is not gameplay certification. Normal auto mode may queue one card; the dispatcher independently enforces provenance, CI, capacity and billing before spending.' };
}

function queueSummary(p) {
  if (!p.ok) return [`Queue feeder: no suggestions — ${p.reason}`];
  const ready = p.suggestions.filter(s => s.ready);
  return [`Queue feeder: ${ready.length} Copilot-ready card(s) suggested of ${p.available} SUPPORTED and unclaimed on ${String(p.baseSha || '').slice(0, 7)}` +
    (p.alreadyQueued.length ? `; already queued: ${p.alreadyQueued.join(', ')}` : '') + '.',
  ...ready.map(s => `${s.name}: ${s.newIssueUrl ? '[open prefilled queue issue](' + s.newIssueUrl + ')' : 'body in JSON below'}`),
  ...(p.autoQueue?.created ? ['Auto-queued #' + p.autoQueue.issue + ' (' + p.autoQueue.name + '); guarded dispatcher requested.'] :
    ['Auto-queue: ' + (p.autoQueue?.reason || 'not attempted') +
      (p.autoQueue?.pending ? ' (#' + p.autoQueue.issue + ' queued; guarded dispatcher requested)' : '') + '.'])];
}


// Queue at most one new issue per pass, and never when a complete unassigned
// queue issue already awaits the normal dispatcher. No billable operation here.
function chooseAutoQueue(payload, openIssues = []) {
  if (!payload?.ok || !/^[a-f0-9]{40}$/.test(String(payload.baseSha || '')) ||
      !/^[A-Za-z0-9._/-]+$/.test(String(payload.base || ''))) return { reason: 'invalid-cardkit-result' };
  const queued = new Set(F.collectNames(openIssues, 1000).map(F.normName));
  // An outsider cannot park a fake "ready" issue and indefinitely block the feed.
  const pending = openIssues.find(i => !i.pull_request && String(i.title || '').startsWith('[Arcana Queue]') &&
    (A.TRUSTED_ASSOCIATIONS.includes(i.author_association) ||
      (i.user?.login === 'github-actions[bot]' && i.user?.type === 'Bot' &&
        String(i.body || '').startsWith(A.AUTO_QUEUE_MARKER + '\n'))) &&
    !(i.assignees || []).some(a => ['copilot', 'copilot-swe-agent[bot]'].includes(String(a.login || '').toLowerCase())) &&
    A.unitActionable(i.body || '', payload.base).ok);
  if (pending) return { pendingIssue: pending.number, reason: 'eligible-queue-item-already-waiting' };
  const s = (payload.suggestions || []).find(x => x.ready && x.body &&
    A.unitActionable(x.body, payload.base).ok && !queued.has(F.normName(x.name)) &&
    !openIssues.some(i => !i.pull_request && String(i.title || '') === x.title));
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
    s.body.trim(), ''].join('\n');
  const issue = (await github.rest.issues.create({ owner, repo, title: s.title, body })).data;
  if (issue.user?.login !== 'github-actions[bot]' || issue.user?.type !== 'Bot')
    throw Error('auto-queue-issuer-not-github-actions-bot; no dispatch');
  return { created: true, issue: issue.number, name: s.name, url: issue.html_url };
}

module.exports = { candidates, newIssueUrl, queuePayload, queueSummary, chooseAutoQueue, createNextAutoQueue };
