'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {selectCandidate,skipReason,verdictOf}=require('./dispatch-runner.cjs');
const ONE_SHOT='arcana:authorize-once';
const unit=(n,labels=[])=>({
  raw:{number:n,title:'[Arcana Queue] ' + n,body:'valid unit',labels},
  derived:{state:'queued',provenance:{ok:true}},prs:[]});
const feasible=()=>({ok:true});
test('normal mode prioritizes the specifically labeled card, not an unrelated older queue unit',()=>{
  const old=unit(100),picked=unit(379,[ONE_SHOT]);
  const r=selectCandidate([old,picked],'normal','gpt/arcana-integration-next',feasible,()=>({ok:true}));
  assert.equal(r.candidate.raw.number,379);
  assert.equal(r.error,null);
});
test('normal mode retains ordinary automatic queue when no specific owner authorization exists',()=>{
  const r=selectCandidate([unit(300),unit(250)],'normal','base',feasible,()=>({ok:true}));
  assert.equal(r.candidate.raw.number,250);
});
test('normal mode queues multiple explicit approvals, one at a time, ahead of ordinary work',()=>{
  const older=unit(100),shield=unit(509,[ONE_SHOT]),surge=unit(510,[ONE_SHOT]);
  const r=selectCandidate([surge,older,shield],'normal','base',feasible,()=>({ok:true}));
  assert.equal(r.error,null);
  assert.equal(r.candidate.raw.number,509,'lowest approved issue number wins regardless of API order');
  assert.deepEqual(r.labeled.map(u=>u.raw.number).sort((a,b)=>a-b),[509,510],'both authorizations remain represented');
  const next=selectCandidate([older,surge,unit(509)],'normal','base',feasible,()=>({ok:true}));
  assert.equal(next.candidate.raw.number,510,'second approval wins after first is consumed');
  assert.ok(next.labeled.includes(surge),'remaining owner approval is not consumed early');
});
test('multiple labels remain forbidden in strict authorize-once mode',()=>{
  const r=selectCandidate([unit(100,[ONE_SHOT]),unit(200,[ONE_SHOT])],'authorize-once','base',feasible,()=>({ok:true}));
  assert.equal(r.candidate,null);assert.equal(r.error,'authorize-once-needs-exactly-one-label');
});
test('a blocked approved card keeps its label while the next eligible approved card runs',()=>{
  const blocked=unit(509,[ONE_SHOT]),ready=unit(510,[ONE_SHOT]);
  const verdict=u=>u.raw.number===509?blockedFeas():feasible();
  const r=selectCandidate([unit(100),blocked,ready],'normal','base',verdict,()=>({ok:true}));
  assert.equal(r.candidate.raw.number,510);
  assert.equal(r.clear,undefined);
  assert.ok(r.labeled.includes(blocked),'the blocked approval is preserved');
});
test('multiple authorizations withdraw only a permanently invalid approval without spending',()=>{
  const bad=unit(509,[ONE_SHOT]),ready=unit(510,[ONE_SHOT]);
  const r=selectCandidate([ready,bad],'normal','base',feasible,u=>u.raw.number===509?{ok:false,missing:['cards-or-discovery']}:{ok:true});
  assert.equal(r.candidate,null,'withdrawal never launches a worker in the same run');
  assert.equal(r.clear.unit.raw.number,509);
  const next=selectCandidate([ready],'normal','base',feasible,()=>({ok:true}));
  assert.equal(next.candidate.raw.number,510,'the other approved card remains available');
});
test('inconclusive approved card denies instead of silently falling back to unapproved work',()=>{
  const flaky=unit(509,[ONE_SHOT]);
  const r=selectCandidate([unit(100),flaky],'normal','base',()=>({ok:false,code:'stale',reason:'feasibility stale'}),()=>({ok:true}));
  assert.equal(r.candidate.raw.number,509,'admission must deny pending exact checks');
  assert.equal(r.clear,undefined,'do not lose the approval');
});
test('one-shot mode demands an exact label and never falls back',()=>{
  const r=selectCandidate([unit(100)],'authorize-once','base',feasible,()=>({ok:true}));
  assert.equal(r.candidate,null);assert.equal(r.error,'authorize-once-needs-exactly-one-label');
});
test('incomplete labeled card is not replaced with a different lower-numbered unit in the same run',()=>{
  const r=selectCandidate([unit(50),unit(379,[ONE_SHOT])],'normal','base',feasible,()=>({ok:false,missing:['base-missing']}));
  assert.equal(r.candidate,null);
  assert.equal(r.clear.unit.raw.number,379);assert.match(r.clear.reason,/^not-actionable: base-missing/);
  assert.equal(r.error,'authorized-unit-not-eligible');
});
const blockedFeas=()=>({ok:false,code:'not-ready',reason:'not Copilot-ready: X=NEEDS_REVIEW'});
test('a labeled card that is only temporarily blocked keeps its approval and does not freeze the queue',()=>{
  const blocked={...unit(379,[ONE_SHOT])};
  const feas=u=>u.raw.number===379?blockedFeas():{ok:true,code:'ok'};
  const r=selectCandidate([unit(50),blocked],'normal','base',feas,()=>({ok:true}));
  assert.equal(r.clear,undefined,'approval kept');assert.equal(r.candidate.raw.number,50,'ordinary order continues');
  assert.ok(!r.labeled.includes(r.candidate),'the ordinary unit is not treated as the approved one');
  const once=selectCandidate([blocked],'authorize-once','base',feas,()=>({ok:true}));
  assert.equal(once.clear,undefined);assert.equal(once.candidate.raw.number,379,'authorize-once: deny later, keep label');
});
test('transient Cardkit checks and unknown codes keep the approval and its priority',()=>{
  for (const f of [{ok:false,code:'unavailable',reason:'feasibility-unavailable'},{ok:false,code:'stale',reason:'feasibility-stale'}]) {
    const r=selectCandidate([unit(379,[ONE_SHOT])],'normal','base',()=>f,()=>({ok:true}));
    assert.equal(r.clear,undefined,f.code);assert.equal(r.candidate.raw.number,379,f.code);
  }
  assert.equal(verdictOf(unit(1),'base',()=>({ok:false,reason:'?'}),()=>({ok:true})).cls,'blocked');
});
test('a labeled card already in an open PR or with no cards is withdrawn; provenance keeps deny-and-keep-label',()=>{
  const pr={...unit(379,[ONE_SHOT]),prs:[{number:500}]};
  const r=selectCandidate([pr],'authorize-once','base',feasible,()=>({ok:true}));
  assert.match(r.clear.reason,/^has-open-pr #500/);assert.equal(r.candidate,null);
  const none=selectCandidate([unit(379,[ONE_SHOT])],'normal','base',()=>({ok:false,code:'no-cards',reason:'no named cards'}),()=>({ok:true}));
  assert.equal(none.clear.unit.raw.number,379);assert.equal(none.candidate,null);
  const untrusted={...unit(379,[ONE_SHOT]),derived:{state:'queued',provenance:{ok:false,reasons:['edited-after-approval']}}};
  const p=selectCandidate([untrusted],'authorize-once','base',feasible,()=>({ok:true}));
  assert.equal(p.clear,undefined);assert.equal(p.candidate.raw.number,379);
});
test('every queued unit gets the dispatcher verdict, not only when nothing was selected',()=>{
  assert.equal(skipReason(unit(1),'base',feasible,()=>({ok:true})),null);
  assert.match(skipReason(unit(2),'base',feasible,()=>({ok:false,missing:['cards-or-discovery','base-missing']})),/^not-actionable: cards-or-discovery,base-missing/);
});
