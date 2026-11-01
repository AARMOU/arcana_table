'use strict';
// Idle-time work for Copilot (owner, 2026-11-01: "give the copilot workers something to work on when they aren't
// working", cap 6 a day). Most custom cards the app plays were integrated before independent certification existed,
// so they have no suite. When the dispatcher has nothing eligible, this opens ONE [Arcana Suite] request for such a
// card, certified against the integration build branch (integration/policy.json `target`) instead of a card PR.
//
// Guards: the dispatcher's own last status says nothing-eligible and billing is normal; at most one idle request
// waits unassigned; at most DAILY_CAP are opened per rolling 24 h; one request per card, ever (a closed one is not
// reopened). Cards in premade or official precon decks go first. It opens issues only; the dispatcher assigns them
// under the usual admission and budget, ranking them after every other kind of work.
const fs = require('node:fs');
const { SUITE_TITLE, SUITE_MARKER, SUITE_BASE } = require('../scripts/admission.cjs');
const { suiteKey, pascal } = require('./suite-requests.cjs');

const DAILY_CAP = 6;
// Owner-requested burst (certification/idle-burst.json): while `until` is in the future, keep up to `parallel`
// idle suite requests open at once (not just one) and open them even when the dispatcher has other work (it still
// ranks idle suites after everything else), within a rolling-24 h `cap`. Billing must still be normal. When the
// window ends this falls back to the normal one-at-a-time, 6-a-day behaviour on its own.
function activeBurst(burst, now = Date.now()) {
  if (!burst || !(Date.parse(burst.until || '') > now)) return null;
  const parallel = Math.min(3, Math.max(1, Number.parseInt(burst.parallel, 10) || 1));
  const cap = Math.min(60, Math.max(DAILY_CAP, Number.parseInt(burst.cap, 10) || DAILY_CAP));
  return { until: burst.until, parallel, cap };
}
const STATUS_ISSUE = 78;
const DISPATCHER_STATUS = '<!-- arcana-control-status-v1:dispatcher -->';
const STATUS_MAX_AGE_MS = 75 * 60 * 1000;
const BOT = 'github-actions[bot]';
const idleMarker = key => `<!-- arcana-idle-suite:${key} -->`;
const IDLE = /^<!-- arcana-idle-suite:([a-z0-9-]+) -->$/m;
const ASSETS = 'android/app/src/main/assets/argentum/';
const REGISTRY = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt';

