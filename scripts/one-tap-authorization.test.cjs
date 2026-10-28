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
test('multiple labels fail closed instead of silently dispatching a different card',()=>{
  const r=selectCandidate([unit(100,[ONE_SHOT]),unit(200,[ONE_SHOT])],'normal','base',feasible,()=>({ok:true}));
  assert.equal(r.candidate,null);assert.equal(r.error,'multiple-owner-authorizations');
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
