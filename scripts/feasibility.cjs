'use strict';
// Engine-feasibility gate for paid Copilot assignments (issue #81, 2026-10-28).
//
// Both paid Copilot runs so far that produced no card (#127 -> PR #320, #173 -> PR #330) were for cards
// Cardkit already classified NEEDS_REVIEW / routed away from Copilot. This gate spends credits only on
// units whose every named card Cardkit (on the exact integration-base SHA) classifies SUPPORTED with
// route "copilot", unless an approver applied FEASIBILITY_LABEL after reviewing the unit by hand.
//
// It is a waste guard, not a security boundary: verdicts come from the integration branch's own
// Cardkit, run in a separate job with no secrets. A wrong verdict can only make the gate as permissive
// as it was before it existed; every other admission check still applies.
const FEASIBILITY_LABEL = 'arcana:feasibility-reviewed';
const READY = { readiness: 'SUPPORTED', route: 'copilot' };

const normName = s => String(s || '').normalize('NFKC').replace(/[’`]/g, "'").replace(/\s+/g, ' ').trim().toLowerCase();
const stripComments = body => String(body || '').replace(/<!--[\s\S]*?(?:-->|$)/g, '');

// Card names from the unit's "## Cards" bullets. A bullet is "Name", "**Name** — note", "Name (note)" or
// "Name: note". Commas stay (e.g. "Ulalek, Fused Atrocity").
function unitCards(body) {
  const names = [];
  let inCards = false;
  for (const line of stripComments(body).split(/\r?\n/)) {
    const h = line.match(/^#{2,3}\s+(.+?)\s*$/);
    if (h) { inCards = /^cards\b/i.test(h[1].trim()); continue; }
    if (!inCards) continue;
    const b = line.match(/^\s*[-*]\s+(.*?)\s*$/);
    if (!b) continue;
    const name = b[1].replace(/[*_`]/g, '').split(/\s+[—–-]\s+|\s*[—–]\s*|\s+\(|:\s/)[0].trim();
    if (name && name.length <= 141 && !/^(card name|tbd|todo|n\/a|none)$/i.test(name)) names.push(name);
  }
  return [...new Set(names)];
}

// Names to classify for every open queue issue. Bounded so a huge issue can't blow up the job.
function collectNames(issues, max = 200) {
  const out = new Set();
  for (const i of issues || []) {
    if (i.pull_request || !String(i.title || '').startsWith('[Arcana Queue]')) continue;
    for (const n of unitCards(i.body)) { if (out.size < max) out.add(n); }
  }
  return [...out];
}

// Raw Cardkit `classify --json` output + the base SHA it ran on -> compact verdict map.
function buildVerdicts({ baseSha, classified }) {
  if (!/^[0-9a-f]{40}$/.test(String(baseSha || '')) || !Array.isArray(classified)) return null;
  const cards = {};
  for (const v of classified) {
    if (!v || typeof v.name !== 'string' || typeof v.readiness !== 'string') return null;
    cards[normName(v.name)] = { name: v.name, readiness: v.readiness, route: typeof v.route === 'string' ? v.route : null,
      hint: typeof v.reviewHint === 'string' ? v.reviewHint : null,
      reason: Array.isArray(v.reasons) && typeof v.reasons[0] === 'string' ? v.reasons[0].slice(0, 200) : null };
  }
  return { schema: 1, baseSha, cards };
}

function parseVerdicts(raw) {
  if (!raw) return null;
  try {
    const d = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!d || d.schema !== 1 || !/^[0-9a-f]{40}$/.test(String(d.baseSha || '')) || !d.cards || typeof d.cards !== 'object') return null;
    return d;
  } catch { return null; }
}

// The latest application of FEASIBILITY_LABEL, if the issue still carries it.
function overrideOf(events, labels) {
  if (!(labels || []).includes(FEASIBILITY_LABEL)) return null;
  const e = (events || []).filter(x => x.event === 'labeled' && x.label?.name === FEASIBILITY_LABEL).pop();
  return e ? { actor: e.actor?.login || null, at: Date.parse(e.created_at) } : { actor: null, at: NaN };
}

// { ok, reason, cards:[{name, readiness, route, hint}] }. Unknown or missing data is "not ok".
function assess({ body, override = null, approvers = [], verdicts, baseSha }) {
  const allowed = (approvers || []).map(a => String(a).toLowerCase());
  // Automatically queued issues never bypass Cardkit, even with an owner-added
  // feasibility override. Manual/legacy units retain their explicit override.
  if (override && String(body || '').startsWith('<!-- arcana-auto-queue-v1 -->\n'))
    return { ok: false, reason: 'automatic queue: Cardkit feasibility override prohibited', cards: [] };
  if (override) {
    if (override.actor && allowed.includes(String(override.actor).toLowerCase()))
      return { ok: true, reason: 'owner-reviewed by ' + override.actor, cards: [] };
    return { ok: false, reason: 'feasibility label not applied by an approver', cards: [] };
  }
  const names = unitCards(body);
  if (!verdicts) return { ok: false, reason: 'feasibility-unavailable (Cardkit verdicts missing)', cards: [] };
  if (baseSha && verdicts.baseSha !== baseSha) return { ok: false, reason: 'feasibility-stale (classified ' + verdicts.baseSha.slice(0, 7) + ', base is ' + String(baseSha).slice(0, 7) + ')', cards: [] };
  if (!names.length) return { ok: false, reason: 'no named cards to classify (discovery unit needs owner review)', cards: [] };
  const cards = names.map(n => ({ name: n, ...(verdicts.cards[normName(n)] || { readiness: 'UNCLASSIFIED', route: null }) }));
  const bad = cards.filter(c => c.readiness !== READY.readiness || c.route !== READY.route);
  if (bad.length) return { ok: false, cards,
    reason: 'not Copilot-ready: ' + bad.map(c => `${c.name}=${c.readiness}${c.hint ? '(' + c.hint + ')' : ''}${c.route && c.route !== 'copilot' ? ' route ' + c.route : ''}`).join('; ') };
  return { ok: true, reason: 'cardkit SUPPORTED', cards };
}

module.exports = { FEASIBILITY_LABEL, unitCards, collectNames, buildVerdicts, parseVerdicts, overrideOf, assess, normName };
