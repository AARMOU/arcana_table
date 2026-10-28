'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {selectCandidate}=require('./dispatch-runner.cjs');
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
test('incomplete labeled card is not replaced with a different lower-numbered unit',()=>{
  const r=selectCandidate([unit(50),unit(379,[ONE_SHOT])],'normal','base',()=>({ok:false}),()=>({ok:false}));
  assert.equal(r.candidate.raw.number,379);
});
