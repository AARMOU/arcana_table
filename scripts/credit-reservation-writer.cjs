'use strict';
// Durable write protocol for reservation comments. Never authorizes paid work.
// A successful reserve comment MUST precede an external paid side effect.
// A failed/uncertain write MUST stop the operation; never retry the side effect.
const { encode, readLiability } = require('./credit-reservation-comments.cjs');
function verifyReservation(event) {
  if (event?.op !== 'reserve') throw Error('reserve-required');
  return encode(event);
}
async function reserveBeforeAttempt({ github, owner, repo, issue, event, loadComments }) {
  const deny = reason => ({ allowed:false, reason });
  if (!github?.rest?.issues?.createComment || !Number.isSafeInteger(issue) || issue < 1 ||
      typeof loadComments !== 'function') return deny('ledger-unavailable');
  let body;
  try { body=verifyReservation(event); } catch { return deny('invalid-reservation'); }
  // Preflight is deliberately strict: this is NOT an atomic compare-and-swap.
  // The caller must hold an external serialized lock across read, reserve, and paid attempt.
  let before;
  try { before=readLiability(await loadComments(), {period:event.period}); }
  catch { return deny('ledger-read-failed'); }
  if (!before.ok) return deny('ledger-unverified:'+before.reason);
  // Refuse duplicate IDs before any write, including previously settled reservations.
  const { decode } = require('./credit-reservation-comments.cjs');
  let inventory;
  try { inventory = decode(await loadComments()); }
  catch { return deny('ledger-read-failed'); }
  if (!inventory.ok) return deny('ledger-unverified:'+inventory.reason);
  if (inventory.events.some(e => e.op === 'reserve' && e.id === event.id))
    return deny('reservation-already-exists');
  try {
    await github.rest.issues.createComment({owner,repo,issue_number:issue,body});
  } catch { return deny('reservation-write-uncertain'); }
  // Acknowledged writes are not sufficient: verify the event is observable.
  let comments;
  try { comments=await loadComments(); }
  catch { return deny('reservation-readback-failed'); }
  const after=readLiability(comments,{period:event.period});
  if (!after.ok || after.outstanding < before.outstanding+event.credits)
    return deny('reservation-readback-unverified');
  const count=comments.filter(c=>String(c?.body||'')===body).length;
  if (count!==1) return deny('reservation-not-unique');
  return {allowed:true,reason:'reservation-recorded',id:event.id,outstanding:after.outstanding};
}
module.exports={reserveBeforeAttempt};
