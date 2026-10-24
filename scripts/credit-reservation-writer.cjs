'use strict';
// Compatibility adapter: a single authoritative reservation implementation lives
// in credit-reservation-store.cjs. This wrapper never bypasses its historical
// inventory, serialized-control or post-write verification requirements.
const { append, CONTROL_ISSUE } = require('./credit-reservation-store.cjs');
async function reserveBeforeAttempt({ github, owner, repo, issue, event, loadComments,
  period, expectedOperationIds, ledgerComplete, allowWrite=false, serializedControl=false } = {}) {
  const deny=reason=>({allowed:false,reason});
  if (issue!==CONTROL_ISSUE || typeof loadComments==='function')
    return deny('legacy-writer-disabled');
  if (!event || event.op!=='reserve') return deny('reserve-required');
  try {
    const result=await append({github,owner,repo,issueNumber:issue,event,
      period:period||event.period,expectedOperationIds,ledgerComplete,allowWrite,serializedControl});
    return {allowed:result.ok===true,reason:'reservation-recorded',id:event.id,
      outstanding:result.outstanding,commentId:result.commentId};
  } catch(e) {
    return deny('reservation-unverified:'+String(e?.message||'unknown'));
  }
}
module.exports={reserveBeforeAttempt};
