'use strict';
// Feed Copilot the exact cached Scryfall record that Cardkit used in the trusted
// read-only feeder checkout. Copilot sandboxes often cannot reach Scryfall.
// This never changes card eligibility, paid dispatch, or authoritative rules.
const fs = require('node:fs');
const KEYS = ['name', 'mana_cost', 'type_line', 'oracle_text', 'power', 'toughness',
  'loyalty', 'defense', 'colors', 'color_identity', 'keywords', 'rarity',
  'set', 'collector_number', 'artist', 'layout', 'scryfall_uri', 'faces'];
const norm = s => String(s || '').trim().replace(/\s+/g, ' ').toLowerCase();

function oracleSnapshot(raw, expectedName) {
  if (typeof raw !== 'string' || raw.length > 20000 ||
      typeof expectedName !== 'string' || !expectedName.trim()) throw Error('invalid-oracle-input');
  let card;
  try { card = JSON.parse(raw); } catch { throw Error('invalid-oracle-json'); }
  if (!card || typeof card !== 'object' || Array.isArray(card)) throw Error('missing-oracle-record');
  const actual = norm(card.name);
  const requested = norm(expectedName);
  if (!actual || (actual !== requested && !actual.startsWith(requested + ' // ')))
    throw Error('oracle-card-name-mismatch');
  if (typeof card.type_line !== 'string' || !card.type_line.trim() ||
      typeof card.oracle_text !== 'string' || !card.oracle_text.trim())
    throw Error('incomplete-oracle-rules');
  let uri;
  try { uri = new URL(card.scryfall_uri); } catch { throw Error('invalid-oracle-source'); }
  if (uri.protocol !== 'https:' || uri.hostname !== 'scryfall.com' ||
      !uri.pathname.startsWith('/card/') || uri.username || uri.password)
    throw Error('invalid-oracle-source');
  const record = {};
  for (const k of KEYS) if (Object.hasOwn(card, k)) record[k] = card[k];
  const jsonl = JSON.stringify(record);
  if (jsonl.length > 12000 || /[\r\n]/.test(jsonl)) throw Error('oversize-oracle-record');
  return jsonl;
}

function appendOracle(body, raw, expectedName) {
  if (typeof body !== 'string' || !body.includes('## Cards') ||
      !body.includes('## Acceptance') || !/^Base:\s*\S+/m.test(body))
    throw Error('invalid-cardkit-unit');
  const jsonl = oracleSnapshot(raw, expectedName);
  if (body.includes('## Offline Oracle record')) throw Error('oracle-record-already-attached');
  return body.trimEnd() + '\n\n' +
    '## Offline Oracle record (trusted feeder snapshot)\n\n' +
    'The trusted Cardkit feeder read this Scryfall cache record while verifying this card on its ' +
    'integration base. This is card data, not evidence of rules-playability or permission to bypass tests.\n\n' +
    'If your Copilot environment cannot reach Scryfall, use the exact JSONL line below to seed ' +
    'tools/cardkit/data/oracle-cache.jsonl in your isolated working checkout, then run Cardkit ' +
    'offline. Do not guess missing card text or weaken acceptance tests. ' +
    'Only commit cache changes when needed and justified in your reviewed card PR.\n\n' +
    '    ' + jsonl + '\n';
}

if (require.main === module) {
  const [unitPath, oraclePath, cardName] = process.argv.slice(2);
  try {
    if (!unitPath || !oraclePath || !cardName) throw Error('usage: oracle-handoff UNIT ORACLE NAME');
    const augmented = appendOracle(fs.readFileSync(unitPath, 'utf8'),
      fs.readFileSync(oraclePath, 'utf8'), cardName);
    fs.writeFileSync(unitPath, augmented, 'utf8');
  } catch (e) {
    console.error('Oracle handoff rejected: ' + e.message);
    process.exitCode = 1;
  }
}
module.exports = { oracleSnapshot, appendOracle };
