/* Existing owner OAuth session. Host invitation kept only in memory, never storage/logs. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  if (!$('gooseForm')) return;
  const API = 'https://arcana-review.arcanamtg.workers.dev';
  const KEY = 'arcana-review-session-v1';
  let requestId = crypto.randomUUID(), currentSession = null, submitted = false, room = '';
  const invite = /^#goose-invite=([A-Z0-9]{6,8})\.([a-f0-9-]{64,100})$/.exec(window.location.hash);
  if (invite) {
    $('gooseCode').value = invite[1]; $('gooseInvite').value = invite[2];
    history.replaceState(null,'',window.location.pathname + window.location.search);
  }
  const token = () => {
    for (const kind of ['localStorage','sessionStorage']) try { const v = window[kind].getItem(KEY); if (v) return v; } catch {}
    return null;
  };
  function show(data) {
    if (data.sessionId) currentSession = data.sessionId;
    $('gooseDismiss').disabled = !currentSession || ['idle','stopped'].includes(data.status);
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
    if (room !== code) { room = code; currentSession = null; submitted = false; requestId = crypto.randomUUID(); }
    if (action === 'dismiss' && !currentSession) return;
    const buttons = ['gooseSummon','gooseDismiss','gooseRefresh','gooseApplyChat']; buttons.forEach(id => $(id).disabled=true);
    try {
      if (action === 'summon') submitted = true;
      const r = await fetch(API+'/v1/goosegeta'+(!action ? '?code='+code : ''), {
        method: action ? 'POST':'GET',cache:'no-store',headers:{Authorization:'Bearer '+session,...(action?{'Content-Type':'application/json'}:{})},
        ...(action?{body:JSON.stringify({action,code,capability:$('gooseInvite').value,deckId:$('gooseDeck').value,
          model:$('gooseModel').checked,requestId,sessionId:currentSession,chatMode:$('gooseChat').value})}:{}) });
      if (r.status === 401) {
        for (const k of ['localStorage','sessionStorage']) try { if(window[k].getItem(KEY)===session)window[k].removeItem(KEY); } catch {}
        throw Error('Owner session expired. Sign in again.');
      }
      const data = await r.json();
      if (!r.ok) throw Error(data.error || 'Request unconfirmed; check status before retrying.');
      show(data);
      if (action === 'dismiss') { submitted = false; requestId = crypto.randomUUID(); $('gooseInvite').value=''; }
      if (action === 'summon') $('gooseStatus').textContent += ' · joining is not proof Android accepted the deck. Check status.';
    } catch (e) { $('gooseStatus').textContent = 'Not confirmed: '+e.message; }
    finally { $('gooseSummon').disabled=false; $('gooseRefresh').disabled=false; $('gooseDismiss').disabled=!currentSession;$('gooseApplyChat').disabled=!currentSession; }
  }
  $('gooseForm').addEventListener('submit',event=>{event.preventDefault();void call('summon');});
  $('gooseRefresh').addEventListener('click',()=>void call(null));
  $('gooseApplyChat').addEventListener('click',()=>void call('chat'));
  $('gooseDismiss').addEventListener('click',()=>void call('dismiss'));
  // Changing setup starts a new idempotent request only before a previous submission.
  for (const id of ['gooseInvite','gooseDeck','gooseModel']) $(id).addEventListener('change',()=>{
    if (!submitted) requestId=crypto.randomUUID();
  });
})();
