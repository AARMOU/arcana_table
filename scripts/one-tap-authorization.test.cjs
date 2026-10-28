'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {selectCandidate,skipReason,permanentRefusal}=require('./dispatch-runner.cjs');
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
test('a labeled card Cardkit refuses is withdrawn; a stale/unavailable Cardkit check keeps the approval',()=>{
  const refused=selectCandidate([unit(379,[ONE_SHOT])],'normal','base',()=>({ok:false,reason:'not Copilot-ready: X=NEEDS_REVIEW'}),()=>({ok:true}));
  assert.equal(refused.clear.unit.raw.number,379);
  for (const reason of ['feasibility-unavailable (Cardkit verdicts missing)','feasibility-stale (classified a, base is b)']) {
    const r=selectCandidate([unit(379,[ONE_SHOT])],'normal','base',()=>({ok:false,reason}),()=>({ok:true}));
    assert.equal(r.clear,undefined,reason);assert.equal(r.candidate.raw.number,379,reason);
  }
});
test('a labeled card already in an open PR is withdrawn; provenance problems keep deny-and-keep-label',()=>{
  const pr={...unit(379,[ONE_SHOT]),prs:[{number:500}]};
  assert.match(selectCandidate([pr],'authorize-once','base',feasible,()=>({ok:true})).clear.reason,/^has-open-pr #500/);
  const untrusted={...unit(379,[ONE_SHOT]),derived:{state:'queued',provenance:{ok:false,reasons:['edited-after-approval']}}};
  const r=selectCandidate([untrusted],'authorize-once','base',feasible,()=>({ok:true}));
  assert.equal(r.clear,undefined);assert.equal(r.candidate.raw.number,379);
});
test('transient denials (capacity, budget, CI) are never classified as permanent',()=>{
  for (const reason of ['capacity: 1 of 1 active','budget: stop','ci-not-ready','untrusted-origin: comments-unavailable',null])
    assert.equal(permanentRefusal(reason),false,String(reason));
});
test('every queued unit gets the dispatcher verdict, not only when nothing was selected',()=>{
  assert.equal(skipReason(unit(1),'base',feasible,()=>({ok:true})),null);
  assert.match(skipReason(unit(2),'base',feasible,()=>({ok:false,missing:['cards-or-discovery','base-missing']})),/^not-actionable: cards-or-discovery,base-missing/);
});
