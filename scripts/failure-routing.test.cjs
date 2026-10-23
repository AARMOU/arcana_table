'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {routeFailure,MARKER,OBSERVED}=require('./failure-routing.cjs');
const sha='a'.repeat(40);
function fixture({comments=[],run={},pr={},budget={level:'normal',maxNewAssignments:2},status}={}) {
  const calls=[];
  const current={number:59,draft:true,state:'open',head:{ref:'copilot/cards',sha,repo:{id:1}},...pr};
  const api={pulls:{list:'prs',get:async()=>({data:current})},
    issues:{listComments:'comments',createComment:async x=>{if(status)throw {status};calls.push(x);},
      updateComment:async x=>calls.push(x)},actions:{listJobsForWorkflowRun:'jobs'}};
  const data={prs:[current],comments,jobs:[{id:123,name:'@someone cards',conclusion:'failure',
    steps:[{name:'Scenarios',conclusion:'failure'}]}]};
  return {calls,current,args:{budget,github:{rest:api,paginate:async x=>data[x]},context:{
    repo:{owner:'AARMOU',repo:'arcana_table'},payload:{repository:{id:1},workflow_run:{id:12,
      workflow_id:4,name:'Argentum six-precon isolated worker regression',conclusion:'failure',
      event:'push',head_branch:'copilot/cards',head_sha:sha,head_repository:{id:1},...run}}}}};
}
const bot=(body,id=1)=>({id,body,user:{type:'Bot',login:'github-actions[bot]'}});
test('authorized failure includes exact SHA/run/job and one controlled mention',async()=>{
  const x=fixture();assert.equal((await routeFailure(x.args)).state,'repair-requested');
  assert.equal(x.calls.length,1);const b=x.calls[0].body;
  assert.match(b,/run-id:12/);assert.ok(b.includes(sha));assert.match(b,/job 123/);
  assert.equal(b.match(/@/g).length,1);
});
test('unknown budget records observation without an agent request',async()=>{
  const x=fixture({budget:{level:'unknown',maxNewAssignments:0}});
  assert.equal((await routeFailure(x.args)).state,'observed-only');
  assert.ok(x.calls[0].body.startsWith(OBSERVED));assert.ok(!x.calls[0].body.includes('@copilot'));
});
test('same SHA/workflow is deduplicated across different run IDs',async()=>{
  const x=fixture({comments:[bot(`${MARKER}\nrun-id:10\nworkflow-id:4\nhead-sha:${sha}`)]});
  assert.equal((await routeFailure(x.args)).state,'already-routed');assert.equal(x.calls.length,0);
});
test('run-id 12 does not collide with run-id 123',async()=>{
  const x=fixture({comments:[bot(`${MARKER}\nrun-id:123\nworkflow-id:3\nhead-sha:${sha}`)]});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
});
test('observed failure can upgrade in place when authorized',async()=>{
  const x=fixture({comments:[bot(`${OBSERVED}\nworkflow-id:4\nhead-sha:${sha}`,88)]});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
  assert.equal(x.calls[0].comment_id,88);
});
test('duplicate withheld observations do not spam',async()=>{
  const x=fixture({budget:{level:'unknown',maxNewAssignments:0},
    comments:[bot(`${OBSERVED}\nworkflow-id:4\nhead-sha:${sha}`)]});
  assert.equal((await routeFailure(x.args)).state,'already-observed');assert.equal(x.calls.length,0);
});
test('untrusted user marker cannot consume repair attempts',async()=>{
  const x=fixture({comments:[{body:`${MARKER}\nrun-id:12`,user:{type:'User',login:'other'}}]});
  assert.equal((await routeFailure(x.args)).state,'repair-requested');
});
test('retry cap is three across prior router versions',async()=>{
  const x=fixture({comments:[1,2,3].map(n=>bot(`<!-- arcana-ci-repair-router -->\nrun-id:${n}`))});
  assert.equal((await routeFailure(x.args)).state,'observed-only');assert.ok(!x.calls[0].body.includes('@copilot'));
});
test('75 percent budget permits only first repair',async()=>{
  const x=fixture({budget:{level:'slow',maxNewAssignments:1},comments:[bot(`${MARKER}\nrun-id:1`)]});
  assert.equal((await routeFailure(x.args)).state,'observed-only');
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
test('permission 403 is reported precisely without duplicate retry',async()=>{
  const x=fixture({status:403});assert.deepEqual(await routeFailure(x.args),
    {state:'permission-blocked',required:'pull-requests: write',pr:59});assert.equal(x.calls.length,0);
});
