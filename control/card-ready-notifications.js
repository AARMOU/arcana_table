// Arcana Control Android installed-PWA notification opt-in. No secrets stored here:
// only the existing owner OAuth session is sent to the review Worker.
(() => {
  'use strict';
  const API = 'https://arcana-review.arcanamtg.workers.dev';
  const SESSION = 'arcana-review-session-v1';
  const RETURN = 'https://aarmou.github.io/arcana_table/control/';
  const url = API + '/v1/card-ready-push';
  const supported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  const token = () => {
    try { return localStorage.getItem(SESSION) || sessionStorage.getItem(SESSION); } catch { return null; }
  };
  const signIn = () => { location.href = API + '/auth/login?return=' + encodeURIComponent(RETURN); };
  function bytes(value) {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
    const raw = atob(base64 + '='.repeat((4 - base64.length % 4) % 4));
    return Uint8Array.from(raw, c => c.charCodeAt(0));
  }
  async function call(method, body) {
    const key = token();
    if (!key) return { error: 'sign-in-required' };
    const response = await fetch(url, { method, cache: 'no-store', headers: {
      Authorization: 'Bearer ' + key,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    }, ...(body ? { body: JSON.stringify(body) } : {}) });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(response.status === 401 ? 'Owner session expired. Sign in again.' :
      result.error === 'push-not-configured' ? 'Push service is not deployed yet.' :
        'Push request failed (HTTP ' + response.status + ').');
    return result;
  }
  const panel = document.querySelector('[aria-label="Card implementation review"]');
  if (!panel) return;
  const notice = document.createElement('div');
  notice.className = 'notice';
  notice.style.cssText = 'margin:12px;display:flex;gap:10px;align-items:center;flex-wrap:wrap';
  const button = document.createElement('button');
  button.type = 'button'; button.className = 'btn';
  button.textContent = 'Enable 🪿 approval honks';
  const status = document.createElement('span');
  status.className = 'muted'; status.setAttribute('role', 'status');
  status.textContent = 'Get an Android alert when a card is independently certified and ready.';
  notice.append(button, status);
  panel.insertBefore(notice, panel.querySelector('.rows'));

  let enabled = false, busy = false;
  function show(message) {
    status.textContent = message;
    button.disabled = busy;
    button.textContent = enabled ? 'Turn off approval honks' : 'Enable 🪿 approval honks';
  }
  async function toggle() {
    if (busy) return;
    if (!supported()) return show('This browser cannot receive Android push notifications. Open installed Control in Chrome.');
    if (!token()) return signIn();
    busy = true; button.disabled = true;
    try {
      if (enabled) {
        await call('DELETE');
        const sub = await (await navigator.serviceWorker.ready).pushManager.getSubscription();
        if (sub) await sub.unsubscribe();
        enabled = false; show('Approval honks disabled on this phone.');
      } else {
        const configResponse = await fetch(url + '/config', { cache: 'no-store' });
        const config = await configResponse.json();
        if (!config.configured || !config.publicKey) throw new Error('Push service is not configured yet.');
        if (Notification.permission !== 'granted') {
          const permission = await Notification.requestPermission();
          if (permission !== 'granted') throw new Error('Android notifications were not allowed. Enable them in Chrome settings.');
        }
        const registration = await navigator.serviceWorker.ready;
        let sub = await registration.pushManager.getSubscription();
        if (!sub) sub = await registration.pushManager.subscribe({
          userVisibleOnly: true, applicationServerKey: bytes(config.publicKey),
        });
        await call('POST', sub.toJSON());
        enabled = true; show('Approval honks enabled. Alerts work even when Control is closed.');
      }
    } catch (error) {
      show(error?.message || 'Could not change notification settings.');
    } finally { busy = false; button.disabled = false; }
  }
  button.addEventListener('click', toggle);
  if (!supported()) return show('Push notifications are not supported in this browser.');
  if (!token()) return show('Sign in as owner first, then tap Enable.');
  call('GET').then(result => {
    enabled = !!result.enabled;
    show(enabled ? 'Approval honks active on the registered phone.' : 'Tap Enable to get approval alerts.');
  }).catch(error => show(error.message));
})();
