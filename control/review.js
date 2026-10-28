/* Arcana Control card review (#81 Phase 2). Reads the public #78 "review" status; decisions go to the
 * owner-only review Worker (control/review-worker), which records a GitHub review by the owner pinned to
 * the reviewed commit. This page never holds a GitHub token: only the Worker's encrypted, expiring session.
 * Approval is card sign-off for integration review only, never a merge, build, signing or release. */
(function (root) {
'use strict';
const REVIEW_API = 'https://arcana-review.arcanamtg.workers.dev';
const PWA_RETURN = 'https://aarmou.github.io/arcana_table/control/';
const KEY = 'arcana-review-session-v1';
const LABELS = { 'awaiting-review': 'Awaiting Review', 'checks-running': 'Checks Running', 'checks-failed': 'Checks Failed',
  certified: 'Independently Certified', approved: 'Owner Approved', 'changes-requested': 'Changes Requested', integrated: 'Integrated' };
const TAGS = { 'awaiting-review': 'blue', certified: 'blue', 'checks-running': 'amber', 'checks-failed': 'gray', approved: 'gray', 'changes-requested': 'amber', integrated: 'gray' };
const CERT = { certified: 'Independently certified', running: 'Independent certification running', failed: 'Independent certification FAILED',
  'not-run': 'Not independently certified yet. Passing checks are not rules certification.' };
const SHA = /^[0-9a-f]{40}$/;

const sessionFromHash = hash => { const m = String(hash || '').match(/(?:^#|&)review-session=([^&]+)/); return m ? decodeURIComponent(m[1]) : null; };
const canDecide = i => !!i && i.state !== 'integrated' && SHA.test(String(i.headSha || '')) && Number.isSafeInteger(i.pr);
const canApprove = i => canDecide(i) && (i.state === 'awaiting-review' || i.state === 'certified');
function decisionMessage(status, body) {
  if (status === 200 && body?.duplicate) return { ok: true, text: 'Already recorded for this commit.' };
  if (status === 200 || status === 207) return { ok: true, text: body?.state === 'approved'
    ? 'Approved. Recorded on GitHub for this exact commit. Integration review is next; nothing was merged or built.'
    : 'Fix requested. Recorded on GitHub; the dispatcher will request a repair only if budget, reserve and capacity allow.' + (body?.warning ? ' (Repair queue not reached: ' + body.warning + ')' : '') };
  if (status === 401) return { ok: false, signIn: true, text: 'Please sign in again (owner only).' };
  if (status === 409 && body?.error === 'stale-commit') return { ok: false, stale: true, text: 'A newer commit arrived. Nothing was recorded. Refresh and review the new commit.' };
  if (status === 422 && body?.error === 'checks-not-green') return { ok: false, text: 'Cannot approve: checks are not all passing (' + (body.checks || []).join(', ') + '). You can still request a fix.' };
  if (status === 400 && body?.error === 'describe-the-fix') return { ok: false, text: 'Describe what needs fixing.' };
  return { ok: false, text: 'Not recorded (' + (body?.error || 'HTTP ' + status) + ').' };
}
const api = { REVIEW_API, LABELS, sessionFromHash, canDecide, canApprove, decisionMessage };
root.ArcanaReview = api;
if (typeof module === 'object' && module.exports) { module.exports = api; return; }
if (typeof document === 'undefined') return;

// ---- Browser wiring ------------------------------------------------------------------------------
const store = { get() { try { return localStorage.getItem(KEY); } catch { return null; } },
  set(v) { try { v ? localStorage.setItem(KEY, v) : localStorage.removeItem(KEY); } catch {} } };
const incoming = sessionFromHash(location.hash);
if (incoming) { store.set(incoming); try { history.replaceState(null, '', location.pathname + location.search); } catch {} }
let serviceUp = null, data = null, listEl = null, onChange = null;
fetch(REVIEW_API + '/health', { cache: 'no-store' }).then(r => r.ok ? r.json() : null).then(j => { serviceUp = !!j?.ok; }).catch(() => { serviceUp = false; });

const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text !== undefined) n.textContent = text; return n; };
const safeUrl = u => typeof u === 'string' && u.startsWith('https://github.com/AARMOU/arcana_table/') ? u : 'https://github.com/AARMOU/arcana_table/pulls';

function render(container, payload, changed) {
  listEl = container; data = payload; onChange = changed || onChange;
  container.replaceChildren();
  if (!payload?.items?.length) { container.append(el('div', 'empty', 'No Copilot card PRs open.')); return; }
  for (const i of payload.items) {
    if (!Number.isSafeInteger(i?.pr)) continue;
    const row = el('button', 'list-row');
    row.type = 'button'; row.style.cssText = 'width:100%;text-align:left;background:none;border:0;cursor:pointer;color:inherit';
    const main = el('div', 'row-main');
    main.append(el('div', 'row-title', '#' + i.pr + ' ' + String(i.card || 'Card')),
      el('div', 'row-sub', (i.problems?.[0] || (LABELS[i.state] || i.state)) + ' · head ' + String(i.headSha || '').slice(0, 7)));
    row.append(main, el('span', 'tag ' + (TAGS[i.state] || 'gray'), LABELS[i.state] || String(i.state)));
    row.addEventListener('click', () => openSheet(i));
    container.append(row);
  }
}

let sheet;
function openSheet(item) {
  if (!sheet) { sheet = el('dialog'); sheet.style.cssText = 'max-width:640px;width:calc(100% - 32px);border:1px solid var(--line,#333);border-radius:14px;background:var(--panel,#111);color:var(--text,#eee);padding:16px'; document.body.append(sheet); }
  sheet.replaceChildren();
  const close = el('button', 'btn', 'Close'); close.type = 'button'; close.addEventListener('click', () => sheet.close());
  const head = el('div'); head.style.cssText = 'display:flex;justify-content:space-between;gap:8px;align-items:start';
  head.append(el('h2', '', String(item.card || 'Card')), close);
  const status = el('p', '', (LABELS[item.state] || item.state) + ' · ' + CERT[item.certification || 'not-run']);
  const link = el('a', '', 'PR #' + item.pr + ' · commit ' + String(item.headSha || '').slice(0, 12) + ' ↗');
  link.href = safeUrl(item.url); link.target = '_blank'; link.rel = 'noopener noreferrer';
  const oracle = el('p', 'muted', 'Loading Oracle rules…');
  const section = (title, lines, empty) => { const s = el('section'); s.append(el('h3', '', title)); const ul = el('ul');
    (lines.length ? lines : [empty]).forEach(x => ul.append(typeof x === 'string' ? el('li', '', x) : x)); s.append(ul); return s; };
  const checkItems = (item.checkRuns || []).map(c => { const li = el('li'); const a = el('a', '', c.name + ': ' + c.result); a.href = safeUrl(c.url); a.target = '_blank'; a.rel = 'noopener noreferrer'; li.append(a); return li; });
  const msg = el('p', '', ''); msg.setAttribute('role', 'status');
  const note = el('textarea'); note.rows = 3; note.maxLength = 1000; note.placeholder = 'What needs fixing? (required for Request Fix)'; note.style.cssText = 'width:100%;margin-top:8px';
  const approve = el('button', 'btn accent', 'Verify / Approve'); approve.type = 'button';
  const reject = el('button', 'btn', 'Request Fix'); reject.type = 'button';
  const signIn = el('button', 'btn', 'Sign in with GitHub (owner)'); signIn.type = 'button';
  signIn.addEventListener('click', () => { location.href = REVIEW_API + '/auth/login?return=' + encodeURIComponent(PWA_RETURN); });
  const actions = el('div'); actions.style.cssText = 'display:flex;gap:8px;flex-wrap:wrap;margin-top:8px';
  const refresh = () => {
    const session = store.get();
    approve.disabled = !session || !canApprove(item) || serviceUp === false;
    reject.disabled = !session || !canDecide(item) || serviceUp === false;
    signIn.hidden = !!session || serviceUp === false;
    if (serviceUp === false) msg.textContent = 'Approvals service is not deployed yet: ask Claude/the owner to finish setup (see #81).';
    else if (!session) msg.textContent = 'Sign in once to approve or request fixes from here.';
  };
  async function send(decision) {
    const session = store.get(); if (!session) return refresh();
    approve.disabled = reject.disabled = true; msg.textContent = 'Recording…';
    let status = 0, body = null;
    try {
      const res = await fetch(REVIEW_API + '/v1/decision', { method: 'POST', cache: 'no-store',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + session },
        body: JSON.stringify({ pr: item.pr, sha: item.headSha, decision, note: note.value }) });
      status = res.status; body = await res.json().catch(() => null);
    } catch { status = 0; }
    const m = decisionMessage(status, body);
    msg.textContent = m.text;
    if (m.signIn) store.set(null);
    if (m.ok && body?.state) { item.state = body.state; item.problems = body.state === 'changes-requested' ? ['You requested a fix on this commit'] : [];
      if (data) { data.awaitingReview = data.items.filter(x => x.state === 'awaiting-review' || x.state === 'certified').length; render(listEl, data); onChange?.(data); } }
    refresh();
  }
  approve.addEventListener('click', () => send('approve'));
  reject.addEventListener('click', () => send('request-fix'));
  actions.append(approve, reject, signIn);
  sheet.append(head, status, link,
    section('Oracle rules', [oracle], ''),
    section('Implemented abilities (requested)', item.abilities || [], 'Not listed for this card.'),
    section('Automated checks (this commit)', checkItems, 'No checks reported yet.'),
    section('Outstanding problems', item.problems || [], 'None reported.'),
    note, actions, msg,
    el('p', 'fine', 'Approval is card sign-off for integration review only. It never merges, builds, signs or releases.'));
  refresh();
  sheet.showModal();
  fetch('https://api.scryfall.com/cards/named?exact=' + encodeURIComponent(String(item.card || '')), { cache: 'no-store' })
    .then(r => r.ok ? r.json() : null).then(c => {
      const text = c?.oracle_text || (c?.card_faces || []).map(f => f.name + ': ' + (f.oracle_text || '')).join('\n');
      oracle.textContent = text ? (c.mana_cost ? c.mana_cost + ' · ' : '') + (c.type_line || '') + '\n' + text : 'Oracle text unavailable for this name.';
      oracle.style.whiteSpace = 'pre-wrap';
    }).catch(() => { oracle.textContent = 'Oracle text unavailable (offline).'; });
}
api.render = render;
})(typeof globalThis === 'object' ? globalThis : this);
