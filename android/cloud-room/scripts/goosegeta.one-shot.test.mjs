/** No-network regression: the paid canary may reach the provider at most once, even concurrently. */
import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { Miniflare, convertV4MiniflareOptions, Response as LocalResponse } from 'miniflare';
import { modelRequest } from '../../../tools/goosegeta/model.mjs';
import { pack, unpack } from '../src/goose-wire.mjs';

const code = new URL('../.local/one-shot-worker.mjs', import.meta.url).pathname;
await build({
  entryPoints:[new URL('../src/index.ts', import.meta.url).pathname],
  bundle:true, format:'esm', outfile:code, external:['cloudflare:workers'],
});
let providerCalls = 0;
const mf = new Miniflare(convertV4MiniflareOptions({
  name:'rooms-canary', host:'127.0.0.1', port:0, modules:true,
  scriptPath:code, compatibilityDate:'2026-10-01',
  durableObjects:{
    ROOMS:{className:'GameRoom',useSQLite:true},
    GOOSE_BUDGET:{className:'GooseBudget',useSQLite:true},
  },
  bindings:{
    GOOSEGETA_ENABLED:'true',
    GOOSEGETA_OPENAI_ENABLED:'true',
    OPENAI_API_KEY:'LOCAL-TEST-ONLY-NO-REAL-KEY',
    GOOSEGETA_CANARY_ONE_SHOT:'true',
    GOOSEGETA_MODEL:'gpt-6-luna',
    GOOSEGETA_GAME_TOKENS:'50000',
    GOOSEGETA_DAILY_TOKENS:'50000',
    GOOSEGETA_CONTROL_KEY:'LOCAL-TEST-CONTROL-KEY-32-CHARACTERS',
  },
  outboundService:async request=>{
    providerCalls++;
    assert.equal(request.url,'https://api.openai.com/v1/responses');
    return LocalResponse.json({
      status:'completed',
      output:[{type:'message',content:[{type:'output_text',text:'{"candidateId":"0"}'}]}],
      usage:{total_tokens:45}
    });
  },
}));
await mf.ready;
test('durable one-shot budget permits exactly one paid attempt across racing calls and reconstruction',async()=>{
  const ns=await mf.getDurableObjectNamespace('GOOSE_BUDGET');
  const stub=ns.get(ns.idFromName('account-daily'));
  const options=[{id:'0',action:{type:'PassPriority',playerId:'offline-ai'}}];
  const {body,reserve}=modelRequest('gpt-6-luna',{type:'stateUpdate',state:{turnNumber:1,phase:'MAIN'}},options);
  const request=()=>stub.fetch('http://budget/decide',{method:'POST',body:JSON.stringify({body,reserve,options})});
  const results=await Promise.all([request(),request(),request()]);
  assert.deepEqual(results.map(r=>r.status).sort(),[200,429,429]);
  assert.equal(providerCalls,1);
  await mf.unsafeEvictDurableObject('rooms-canary','GooseBudget',{name:'account-daily'});
  const after=await request();
  assert.equal(after.status,429);
  assert.equal(providerCalls,1);
});

