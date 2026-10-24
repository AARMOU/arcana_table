'use strict';
// Pure adapter for bot-authenticated, append-only GitHub issue-comment credit records.
// No dispatch or paid admission is authorized by this module.
const { reconcile } = require('./credit-reservations.cjs');
const MARKER = '<!-- arcana-credit-reservation-v1 -->';
const OPS = new Set(['reserve', 'settle', 'cancel']);
const ID = /^[A-Za-z0-9:_-]{1,120}$/;
const PERIOD = /^\d{4}-\d{2}$/;
const EVIDENCE = /^(?:billing|owner):[A-Za-z0-9:_-]{1,120}$/;
function validEvent(e) {
  if (!e || typeof e !== 'object' || Array.isArray(e) || !OPS.has(e.op) ||
      !ID.test(e.id || '') || !Number.isSafeInteger(e.credits)) return false;
  if (e.op === 'reserve')
    return e.credits > 0 && PERIOD.test(e.period || '') &&
      Object.keys(e).every(k => ['op', 'id', 'credits', 'period'].includes(k));
  return e.credits >= 0 && e.authorized === true && EVIDENCE.test(e.evidence || '') &&
    Object.keys(e).every(k => ['op', 'id', 'credits', 'authorized', 'evidence'].includes(k));
}
function decode(comments) {
  if (!Array.isArray(comments)) return { ok: false, reason: 'comments-unavailable' };
  const events = [];
  for (const c of comments) {
    if (!String(c?.body || '').startsWith(MARKER)) continue;
    if (c.user?.login !== 'github-actions[bot]' || c.user?.type !== 'Bot')
      return { ok: false, reason: 'untrusted-ledger-author' };
    if (!c.created_at || !c.updated_at || c.updated_at !== c.created_at)
      return { ok: false, reason: 'edited-or-undated-ledger-event' };
    const lines = String(c.body).slice(MARKER.length).trim().split('\n');
    if (lines.length !== 1) return { ok: false, reason: 'malformed-ledger-event' };
    let event;
    try { event = JSON.parse(lines[0]); }
    catch { return { ok: false, reason: 'malformed-ledger-json' }; }
    if (!validEvent(event)) return { ok: false, reason: 'invalid-ledger-fields' };
    events.push(event);
  }
  return { ok: true, events };
}
function readLiability(comments, { period } = {}) {
  const parsed = decode(comments);
  return parsed.ok ? reconcile(parsed.events, { period }) : { ...parsed, outstanding: null };
}
function encode(event) {
  if (!validEvent(event)) throw Error('invalid-credit-event');
  if (event.op === 'reserve' && !reconcile([event]).ok) throw Error('invalid-reservation');
  return MARKER + '\n' + JSON.stringify(event);
}
module.exports = { MARKER, decode, readLiability, encode, validEvent };
