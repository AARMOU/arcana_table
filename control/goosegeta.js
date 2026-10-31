/* Existing owner OAuth session. Host invitation kept only in memory, never storage/logs. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  if (!$('gooseForm')) return;
  const API = 'https://arcana-review.arcanamtg.workers.dev';
  const KEY = 'arcana-review-session-v1';
  // Room code is a locator only; the host invitation is a private capability and
  // MUST NEVER be stored. Status always rehydrates through owner OAuth.
  const LAST_ROOM = 'arcana-goosegeta-last-room-v1';
  const saveRoom = code => { try { window.localStorage.setItem(LAST_ROOM,code); } catch {} };
  const clearRoom = () => { try { window.localStorage.removeItem(LAST_ROOM); } catch {} };
  const previousRoom = (() => { try { return window.localStorage.getItem(LAST_ROOM); } catch { return null; } })();
  let requestId = crypto.randomUUID(), currentSession = null, submitted = false, room = '', recoverable = false;
  const invite = /^#goose-invite=([A-Z0-9]{6,8})\.([a-f0-9-]{64,100})$/.exec(window.location.hash);
  if (invite) {
    $('gooseCode').value = invite[1]; $('gooseInvite').value = invite[2];
    history.replaceState(null,'',window.location.pathname + window.location.search);
  } else if (/^[A-Z0-9]{6,8}$/.test(previousRoom ?? '')) {
    $('gooseCode').value = previousRoom; // locator only, never host invitation
  }
  const token = () => {
    for (const kind of ['localStorage','sessionStorage']) try { const v = window[kind].getItem(KEY); if (v) return v; } catch {}
    return null;
  };
  function show(data) {
    currentSession = data.sessionId || null;
    if (currentSession) {
      if (['muted','basic','ruthless'].includes(data.chatMode)) $('gooseChat').value = data.chatMode;
      $('gooseModel').checked = data.model === 'opted in';
      if (['goosegeta-flock','goosegeta-glue'].includes(data.deckId)) $('gooseDeck').value = data.deckId;
    }
    $('gooseDismiss').disabled = !currentSession || ['idle','stopped'].includes(data.status);
    recoverable = !!currentSession && data.status === 'needs attention' &&
      data.recoveryAvailable === true;
    $('gooseRecover').disabled = !recoverable;
    $('gooseStatus').textContent = (data.status || 'unknown') + ' · model ' + (data.model || 'off') +
      (data.sessionId ? ' · moves sent '+(data.actionsSubmitted||0)+' · host accepted '+(data.actionsAccepted||0)+' · later host updates '+(data.observedTransitions||0)+
      ' · reserved tokens '+(data.reservedTokens||0) : '') +
      ' · chat '+(data.chatMode||'muted')+' ('+(data.banter||'canned')+')' + (data.reason?' · '+data.reason:'') +
      (data.awaitingHost ? ' · waiting for host response' : '');
  }
  async function call(action) {
    const session = token();
    if (!session) { $('gooseStatus').textContent = 'Sign in as owner first. Then request a fresh host invite.';
      window.location.href = API+'/auth/login?return='+encodeURIComponent('https://aarmou.github.io/arcana_table/control/'); return; }
    const code = $('gooseCode').value.trim().toUpperCase();
    if (!/^[A-Z0-9]{6,8}$/.test(code)) { $('gooseStatus').textContent='Enter the room code.'; return; }
    if (room !== code) { room = code; currentSession = null; submitted = false; recoverable = false; requestId = crypto.randomUUID(); }
    saveRoom(code);
    if (action === 'dismiss' && !currentSession) return;
    if (action === 'recover' && (!currentSession || !recoverable)) return;
    const buttons = ['gooseSummon','gooseDismiss','gooseRefresh','gooseApplyChat','gooseRecover']; buttons.forEach(id => $(id).disabled=true);
    try {
      if (action === 'summon') submitted = true;
      const r = await fetch(API+'/v1/goosegeta'+(!action ? '?code='+code : ''), {
        method: action ? 'POST':'GET',cache:'no-store',headers:{Authorization:'Bearer '+session,...(action?{'Content-Type':'application/json'}:{})},
        ...(action?{body:JSON.stringify({action,code,capability:$('gooseInvite').value,deckId:$('gooseDeck').value,
          model:$('gooseModel').checked,requestId,sessionId:currentSession,
          chatMode:$('gooseChat').value})}:{}) });
      if (r.status === 401) {
        for (const k of ['localStorage','sessionStorage']) try { if(window[k].getItem(KEY)===session)window[k].removeItem(KEY); } catch {}
        throw Error('Owner session expired. Sign in again.');
      }
      const data = await r.json();
      if (!r.ok) throw Error(data.error || 'Request unconfirmed; check status before retrying.');
      show(data);
      if (action === 'dismiss') { submitted = false; requestId = crypto.randomUUID(); $('gooseInvite').value=''; clearRoom(); }
      if (action === 'summon') $('gooseStatus').textContent += ' · joining is not proof Android accepted the deck. Check status.';
      if (action === 'recover') {
        recoverable = false;
        $('gooseStatus').textContent += ' · requested fresh Android state; check status to confirm movement.';
      }
    } catch (e) { $('gooseStatus').textContent = 'Not confirmed: '+e.message; }
    finally { $('gooseSummon').disabled=false; $('gooseRefresh').disabled=false; $('gooseDismiss').disabled=!currentSession;
      $('gooseApplyChat').disabled=!currentSession; $('gooseRecover').disabled=!recoverable; }
  }
  $('gooseForm').addEventListener('submit',event=>{event.preventDefault();void call('summon');});
  $('gooseRefresh').addEventListener('click',()=>void call(null));
  $('gooseRecover').addEventListener('click',()=>void call('recover'));
  $('gooseApplyChat').addEventListener('click',()=>void call('chat'));
  $('gooseDismiss').addEventListener('click',()=>void call('dismiss'));
  // Changing setup starts a new idempotent request only before a previous submission.
  for (const id of ['gooseInvite','gooseDeck','gooseModel']) $(id).addEventListener('change',()=>{
    if (!submitted) requestId=crypto.randomUUID();
  });
  // After Control/PWA refresh the browser lost in-memory sessionId, not the
  // Android-hosted game. Recover it via authenticated read-only status. Never
  // automatically summon, recover, dismiss, or submit a paid model request.
  if (!invite && /^[A-Z0-9]{6,8}$/.test(previousRoom ?? '') && token()) void call(null);
})();
