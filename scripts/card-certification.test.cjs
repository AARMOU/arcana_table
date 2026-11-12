'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const C=require('./card-certification.cjs');
const SHA='a'.repeat(40), OLD='b'.repeat(40);
const pr=(more={})=>({number:353,title:'Implement Blood for the Blood God!',body:'Fixes #352',
  user:{login:'copilot-swe-agent[bot]'},head:{sha:SHA},base:{sha:'d'.repeat(40)},...more});
const unit=(more={})=>({number:352,state:'open',title:'[Arcana Queue] Blood for the Blood God!',...more});
const check=(name,conclusion='success',sha=SHA,status='completed')=>({name,head_sha:sha,status,conclusion});
const green=()=>[check('Arcana Cardkit / toolkit'),check('Arcana Development CI / cards-and-android')];
const proof=(more={})=>({source:'independent-oracle-runner',provenanceVerified:true,oracleReviewed:true,
  headSha:SHA,issue:352,integrationBaseSha:'d'.repeat(40),testHeadSha:'e'.repeat(40),
  testRunHeadSha:'e'.repeat(40),cardImplementationAncestorVerified:true,testOnlyDeltaVerified:true,
  runConclusion:'success',positiveCases:3,negativeCases:2,
  noSkippedCases:true,noUnresolvedRulesGaps:true,
  runUrl:'https://github.com/AARMOU/arcana_table/actions/runs/40735018029',...more});

test('green cardkit and Android checks alone NEVER certify gameplay',()=>{
  const x=C.assess({pr:pr(),unit:unit(),checks:green()});
  assert.equal(x.state,'independent-proof-required');assert.equal(x.certified,false);
});
test('required check cannot be borrowed from a different commit',()=>{
  const x=C.assess({pr:pr(),unit:unit(),checks:green().map(c=>({...c,head_sha:OLD}))});
  assert.equal(x.state,'checks-incomplete');assert.equal(x.certified,false);
});
test('incomplete, running, failed and skipped checks block certification',()=>{
  assert.equal(C.assess({pr:pr(),unit:unit(),checks:[green()[0]]}).state,'checks-incomplete');
  assert.equal(C.assess({pr:pr(),unit:unit(),checks:[green()[0],check('cards-and-android',null,SHA,'in_progress')]}).state,'checks-running');
  for(const bad of ['failure','skipped','neutral','cancelled','timed_out']){
    assert.equal(C.assess({pr:pr(),unit:unit(),checks:[green()[0],check('cards-and-android',bad)]}).state,'checks-failed');
  }
});
test('invalid origin, incomplete head, or wrong queue unit blocks',()=>{
  assert.equal(C.assess({pr:pr({user:{login:'untrusted'}}),unit:unit(),checks:green()}).state,'blocked');
  assert.equal(C.assess({pr:pr({head:{sha:'abc'}}),unit:unit(),checks:green()}).state,'blocked');
  assert.equal(C.assess({pr:pr(),unit:unit({number:999}),checks:green()}).state,'blocked');
  assert.equal(C.assess({pr:pr(),unit:unit({state:'closed'}),checks:green()}).state,'blocked');
});
test('independent proof is exact-SHA, real-run, positive+negative, no skipped or open gaps',()=>{
  const changes=[
    {headSha:OLD},{issue:777},{integrationBaseSha:OLD},{testHeadSha:'bad'},
    {testRunHeadSha:OLD},{cardImplementationAncestorVerified:false},{testOnlyDeltaVerified:false},
    {source:'copilot-claim'},{provenanceVerified:false},
    {oracleReviewed:false},{runConclusion:'failure'},{positiveCases:0},{negativeCases:0},
    {noSkippedCases:false},{noUnresolvedRulesGaps:false},{runUrl:'https://evil.example/123'},
  ];
  for(const bad of changes){
    const x=C.assess({pr:pr(),unit:unit(),checks:green(),proof:proof(bad)});
    assert.equal(x.state,'independent-proof-required',JSON.stringify(bad));
  }
  assert.equal(C.assess({pr:pr(),unit:unit(),checks:green(),proof:proof()}).state,'certified-candidate');
});
test('new commit revokes previous independent proof',()=>{
  const next='c'.repeat(40);
  const changed=pr({head:{sha:next}});
  const nextChecks=green().map(c=>({...c,head_sha:next}));
  const x=C.assess({pr:changed,unit:unit(),checks:nextChecks,proof:proof()});
  assert.equal(x.state,'independent-proof-required');
});
test('report is fail closed, sorted, and explicitly separates CI and certification',()=>{
  const a=C.assess({pr:pr(),unit:unit(),checks:green()});
  const b=C.assess({pr:pr({number:360}),unit:unit(),checks:[green()[0],check('cards-and-android','failure')]});
  const x=C.snapshot([a,b],new Date('2026-10-28T18:00:00Z'));
  assert.deepEqual(x.items.map(i=>i.pr),[360,353]);
  assert.equal(x.certifiedCandidates,0);assert.equal(x.needsIndependentVerification,2);
  assert.match(C.bodyOf(x),/Independent Oracle certification preflight/);
});
test('trusted-main collector only checks Copilot PRs linked to an open Arcana Queue issue',async()=>{
  const calls=[];
  const github={
    paginate:async (fn,args)=>{calls.push('paginate');return [pr(),pr({number:360,user:{login:'AARMOU'}}),pr({number:999,body:'no unit'})];},
    rest:{pulls:{list:function list(){}},issues:{get:async()=>({data:unit()})},
      checks:{listForRef:async()=>({data:{check_runs:green()}})}}};
  const p=await C.collect({github,owner:'AARMOU',repo:'arcana_table',now:new Date('2026-10-28T18:00:00Z')});
  assert.equal(p.items.length,1);assert.equal(p.items[0].pr,353);
  assert.equal(p.items[0].state,'independent-proof-required');assert.equal(p.certifiedCandidates,0);
});
test('status only edits bot marker; never issues instructions to approve or merge',async()=>{
  const calls=[];
  const existing={id:42,user:{login:'github-actions[bot]',type:'Bot'},body:C.MARKER+'\nold'};
  const github={paginate:async()=>[existing,{id:12,user:{login:'stranger'},body:C.MARKER}],
    rest:{issues:{listComments(){},updateComment:async args=>calls.push(['update',args]),createComment:async args=>calls.push(['create',args])}}};
  const payload=C.snapshot([]);
  assert.equal(await C.publish({github,owner:'AARMOU',repo:'arcana_table',payload}),'updated');
  assert.deepEqual(calls.map(c=>c[0]),['update']);assert.equal(calls[0][1].comment_id,42);
  assert.match(calls[0][1].body,/No owner approval, merge, APK, signing or release/);
});
