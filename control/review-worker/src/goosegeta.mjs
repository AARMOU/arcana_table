/** Called only after existing OAuth owner/session/origin checks. No GitHub calls or API key in browser. */
export async function goosegeta(request, env) {
  if (env.GOOSEGETA_ENABLED !== 'true' || !env.GOOSEGETA_ROOMS || !env.GOOSEGETA_CONTROL_KEY || env.GOOSEGETA_CONTROL_KEY.length < 32)
    return [503,{error:'goosegeta-not-enabled'}];
  let input;
  if (request.method === 'GET') input = {code:new URL(request.url).searchParams.get('code')};
  else {
    const raw = await request.text(); if (raw.length > 2048) return [413,{error:'too-large'}];
    try { input = JSON.parse(raw); } catch { return [400,{error:'bad-json'}]; }
  }
  if (!input || typeof input !== 'object' || !/^[A-Z0-9]{6,8}$/.test(input.code ?? '')) return [400,{error:'invalid-room-code'}];
  if (request.method === 'POST' && !['summon','dismiss','chat','recover'].includes(input.action)) return [400,{error:'invalid-action'}];
  if (input.action === 'chat' && input.chatMode === 'recover') return [400,{error:'use-recover-action'}];
  if (input.action === 'recover' && (typeof input.sessionId !== 'string' || !input.sessionId || input.sessionId.length > 80))
    return [400,{error:'invalid-session'}];
  try {
    const response = await env.GOOSEGETA_ROOMS.fetch('https://rooms/internal/goosegeta/'+input.code, {
      method:request.method,headers:{Authorization:'Bearer '+env.GOOSEGETA_CONTROL_KEY,'Content-Type':'application/json'},
      ...(request.method === 'POST' ? {body:JSON.stringify(input.action === 'recover' ? {action:'recover',sessionId:input.sessionId} : {action:input.action,capability:input.capability,
        deckId:input.deckId,model:input.model === true,requestId:input.requestId,sessionId:input.sessionId,chatMode:input.chatMode})}: {}) });
    const text = await response.text(); if (text.length > 4096) throw Error('upstream-size');
    const data = JSON.parse(text);
    // No upstream body, hand, capability or credentials may enter public UI/logs.
    if (!response.ok) return [response.status,{error:/^[a-z-]{1,80}$/.test(data.error) ? data.error:'room-unavailable'}];
    const out = {};
    for (const key of ['sessionId','seat','deckId','status','actionsSubmitted','actionsAccepted','reason','chatMode','banter','observedTransitions','lastTurn','awaitingHost','reservedTokens','model','rejectionCategory','recoveryAvailable','modelOffReason'])
      if (['string','number','boolean'].includes(typeof data[key]) || data[key] === null) out[key] = data[key];
    return [200,out];
  } catch { return [503,{error:'room-unavailable'}]; }
}
