/* Read-only card identity and Copilot pre-submission checks.
 * This is a UX gate, not a dispatcher authorization or Oracle rules certification. */
(function(root) {
'use strict';
const norm = s => String(s || '').normalize('NFKC').replace(/[’`]/g,"'").replace(/\s+/g,' ').trim().toLocaleLowerCase('en');
const HEX_SHA = /^[0-9a-f]{40}$/;
const MAX_AGE_MS = 6 * 60 * 60 * 1000;
const isCard = x => x && x.object === 'card' && typeof x.name === 'string' &&
  x.name.length >= 2 && x.name.length <= 150 && typeof x.id === 'string';
function parseSnapshot(data, sourceRef, now = Date.now()) {
  if (!data || data.ok !== true || data.eligibilityComplete !== true ||
      !Array.isArray(data.eligibleNames) || !HEX_SHA.test(String(data.baseSha || '')))
    return { ok:false, reason:'Complete verified Cardkit results are unavailable. No Copilot request can be submitted.' };
  const source = String(sourceRef || '').match(/@([a-f0-9]{40})$/);
  if (!source || source[1] !== data.baseSha)
    return { ok:false, reason:'Cardkit and the app library are from different source versions. Refresh the site/feeder.' };
  const timestamp = Date.parse(data.checkedAt);
  if (!Number.isFinite(timestamp) || timestamp > now + 300000 || now - timestamp > MAX_AGE_MS)
    return { ok:false, reason:'Cardkit eligibility snapshot is stale. Wait for a fresh queue feeder check.' };
  if (data.eligibleNames.length > 500 || data.eligibleNames.some(n => typeof n !== 'string' || n.length > 150))
    return { ok:false, reason:'Invalid or incomplete Cardkit allowlist. No submission.' };
  return { ok:true, checkedAt:data.checkedAt, baseSha:data.baseSha,
    eligible:new Set(data.eligibleNames.map(norm)), reason:null };
}
function assessCard(name, snapshot) {
  if (!snapshot?.ok) return { ok:false, reason:snapshot?.reason || 'Cardkit status unavailable.' };
  return snapshot.eligible.has(norm(name))
    ? { ok:true, reason:'Cardkit SUPPORTED / copilot, not currently claimed (not guaranteed gameplay success).' }
    : { ok:false, reason:'Not certified Copilot-ready/unclaimed by Cardkit. May need engine work, review or already be claimed.' };
}
async function verifyPrintedCard(fetchCard, rawName) {
  const name=String(rawName || '').trim();
  if (name.length < 2 || name.length > 150) return { ok:false, reason:'Enter a complete card name.' };
  try {
    const opts={cache:'no-store',headers:{Accept:'application/json'}};
    const res=await fetchCard('https://api.scryfall.com/cards/named?exact='+encodeURIComponent(name),opts);
    if (res.ok) {
      const card=await res.json();
      if (!isCard(card)) return { ok:false, reason:'Card database returned invalid card information.' };
      if (norm(card.name)!==norm(name)) return { ok:false, suggestion:card.name, reason:'Use the printed card name.' };
      return { ok:true, name:card.name, id:card.id, reason:'Exact printed Magic card verified.' };
    }
    if (res.status !== 404) return { ok:false, reason:'Card database unavailable (HTTP '+res.status+'). No submission.' };
    const suggestion=await fetchCard('https://api.scryfall.com/cards/named?fuzzy='+encodeURIComponent(name),opts);
    if (suggestion.ok) {
      const card=await suggestion.json();
      if (isCard(card)) return { ok:false, suggestion:card.name, reason:'Exact name not found. Confirm the suggested printed card.' };
    }
    return { ok:false, reason:'No exact printed Magic card found. Check the spelling.' };
  } catch (_e) {
    return { ok:false, reason:'Unable to contact the card database. No submission.' };
  }
}
const api={norm,parseSnapshot,assessCard,verifyPrintedCard};
root.ArcanaCardPreflight=api;
if (typeof module==='object' && module.exports) module.exports=api;
})(typeof globalThis==='object' ? globalThis : this);
