import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{sealSession} from '../src/index.mjs';
const ORIGIN='https://aarmou.github.io';
const secret='local-test-session-secret-at-least-32-chars';
async function harness(){
  const session=await sealSession(secret,{t:'never-forward-this-oauth-token',login:'AARMOU',exp:Math.floor(Date.now()/1000)+1000});
  const calls=[];
  const env={SESSION_SECRET:secret,GITHUB_OAUTH_CLIENT_ID:'test',GITHUB_OAUTH_CLIENT_SECRET:'test',GOOSEGETA_ENABLED:'true',
    GOOSEGETA_CONTROL_KEY:'local-test-service-secret-at-least-32-chars',
    GOOSEGETA_ROOMS:{async fetch(url,init){calls.push({url,init});return Response.json({sessionId:'s',status:'joining',model:'off',hand:['NEVER-RETURN'],capability:'NEVER-RETURN'});}}};
  const request=(input={},headers={},method='POST')=>new Request('https://review/v1/goosegeta'+(method==='GET'?'?code=GOOSE1':''),{
    method,headers:{Origin:ORIGIN,Authorization:'Bearer '+session,...headers},...(method==='POST'?{body:JSON.stringify({code:'GOOSE1',action:'summon',...input})}:{})});
  const run=(r,e=env)=>worker.fetch(r,e,{},()=>{throw Error('No GitHub, OpenAI or arbitrary HTTP request permitted');});
  return {env,calls,request,run};
}
test('Goosegeta owner OAuth, origin and feature gates run before service access',async()=>{
  const h=await harness();
  assert.equal((await h.run(h.request({}, {Authorization:''}))).status,401);
  assert.equal((await h.run(h.request({}, {Origin:'https://evil.test'}))).status,403);
  assert.equal((await h.run(h.request(),{...h.env,GOOSEGETA_ENABLED:'false'})).status,503);
  assert.equal(h.calls.length,0);
});
test('Goosegeta service binding gets only fixed room path and separate credential; response is private allowlist',async()=>{
  const h=await harness();const r=await h.run(h.request({capability:'host-cap',deckId:'goosegeta-glue',requestId:'unique-request-id'}));
  assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');
  assert.deepEqual(await r.json(),{sessionId:'s',status:'joining',model:'off'});
  assert.equal(h.calls[0].url,'https://rooms/internal/goosegeta/GOOSE1');
  assert.ok(!JSON.stringify(h.calls).includes('never-forward-this-oauth-token'));
  assert.equal(JSON.parse(h.calls[0].init.body).model,false);
});
test('Goosegeta rejects URLs and invalid methods; status read never summons',async()=>{
  const h=await harness();assert.equal((await h.run(h.request({code:'https://evil.test'}))).status,400);
  assert.equal((await h.run(h.request({}, {}, 'PUT'))).status,405);assert.equal(h.calls.length,0);
  assert.equal((await h.run(h.request({}, {}, 'GET'))).status,200);
  assert.equal(h.calls[0].init.method,'GET');assert.equal(h.calls[0].init.body,undefined);
});
