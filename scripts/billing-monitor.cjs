'use strict';
const {budgetFromEnv}=require('./usage-gate.cjs');
const ACCOUNT='AARMOU', API='https://api.github.com';
const MARKER='<!-- arcana-billing-alert-v1 -->';
const KNOWN_REASON=/^(invalid-owner-budget|invalid-reservation|missing-token|billing-http-\d{3}|billing-rate-limited|billing-timeout|billing-network-error|invalid-billing-response|wrong-account|wrong-period|wrong-unit-or-quantity|overflow|empty-billing-usage-items|unrecognized-copilot-sku|no-copilot-usage-items|unverified-snapshot)$/;
const SETUP='docs/BILLING-SETUP.md';
const EMPTY_MONTH_START_DAYS=3;
// Owner-facing next step for every fail-closed billing reason. Never includes secrets or response bodies.
function billingAction(reason,env={}){
 const legacy=env.ARCANA_PREMIUM_REQUEST_BUDGET!==undefined&&env.ARCANA_PREMIUM_REQUEST_BUDGET!==null&&env.ARCANA_PREMIUM_REQUEST_BUDGET!=='';
 const r=String(reason||'');
 if(r==='missing-token')return 'Owner: add the Actions secret COPILOT_BILLING_READ_TOKEN (step 1-2 of '+SETUP+').';
 if(r==='invalid-owner-budget')return 'Owner: set the Actions variable ARCANA_AI_CREDIT_BUDGET to the monthly included AI credits as a whole number (step 3 of '+SETUP+')'+
  (legacy?'. ARCANA_PREMIUM_REQUEST_BUDGET is a retired variable and is ignored.':'.');
 if(r==='invalid-reservation')return 'Owner: ARCANA_AI_CREDIT_RESERVE must be a whole number of credits, or unset for the 10% default.';
 if(r==='billing-http-401')return 'Owner: COPILOT_BILLING_READ_TOKEN is invalid or expired. Create a new token and replace the secret ('+SETUP+').';
 if(r==='billing-rate-limited')return 'None: GitHub is rate-limiting billing reads; new work waits until the next check succeeds.';
 if(r==='billing-http-403')return 'Owner: the token is not allowed to read AARMOU billing. Recreate it from the AARMOU account with the permission in '+SETUP+'.';
 if(r==='billing-http-404')return 'Owner: GitHub did not find personal AI-credit billing for AARMOU. Confirm the token belongs to AARMOU and Copilot is billed to that personal account.';
 if(/^billing-http-5\d\d$|^billing-timeout$|^billing-network-error$/.test(r))return 'None: GitHub billing was temporarily unavailable (retried once). The next scheduled check retries.';
 if(/^billing-http-4\d\d$/.test(r))return 'Owner: GitHub rejected the billing request ('+r+'). Check the token and '+SETUP+'.';
 if(r==='empty-billing-usage-items')return 'Wait: GitHub reports no AI-credit usage for this month, and either it is past the first '+EMPTY_MONTH_START_DAYS+' days of the month or last month showed no Copilot AI credits billed to AARMOU. Dispatch stays blocked until usage is reported; check where Copilot is billed.';
 if(r==='no-copilot-usage-items'||r==='unrecognized-copilot-sku')return 'Engineer: the billing report has no recognizable Copilot AI-credit rows. Inspect the report format before trusting any figure.';
 if(r==='wrong-account'||r==='wrong-period'||r==='wrong-unit-or-quantity'||r==='invalid-billing-response'||r==='overflow')
  return 'Engineer: the billing response failed validation ('+r+'). Do not trust or substitute figures; inspect the API contract.';
 if(r==='unverified-snapshot')return 'Engineer: the collected snapshot failed the freshness/provenance contract.';
 if(r)return 'Engineer: billing unavailable ('+r+'). Check the Billing Watch run.';
 return null;
}
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
  // AI credit API and account/period/unit are the authority; SKU labels can vary by model.
  if(!/^(copilot|copilot ai credits)$/i.test(String(x.product||'').trim()))continue;
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
// One retry for transient GitHub errors (5xx) and timeouts. 4xx answers are definitive.
const TRANSIENT=new Set([500,502,503,504]);
async function fetchReport({url,token,fetchImpl,retryDelayMs}){
 let lastError;
 for(let attempt=0;attempt<2;attempt++){
  if(attempt&&retryDelayMs)await new Promise(r=>setTimeout(r,retryDelayMs));
  let response;
  try{
   response=await fetchImpl(url,{headers:{
    Authorization:'Bearer '+token,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2026-03-10'
   },signal:AbortSignal.timeout(12000)});
  }catch(e){
   lastError=Error(e?.name==='TimeoutError'||e?.name==='AbortError'?'billing-timeout':'billing-network-error');
   continue;
  }
  if(response.ok)return response;
  // GitHub answers rate limiting with 403 or 429 plus x-ratelimit-remaining: 0 or retry-after; that is not a
  // permission problem, and admission still fails closed while it lasts.
  const h=k=>response.headers?.get?.(k);
  if((response.status===403||response.status===429)&&(h('x-ratelimit-remaining')==='0'||h('retry-after')!=null)){lastError=Error('billing-rate-limited');break;}
  lastError=Error('billing-http-'+response.status);
  if(!TRANSIENT.has(response.status))break;
 }
 throw lastError;
}
async function collect({token,budget:raw,reserveCredits,now=new Date(),fetchImpl=fetch,retryDelayMs=2000}){
 const allowance=positiveBudget(raw),pendingReserve=reserve(allowance,reserveCredits);
 if(!token)throw Error('missing-token');
 const period=now.toISOString().slice(0,7);
 const url=API+'/users/'+ACCOUNT+'/settings/billing/ai_credit/usage?year='+
 now.getUTCFullYear()+'&month='+(now.getUTCMonth()+1);
 const response=await fetchReport({url,token,fetchImpl,retryDelayMs});
 let body;
 try{body=await response.json();}catch{throw Error('invalid-billing-response');}
 let measured,basis;
 try{measured=usageQuantity(body,now);}
 catch(e){
  // Month rollover: GitHub returns an empty report until the new month's first usage is metered, and nothing gets
  // metered while dispatch is blocked, so "empty = unknown" deadlocked the whole pipeline on Nov 1 2026. An empty
  // report may also mean Copilot is billed elsewhere, so treat it as zero ONLY in the first days of the month AND
  // only when last month's report proves Copilot AI credits are billed to this account. Otherwise stay fail-closed.
  if(e.message!=='empty-billing-usage-items'||now.getUTCDate()>EMPTY_MONTH_START_DAYS)throw e;
  const prev=new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),0,12));
  const prevResponse=await fetchReport({url:API+'/users/'+ACCOUNT+'/settings/billing/ai_credit/usage?year='+
   prev.getUTCFullYear()+'&month='+(prev.getUTCMonth()+1),token,fetchImpl,retryDelayMs});
  let prevBody;
  try{prevBody=await prevResponse.json();}catch{throw e;}
  try{if(!(usageQuantity(prevBody,prev)>0))throw e;}catch{throw e;}
  measured=0;basis='new-month-empty-report; previous month billed Copilot AI credits to this account';
 }
 const snapshot={account:ACCOUNT,metric:'ai_credits',source:'github-billing-api',
 sourceUrl:API+'/users/'+ACCOUNT+'/settings/billing/ai_credit/usage',
 period,allowanceSource:'owner-configured-budget',used:measured+pendingReserve,
 allowance,measuredAt:now.toISOString(),reportedUsed:measured,reserved:pendingReserve,...(basis?{basis}:{})};
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
  const reason=KNOWN_REASON.test(e.message)?e.message:'billing-unavailable';
  return {snapshot:null,status:{level:'unknown',maxNewAssignments:0,warn:true,reason},error:reason};
 }
}
const messages={normal:'Usage returned below the warning threshold',warn:'Copilot AI credit usage at warning level (50%)',
 slow:'Copilot AI credit usage at throttling level (75%)',paused:'Copilot AI credit usage at pause level (90%)',
 unknown:'Copilot billing data unavailable; new assignments blocked'};
