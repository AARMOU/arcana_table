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
test('parked Goosegeta retains owner OAuth and origin checks',async()=>{
  const h=await harness();
  assert.equal((await h.run(h.request({}, {Authorization:''}))).status,401);
  assert.equal((await h.run(h.request({}, {Origin:'https://evil.test'}))).status,403);
  assert.equal((await h.run(h.request(),{...h.env,GOOSEGETA_ENABLED:'false'})).status,410);
  assert.equal(h.calls.length,0);
});
test('parked endpoint never forwards old model, retry or dismiss requests even with enabled bindings',async()=>{
  const h=await harness();
  for(const action of ['summon','chat','recover','dismiss']) {
    const r=await h.run(h.request({action,model:true,capability:'private-host-cap'}));
    assert.equal(r.status,410);assert.equal(r.headers.get('Cache-Control'),'no-store');
    assert.deepEqual(await r.json(),{error:'goosegeta-parked'});
  }
  assert.equal(h.calls.length,0);
});
test('parked status and malformed room payloads cannot invoke the room service',async()=>{
  const h=await harness();
  assert.equal((await h.run(h.request({code:'https://evil.test'}))).status,410);
  assert.equal((await h.run(h.request({}, {}, 'PUT'))).status,405);
  assert.equal((await h.run(h.request({}, {}, 'GET'))).status,410);
  assert.equal(h.calls.length,0);
});
