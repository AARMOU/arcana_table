'use strict';
const fs = require('node:fs');
const { oracleSnapshot } = require('./oracle-handoff.cjs');
const T = String.fromCharCode(96);

const MAX_REQUEST_CHARS = 7000;
const MIN_SIMILARITY = 0.5;
const RECIPE_DOC = 'docs/agents/CARD-PATTERNS.md';

function classifyResult(raw) {
  let value;
  try { value = JSON.parse(raw); } catch { throw Error('invalid-cardkit-classification'); }
  const v = Array.isArray(value) ? value[0] : value;
  if (!v || typeof v.readiness !== 'string' || !v.readiness) throw Error('missing-cardkit-classification');
  return v;
}
function closestCustom(similar) {
  const lines = String(similar || '').split(/\r?\n/);
  const found = [];
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^\s+~(\d+(?:\.\d+)?)\s+(.+?)\s+\[custom\]\s*$/);
    if (!m) continue;
    const codeLine = (lines[i + 1] || '').trim();
    const where = (lines[i + 2] || '').trim();
    const paths = where.match(/^([^\s]+\.kt)\s+\(test:\s*([^\s]+ScenarioTest\.kt)\)$/);
    if (!paths || !/^android\/custom-cards\/src\/main\/kotlin\//.test(paths[1]) ||
        !/^android\/custom-cards\/src\/test\/kotlin\//.test(paths[2])) continue;
    const score = Number(m[1]);
    if (!Number.isFinite(score) || score < MIN_SIMILARITY) continue;
    found.push({ score, card: m[2], codeLine, implementation: paths[1], scenarioTest: paths[2] });
  }
  return found.sort((a, b) => b.score - a.score || a.card.localeCompare(b.card))[0] || null;
}
function recipeFor(cookbook, implementation) {
  const source = String(cookbook || '');
  const marker = '<!-- excerpt: ' + implementation + ' -->';
  const at = source.indexOf(marker);
  if (at < 0) return null;
  const before = source.slice(0, at);
  const headings = [...before.matchAll(/^##\s+(.+)\s*$/gm)];
  const heading = headings.at(-1)?.[1]?.trim();
  if (!heading) return null;
  const slug = heading.replace(new RegExp(T, 'g'), '').toLowerCase().replace(/[^a-z0-9\s-]/g, '')
    .trim().replace(/\s+/g, '-');
  return { heading, link: RECIPE_DOC + '#' + slug };
}
function referencePacket({ classification, similar, cookbook, base = 'gpt/arcana-integration-next' }) {
  const v = classifyResult(classification);
  const best = closestCustom(similar);
  const out = [
    '## Cardkit starting packet',
    '',
    'Cardkit classify: ' + T + v.readiness + T + (v.route ? ' (' + T + v.route + T + ')' : '') + '.',
  ];
  if (best) {
    const recipe = recipeFor(cookbook, best.implementation);
    out.push(recipe
      ? 'Closest verified CARD-PATTERNS recipe: [' + recipe.heading + '](' + recipe.link + ').'
      : 'Closest verified CARD-PATTERNS recipe: no cookbook section contains the closest example; use the example below and do not guess a recipe.');
    out.push('Closest finished custom-card example: **' + best.card + '** (Cardkit similar score ' + best.score.toFixed(2) + ').');
    out.push('- Implementation: ' + T + best.implementation + T);
    out.push('- Scenario test: ' + T + best.scenarioTest + T);
    if (best.codeLine) out.push('- Matching implementation line: ' + T + best.codeLine.replace(new RegExp(T, 'g'), "'") + T);
  } else {
    out.push('No close, tested custom-card match was found by ' + T + 'cardkit similar' + T + '; no recipe or example is guessed.');
  }
  out.push('', 'References come from the integration-base Cardkit checkout at ' + T + base + T + '; they guide implementation and do not replace scenario tests.', '');
  return out.join('\n');
}
function appendReferencePacket(body, inputs) {
  if (typeof body !== 'string' || !body.includes('## Offline Oracle record (trusted feeder snapshot)'))
    throw Error('oracle-handoff-required-before-card-context');
  if (body.includes('## Cardkit starting packet')) throw Error('cardkit-packet-already-attached');
  const result = body.trimEnd() + '\n\n' + referencePacket(inputs);
  if (result.length > MAX_REQUEST_CHARS) throw Error('card-request-exceeds-size-limit');
  return result;
}
if (require.main === module) {
  const [mode, bodyPath, oraclePath, classifyPath, similarPath, cookbookPath, name] = process.argv.slice(2);
  try {
    if (!mode || !oraclePath || !classifyPath || !similarPath || !cookbookPath || !name)
      throw Error('usage: card-reference-handoff append BODY ORACLE CLASSIFY SIMILAR COOKBOOK NAME | packet ORACLE CLASSIFY SIMILAR COOKBOOK NAME');
    oracleSnapshot(fs.readFileSync(oraclePath, 'utf8'), name);
    const inputs = { classification: fs.readFileSync(classifyPath, 'utf8'),
      similar: fs.existsSync(similarPath) ? fs.readFileSync(similarPath, 'utf8') : '',
      cookbook: fs.readFileSync(cookbookPath, 'utf8') };
    if (mode === 'append') {
      if (!bodyPath) throw Error('missing-unit-body');
      fs.writeFileSync(bodyPath, appendReferencePacket(fs.readFileSync(bodyPath, 'utf8'), inputs), 'utf8');
    } else if (mode === 'packet') process.stdout.write(referencePacket(inputs));
    else throw Error('unknown-card-context-mode');
  } catch (e) {
    console.error('Cardkit handoff rejected: ' + e.message);
    process.exitCode = 1;
  }
}
module.exports = { MAX_REQUEST_CHARS, closestCustom, recipeFor, referencePacket, appendReferencePacket };