async function alertChange({github,level,period,reason,env={}}){
 if(!Object.hasOwn(messages,level)||! /^\d{4}-\d{2}$/.test(period))throw Error('invalid-alert');
 const owner='AARMOU',repo='arcana_table',issue_number=78;
 const comments=await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number,per_page:100});
 const previous=[...comments].reverse().find(c=>c.user?.login==='github-actions[bot]'&&c.user?.type==='Bot'&&c.body?.startsWith(MARKER));
 if(previous?.body?.includes('period:'+period)&&previous?.body?.includes('level:'+level))return 'unchanged';
 if(!previous&&level==='normal')return 'healthy-initial';
 const body=[MARKER,'period:'+period,'level:'+level,
  '@AARMOU **Arcana Table usage alert:** '+messages[level]+'.',
  'The billing API can lag. The existing readiness gate is independent; no automatic worker launch is authorized by this comment.',
  level==='unknown'?'Reason: `'+(KNOWN_REASON.test(String(reason||''))?reason:'billing-unavailable')+'`. Next step: '+
   (billingAction(KNOWN_REASON.test(String(reason||''))?reason:'billing-unavailable',env)||'check the Billing Watch run.'):'The worker budget gate uses a conservative buffer.',
  'Live status: https://aarmou.github.io/arcana_table/control/#safety',
  'Details: https://github.com/AARMOU/arcana_table/issues/81'].join('\n\n');
 await github.rest.issues.createComment({owner,repo,issue_number,body});
 return 'posted';
}
module.exports={positiveBudget,reserve,usageQuantity,collect,readBudget,alertChange,billingAction,fetchReport,MARKER};
