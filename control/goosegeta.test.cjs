const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/goosegeta.js','utf8');
function harness(responses,hash='',hasToken=true,sharedStores=null){
 const nodes=new Map();for(const id of ['gooseForm','gooseCode','gooseInvite','gooseModel','gooseChat','gooseDeck','gooseStatus','gooseDismiss','gooseSummon','gooseRefresh','gooseApplyChat','gooseRecover'])
 nodes.set(id,{value:id==='gooseChat'?'muted':id==='gooseDeck'?'goosegeta-glue':'',checked:false,disabled:false,textContent:'',handlers:{},addEventListener(k,f){this.handlers[k]=f;}});
 const stores=sharedStores || new Map(hasToken?[['arcana-review-session-v1','FAKE-OWNER-SESSION']]:[]);const calls=[];let uuid=0;
 const window={location:{hash,pathname:'/control/',search:'',href:''},localStorage:{getItem:k=>stores.get(k),setItem:(k,v)=>stores.set(k,String(v)),removeItem:k=>stores.delete(k)},sessionStorage:{getItem:()=>null,removeItem(){}}};
 vm.runInNewContext(source,{window,document:{getElementById:id=>nodes.get(id)},crypto:{randomUUID:()=>`request-id-${++uuid}-abcdefghijkl`},history:{replaceState(){window.location.hash='';}},
 fetch:async(url,options)=>{calls.push({url,...options});const r=responses.shift();assert.ok(r,'unexpected request');return {status:r.status,ok:r.status===200,json:async()=>r.data};}});
 return {nodes,calls,stores,window,click:async id=>{nodes.get(id).handlers[id==='gooseForm'?'submit':'click']?.({preventDefault(){}});await new Promise(r=>setImmediate(r));}};
}
test('private fragment is consumed in memory; default summon is muted/model-off; dismiss matches session',async()=>{
 const h=harness([{status:200,data:{status:'joining',sessionId:'s',model:'off',chatMode:'muted'}},{status:200,data:{status:'stopped',sessionId:'s',model:'off'}}], '#goose-invite=GOOSE1.'+'a'.repeat(72));
 assert.equal(h.window.location.hash,'');assert.equal(h.nodes.get('gooseInvite').value.length,72);assert.equal(h.stores.size,1);
 await h.click('gooseForm');const first=JSON.parse(h.calls[0].body);assert.equal(first.model,false);assert.equal(first.chatMode,'muted');assert.equal(first.code,'GOOSE1');
 assert.match(h.nodes.get('gooseStatus').textContent,/joining is not proof/);
 await h.click('gooseDismiss');const second=JSON.parse(h.calls[1].body);assert.equal(second.action,'dismiss');assert.equal(second.sessionId,'s');assert.equal(h.nodes.get('gooseInvite').value,'');
});
test('uncertain network submission retains idempotency key and expired owner credential cannot repeat writes',async()=>{
 const h=harness([{status:503,data:{error:'room-unavailable'}},{status:200,data:{status:'joining',sessionId:'s'}},{status:401,data:{}}]);h.nodes.get('gooseCode').value='GOOSE1';h.nodes.get('gooseInvite').value='private';
 await h.click('gooseForm');await h.click('gooseForm');assert.equal(JSON.parse(h.calls[0].body).requestId,JSON.parse(h.calls[1].body).requestId);
 await h.click('gooseRefresh');assert.equal(h.stores.size,0);await h.click('gooseForm');assert.equal(h.calls.length,3);assert.match(h.window.location.href,/auth\/login/);
});

