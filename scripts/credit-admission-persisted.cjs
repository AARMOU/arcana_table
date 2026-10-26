'use strict';
// Read-only admission evidence composition. NEVER performs a paid action.
// Caller must independently verify inventory completeness, billing identity,
// settlement provenance, serialization, shared CI, and worker ownership.
const { load } = require('./credit-reservation-store.cjs');
const { evaluateCreditAdmission } = require('./credit-admission.cjs');
const deny=reason=>({allowed:false,reason});
async function evaluatePersistedCreditAdmission({github,owner,repo,period,expectedOperationIds,
  ledgerComplete,snapshot,candidateCredits,now,serializedControl=false,sharedCiVerified=false,
  ownershipVerified=false}={}) {
  if (serializedControl!==true) return deny('serialization-unverified');
  if (sharedCiVerified!==true) return deny('shared-ci-unverified');
  if (ownershipVerified!==true) return deny('ownership-unverified');
  if (!snapshot || snapshot.period!==period) return deny('billing-period-mismatch');
  const ledger=await load({github,owner,repo,period,expectedOperationIds,ledgerComplete});
  if (!ledger.ok) return deny('ledger-'+ledger.reason);
  const decision=evaluateCreditAdmission({events:ledger.events,expectedOperationIds,
    ledgerComplete,snapshot,candidateCredits,now});
  return {...decision,ledgerCommentIds:ledger.commentIds};
}
module.exports={evaluatePersistedCreditAdmission};
