'use strict';
// Read-only audit of evidence metadata. Does not enable paid operations.
const sha=/^[0-9a-f]{40}$/;
function verifyEvidence(e,now=Date.now()){
 const deny=reason=>({ok:false,reason});
 if(!e?.billing||e.billing.metric!=='ai_credits'||!Number.isFinite(e.billing.used)||
 !Number.isFinite(e.billing.allowance)||e.billing.allowance<=0||
 !Number.isSafeInteger(e.billing.reportingLagMinutes)||
 e.billing.reportingLagMinutes<0||!e.billing.requestId)return deny('billing-evidence-missing');
 const time=Date.parse(e.billing.measuredAt);
 if(!Number.isFinite(time)||time>now||now-time>900000)return deny('billing-evidence-stale');
 if(e.history?.complete!==true||!sha.test(e.history.auditCommit||'')||
 !Array.isArray(e.history.operationIds))return deny('history-unverified');
 if(e.sharedCi?.conclusion!=='success'||!sha.test(e.sharedCi.headSha||'')||
 !Number.isSafeInteger(e.sharedCi.runId))return deny('shared-ci-unverified');
 if(e.serialization?.verified!==true||!sha.test(e.serialization.workflowSha||''))
 return deny('serialization-unverified');
 if(e.ownership?.verified!==true||!sha.test(e.ownership.auditCommit||''))
 return deny('ownership-unverified');
 return {ok:true};
}
module.exports={verifyEvidence};