test('explicit owner retry goes through authorized existing proxy, without new invite or paid mode',async()=>{
 const h=harness([
  {status:200,data:{status:'needs attention',sessionId:'s',model:'off',reason:'unsupported-decision-or-action:decision-SelectCardsDecision',chatMode:'ruthless'}},
  {status:200,data:{status:'needs attention',sessionId:'s',model:'off',reason:'unsupported-decision-or-action:decision-SelectCardsDecision',chatMode:'ruthless'}},
  {status:200,data:{status:'playing',sessionId:'s',model:'off',chatMode:'ruthless'}},
 ]);
 h.nodes.get('gooseCode').value='GOOSE1';
 h.nodes.get('gooseChat').value='ruthless';
 await h.click('gooseRefresh');
 assert.equal(h.nodes.get('gooseRecover').disabled,false);
 await h.click('gooseRecover');
 const body=JSON.parse(h.calls[1].body);
 assert.equal(body.action,'chat','existing owner proxy accepts the verb');
 assert.equal(body.chatMode,'recover','reserved explicit recovery route');
 assert.equal(body.model,false,'no paid model re-enable');
 assert.equal(body.sessionId,'s');
 assert.equal(h.nodes.get('gooseChat').value,'ruthless');
 assert.equal(h.nodes.get('gooseRecover').disabled,true,'no automatic retries');
 await h.click('gooseRefresh');
 assert.equal(h.nodes.get('gooseRecover').disabled,true);
 assert.equal(h.calls.length,3);
});

test('refresh restores authenticated existing room and session without storing the host invitation',async()=>{
 const first=harness([{status:200,data:{
  sessionId:'game-still-live',status:'needs attention',reason:'unsupported-decision-or-action:decision-SelectCardsDecision',
  model:'off',chatMode:'ruthless',deckId:'goosegeta-flock',actionsSubmitted:18,actionsAccepted:18
 }}]);
 first.nodes.get('gooseCode').value='GOOSE1';
 first.nodes.get('gooseInvite').value='private-should-never-be-stored';
 await first.click('gooseRefresh');
 assert.equal(first.stores.get('arcana-goosegeta-last-room-v1'),'GOOSE1');
 assert.ok(![...first.stores.values()].some(x=>String(x).includes('private-should-never-be-stored')));
 const reloaded=harness([{status:200,data:{
  sessionId:'game-still-live',status:'needs attention',reason:'unsupported-decision-or-action:decision-SelectCardsDecision',
  model:'off',chatMode:'ruthless',deckId:'goosegeta-flock',actionsSubmitted:18,actionsAccepted:18
 }}],'',true,first.stores);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(reloaded.nodes.get('gooseCode').value,'GOOSE1');
 assert.equal(reloaded.nodes.get('gooseInvite').value,'');
 assert.equal(reloaded.nodes.get('gooseChat').value,'ruthless');
 assert.equal(reloaded.nodes.get('gooseDeck').value,'goosegeta-flock');
 assert.equal(reloaded.nodes.get('gooseRecover').disabled,false);
 assert.equal(reloaded.calls.length,1);
 assert.equal(reloaded.calls[0].method,'GET');
 assert.match(reloaded.calls[0].url,/code=GOOSE1/);
 assert.equal(reloaded.calls[0].headers.Authorization,'Bearer FAKE-OWNER-SESSION');
 assert.equal(reloaded.calls[0].body,undefined);
});
test('reload never makes an authenticated request or persists a private invite without an owner session',async()=>{
 const stores=new Map([['arcana-goosegeta-last-room-v1','GOOSE1']]);
 const h=harness([], '',false,stores);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(h.nodes.get('gooseCode').value,'GOOSE1');
 assert.equal(h.nodes.get('gooseInvite').value,'');
 assert.equal(h.calls.length,0);
});
test('dismiss clears persisted room locator, next reload does not auto-query a stopped game',async()=>{
 const shared=new Map([['arcana-review-session-v1','FAKE-OWNER-SESSION'],['arcana-goosegeta-last-room-v1','GOOSE1']]);
 const h=harness([
 {status:200,data:{sessionId:'live',status:'playing',model:'opted in',chatMode:'ruthless',deckId:'goosegeta-glue'}},
 {status:200,data:{sessionId:'live',status:'stopped',model:'off',chatMode:'muted'}}
 ],'',true,shared);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(h.nodes.get('gooseModel').checked,true);
 await h.click('gooseDismiss');
 assert.equal(shared.has('arcana-goosegeta-last-room-v1'),false);
 const again=harness([], '',true,shared);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(again.calls.length,0);
 assert.equal(again.nodes.get('gooseInvite').value,'');
});
