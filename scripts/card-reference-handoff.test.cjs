const fs = require('node:fs');
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { appendReferencePacket, closestCustom, referencePacket, recipeFor } = require('./card-reference-handoff.cjs');

const IMPLEMENTATION = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/BloodArtist.kt';
const SCENARIO = 'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/BloodArtistScenarioTest.kt';
const COOKBOOK = [
  '# Card pattern cookbook',
  '## 4. Dies trigger',
  '<!-- excerpt: ' + IMPLEMENTATION + ' -->',
  'when dies, create a token',
].join('\n');
const SIMILAR = [
  'Whenever a creature dies, create a 1/1 token.',
  '  ~0.91 Blood Artist [custom]',
  '        trigger = Triggers.otherCreature.dies()',
  '        ' + IMPLEMENTATION + ' (test: ' + SCENARIO + ')',
].join('\n');
const CLASSIFY = JSON.stringify([{ name: 'Blood Artist', readiness: 'SUPPORTED', route: 'copilot', reasons: [] }]);
const BODY = [
  '## Cards', '- Blood Artist', '',
  '## Acceptance', '- exact Oracle text and scenario tests',
  'Base: gpt/arcana-integration-next', '',
  '## Offline Oracle record (trusted feeder snapshot)', '',
  '    {"name":"Blood Artist","type_line":"Creature","oracle_text":"Whenever a creature dies, create a 1/1 token."}',
].join('\n');

test('request packet includes classification, closest recipe, and a tested implementation plus scenario', () => {
  const packet = referencePacket({ classification: CLASSIFY, similar: SIMILAR, cookbook: COOKBOOK });
  assert.ok(packet.includes('Cardkit classify: ' + String.fromCharCode(96) + 'SUPPORTED' + String.fromCharCode(96)));
  assert.match(packet, /CARD-PATTERNS recipe: \[4\. Dies trigger\]/);
  assert.match(packet, /Blood Artist/);
  assert.ok(packet.includes(IMPLEMENTATION));
  assert.ok(packet.includes(SCENARIO));
  const body = appendReferencePacket(BODY, { classification: CLASSIFY, similar: SIMILAR, cookbook: COOKBOOK });
  assert.ok(body.length <= 7000);
  assert.ok(body.includes('"oracle_text":"Whenever a creature dies, create a 1/1 token."'));
  assert.match(body, /## Cardkit starting packet/);
});

test('no-good-match is stated plainly without inventing a recipe or example', () => {
  assert.equal(closestCustom('line\n  ~0.98 Upstream Card [library]\n        code\n        Argentum library card'), null);
  const packet = referencePacket({ classification: CLASSIFY, similar: '', cookbook: COOKBOOK });
  assert.match(packet, /No close, tested custom-card match was found/);
  assert.doesNotMatch(packet, /Closest finished custom-card example/);
});

test('rejects absent oracle handoff, repeated packet, and oversized request body', () => {
  const inputs = { classification: CLASSIFY, similar: SIMILAR, cookbook: COOKBOOK };
  assert.throws(() => appendReferencePacket('## Cards\\n', inputs), /oracle-handoff-required/);
  assert.throws(() => appendReferencePacket(BODY + '\\n' + 'x'.repeat(7000), inputs), /size-limit/);
  assert.throws(() => appendReferencePacket(BODY + '\\n\\n## Cardkit starting packet', inputs), /already-attached/);
});

test('nearest cookbook-backed custom example wins over a closer example with no recipe', () => {
  const secondFile = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Other.kt';
  const secondTest = 'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/OtherScenarioTest.kt';
  const similar = [
    'line one',
    '  ~0.99 Unlisted [custom]',
    '        Effects.destroy(target)',
    '        android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Unlisted.kt (test: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/UnlistedScenarioTest.kt)',
    'line two',
    '  ~0.81 Other [custom]',
    '        Effects.destroy(target)',
    '        ' + secondFile + ' (test: ' + secondTest + ')',
  ].join('\\n');
  const cookbook = COOKBOOK + '\\n<!-- excerpt: ' + secondFile + ' -->';
  assert.equal(closestCustom(similar, cookbook).card, 'Other');
});

test('recipe selection only links the cookbook section that contains the matched implementation', () => {
  assert.deepEqual(recipeFor(COOKBOOK, IMPLEMENTATION), {
    heading: '4. Dies trigger', link: 'https://github.com/AARMOU/arcana_table/blob/gpt/arcana-integration-next/docs/agents/CARD-PATTERNS.md#4-dies-trigger',
  });
  assert.equal(recipeFor(COOKBOOK, 'android/custom-cards/Other.kt'), null);
});

test('trusted read-only feeder generates and attaches the Cardkit packet before publish', () => {
  const workflow = fs.readFileSync('.github/workflows/arcana-queue-feeder.yml', 'utf8');
  const suggest = workflow.slice(workflow.indexOf('  suggest:'), workflow.indexOf('  publish:'));
  assert.match(suggest, /cardkit\.py classify --json/);
  assert.match(suggest, /cardkit\.py similar/);
  assert.match(suggest, /card-reference-handoff\.cjs append/);
  assert.match(suggest, /card-reference-handoff\.cjs packet/);
  assert.match(suggest, /CARD-PATTERNS\.md/);
  assert.match(suggest, /referencePacket:packetFor\.get/);
});
