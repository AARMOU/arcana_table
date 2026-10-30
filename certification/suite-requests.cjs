'use strict';
// Turns "this Copilot card PR has no certification suite" into work: one [Arcana Suite] issue per
// card unit, opened by github-actions[bot]. The dispatcher (scripts/dispatch-runner.cjs) assigns it
// to Copilot on main through the same admission/budget gate as card work, and
// .github/workflows/arcana-suite-automerge.yml merges the suite PR only after a green premerge.
const { SUITE_TITLE, SUITE_MARKER, SUITE_BASE } = require('../scripts/admission.cjs');

const BOT = 'github-actions[bot]';
const unitMarker = issue => `<!-- arcana-suite-for-unit:${issue} -->`;

// "Shield Sphere" -> key shield-sphere, class ShieldSphereIndependentCertificationTest.
const words = name => String(name).normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/['’]/g, '').split(/[^A-Za-z0-9]+/).filter(Boolean);
const suiteKey = name => words(name).join('-').toLowerCase();
const pascal = name => words(name).map(w => w[0].toUpperCase() + w.slice(1)).join('');

// The single card a queue unit names, or null when it names none or several.
function cardOf(body) {
  const m = String(body || '').match(/^##\s+Cards\s*$([\s\S]*?)(?=^##\s|$(?![\s\S]))/m);
  const cards = (m ? m[1] : '').split(/\r?\n/).map(l => (l.match(/^\s*[-*]\s+(.+?)\s*$/) || [])[1]).filter(Boolean);
  return cards.length === 1 ? cards[0] : null;
}

// The feeder's offline Oracle record (one indented JSONL line) for exactly this card.
function oracleRecord(body, name) {
  for (const line of String(body || '').split(/\r?\n/)) {
    const t = line.trim();
    if (!t.startsWith('{') || !t.includes('"oracle_text"')) continue;
    try { const r = JSON.parse(t); if (r?.name === name && typeof r.oracle_text === 'string') return { line: t, record: r }; } catch {}
  }
  return null;
}

function suiteRequest({ pr, issue, name, oracle }) {
  const key = suiteKey(name), cls = pascal(name) + 'IndependentCertificationTest';
  return {
    key,
    title: `${SUITE_TITLE}${name} — independent certification suite for #${pr}`,
    body: [SUITE_MARKER, unitMarker(issue), '',
      `Card unit: #${issue}`, `Card PR: #${pr}`, `Suite key: ${key}`, `Base: ${SUITE_BASE}`, '',
      '## Task', '',
      `Write the independent certification suite for **${name}**, strictly from the Oracle record below.`,
      `Do **not** open, fetch, diff or read card PR #${pr} or its branch: the suite must not depend on that implementation.`,
      'Test helpers live in `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/support/` on `gpt/arcana-integration-next`; existing suites in `certification/` show how they are used.', '',
      'Add exactly these two files, and nothing else:', '',
      `- \`certification/${cls}.kt\`: class \`com.arcanatable.customcards.${cls}\`, importing the card object \`com.arcanatable.customcards.cards.${pascal(name)}\`. Follow \`certification/CopperGnomesIndependentCertificationTest.kt\`.`,
      `- \`certification/suites/${key}.json\`: \`{"key":"${key}","name":"${name.replace(/"/g, '\\"')}","issue":${issue},"source":"certification/${cls}.kt","testClass":"com.arcanatable.customcards.${cls}","tests":<number of @Test methods, 3-100>}\`. Follow \`certification/suites/copper-gnomes.json\`.`, '',
      '## Acceptance', '',
      '- Every ability, cost, restriction and timing in the Oracle text has a positive scenario, with at least one negative scenario.',
      '- No placeholders, approximations, skipped or weakened assertions.',
      `- \`Arcana Card Certification Premerge\` runs the suite against card PR #${pr}; it merges automatically only when that run is green.`, '',
      '## Oracle record', '', '    ' + oracle.line, ''].join('\n'),
  };
}

// For each missing-suite entry: an existing request (open or closed) wins; otherwise one is opened.
// Entries that cannot be requested exactly (engine gap, no single card, no Oracle record) are left for the #81 report.
async function requestSuites({ github, owner, repo, list, dry = false }) {
  const mine = (await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'all', creator: BOT, per_page: 100 }))
    .filter(i => !i.pull_request && String(i.title || '').startsWith(SUITE_TITLE) && String(i.body || '').startsWith(SUITE_MARKER + '\n'));
  for (const m of list) {
    if (m.engineGap) continue;
    const existing = mine.find(i => String(i.body).includes(unitMarker(m.issue)));
    if (existing) { m.request = { issue: existing.number, state: existing.state }; continue; }
    const name = cardOf(m.unitBody);
    const oracle = name && oracleRecord(m.unitBody, name);
    if (!oracle) { m.request = { skipped: name ? 'no Oracle record on the queue issue' : 'queue issue does not name exactly one card' }; continue; }
    const req = suiteRequest({ pr: m.pr, issue: m.issue, name, oracle });
    if (dry) { m.request = { planned: req.title }; continue; }
    const created = (await github.rest.issues.create({ owner, repo, title: req.title, body: req.body })).data;
    mine.push(created);
    m.request = { issue: created.number, state: 'open', created: true };
  }
  return list;
}

module.exports = { requestSuites, suiteRequest, cardOf, oracleRecord, suiteKey, pascal, unitMarker };
