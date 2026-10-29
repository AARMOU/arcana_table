'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {routeFailure,reconcileFailures,createAuthorizer,MARKER,OBSERVED,WORKFLOWS}=require('./failure-routing.cjs');
test('Cardkit failures are eligible for existing repair routing', () => {
  assert.equal(WORKFLOWS.has('Arcana Cardkit'), true);
});
const A=require('./admission.cjs');
const sha='a'.repeat(40);
const allow=records=>async()=>({allowed:true,reason:'admitted',attempt:1,unit:67,record:async()=>records.push(1)});
const deny=reason=>async()=>({allowed:false,reason});
function fixture({comments=[],run={},pr={},authorize,status,runs}={}) {
  const calls=[],records=[],deliveryCalls=[];
  const delivery={preflight:async()=>({ok:true}),post:async x=>{deliveryCalls.push(x);calls.push(x);return {ok:true,id:543};}};
  const current={number:85,draft:true,state:'open',body:'Unit: #67',head:{ref:'copilot/cards',sha,repo:{id:1}},...pr};
  const api={pulls:{list:'prs',get:async()=>({data:current})},
    issues:{listComments:'comments',createComment:async x=>{if(status)throw {status};calls.push(x);},
      updateComment:async x=>calls.push(x)},actions:{listJobsForWorkflowRun:'jobs',listWorkflowRunsForRepo:'runs'}};
  const workflowRun={id:12,workflow_id:4,name:'Argentum six-precon isolated worker regression',conclusion:'failure',
    event:'push',head_branch:'copilot/cards',head_sha:sha,head_repository:{id:1},...run};
  const data={prs:[current],comments,runs:runs||[workflowRun],jobs:[{id:123,name:'@someone cards',conclusion:'failure',
    steps:[{name:'Scenarios',conclusion:'failure'}]}]};
  const github={rest:api,paginate:async x=>data[x]};
  const context={repo:{owner:'AARMOU',repo:'arcana_table'},payload:{repository:{id:1},workflow_run:workflowRun}};
  return {calls,records,deliveryCalls,current,github,context,args:{github,context,delivery,authorize:authorize||allow(records)}};
}
const bot=(body,id=1)=>({id,body,user:{type:'Bot',login:'github-actions[bot]'}});

