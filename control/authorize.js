/* Arcana Control — owner-only, one-tap work authorization.
 * Only existing queue issues can be authorized. This client is never an admission authority:
 * the Worker checks the live issue and the existing dispatcher handles billing and feasibility.
 */
(function (root) {
'use strict';
const API = 'https://arcana-review.arcanamtg.workers.dev';
const KEY = 'arcana-review-session-v1';
const LOGIN = API + '/auth/login?return=' + encodeURIComponent('https://aarmou.github.io/arcana_table/control/');
const issueUrl = n => 'https://github.com/AARMOU/arcana_table/issues/' + n;
const safeNumber = n => Number.isSafeInteger(n) && n > 0 && n < 10000000;
// Eligibility is the dispatcher's own published verdict (`eligible`, else its `skip` reason), not a copy of its
// rules. The Worker still re-checks the live issue on tap, and the dispatcher re-checks everything.
function reasonText(skip) {
  const s = String(skip || '');
  if (/^has-open-pr/.test(s)) return 'Already being worked on in an open PR (' + s.replace(/^has-open-pr\s*/, '') + ').';
  if (/^feasibility: .*(unavailable|stale)/.test(s)) return 'Cardkit check for the current base is pending. Try again after the next dispatcher run.';
  if (/^feasibility/.test(s)) return 'Not Copilot-ready per Cardkit (claimed elsewhere, needs engine work, or rules review). Routed away from Copilot.';
  if (/^not-actionable/.test(s)) return 'Old-format or incomplete work unit. Not authorizable; the feeder queues a fresh unit when the card is Copilot-ready.';
  if (/^untrusted-origin/.test(s)) return 'Contains content from an untrusted account. Not authorizable.';
  if (/^not-a-queue-issue/.test(s)) return 'Not a queue work unit.';
  return 'Not eligible: ' + s.slice(0, 160);
}
// A status published before verdicts existed has no `eligible` flag: fail closed until the next run.
const eligibility = unit => unit?.eligible === true ? { ok: true }
  : { ok: false, reason: unit?.skip ? reasonText(unit.skip) : 'Waiting for the dispatcher to check this card.' };
function session() {
  for (const name of ['localStorage', 'sessionStorage']) {
    try { const v = root[name]?.getItem(KEY); if (v) return v; } catch {}
  }
  return null;
}
function el(tag, cls, value) {
  const n = document.createElement(tag);
  if (cls) n.className = cls;
  if (value !== undefined) n.textContent = value;
  return n;
}
async function call(path, sessionToken, method, data) {
  const r = await fetch(API + path, { method, cache: 'no-store',
    headers: { Authorization: 'Bearer ' + sessionToken, ...(method === 'POST' ? {'Content-Type': 'application/json'} : {}) },
    ...(method === 'POST' ? { body: JSON.stringify(data) } : {}) });
  return { status: r.status, body: await r.json().catch(() => null) };
}
const statusText = (status, body) => {
  if (status === 200 && body?.ok) return 'Authorized — waiting for the guarded dispatcher. This does not mean Copilot has started.';
  if (status === 401) return 'Owner sign-in expired. Please sign in again.';
  if (status === 409 && body?.error === 'another-card-authorized') return 'Another card is awaiting authorization pickup (#' + (body.issue || '?') + '). Wait for it to clear.';
  if (status === 409 && body?.error === 'stale-issue') return 'This card changed. Refresh the dashboard, then try again.';
  if (status === 409 && body?.error === 'already-assigned') return 'Copilot already has this card. No new work was authorized.';
  return 'Authorization refused (' + (body?.error || 'HTTP ' + status) + '). No work was launched.';
};
function render(container, units, checkedAt) {
  // How old the dispatcher verdicts are; shown so a stale snapshot never looks freshly verified.
  const ageMin = Math.round((Date.now() - Date.parse(checkedAt || '')) / 60000);
  const age = Number.isFinite(ageMin) && ageMin >= 0 ? ' · checked ' + (ageMin < 1 ? 'just now' : ageMin + ' min ago') : '';
  if (!container) return;
  container.replaceChildren();
  const queued = (Array.isArray(units) ? units : []).filter(u => safeNumber(u?.n) && u.state === 'queued').slice(0,25);
  if (!queued.length) { container.append(el('div', 'empty', 'No queued card units currently waiting. Check back when new work appears.')); return; }
  for (const unit of queued) {
    const row = el('div', 'list-row');
    row.style.cssText = 'flex-wrap:wrap;gap:10px';
    const main = el('div', 'row-main');
    const link = el('a', 'row-title', 'Card work #' + unit.n);
    link.href = issueUrl(unit.n); link.target = '_blank'; link.rel = 'noopener noreferrer';
    const verdict = eligibility(unit);
    const message = el('div', 'row-sub', 'Checking the card request…');
    main.append(link, message);
    const button = el('button', 'btn accent', 'I agree. Do it');
    button.type = 'button';
    button.disabled = true;
    button.setAttribute('aria-label', 'Authorize card work issue ' + unit.n);
    row.append(main, button); container.append(row);

    // Authenticated owner status is authoritative and uncached. The public GitHub proxy can
    // lag after a label write, so it must never re-enable a previously authorized card.
    // If the live owner check fails, fail closed instead of inviting duplicate approvals.
    (async () => {
      const token = session();
      if (token) {
        const live = await call('/v1/authorization?issue=' + unit.n, token, 'GET');
        if (live.status !== 200 || !live.body?.ok) throw Error('Owner status unavailable');
        const info = live.body;
        link.textContent = '#' + unit.n + ' · ' + String(info.title || '').replace(/^\[Arcana Queue\]\s*/, '');
        if (info.authorized) {
          button.disabled = true; button.textContent = 'Authorized';
          message.textContent = 'Authorization recorded — waiting for guarded dispatcher' +
            (unit.skip && /^capacity/.test(unit.skip) ? ' (worker slot occupied).' : '.');
        } else if (info.claimed) {
          button.disabled = true; button.textContent = 'Already assigned';
          message.textContent = 'Copilot already has this work.';
        } else if (!verdict.ok) {
          button.disabled = true; button.textContent = 'Not eligible';
          message.textContent = verdict.reason + age;
        } else {
          button.disabled = false;
          message.textContent = 'Eligible queue unit · owner authorization required' + age;
        }
        return;
      }
      // Signed-out visitors may see public status, but cannot authorize without login.
      const response = await fetch(API + '/v1/public/github?path=' +
        encodeURIComponent('/issues/' + unit.n)).catch(() => null);
      let info = response?.ok ? await response.json().catch(() => null) : null;
      // Edge GitHub quota can fail while the owner's browser can still read the
      // public issue. Always verify current live issue state BEFORE offering any
      // authorization: a cached issue alone must never enable duplicate approval.
      if (!info || (info.state === 'open' && verdict.ok &&
          !(info.labels || []).some(l => l.name === 'arcana:authorize-once'))) {
        const direct = await fetch('https://api.github.com/repos/AARMOU/arcana_table/issues/' +
          unit.n, { cache: 'no-store', headers: { Accept: 'application/vnd.github+json' } }).catch(() => null);
        if (!direct?.ok) throw Error('Fresh issue state unavailable');
        info = await direct.json();
      }
      if (!info || info.number !== unit.n || !String(info.title || '').startsWith('[Arcana Queue]'))
        throw Error('No verified queue issue');
      if (info.state !== 'open') {
        row.remove();
        if (!container.children.length) container.append(el('div', 'empty', 'No queued card units currently waiting.'));
        return;
      }
      link.textContent = '#' + unit.n + ' · ' + info.title.replace(/^\[Arcana Queue\]\s*/, '');
      if ((info.labels || []).some(l => l.name === 'arcana:authorize-once')) {
        button.disabled = true; button.textContent = 'Authorized';
        message.textContent = 'Authorization recorded — waiting for guarded dispatcher.';
      } else if ((info.assignees || []).some(a =>
        ['copilot', 'copilot-swe-agent[bot]'].includes(String(a.login || '').toLowerCase()))) {
        button.disabled = true; button.textContent = 'Already assigned';
        message.textContent = 'Copilot already has this work.';
      } else if (!verdict.ok) {
        button.disabled = true; button.textContent = 'Not eligible';
        message.textContent = verdict.reason + age;
      } else {
        button.disabled = false;
        button.textContent = 'Sign in to authorize';
        message.textContent = 'Eligible queue unit · GitHub sign-in required' + age;
      }
    })().catch(() => {
      button.disabled = true;
      message.textContent = 'Live authorization status unavailable — refresh later. Duplicate approval blocked.';
    });

    button.addEventListener('click', async () => {
      if (button.disabled) return;
      button.disabled = true;
      const token = session();
      if (!token) {
        message.textContent = 'GitHub owner sign-in required. Returning to this dashboard after sign-in.';
        root.location.href = LOGIN;
        return;
      }
      button.textContent = 'Checking…';
      try {
        // This GET is authenticated and uncached; it revalidates the exact selected issue.
        const live = await call('/v1/authorization?issue=' + unit.n, token, 'GET');
        if (live.status === 401) { message.textContent = statusText(live.status, live.body); root.location.href = LOGIN; return; }
        if (live.status !== 200 || !live.body?.ok) throw Error(live.body?.error || 'live issue check failed');
        if (live.body.authorized) { button.textContent = 'Authorized'; message.textContent = statusText(200, {ok:true}); return; }
        if (live.body.claimed) { button.textContent = 'Already assigned'; message.textContent = 'Copilot already has this work.'; return; }
        const result = await call('/v1/authorize', token, 'POST',
          { issue: unit.n, expectedUpdatedAt: live.body.updatedAt });
        message.textContent = statusText(result.status, result.body);
        if (result.status === 200 && result.body?.ok) { button.textContent = 'Authorized'; return; }
        button.textContent = 'I agree. Do it';
      } catch (e) {
        message.textContent = 'Unable to authorize: ' + String(e.message || 'connection failed') + '. No worker was started.';
        button.textContent = 'I agree. Do it';
      } finally { if (button.textContent !== 'Authorized' && button.textContent !== 'Already assigned') button.disabled = false; }
    });
  }
}
const api = { render, statusText, eligibility, reasonText };
root.ArcanaAuthorize = api;
if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof globalThis === 'object' ? globalThis : this);
