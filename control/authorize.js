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
function render(container, units) {
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
    const message = el('div', 'row-sub', 'Checking the card request…');
    main.append(link, message);
    const button = el('button', 'btn accent', 'I agree. Do it');
    button.type = 'button';
    button.disabled = true;
    button.setAttribute('aria-label', 'Authorize card work issue ' + unit.n);
    row.append(main, button); container.append(row);

    // The cached public issue is display-only. A tap always re-fetches the current issue through
    // the authenticated Worker and uses that timestamp; cached values cannot authorize spending.
    fetch(API + '/v1/public/github?path=' + encodeURIComponent('/issues/' + unit.n), { cache: 'no-store' })
      .then(r => r.ok ? r.json() : null).then(info => {
        if (!info || info.number !== unit.n || !String(info.title || '').startsWith('[Arcana Queue]'))
          throw Error('No verified queue issue');
        link.textContent = '#' + unit.n + ' · ' + info.title.replace(/^\[Arcana Queue\]\s*/, '');
        if ((info.labels || []).some(l => l.name === 'arcana:authorize-once')) {
          button.disabled = true; button.textContent = 'Authorized';
          message.textContent = 'Authorized — waiting for safe dispatcher pickup.';
        } else {
          button.disabled = false;
          message.textContent = 'Not authorized · owner-only permission for this specific card';
        }
      }).catch(() => { message.textContent = 'Issue details unavailable — authorization disabled for safety.'; });

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
const api = { render, statusText };
root.ArcanaAuthorize = api;
if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof globalThis === 'object' ? globalThis : this);