// Pure: the custom cards the app actually plays as Arcana's own definition, with their Kotlin identifiers and how
// many decks use them. A card Argentum also has is played as Argentum's unless custom-overrides.txt keeps it.
function playedCustomCards({ registry, customIndex, libraryIndex, overrides, deckLists = [] }) {
  const names = text => String(text || '').split(/\r?\n/).map(l => l.split('\t')[0].trim()).filter(Boolean);
  const library = new Set(names(libraryIndex).map(n => n.toLowerCase()));
  const kept = new Set(String(overrides || '').split(/\r?\n/).map(l => l.split('#')[0].trim().toLowerCase()).filter(Boolean));
  const idents = [...String(registry || '').matchAll(/Entry\((\w+),\s*"[^"]*"/g)].map(m => m[1]);
  const custom = names(customIndex);
  const uses = new Map();
  for (const list of deckLists) for (const line of String(list).split(/\r?\n/)) {
    const m = line.match(/^\s*\d+x?\s+(.+?)\s*$/); if (m) uses.set(m[1].toLowerCase(), (uses.get(m[1].toLowerCase()) || 0) + 1);
  }
  const byIdent = new Map(idents.map(i => [i.toLowerCase(), i]));
  return custom.filter(n => !library.has(n.toLowerCase()) || kept.has(n.toLowerCase()))
    .map(name => ({ name, ident: byIdent.get(pascal(name).toLowerCase()) || null, decks: uses.get(name.toLowerCase()) || 0 }))
    .filter(c => c.ident);
}

// Pure: what to request next, or why nothing.
function plan({ cards, manifest, requests, status, now = Date.now(), burst = null }) {
  const b = activeBurst(burst, now);
  if (!status || (!b && status.state !== 'nothing-eligible')) return { skip: 'dispatcher has eligible work (or no status)' };
  if (!(now - Date.parse(status.checkedAt || '') <= STATUS_MAX_AGE_MS)) return { skip: 'dispatcher status is stale' };
  if (status.billing?.level !== 'normal') return { skip: 'billing not normal' };
  const idle = requests.filter(r => IDLE.test(String(r.body || '')));
  let slots = 1;
  if (b) {
    // Busy = Copilot sessions running or awaiting pickup (dispatcher status) + idle requests still unassigned.
    // An open request whose session already finished (PR in review) does not hold a worker.
    const w = status.workers || {};
    const waiting = idle.filter(r => r.state === 'open' && !(r.assignees || []).length).length;
    const busy = (Number(w.running) || 0) + (Number(w.pendingPickup) || 0) + waiting;
    if (busy >= b.parallel) return { skip: `burst: ${busy}/${b.parallel} workers busy or waiting` };
    slots = b.parallel - busy;
  } else if (idle.some(r => r.state === 'open' && !(r.assignees || []).length)) return { skip: 'an idle suite request is still waiting' };
  const cap = b ? b.cap : DAILY_CAP;
  const today = idle.filter(r => now - Date.parse(r.created_at || '') < 24 * 3600e3).length;
  if (today >= cap) return { skip: `daily cap reached (${today}/${cap})` };
  slots = Math.min(slots, cap - today);
  const certified = new Set(manifest.suites.map(s => s.name.toLowerCase()));
  const requested = new Set(idle.map(r => String(r.body).match(IDLE)[1]));
  const next = cards.filter(c => !certified.has(c.name.toLowerCase()) && !requested.has(suiteKey(c.name)))
    .sort((a, b) => b.decks - a.decks || a.name.localeCompare(b.name));
  return next.length ? { candidates: next, today, slots, burst: !!b } : { skip: 'every played custom card has a suite or a request' };
}

function idleRequest({ card, oracleLine, target }) {
  const key = suiteKey(card.name), cls = pascal(card.name) + 'IndependentCertificationTest';
  return {
    key,
    title: `${SUITE_TITLE}${card.name} — independent certification suite (integrated card)`,
    body: [SUITE_MARKER, idleMarker(key), '',
      'Card unit: integrated', 'Card PR: integrated', `Suite key: ${key}`, `Base: ${SUITE_BASE}`, `Build branch: ${target}`, '',
      '## Task', '',
      `**${card.name}** is already in the app (branch \`${target}\`) but has no independent certification suite. Write one strictly from the Oracle record below.`,
      `Do **not** read the card's implementation (\`android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/\` on \`${target}\`) or its existing scenario test: the suite must not depend on them.`,
      `The card object is \`com.arcanatable.customcards.cards.${card.ident}\`. Test helpers: \`android/custom-cards/src/test/kotlin/com/arcanatable/customcards/support/\` **on \`${target}\`** (the suite runs there); existing suites in \`certification/\` show how they are used.`, '',
      'Add exactly these two files, and nothing else:', '',
      `- \`certification/${cls}.kt\`: class \`com.arcanatable.customcards.${cls}\`, importing \`com.arcanatable.customcards.cards.${card.ident}\`. Follow \`certification/CopperGnomesIndependentCertificationTest.kt\`.`,
      `- \`certification/suites/${key}.json\`: \`{"key":"${key}","name":"${card.name.replace(/"/g, '\\"')}","issue":<THIS issue's number>,"source":"certification/${cls}.kt","testClass":"com.arcanatable.customcards.${cls}","tests":<number of test methods, 3-100>,"target":"build"}\`.`, '',
      '## Acceptance', '',
      '- Every ability, cost, restriction and timing in the Oracle text has a positive scenario, with at least one negative scenario.',
      '- No placeholders, approximations, skipped or weakened assertions.',
      `- \`Arcana Card Certification Premerge\` runs the suite against \`${target}\`; it merges automatically only when that run is green. If the card itself is wrong, keep the correct assertion and say exactly which one fails and why: that is a regression for the card lane, not a suite fix.`, '',
      '## Oracle record', '', '    ' + oracleLine, ''].join('\n'),
  };
}

// Exact-name Oracle record: Cardkit's offline cache first, then Scryfall. Multi-face cards join their faces.
async function oracleFor(name, { cache = '', fetchImpl = fetch } = {}) {
  const fields = r => ({ name: r.name, mana_cost: r.mana_cost ?? null, type_line: r.type_line, oracle_text: r.oracle_text,
    power: r.power ?? null, toughness: r.toughness ?? null, loyalty: r.loyalty ?? null, layout: r.layout ?? null });
  const usable = r => r && r.name === name && typeof r.oracle_text === 'string' && r.oracle_text.length > 0;
  const join = r => r && !r.oracle_text && Array.isArray(r.card_faces)
    ? { ...r, oracle_text: r.card_faces.map(f => `${f.name}: ${f.oracle_text || ''}`).join('\n//\n'), type_line: r.type_line || r.card_faces.map(f => f.type_line).join(' // ') } : r;
  for (const line of String(cache || '').split('\n')) {
    if (!line.includes(JSON.stringify(name).slice(1, -1))) continue;
    try { const p = JSON.parse(line); const r = join(p?.record || p?.card || p); if (usable(r)) return JSON.stringify(fields(r)); } catch {}
  }
  try {
    const res = await fetchImpl('https://api.scryfall.com/cards/named?exact=' + encodeURIComponent(name),
      { headers: { 'User-Agent': 'ArcanaTable-Certification/1.0', Accept: 'application/json' } });
    if (res.ok) { const r = join(await res.json()); if (usable(r)) return JSON.stringify(fields(r)); }
  } catch {}
  return null;
}

async function readText(github, owner, repo, path, ref) {
  try {
    const f = (await github.rest.repos.getContent({ owner, repo, path, ref })).data;
    return Array.isArray(f) ? f : Buffer.from(f.content, 'base64').toString('utf8');
  } catch (e) { if (e.status === 404) return null; throw e; }
}

async function dispatcherStatus({ github, owner, repo }) {
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: STATUS_ISSUE, per_page: 100 });
  const c = comments.filter(x => x.user?.login === BOT && String(x.body || '').startsWith(DISPATCHER_STATUS)).at(-1);
  const m = String(c?.body || '').match(/```json\n([\s\S]*?)\n```/);
  try { return m ? JSON.parse(m[1]) : null; } catch { return null; }
}

