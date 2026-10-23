'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');
const {planDispatch}=require('./worker-dispatch.cjs');
const q=n=>({number:n,state:'open',title:'[Arcana Queue] deck '+n,assignees:[]});
const options={ready:true,ciReady:true,budget:{level:'normal',maxNewAssignments:2},issues:[q(66),q(67),q(68)]};
test('default and unverified CI readiness block assignments',()=>{
 assert.deepEqual(planDispatch({budget:options.budget}).issues,[]);
 assert.deepEqual(planDispatch({...options,ciReady:false}).issues,[]);
});
test('normal, slow, paused and unknown budgets bound new claims',()=>{
 assert.deepEqual(planDispatch(options).issues,[66,67]);
 assert.deepEqual(planDispatch({...options,budget:{level:'slow',maxNewAssignments:1}}).issues,[66]);
 assert.deepEqual(planDispatch({...options,budget:{level:'unknown',maxNewAssignments:0}}).issues,[]);
 assert.deepEqual(planDispatch({...options,budget:{level:'paused',maxNewAssignments:0}}).issues,[]);
});
test('test_issue bypasses neither budget nor existing claim capacity',()=>{
 assert.deepEqual(planDispatch({...options,testIssue:'68'}).issues,[68]);
 const issues=[...options.issues,...[1,2].map(n=>({...q(n),assignees:[{login:'Copilot'}]}))];
 assert.deepEqual(planDispatch({...options,issues,testIssue:'68'}).issues,[]);
 assert.deepEqual(planDispatch({...options,testIssue:'68',budget:{level:'unknown',maxNewAssignments:0}}).issues,[]);
});
test('existing issue and PR ownership prevent duplicate work',()=>{
 const issues=[{...q(66),assignees:[{login:'AARMOU'}]},q(67),q(68)];
 assert.deepEqual(planDispatch({...options,issues,prs:[{state:'open',body:'Closes #67'}]}).issues,[68]);
});
test('capacity is conservative and malformed overrides fail closed',()=>{
 const issues=[...options.issues,{...q(1),assignees:[{login:'copilot-swe-agent[bot]'}]}];
 assert.deepEqual(planDispatch({...options,issues}).issues,[66]);
 for(const maxClaims of [0,3,NaN,'2'])assert.deepEqual(planDispatch({...options,maxClaims}).issues,[]);
 assert.deepEqual(planDispatch({...options,testIssue:'68;anything'}).issues,[]);
});
