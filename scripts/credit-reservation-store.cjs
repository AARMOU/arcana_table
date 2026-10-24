'use strict';
// Trusted reservation storage, deliberately NOT wired to dispatcher or repair router.
// The shared CI concurrency group must serialize callers; this module cannot create
// an atomic compare-and-swap on GitHub issue comments. Never retry an uncertain write.
const { decode, encode } = require('./credit-reservation-comments.cjs');
const { reconcile } = require('./credit-reservations.cjs');
const ID = /^[A-Za-z0-9:_-]{1,120}$/;
const CONTROL_ISSUE = 81;
const deny = reason => ({ ok: false, reason, outstanding: null });

async function load({ github, owner, repo, period, expectedOperationIds, ledgerComplete,
  issueNumber = CONTROL_ISSUE } = {}) {
  if (ledgerComplete !== true || !Array.isArray(expectedOperationIds) ||
      expectedOperationIds.some(id => typeof id !== 'string' || !ID.test(id)) ||
      new Set(expectedOperationIds).size !== expectedOperationIds.length)
    return deny('unverified-historical-inventory');
  if (!/^\d{4}-\d{2}$/.test(period || '') || !github?.paginate ||
      !github?.rest?.issues?.listComments || !owner || !repo || issueNumber !== CONTROL_ISSUE)
    return deny('invalid-store-configuration');
  let comments;
  try {
    comments = await github.paginate(github.rest.issues.listComments,
      { owner, repo, issue_number: issueNumber, per_page: 100 });
  } catch { return deny('ledger-read-unavailable'); }
  const parsed = decode(comments);
  if (!parsed.ok) return deny(parsed.reason);
  const settlementKeys = new Set();
  for (const e of parsed.events.filter(e => e.op !== 'reserve')) {
    const key = e.id + ':' + e.op + ':' + e.evidence;
    if (settlementKeys.has(key)) return deny('duplicate-settlement-evidence');
    settlementKeys.add(key);
  }
  const liability = reconcile(parsed.events, { period });
  if (!liability.ok) return deny(liability.reason);
  const reserved = new Set(parsed.events.filter(e => e.op === 'reserve').map(e => e.id));
  if (expectedOperationIds.some(id => !reserved.has(id))) return deny('unreserved-historical-operation');
  const commentIds = comments.filter(c => String(c?.body || '').startsWith(require('./credit-reservation-comments.cjs').MARKER))
    .map(c => c.id);
  if (commentIds.some(id => !Number.isSafeInteger(id) || id <= 0)) return deny('ledger-comment-id-unavailable');
  return { ok: true, events: parsed.events, outstanding: liability.outstanding, open: liability.open, commentIds };
}

// Writer is opt-in for a future separately reviewed admission path, never called by
// current workflows. Must be invoked *before* any potentially paid external request.
async function append({ github, owner, repo, period, expectedOperationIds, ledgerComplete,
  event, issueNumber = CONTROL_ISSUE, allowWrite = false, serializedControl = false } = {}) {
  if (allowWrite !== true || serializedControl !== true) throw Error('reservation-write-disabled');
  const body = encode(event); // Reject bad event before touching GitHub.
  if (!Array.isArray(expectedOperationIds) || !expectedOperationIds.includes(event.id))
    throw Error('operation-not-in-inventory');
  if (!github?.rest?.issues?.createComment) throw Error('ledger-writer-unavailable');
  const beforeExpected = event.op === 'reserve' ?
    expectedOperationIds.filter(id => id !== event.id) : expectedOperationIds;
  const before = await load({ github, owner, repo, period, expectedOperationIds: beforeExpected,
    ledgerComplete, issueNumber });
  if (!before.ok) throw Error('ledger-preflight-' + before.reason);
  const projected = reconcile([...before.events, event], { period });
  if (!projected.ok) throw Error('ledger-projection-' + projected.reason);
  if (event.op !== 'reserve' && before.events.some(e => e.op !== 'reserve' &&
      e.id === event.id && e.op === event.op && e.evidence === event.evidence))
    throw Error('duplicate-settlement-evidence');
  // Any 403, timeout or transport error is an UNKNOWN write outcome, never auto-retry.
  const response = await github.rest.issues.createComment({
    owner, repo, issue_number: issueNumber, body });
  const writtenId = response?.data?.id;
  if (!Number.isSafeInteger(writtenId) || writtenId <= 0)
    throw Error('reservation-write-unverified');
  // Read back complete authenticated bot ledger before returning success.
  const after = await load({ github, owner, repo, period, expectedOperationIds,
    ledgerComplete, issueNumber });
  if (!after.ok || !after.commentIds.includes(writtenId) ||
      after.outstanding !== projected.outstanding ||
      after.events.length !== before.events.length + 1)
    throw Error('reservation-write-unverified');
  return { ok: true, commentId: writtenId, outstanding: after.outstanding };
}
module.exports = { load, append, CONTROL_ISSUE };
