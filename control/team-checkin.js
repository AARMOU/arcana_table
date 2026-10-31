/* Arcana Control: owner-only all-hands check-in. Reporting only. */
(() => {
  'use strict';
  if (typeof document === 'undefined') return;
  const API = 'https://arcana-review.arcanamtg.workers.dev';
  const KEY = 'arcana-review-session-v1';
  const LOGIN = API + '/auth/login?return=' +
    encodeURIComponent('https://aarmou.github.io/arcana_table/control/');
  const button = document.getElementById('allHandsCheckIn');
  const status = document.getElementById('allHandsStatus');
  const agents = document.getElementById('allHandsAgents');
  const link = document.getElementById('allHandsIssue');
  if (!button || !status || !agents || !link) return;
  const label = button.textContent;
  const roles = [['goosegeta', 'Goosegeta'], ['claude', 'Claude'], ['boneknuckles', 'Boneknuckles / GPT Work']];
  function session() {
    for (const kind of ['localStorage', 'sessionStorage']) {
      try { const v = window[kind]?.getItem(KEY); if (v) return v; } catch {}
    }
    return null;
  }
  async function call(method) {
    const token = session();
    if (!token) return { status: 401, data: null };
    const response = await fetch(API + '/v1/team-checkin', {
      method, cache: 'no-store', headers: {
        Authorization: 'Bearer ' + token,
        ...(method === 'POST' ? { 'Content-Type': 'application/json' } : {})
      }, ...(method === 'POST' ? { body: '{}' } : {})
    });
    return { status: response.status, data: await response.json().catch(() => null) };
  }
  function render(info) {
    agents.replaceChildren();
    for (const [role, name] of roles) {
      const row = document.createElement('div');
      const reply = info?.acknowledgments?.[role];
      row.textContent = name + ': ' + (reply ? 'Reply reported' : 'No reply observed');
      if (reply?.url?.startsWith('https://github.com/AARMOU/arcana_table/issues/81#issuecomment-')) {
        const a = document.createElement('a');
        a.href = reply.url; a.textContent = ' · View'; a.target = '_blank'; a.rel = 'noopener noreferrer';
        row.append(a);
      }
      agents.append(row);
    }
    const url = info?.request?.url;
    if (typeof url === 'string' && /^https:\/\/github\.com\/AARMOU\/arcana_table\/issues\/81#issuecomment-\d+$/.test(url)) {
      link.href = url; link.hidden = false; link.textContent = 'Open check-in request ↗';
    } else { link.hidden = true; link.removeAttribute('href'); }
  }
  async function refresh() {
    if (!session()) { status.textContent = 'Sign in with GitHub owner access to request a check-in.'; return; }
    try {
      const {status: code, data} = await call('GET');
      if (code === 401) { status.textContent = 'Owner session expired. Tap the button to sign in.'; return; }
      if (code !== 200 || !data?.ok) { status.textContent = 'Check-in status unavailable; no new request sent.'; return; }
      render(data);
      status.textContent = data.request ? 'Last owner request recorded in #81. Replies are self-reported, not live agent telemetry.'
        : 'No check-in request in the last 24 hours.';
    } catch { status.textContent = 'Cannot confirm check-in status right now.'; }
  }
  button.addEventListener('click', async () => {
    if (!session()) { window.location.href = LOGIN; return; }
    button.disabled = true;
    status.textContent = 'Sending request to issue #81…';
    try {
      const {status: code, data} = await call('POST');
      if (code === 401) { status.textContent = 'Owner sign-in expired. Tap again to sign in.'; return; }
      if (code === 200 && data?.ok && data?.request) {
        render(data);
        status.textContent = data.duplicate
          ? 'A recent check-in request already exists. No duplicate posted.'
          : 'Request posted to #81. Awaiting each agent’s next existing check-in; no live wake-up or paid session.';
      } else {
        status.textContent = data?.error === 'post-unconfirmed'
          ? 'Post could not be confirmed. Check #81 before retrying.'
          : 'Request NOT confirmed (' + (data?.error || 'HTTP ' + code) + ').';
      }
    } catch { status.textContent = 'Network error: request unconfirmed. Check #81 before retrying.'; }
    finally { button.disabled = false; button.textContent = label; }
  });
  document.getElementById('refresh')?.addEventListener('click', () => { void refresh(); });
  void refresh();
})();
