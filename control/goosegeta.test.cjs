const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/goosegeta.js','utf8');
function harness(responses,hash='',hasToken=true){
 const nodes=new Map();for(const id of ['gooseForm','gooseCode','gooseInvite','gooseModel','gooseChat','gooseDeck','gooseStatus','gooseDismiss','gooseSummon','gooseRefresh','gooseApplyChat'])
 nodes.set(id,{value:id==='gooseChat'?'muted':id==='gooseDeck'?'goosegeta-glue':'',checked:false,disabled:false,textContent:'',handlers:{},addEventListener(k,f){this.handlers[k]=f;}});
 const stores=new Map(hasToken?[['arcana-review-session-v1','FAKE-OWNER-SESSION']]:[]);const calls=[];let uuid=0;
 const window={location:{hash,pathname:'/control/',search:'',href:''},localStorage:{getItem:k=>stores.get(k),removeItem:k=>stores.delete(k)},sessionStorage:{getItem:()=>null,removeItem(){}}};
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
