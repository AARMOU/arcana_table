'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { appendOracle, oracleSnapshot } = require('./oracle-handoff.cjs');
const { unitActionable } = require('./admission.cjs');
const BASE = 'gpt/arcana-integration-next';
const name = 'Copper Gnomes';
const card = {
  name, mana_cost: '{2}', type_line: 'Artifact Creature — Gnome',
  oracle_text: '{4}, Sacrifice Copper Gnomes: You may put an artifact card from your hand onto the battlefield.',
  color_identity: [], keywords: [], set: 'uz', collector_number: '1',
  scryfall_uri: 'https://scryfall.com/card/uz/1/copper-gnomes?utm_source=api',
};
const unit = ['## Cards', '- Copper Gnomes', '', '## Acceptance',
  '- Copper Gnomes: Put an artifact card from your hand onto the battlefield.',
  '- Exact current Oracle behavior and executable positive/negative tests and scenarios.',
  '', 'Base: ' + BASE, ''].join('\n');

test('appends the exact offline JSONL record without weakening the verified unit', () => {
  const body = appendOracle(unit, JSON.stringify(card, null, 2), name);
  assert.match(body, /## Offline Oracle record \(trusted feeder snapshot\)/);
  assert.match(body, /Copilot environment cannot reach Scryfall/);
  assert.equal(unitActionable(body, BASE).ok, true);
  const raw = body.split('\n').find(l => l.startsWith('    {"name":'));
  assert.deepEqual(JSON.parse(raw.trim()), card);
  assert.equal((body.match(/## Cards/g) || []).length, 1);
  assert.equal((body.match(/^Base:/gm) || []).length, 1);
});

test('fails closed for missing, wrong-name, or incomplete Oracle records', () => {
  for (const raw of ['null', '{}', '{"name":"Copper Gnomes"}', '{garbled']) {
    assert.throws(() => appendOracle(unit, raw, name));
  }
  assert.throws(() => appendOracle(unit, JSON.stringify({ ...card, name: 'Shield Sphere' }), name),
    /name-mismatch/);
  assert.throws(() => appendOracle(unit, JSON.stringify({ ...card, oracle_text: '' }), name),
    /incomplete/);
  assert.throws(() => appendOracle(unit, JSON.stringify({ ...card, type_line: '' }), name),
    /incomplete/);
});

test('rejects untrusted links and oversize data instead of queuing a paid worker', () => {
  for (const uri of ['https://scryfall.com.evil.test/card/xx/1', 'http://scryfall.com/card/xx/1',
    'https://evil.test/card/xx/1', 'https://scryfall.com/search?q=foo']) {
    assert.throws(() => oracleSnapshot(JSON.stringify({ ...card, scryfall_uri: uri }), name),
      /invalid-oracle-source/);
  }
  assert.throws(() => oracleSnapshot(JSON.stringify({ ...card, oracle_text: 'x'.repeat(12500) }), name),
    /oversize-oracle-record/);
});

test('supports exact double-face/front-face identity without treating a different card as same', () => {
  const front = { ...card, name: 'Front // Back', oracle_text: 'Front ability',
    faces: [{ name: 'Front', oracle_text: 'Front ability' }, { name: 'Back', oracle_text: 'Back ability' }] };
  assert.match(oracleSnapshot(JSON.stringify(front), 'Front'), /"faces"/);
  assert.throws(() => oracleSnapshot(JSON.stringify(front), 'Another'), /name-mismatch/);
});

test('refuses malformed units and repeated injection', () => {
  const body = appendOracle(unit, JSON.stringify(card), name);
  assert.throws(() => appendOracle(body, JSON.stringify(card), name), /already-attached/);
  assert.throws(() => appendOracle('## Cards\n- Copper Gnomes\n', JSON.stringify(card), name),
    /invalid-cardkit-unit/);
});

test('trusted workflow attaches the Oracle record in the no-secret feeder before issue creation', () => {
  const workflow = fs.readFileSync('.github/workflows/arcana-queue-feeder.yml', 'utf8');
  const suggest = workflow.slice(workflow.indexOf('  suggest:'), workflow.indexOf('  publish:'));
  const publish = workflow.slice(workflow.indexOf('  publish:'));
  assert.match(suggest, /cardkit\.py oracle show -- "\$name"/);
  assert.match(suggest, /node ctl\/scripts\/oracle-handoff\.cjs/);
  assert.match(suggest, /rm -f "units\/\$i\.body"/);
  assert.ok(!/\$\{\{\s*secrets\./.test(suggest));
  assert.match(publish, /Q\.createNextAutoQueue\(/);
});
