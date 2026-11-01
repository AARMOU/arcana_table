'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const m=require('./billing-monitor.cjs'),now=new Date('2026-10-23T18:00:00Z');
const item=q=>({product:'Copilot AI Credits',sku:'AI Credit',unitType:'ai-credits',grossQuantity:q});
const report=items=>({user:'AARMOU',timePeriod:{year:2026,month:10},usageItems:items});
const fetchImpl=async()=>({ok:true,json:async()=>report([item(670),{product:'Actions',sku:'Actions',grossQuantity:5}])});
test('verified monthly AI credit usage plus safety reserve controls throttling',async()=>{
 const x=await m.collect({token:'dummy',budget:'1000',now,fetchImpl});
 assert.equal(x.snapshot.reportedUsed,670);assert.equal(x.snapshot.reserved,100);
 assert.equal(x.status.level,'slow');
});
test('explicit reserve can be customized',async()=>{
 const x=await m.collect({token:'dummy',budget:'1000',reserveCredits:'0',now,fetchImpl});
 assert.equal(x.status.level,'warn');
});
test('reject requests, wrong account, stale period, invalid quantities',()=>{
 assert.throws(()=>m.usageQuantity(report([{...item(5),unitType:'requests'}]),now));
 assert.throws(()=>m.usageQuantity(report([{product:'Copilot',sku:'Copilot Premium Request',unitType:'requests',grossQuantity:2}]),now));
 assert.throws(()=>m.usageQuantity({...report([item(1)]),user:'other'},now));
 assert.throws(()=>m.usageQuantity({...report([item(1)]),timePeriod:{year:2026,month:9}},now));
 assert.throws(()=>m.usageQuantity(report([item(-1)]),now));
});
test('missing access/budget or HTTP403 fail closed',async()=>{
 for(const e of [{},{COPILOT_BILLING_READ_TOKEN:'x',ARCANA_AI_CREDIT_BUDGET:'0'},
 {COPILOT_BILLING_READ_TOKEN:'x',ARCANA_AI_CREDIT_BUDGET:'1000'}]){
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

test('current 471/1500 credits snapshot below warning with reserve',async()=>{
 const x=await m.collect({token:'dummy',budget:'1500',now,fetchImpl:async()=>({ok:true,json:async()=>report([item(471)])})});
 assert.equal(x.snapshot.reportedUsed,471);
 assert.equal(x.snapshot.reserved,150);
 assert.equal(x.snapshot.metric,'ai_credits');
 assert.equal(x.status.level,'normal');
});
test('no matching AI credit SKU fails closed',async()=>{
 const x=await m.readBudget({COPILOT_BILLING_READ_TOKEN:'x',ARCANA_AI_CREDIT_BUDGET:'1500'},
 {now,fetchImpl:async()=>({ok:true,json:async()=>report([])})});
 assert.equal(x.status.maxNewAssignments,0);
});

test('GitHub Copilot SKU variation and fractional credit quantities are accepted',()=>{
 const actual=report([
   {product:'Copilot',sku:'Copilot AI Credits',unitType:'ai-credits',grossQuantity:135.625},
   {product:'Copilot',sku:'Copilot AI Credits',unitType:'ai-credits',grossQuantity:100.125}
 ]);
 assert.equal(m.usageQuantity(actual,now),235.75);
});
test('real API decimal totals meet snapshot gate at requested budget',async()=>{
 const x=await m.collect({token:'dummy',budget:'1500',now,fetchImpl:async()=>({
  ok:true,json:async()=>report([{product:'Copilot',sku:'Copilot AI Credits',
   unitType:'ai-credits',grossQuantity:471.245}])
 })});
 assert.equal(x.snapshot.reportedUsed,471.245);
 assert.equal(x.status.level,'normal');
});
test('non numeric and wrong-unit credit quantities still fail closed',()=>{
 assert.throws(()=>m.usageQuantity(report([{product:'Copilot',sku:'Copilot AI Credits',
   unitType:'ai-credits',grossQuantity:'470'}]),now));
 assert.throws(()=>m.usageQuantity(report([{product:'Copilot',sku:'Copilot AI Credits',
   unitType:'requests',grossQuantity:470}]),now));
});

test('empty GitHub billing response provides safe, specific diagnostic',async()=>{
 const x=await m.readBudget({COPILOT_BILLING_READ_TOKEN:'dummy',ARCANA_AI_CREDIT_BUDGET:'1500'},
 {now,fetchImpl:async()=>({ok:true,json:async()=>report([])})});
 assert.equal(x.error,'empty-billing-usage-items');
 assert.equal(x.status.maxNewAssignments,0);
});
test('month rollover: an empty report counts as zero only early in the month and only if last month billed Copilot here',async()=>{
 const env={COPILOT_BILLING_READ_TOKEN:'dummy',ARCANA_AI_CREDIT_BUDGET:'1500'};
 const nov1=new Date('2026-11-01T00:05:00Z');
 const byMonth=prevItems=>async url=>({ok:true,json:async()=>/month=11\b/.test(url)
   ? {user:'AARMOU',timePeriod:{year:2026,month:11},usageItems:[]}
   : {user:'AARMOU',timePeriod:{year:2026,month:10},usageItems:prevItems}});
 const billed=[{product:'Copilot',sku:'Copilot AI Credits',unitType:'ai-credits',grossQuantity:2400}];
 const ok=await m.readBudget(env,{now:nov1,fetchImpl:byMonth(billed)});
 assert.equal(ok.error,null);
 assert.equal(ok.snapshot.reportedUsed,0);
 assert.match(ok.snapshot.basis,/new-month-empty-report/);
 assert.equal(ok.status.level,'normal');
 // Last month had no Copilot billed to this account: still fail closed.
 for(const prev of [[],[{product:'Actions',unitType:'minutes',grossQuantity:5}],[{product:'Copilot',unitType:'ai-credits',grossQuantity:0}]]){
  const x=await m.readBudget(env,{now:nov1,fetchImpl:byMonth(prev)});
  assert.equal(x.error,'empty-billing-usage-items');assert.equal(x.status.maxNewAssignments,0);
 }
 // Past the first days of the month: still fail closed even if last month was billed.
 const late=await m.readBudget(env,{now:new Date('2026-11-04T00:05:00Z'),fetchImpl:byMonth(billed)});
 assert.equal(late.error,'empty-billing-usage-items');
 // A failed previous-month read fails closed.
 const err=await m.readBudget(env,{now:nov1,retryDelayMs:0,fetchImpl:async url=>/month=11\b/.test(url)
   ? {ok:true,json:async()=>({user:'AARMOU',timePeriod:{year:2026,month:11},usageItems:[]})} : {ok:false,status:503}});
 assert.ok(err.error);assert.equal(err.status.maxNewAssignments,0);
});
test('new Copilot SKU is accepted only with verified AI-credit unit',async()=>{
 const x=await m.readBudget({COPILOT_BILLING_READ_TOKEN:'dummy',ARCANA_AI_CREDIT_BUDGET:'1500'},
 {now,fetchImpl:async()=>({ok:true,json:async()=>report([
  {product:'Copilot',sku:'Future SKU',unitType:'ai-credits',grossQuantity:471}
 ])})});
 assert.equal(x.error,null);
 assert.equal(x.snapshot.reportedUsed,471);
 assert.equal(x.status.level,'normal');
});
test('no Copilot item is distinguished from an empty report',async()=>{
 const x=await m.readBudget({COPILOT_BILLING_READ_TOKEN:'dummy',ARCANA_AI_CREDIT_BUDGET:'1500'},
 {now,fetchImpl:async()=>({ok:true,json:async()=>report([
  {product:'Actions',sku:'Actions Linux',unitType:'minutes',grossQuantity:471}
 ])})});
 assert.equal(x.error,'no-copilot-usage-items');
 assert.equal(x.status.maxNewAssignments,0);
});

test('new SKU with wrong units never authorizes spending',async()=>{
 const x=await m.readBudget({COPILOT_BILLING_READ_TOKEN:'dummy',ARCANA_AI_CREDIT_BUDGET:'1500'},
 {now,fetchImpl:async()=>({ok:true,json:async()=>report([
  {product:'Copilot',sku:'Future SKU',unitType:'requests',grossQuantity:471}
 ])})});
 assert.equal(x.error,'wrong-unit-or-quantity');
 assert.equal(x.status.maxNewAssignments,0);
});
test('mixed unrelated Actions usage excluded by Copilot product',()=>{
 assert.equal(m.usageQuantity(report([
  {product:'Actions',sku:'Compute',unitType:'ai-credits',grossQuantity:999999},
  {product:'Copilot',sku:'Unrecognized AI Credit SKU',unitType:'ai-credits',grossQuantity:471.4}
 ]),now),471.4);
});
