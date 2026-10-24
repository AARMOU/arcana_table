'use strict';
// Read-only evidence-shape audit. Never authorizes paid operations by itself.
const sha=/^[0-9a-f]{40}$/;
const operation=/^[A-Za-z0-9:_-]{1,120}$/;
function verifyEvidence(e,now=Date.now()){
 const deny=reason=>({ok:false,reason});
 const b=e?.billing;
 if(!b||b.metric!=='ai_credits'||typeof b.account!=='string'||!b.account.trim()||
 typeof b.sku!=='string'||!b.sku.trim()||!/^\d{4}-\d{2}$/.test(b.period||'')||
 !Number.isFinite(b.used)||b.used<0||!Number.isFinite(b.allowance)||b.allowance<=0||
 !Number.isSafeInteger(b.reportingLagMinutes)||b.reportingLagMinutes<0||
 typeof b.requestId!=='string'||!operation.test(b.requestId))
 return deny('billing-evidence-missing');
 if(!Number.isFinite(now))return deny('clock-unverified');
 const time=Date.parse(b.measuredAt);
 if(!Number.isFinite(time)||time>now||now-time>900000)return deny('billing-evidence-stale');
 const h=e.history;
 if(h?.complete!==true||!sha.test(h.auditCommit||'')||!Array.isArray(h.operationIds)||
 h.operationIds.some(id=>typeof id!=='string'||!operation.test(id))||
 new Set(h.operationIds).size!==h.operationIds.length)return deny('history-unverified');
 if(e.sharedCi?.conclusion!=='success'||!sha.test(e.sharedCi.headSha||'')||
 !Number.isSafeInteger(e.sharedCi.runId)||e.sharedCi.runId<=0)return deny('shared-ci-unverified');
 if(e.serialization?.verified!==true||!sha.test(e.serialization.workflowSha||'')||
 typeof e.serialization.group!=='string'||!operation.test(e.serialization.group))
 return deny('serialization-unverified');
 if(e.ownership?.verified!==true||!sha.test(e.ownership.auditCommit||'')||
 typeof e.ownership.unitId!=='string'||!operation.test(e.ownership.unitId)||
 typeof e.ownership.owner!=='string'||!operation.test(e.ownership.owner))
 return deny('ownership-unverified');
 if(!h.operationIds.includes(e.ownership.unitId))return deny('ownership-not-in-inventory');
 return {ok:true};
}
module.exports={verifyEvidence};
