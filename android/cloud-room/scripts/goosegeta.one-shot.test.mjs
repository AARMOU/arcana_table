/** No-network regression: the paid canary may reach the provider at most once, even concurrently. */
import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { Miniflare, convertV4MiniflareOptions, Response as LocalResponse } from 'miniflare';
import { modelRequest } from '../../../tools/goosegeta/model.mjs';

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
test.after(async()=>{await mf.dispose();});
