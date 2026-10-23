'use strict';
const {budgetFromEnv}=require('./usage-gate.cjs');
const ACCOUNT='AARMOU', API='https://api.github.com';
const MARKER='<!-- arcana-billing-alert-v1 -->';
function positiveBudget(raw){
 if(!/^[1-9]\d*$/.test(String(raw||'')) || !Number.isSafeInteger(Number(raw))) throw Error('invalid-owner-budget');
 return Number(raw);
}
function reserve(budget,raw){
 if(raw===undefined||raw===null||raw==='')return Math.ceil(budget*0.1);
 if(!/^(0|[1-9]\d*)$/.test(String(raw))||!Number.isSafeInteger(Number(raw)))throw Error('invalid-reservation');
 return Number(raw);
}
function usageQuantity(report,now){
 const period={year:now.getUTCFullYear(),month:now.getUTCMonth()+1};
 if(!report||!Array.isArray(report.usageItems))throw Error('invalid-billing-response');
 if(report.user && String(report.user).toLowerCase()!==ACCOUNT.toLowerCase())throw Error('wrong-account');
 if(report.timePeriod&&(report.timePeriod.year!==period.year||
 (report.timePeriod.month!==undefined&&report.timePeriod.month!==period.month)))throw Error('wrong-period');
 let sum=0,found=0;
 for(const x of report.usageItems){
  if(!/copilot/i.test(String(x.product||''))||!/^(AI Credit|AI Credits|Copilot AI Credits)$/i.test(String(x.sku||'')))continue;
  found++;
  if(x.unitType!=='ai-credits'||!Number.isFinite(x.grossQuantity)||x.grossQuantity<0)throw Error('wrong-unit-or-quantity');
  sum+=x.grossQuantity;
  if(!Number.isFinite(sum))throw Error('overflow');
 }
 // Missing SKU might indicate organization billing, not zero personal usage.
 if(!found)throw Error(report.usageItems.length===0?'empty-billing-usage-items':
  report.usageItems.some(x=>/copilot/i.test(String(x.product||'')))?'unrecognized-copilot-sku':'no-copilot-usage-items');
 return sum;
}
async function collect({token,budget:raw,reserveCredits,now=new Date(),fetchImpl=fetch}){
 const allowance=positiveBudget(raw),pendingReserve=reserve(allowance,reserveCredits);
 if(!token)throw Error('missing-token');
 const period=now.toISOString().slice(0,7);
 const url=API+'/users/'+ACCOUNT+'/settings/billing/ai_credit/usage?year='+
 now.getUTCFullYear()+'&month='+(now.getUTCMonth()+1);
 const response=await fetchImpl(url,{headers:{
  Authorization:'Bearer '+token,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2026-03-10'
 },signal:AbortSignal.timeout(12000)});
 if(!response.ok)throw Error('billing-http-'+response.status);
 const measured=usageQuantity(await response.json(),now);
 const snapshot={account:ACCOUNT,metric:'ai_credits',source:'github-billing-api',
 sourceUrl:API+'/users/'+ACCOUNT+'/settings/billing/ai_credit/usage',
 period,allowanceSource:'owner-configured-budget',used:measured+pendingReserve,
 allowance,measuredAt:now.toISOString(),reportedUsed:measured,reserved:pendingReserve};
 const status=budgetFromEnv(JSON.stringify(snapshot),now.getTime());
 if(status.level==='unknown')throw Error('unverified-snapshot');
 return {snapshot,status};
}
async function readBudget(env=process.env,opts={}){
 try{
  const x=await collect({token:env.COPILOT_BILLING_READ_TOKEN,
   budget:env.ARCANA_AI_CREDIT_BUDGET,
   reserveCredits:env.ARCANA_AI_CREDIT_RESERVE,...opts});
  return {...x,error:null};
 }catch(e){
  // Never include HTTP response bodies, request headers or tokens in public logs.
  const reason=/^(invalid-owner-budget|invalid-reservation|missing-token|billing-http-\d{3}|invalid-billing-response|wrong-account|wrong-period|wrong-unit-or-quantity|overflow|empty-billing-usage-items|unrecognized-copilot-sku|no-copilot-usage-items|unverified-snapshot)$/.test(e.message)?e.message:'billing-unavailable';
  return {snapshot:null,status:{level:'unknown',maxNewAssignments:0,warn:true,reason},error:reason};
 }
}
const messages={normal:'Usage returned below the warning threshold',warn:'Copilot AI credit usage at warning level (50%)',
 slow:'Copilot AI credit usage at throttling level (75%)',paused:'Copilot AI credit usage at pause level (90%)',
 unknown:'Copilot billing data unavailable; new assignments blocked'};
async function alertChange({github,level,period,reason}){
 if(!Object.hasOwn(messages,level)||! /^\d{4}-\d{2}$/.test(period))throw Error('invalid-alert');
 const owner='AARMOU',repo='arcana_table',issue_number=78;
 const comments=await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number,per_page:100});
 const previous=[...comments].reverse().find(c=>c.user?.login==='github-actions[bot]'&&c.user?.type==='Bot'&&c.body?.startsWith(MARKER));
 if(previous?.body?.includes('period:'+period)&&previous?.body?.includes('level:'+level))return 'unchanged';
 if(!previous&&level==='normal')return 'healthy-initial';
 const body=[MARKER,'period:'+period,'level:'+level,
  '@AARMOU **Arcana Table usage alert:** '+messages[level]+'.',
  'The billing API can lag. The existing readiness gate is independent; no automatic worker launch is authorized by this comment.',
  level==='unknown'?'Please check the read-only billing token and owner-configured AI credit allowance.':'The worker budget gate uses a conservative buffer.',
  'Details: https://github.com/AARMOU/arcana_table/issues/81'].join('\n\n');
 await github.rest.issues.createComment({owner,repo,issue_number,body});
 return 'posted';
}
module.exports={positiveBudget,reserve,usageQuantity,collect,readBudget,alertChange,MARKER};