test('Cardkit failed draft PR requests one repair and deduplicates the same head',async()=>{
  const x=fixture({run:{name:'Arcana Cardkit',workflow_id:987}});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
  assert.equal(x.records.length,1);
  assert.match(x.calls[0].body,/workflow-id:987/);
  const repeat=fixture({run:{name:'Arcana Cardkit',workflow_id:987,id:13},comments:[bot(x.calls[0].body)]});
  assert.equal((await routeFailure(repeat.args)).state,'already-routed');
  assert.equal(repeat.records.length,0);
  assert.equal(repeat.calls.length,0);
});
test('admitted failure records the unit reservation, then one controlled mention',async()=>{
  const x=fixture();assert.equal((await routeFailure(x.args)).state,'repair-requested');
  assert.equal(x.records.length,1);assert.equal(x.calls.length,1);const b=x.calls[0].body;
  assert.match(b,/run-id:12/);assert.ok(b.includes(sha));assert.match(b,/job 123/);
  assert.match(b,/Repair request 1 of 2 for unit #67/);assert.equal(b.match(/@/g).length,1);
});
test('denied admission records an observation with its reason and no agent request',async()=>{
  const x=fixture({authorize:deny('capacity: 1 of 1 active')});
  assert.equal((await routeFailure(x.args)).state,'observed-only');
  assert.ok(x.calls[0].body.startsWith(OBSERVED));assert.ok(!x.calls[0].body.includes('@copilot'));
  assert.match(x.calls[0].body,/capacity: 1 of 1 active/);assert.equal(x.records.length,0);
});
test('default authorizer is observation only',async()=>{
  const x=fixture();delete x.args.authorize;
  assert.equal((await routeFailure(x.args)).state,'observed-only');
});
test('denial reasons cannot inject mentions',async()=>{
  const x=fixture({authorize:deny('ping @copilot\nnow')});await routeFailure(x.args);
  assert.ok(!x.calls[0].body.includes('@'));
});
test('same SHA/workflow is deduplicated across different run IDs',async()=>{
  const x=fixture({comments:[bot(`${MARKER}\nrun-id:10\nworkflow-id:4\nhead-sha:${sha}`)]});
  assert.equal((await routeFailure(x.args)).state,'already-routed');assert.equal(x.calls.length,0);
});
test('run-id 12 does not collide with run-id 123',async()=>{
  const x=fixture({comments:[bot(`${MARKER}\nrun-id:123\nworkflow-id:3\nhead-sha:${sha}`)]});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
});
test('observed failure can upgrade in place when admitted',async()=>{
  const x=fixture({comments:[bot(`${OBSERVED}\nworkflow-id:4\nhead-sha:${sha}`,88)]});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
  assert.equal(x.calls[0].comment_id,undefined); // Owner adds an authorized comment; bot observation stays immutable.
});
test('duplicate withheld observations do not spam',async()=>{
  const x=fixture({authorize:deny('budget: unknown'),comments:[bot(`${OBSERVED}\nworkflow-id:4\nhead-sha:${sha}`)]});
  assert.equal((await routeFailure(x.args)).state,'already-observed');assert.equal(x.calls.length,0);
});
test('owner-authored delivery deduplicates the same head; other users cannot forge it',async()=>{
  const own={id:1,user:{login:'AARMOU',type:'User'},author_association:'OWNER',
    body:`${MARKER}\nworkflow-id:4\nhead-sha:${sha}`};
  const x=fixture({comments:[own]});
  assert.equal((await routeFailure(x.args)).state,'already-routed');
  assert.equal(x.records.length,0);
  const stranger=fixture({comments:[{...own,user:{login:'mallory'},author_association:'NONE'}]});
  assert.equal((await routeFailure(stranger.args)).state,'repair-requested');
});
test('missing or incorrect delivery identity fails closed before consuming repairs',async()=>{
  const x=fixture();
  let invoked=0;
  x.args.authorize=async()=>{invoked++;return {allowed:true,record:async()=>x.records.push(1)};};
  x.args.delivery.preflight=async()=>({ok:false,reason:'owner-delivery-identity-not-owner'});
  const r=await routeFailure(x.args);
  assert.equal(r.state,'observed-only');
  assert.equal(invoked,0);assert.equal(x.records.length,0);
  assert.ok(x.calls[0].body.includes('owner-delivery-identity-not-owner'));
  assert.ok(!x.calls[0].body.includes('@copilot'));
});
test('untrusted user marker does not count as routed',async()=>{
  const x=fixture({comments:[{body:`${MARKER}\nrun-id:12`,user:{type:'User',login:'other'}}]});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
});
test('external, protected, successful and unrecognized runs are ignored',async()=>{
  for(const run of [{head_repository:{id:2}},{head_branch:'main'},{event:'workflow_run'},
    {conclusion:'success'},{name:'Release APK'},{head_sha:'invalid'}]){
    const x=fixture({run});assert.equal((await routeFailure(x.args)).state,'ignored');assert.equal(x.calls.length,0);
  }
});
test('non-draft and stale heads are not routed',async()=>{
  for(const pr of [{draft:false},{head:{sha:'b'.repeat(40),ref:'copilot/cards',repo:{id:1}}}]){
    const x=fixture({pr});assert.equal((await routeFailure(x.args)).state,'stale-or-unmatched');
  }
});
test('uncertain owner delivery reserves the attempt but never reports pickup',async()=>{
  const x=fixture();
  x.args.delivery.post=async()=>({ok:false,reason:'owner-delivery-post-http-403'});
  assert.deepEqual(await routeFailure(x.args),
    {state:'delivery-uncertain',pr:85,reason:'owner-delivery-post-http-403'});
  assert.equal(x.records.length,1);assert.equal(x.calls.length,0);
});
test('reconcile backfills observations only, even when admission would allow',async()=>{
  const x=fixture();const r=await reconcileFailures({github:x.github,context:x.context,now:Date.now()});
  assert.deepEqual(r,[{run:12,state:'observed-only'}]);assert.ok(!x.calls[0].body.includes('@copilot'));
  const again=fixture({comments:[bot(`${OBSERVED}\nworkflow-id:4\nhead-sha:${sha}`)]});
  assert.deepEqual(await reconcileFailures({github:again.github,context:again.context}),[{run:12,state:'already-observed'}]);
});

test('scheduled backfill upgrades an observation only when admitted',async()=>{
  const x=fixture({comments:[bot(`${OBSERVED}\nworkflow-id:4\nhead-sha:${sha}`,88)]});
  const r=await reconcileFailures({github:x.github,context:x.context,authorize:x.args.authorize,delivery:x.args.delivery});
  assert.deepEqual(r,[{run:12,state:'repair-requested'}]);
  assert.equal(x.calls.length,1);assert.equal(x.calls[0].comment_id,88);
  assert.equal(x.records.length,1);
});
test('scheduled backfill caps paid requests at one per tick',async()=>{
  const x=fixture({runs:[{id:12,workflow_id:4,name:'Arcana Cardkit',conclusion:'failure',
    event:'push',head_branch:'copilot/cards',head_sha:sha,head_repository:{id:1}},
    {id:13,workflow_id:5,name:'Arcana Cardkit',conclusion:'failure',
    event:'push',head_branch:'copilot/cards',head_sha:sha,head_repository:{id:1}}]});
  const r=await reconcileFailures({github:x.github,context:x.context,authorize:x.args.authorize,delivery:x.args.delivery});
  assert.deepEqual(r,[{run:12,state:'repair-requested'},{run:13,state:'observed-only'}]);
  assert.equal(x.records.length,1);
  assert.equal(x.calls.filter(c=>c.body.includes('@copilot')).length,1);
});
test('scheduled backfill respects capacity denial without consuming a repair',async()=>{
  const x=fixture({authorize:deny('capacity: 1 of 1 active')});
  const r=await reconcileFailures({github:x.github,context:x.context,authorize:x.args.authorize,delivery:x.args.delivery});
  assert.deepEqual(r,[{run:12,state:'observed-only'}]);
  assert.equal(x.records.length,0);
  assert.equal(x.calls.length,1);
  assert.match(x.calls[0].body,/capacity: 1 of 1 active/);
});

test('scheduled backfill skips stale heads without spending admission work',async()=>{
  const x=fixture({pr:{head:{ref:'copilot/cards',sha:'b'.repeat(40),repo:{id:1}}}});
  const r=await reconcileFailures({github:x.github,context:x.context,authorize:x.args.authorize,delivery:x.args.delivery});
  assert.deepEqual(r,[]);assert.equal(x.records.length,0);assert.equal(x.calls.length,0);
});
test('shared capacity denial short-circuits subsequent paid admissions',async()=>{
  const x=fixture({runs:[{id:12,workflow_id:4,name:'Arcana Cardkit',conclusion:'failure',
    event:'push',head_branch:'copilot/cards',head_sha:sha,head_repository:{id:1}},
    {id:13,workflow_id:5,name:'Arcana Development CI',conclusion:'failure',
    event:'push',head_branch:'copilot/cards',head_sha:sha,head_repository:{id:1}}]});
  let admissions=0;
  const authorize=async()=>{admissions++;return {allowed:false,reason:'capacity: 1 of 1 active'};};
  const r=await reconcileFailures({github:x.github,context:x.context,authorize,delivery:x.args.delivery});
  assert.equal(admissions,1);assert.equal(r.length,2);
  assert.ok(r.every(x=>x.state==='observed-only'));assert.equal(x.records.length,0);
});

// createAuthorizer against a fake GitHub with one finished unit (#67) and its PR.
function unitsGithub({assigned=true,sessions=true,ledger=[]}={}){
  const comments=[];
  const now=Date.now();
  const ev=sessions?[{event:'copilot_work_started',created_at:new Date(now-3600e3).toISOString()},
    {event:'copilot_work_finished',created_at:new Date(now-1800e3).toISOString()}]:[];
  const data={issues:[{number:67,title:'[Arcana Queue] Eldrazi Unbound',labels:[],assignees:assigned?[{login:'Copilot'}]:[],author_association:'OWNER'}],
    prs:[{number:85,body:'Unit: #67'}],comments:ledger,timeline:ev};
  return {comments,github:{rest:{issues:{listForRepo:'issues',listComments:'comments',listEventsForTimeline:'timeline',
    createComment:async x=>comments.push(x)},pulls:{list:'prs'}},paginate:async x=>data[x]}};
}
const env={ARCANA_AUTOMATION_READY:'true',ARCANA_REPAIR_MODE:'request',ARCANA_DISPATCH_MODE:'off'};
// The worker PR as GitHub reports it: opened by Copilot.
const COPILOT_PR={number:85,body:'Unit: #67',author_association:'NONE',user:{login:'Copilot',type:'Bot'}};
const readBudget=async()=>({snapshot:{used:300,allowance:1500,measuredAt:new Date().toISOString()}});
test('authorizer: repairs need the master switch and request mode',async()=>{
  const g=unitsGithub();
  for(const e of [{...env,ARCANA_AUTOMATION_READY:''},{...env,ARCANA_REPAIR_MODE:'observe'},{...env,ARCANA_REPAIR_MODE:'off'}]){
    const d=await createAuthorizer({github:g.github,owner:'o',repo:'r',env:e,readBudget})({pr:{body:'Unit: #67'},run:{id:1,head_sha:sha}});
    assert.equal(d.allowed,false);
  }
});
test('authorizer: admitted repair writes a repair-request ledger record on the unit',async()=>{
  const g=unitsGithub();
  const d=await createAuthorizer({github:g.github,owner:'o',repo:'r',env,readBudget})({pr:COPILOT_PR,run:{id:9,head_sha:sha}});
  assert.equal(d.allowed,true,d.reason);assert.equal(d.attempt,1);
  await d.record();assert.equal(g.comments[0].issue_number,67);assert.match(g.comments[0].body,/op:repair-request/);
});
test('authorizer: unlinked PRs and exhausted ceilings are refused',async()=>{
  const g=unitsGithub();
  assert.equal((await createAuthorizer({github:g.github,owner:'o',repo:'r',env,readBudget})({pr:{body:'no unit'},run:{id:1,head_sha:sha}})).allowed,false);
  const old=new Date(Date.now()-7200e3).toISOString();
  const two=[1,2].map(n=>bot(A.formatLedger({op:'repair-request',issue:67,run:n,at:old})));
  const h=unitsGithub({ledger:two});
  const d=await createAuthorizer({github:h.github,owner:'o',repo:'r',env,readBudget})({pr:{body:'Unit: #67'},run:{id:3,head_sha:sha}});
  assert.equal(d.allowed,false);
});
