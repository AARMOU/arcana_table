'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const m=require('./billing-monitor.cjs'),now=new Date('2026-10-23T18:00:00Z');
const item=q=>({product:'Copilot',sku:'Copilot Premium Request',unitType:'requests',grossQuantity:q});
const report=items=>({user:'AARMOU',timePeriod:{year:2026,month:10},usageItems:items});
const fetchImpl=async()=>({ok:true,json:async()=>report([item(670),{product:'Actions',sku:'Actions',grossQuantity:5}])});
test('verified monthly premium usage plus safety reserve controls throttling',async()=>{
 const x=await m.collect({token:'dummy',budget:'1000',now,fetchImpl});
 assert.equal(x.snapshot.reportedUsed,670);assert.equal(x.snapshot.reserved,100);
 assert.equal(x.status.level,'slow');
});
test('explicit reserve can be customized',async()=>{
 const x=await m.collect({token:'dummy',budget:'1000',reserveRequests:'0',now,fetchImpl});
 assert.equal(x.status.level,'warn');
});
test('reject credits, wrong account, stale period, invalid quantities',()=>{
 assert.throws(()=>m.usageQuantity(report([{...item(5),unitType:'ai-credits'}]),now));
 assert.throws(()=>m.usageQuantity(report([{product:'Copilot',sku:'AI Credits',grossQuantity:2}]),now));
 assert.throws(()=>m.usageQuantity({...report([item(1)]),user:'other'},now));
 assert.throws(()=>m.usageQuantity({...report([item(1)]),timePeriod:{year:2026,month:9}},now));
 assert.throws(()=>m.usageQuantity(report([item(-1)]),now));
});
test('missing access/budget or HTTP403 fail closed',async()=>{
 for(const e of [{},{COPILOT_BILLING_READ_TOKEN:'x',ARCANA_PREMIUM_REQUEST_BUDGET:'0'},
 {COPILOT_BILLING_READ_TOKEN:'x',ARCANA_PREMIUM_REQUEST_BUDGET:'1000'}]){
  const x=await m.readBudget(e,{now,fetchImpl:async()=>({ok:false,status:403})});
  assert.equal(x.status.level,'unknown');assert.equal(x.status.maxNewAssignments,0);
 }
});
test('collector output meets existing strict snapshot contract',async()=>{
 const x=await m.collect({token:'x',budget:'1000',now,fetchImpl});
 const {budgetFromEnv}=require('./usage-gate.cjs');
 assert.equal(budgetFromEnv(JSON.stringify(x.snapshot),now.getTime()).level,'slow');
 assert.equal(budgetFromEnv(JSON.stringify(x.snapshot),now.getTime()+3600001).level,'unknown');
});
test('alert only once per threshold and not on initially healthy reading',async()=>{
 const posted=[],fake=comments=>({paginate:async()=>comments,rest:{issues:{listComments(){},createComment:async args=>posted.push(args)}}});
 assert.equal(await m.alertChange({github:fake([]),level:'normal',period:'2026-10'}),'healthy-initial');
 assert.equal(await m.alertChange({github:fake([]),level:'slow',period:'2026-10'}),'posted');
 assert.equal(posted.length,1);
 assert.match(posted[0].body,/@AARMOU/);
 const prior=[{user:{login:'github-actions[bot]',type:'Bot'},body:posted[0].body}];
 assert.equal(await m.alertChange({github:fake(prior),level:'slow',period:'2026-10'}),'unchanged');
 assert.equal(posted.length,1);
});