function readBurst(root = '.') {
  try { return JSON.parse(fs.readFileSync(root + '/certification/idle-burst.json', 'utf8')); } catch { return null; }
}

async function requestIdleSuite({ github, owner, repo, root = '.', manifest, now = Date.now(), dry = false, fetchImpl = fetch, burst = readBurst(root) }) {
  const status = await dispatcherStatus({ github, owner, repo });
  const requests = (await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'all', creator: BOT, per_page: 100 }))
    .filter(i => !i.pull_request && String(i.title || '').startsWith(SUITE_TITLE) && String(i.body || '').startsWith(SUITE_MARKER + '\n'));
  const early = plan({ cards: [], manifest, requests, status, now, burst });
  if (early.skip && early.skip !== 'every played custom card has a suite or a request') return { requested: null, reason: early.skip };
  const target = JSON.parse(fs.readFileSync(root + '/integration/policy.json', 'utf8')).target;
  const [registry, customIndex, libraryIndex, overrides] = await Promise.all([REGISTRY, ASSETS + 'custom-cards.index.tsv',
    ASSETS + 'all-cards.index.tsv', ASSETS + 'custom-overrides.txt'].map(p => readText(github, owner, repo, p, target)));
  const deckDir = await readText(github, owner, repo, 'android/app/src/main/assets/precons', target);
  const deckLists = [];
  for (const f of (Array.isArray(deckDir) ? deckDir : []).filter(f => f.name.endsWith('.txt')).slice(0, 40))
    deckLists.push(await readText(github, owner, repo, f.path, target));
  try {
    const six = JSON.parse(await readText(github, owner, repo, 'investigation/argentum-six-precons/deck-manifest.json', target) || '{}');
    for (const d of six.decks || []) deckLists.push((d.cards || []).map(c => `1 ${c.name}`).join('\n'));
  } catch {}
  const cards = playedCustomCards({ registry, customIndex, libraryIndex, overrides, deckLists });
  const p = plan({ cards, manifest, requests, status, now, burst });
  if (p.skip) return { requested: null, reason: p.skip, played: cards.length };
  const cache = await readText(github, owner, repo, 'tools/cardkit/data/oracle-cache.jsonl', target);
  const made = [], planned = [];
  for (const card of p.candidates.slice(0, 5 + p.slots)) {
    if (made.length + planned.length >= p.slots) break;
    const oracleLine = await oracleFor(card.name, { cache, fetchImpl });
    if (!oracleLine) continue;
    const req = idleRequest({ card, oracleLine, target });
    if (dry) { planned.push(req.title); continue; }
    const created = (await github.rest.issues.create({ owner, repo, title: req.title, body: req.body })).data;
    made.push({ number: created.number, card: card.name, decks: card.decks });
  }
  if (dry && planned.length) return { requested: null, planned: planned[0], plannedAll: planned, played: cards.length, today: p.today, burst: p.burst };
  if (!made.length) return { requested: null, reason: 'no Oracle record for the next candidates', played: cards.length };
  return { requested: made[0].number, card: made[0].card, decks: made[0].decks, all: made, played: cards.length, today: p.today + made.length, burst: p.burst };
}

module.exports = { requestIdleSuite, playedCustomCards, plan, activeBurst, readBurst, idleRequest, oracleFor, DAILY_CAP, idleMarker };