test('spent paid canary falls back to an offered legal action without freezing the host game',async()=>{
  const res=await mf.dispatchFetch('http://local/room/FALL01',{headers:{Upgrade:'websocket'}});
  assert.equal(res.status,101);
  const socket=res.webSocket,queue=[],waiters=[];
  socket.addEventListener('message',e=>{
    const p=JSON.parse(e.data);
    const at=waiters.findIndex(w=>w.test(p));
    if(at<0)queue.push(p);else waiters.splice(at,1)[0].resolve(p);
  });
  socket.accept();
  const wait=(test,ms=5000)=>{
    const at=queue.findIndex(test);
    if(at>=0)return Promise.resolve(queue.splice(at,1)[0]);
    return new Promise((resolve,reject)=>{
      const w={test,resolve:p=>{clearTimeout(timer);resolve(p);}};
      const timer=setTimeout(()=>{const idx=waiters.indexOf(w);if(idx>=0)waiters.splice(idx,1);reject(Error('host-packet-timeout'));},ms);
      waiters.push(w);
    });
  };
  const outbound=async kind=>{
    while(true){
      const message=await wait(p=>p.type==='relay');
      const packet=await unpack(message.data);
      if(packet.type===kind)return packet;
    }
  };
  try {
    socket.send(JSON.stringify({type:'create',code:'FALL01',password:'arcana-argentum-FALL01',name:'Host'}));
    await wait(p=>p.type==='welcome');
    socket.send(JSON.stringify({type:'relaySeats',kinds:['friend']}));
    await wait(p=>p.type==='relay-seats-ack');
    socket.send(JSON.stringify({type:'goosegetaInvite',seat:1,requestId:'fixture-invite'}));
    const invite=await wait(p=>p.type==='goosegeta-invite');
    assert.ok(invite.capability);
    const summoned=await mf.dispatchFetch('http://local/internal/goosegeta/FALL01',{
      method:'POST',headers:{Authorization:'Bearer LOCAL-TEST-CONTROL-KEY-32-CHARACTERS'},
      body:JSON.stringify({action:'summon',capability:invite.capability,requestId:crypto.randomUUID(),deckId:'goosegeta-glue',model:true})
    });
    assert.equal(summoned.status,200);
    await outbound('lanHello');
    const send=async(seq,packet)=>socket.send(JSON.stringify({type:'relay',to:1,seq,data:await pack({...packet,_relayLink:'host-link'})}));
    await send(1,{type:'lanSeat',strictDeck:true});
    assert.equal((await outbound('lanReady')).ready,true);
    const offered=[
      {action:{type:'PlayLand',playerId:'offline-ai',cardId:'fixture-land'},isAffordable:true},
      {action:{type:'PassPriority',playerId:'offline-ai'},isAffordable:true}
    ];
    await send(2,{type:'stateUpdate',interactive:true,stateVersion:90,interactionEpoch:'offline-90',
      legalActions:offered,state:{turnNumber:4,phase:'MAIN',cards:{}}});
    const move=await outbound('submitAction');
    assert.ok(offered.some(x=>JSON.stringify(x.action)===JSON.stringify(move.action)),
      'fallback must be a host-offered action');
    assert.equal(move.interactionEpoch,'offline-90');
    const status=await mf.dispatchFetch('http://local/internal/goosegeta/FALL01',{
      headers:{Authorization:'Bearer LOCAL-TEST-CONTROL-KEY-32-CHARACTERS'}
    });
    const state=await status.json();
    assert.equal(state.status,'playing','provider budget exhaustion must not freeze session');
    assert.equal(state.model,'off','only one paid model attempt permitted');
    assert.equal(state.reason,'model-paused-free-fallback');
    assert.equal(state.modelOffReason,'provider-or-budget');
    assert.equal(state.actionsSubmitted,1);
    assert.equal(providerCalls,1,'never contact paid provider again');
    await send(3,{type:'error',code:'LOCAL_ENGINE',message:'private engine diagnostic'});
    let rejected;
    for(let i=0;i<50;i++){
      rejected=await(await mf.dispatchFetch('http://local/internal/goosegeta/FALL01',{
        headers:{Authorization:'Bearer LOCAL-TEST-CONTROL-KEY-32-CHARACTERS'}
      })).json();
      if(rejected.reason==='host-rejected:local-engine')break;
      await new Promise(r=>setTimeout(r,5));
    }
    assert.equal(rejected.modelOffReason,'provider-or-budget','later gameplay error cannot erase budget/provider cause');
    const reenable=await mf.dispatchFetch('http://local/internal/goosegeta/FALL01',{
      method:'POST',headers:{Authorization:'Bearer LOCAL-TEST-CONTROL-KEY-32-CHARACTERS'},
      body:JSON.stringify({action:'chat',sessionId:state.sessionId,model:true,chatMode:'ruthless'})
    });
    assert.equal(reenable.status,409,'overwritten gameplay reason cannot revive a paused paid model');
    assert.equal(providerCalls,1);
  } finally {socket.close();}
});


// Owner-authorized repeated paid decisions still have one permanent account-wide
// safety ceiling; all network responses below are stubbed, so API spend is zero.
test('ongoing GPT-6 play allows multiple calls, then respects durable lifetime allowance',async()=>{
  const options=[{id:'0',action:{type:'PassPriority',playerId:'offline-ai'}}];
  const {body,reserve}=modelRequest('gpt-6-luna',{type:'stateUpdate',state:{turnNumber:2,phase:'MAIN'}},options);
  const totalCap=reserve*2+10;
  let calls=0;
  const ongoing = new Miniflare(convertV4MiniflareOptions({
    name:'rooms-ongoing',host:'127.0.0.1',port:0,modules:true,
    scriptPath:code,compatibilityDate:'2026-10-01',
    durableObjects:{ROOMS:{className:'GameRoom',useSQLite:true},GOOSE_BUDGET:{className:'GooseBudget',useSQLite:true}},
    bindings:{GOOSEGETA_ENABLED:'true',GOOSEGETA_OPENAI_ENABLED:'true',
      OPENAI_API_KEY:'LOCAL-FIXTURE-KEY',GOOSEGETA_CANARY_ONE_SHOT:'false',
      GOOSEGETA_MODEL:'gpt-6-luna',GOOSEGETA_GAME_TOKENS:'1000000',
      GOOSEGETA_DAILY_TOKENS:'1000000',GOOSEGETA_TOTAL_TOKENS:String(totalCap),
      GOOSEGETA_CONTROL_KEY:'LOCAL-TEST-CONTROL-KEY-32-CHARACTERS'},
    outboundService:async()=>{
      calls++;
      return LocalResponse.json({status:'completed',output:[{type:'message',
        content:[{type:'output_text',text:'{"candidateId":"0"}'}]}],usage:{total_tokens:45}});
    }
  }));
  await ongoing.ready;
  try {
    const ns=await ongoing.getDurableObjectNamespace('GOOSE_BUDGET');
    const stub=ns.get(ns.idFromName('account-daily'));
    const request=()=>stub.fetch('http://budget/decide',{method:'POST',body:JSON.stringify({body,reserve,options})});
    assert.equal((await request()).status,200);
    assert.equal((await request()).status,200,'second real-model attempt is allowed');
    assert.equal((await request()).status,429,'lifetime reserve ceiling blocks a third attempt');
    assert.equal(calls,2);
    await ongoing.unsafeEvictDurableObject('rooms-ongoing','GooseBudget',{name:'account-daily'});
    assert.equal((await request()).status,429,'same cap after Durable Object eviction');
    assert.equal(calls,2);
  } finally {await ongoing.dispose();}
});

test.after(async()=>{await mf.dispose();});
