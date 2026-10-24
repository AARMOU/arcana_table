'use strict';
// Read-only adapter for a bot-authenticated, append-only issue-comment reservation ledger.
// This module NEVER authorizes spending or writes comments. It fails closed on malformed data.
const { reconcile } = require('./credit-reservations.cjs');
const MARKER = '<!-- arcana-credit-reservation-v1 -->';
const OPS = new Set(['reserve','settle','cancel']);
function decode(comments) {
  if (!Array.isArray(comments)) return { ok:false, reason:'comments-unavailable' };
  const events=[];
  for (const c of comments) {
    if (!String(c?.body||'').startsWith(MARKER)) continue;
    if (c.user?.login !== 'github-actions[bot]' || c.user?.type !== 'Bot')
      return { ok:false, reason:'untrusted-ledger-author' };
    if (c.updated_at && c.created_at && c.updated_at !== c.created_at)
      return { ok:false, reason:'edited-ledger-event' };
    const lines=String(c.body).slice(MARKER.length).trim().split('\n');
    if (lines.length!==1) return {ok:false,reason:'malformed-ledger-event'};
    let event;
    try { event=JSON.parse(lines[0]); } catch {return {ok:false,reason:'malformed-ledger-json'};}
    if (!event || !OPS.has(event.op) || Object.keys(event).some(k=>!['op','id','credits','period','authorized'].includes(k)))
      return {ok:false,reason:'invalid-ledger-fields'};
    events.push(event);
  }
  return {ok:true,events};
}
function readLiability(comments, {period}={}) {
  const parsed=decode(comments);
  return parsed.ok ? reconcile(parsed.events,{period}) : {...parsed,outstanding:null};
}
function encode(event) {
  if (!event || !OPS.has(event.op)) throw Error('invalid-event');
  const check=reconcile(event.op==='reserve'?[event]:[]);
  if (event.op==='reserve' && !check.ok) throw Error('invalid-reservation');
  if (event.op!=='reserve' && (event.authorized!==true || !Number.isSafeInteger(event.credits) || event.credits<0 ||
    typeof event.id!=='string' || !/^[A-Za-z0-9:_-]{1,120}$/.test(event.id))) throw Error('invalid-settlement');
  return MARKER+'\n'+JSON.stringify(event);
}
module.exports={MARKER,decode,readLiability,encode};
