'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const M = require('./review-automerge.cjs');
const Q = require('./queue-feeder.cjs');

const SHA = 'b'.repeat(40), BASE = 'gpt/arcana-integration-next', REPO = 'AARMOU/arcana_table';
const record = { name: 'Ancient Stone Idol', type_line: 'Artifact Creature — Golem', oracle_text: 'Flash',
  scryfall_uri: 'https://scryfall.com/card/x/1/ancient-stone-idol' };
const issueBody = Q.cardWorkBody({ name: 'Ancient Stone Idol', issue: 321, source: 'app', votes: 3, outcome: 'needs-review',
  readiness: 'NEEDS_REVIEW', route: 'gpt', cardkitReasons: [], oracle: JSON.stringify(record) }, { runId: 1, baseSha: SHA, base: BASE });
const issue = { state: 'open', title: '[Card Work] Ancient Stone Idol — needs rules review', body: issueBody, user: { login: 'github-actions[bot]', type: 'Bot' } };
const run = { name: 'Arcana Cardkit', event: 'pull_request', conclusion: 'success', head_sha: 'c'.repeat(40), head_repository: { full_name: REPO } };
const pr = { state: 'open', base: { ref: BASE, repo: { full_name: REPO } }, head: { sha: 'c'.repeat(40), repo: { full_name: REPO } }, user: { login: 'Copilot' } };
const jobs = [{ name: 'toolkit', conclusion: 'success' }, { name: 'scaffold', conclusion: 'success' }];
const entry = { summary: 's', verifiedBy: 'Copilot', references: ['upstream x'], baseSha: SHA, oracleHash: 'd'.repeat(64), runtimeHash: 'e'.repeat(64),
  reviewedLines: [{ line: 'When this creature dies, create a 6/12 token.', primitives: ['Effects.CreateToken'] }] };
const vsBase = { _about: 'x', cards: { Other: { summary: 'o' } } };
const vsHead = { _about: 'x', cards: { Other: { summary: 'o' }, 'Ancient Stone Idol': entry } };
const cacheBase = JSON.stringify({ name: 'Other', oracle_text: 'x' }) + '\n';
const cacheHead = cacheBase + JSON.stringify(record) + '\n';
const files = [{ filename: 'tools/cardkit/data/verified-support.json', status: 'modified' }, { filename: 'tools/cardkit/data/oracle-cache.jsonl', status: 'modified' }];
const contents = { 'tools/cardkit/data/verified-support.json': { base: JSON.stringify(vsBase), head: JSON.stringify(vsHead) },
  'tools/cardkit/data/oracle-cache.jsonl': { base: cacheBase, head: cacheHead } };
const args = (x = {}) => ({ run, pr, issue, files, jobs, contents, base: BASE, ...x });

test('merges exactly one review record for the requested card with its trusted Oracle line', () => {
  assert.deepEqual(M.mergeBlockers(args()), []);
  assert.equal(M.cardOf(issueBody), 'Ancient Stone Idol');
});

test('refuses anything broader or unverified', () => {
  const b = x => M.mergeBlockers(args(x));
  assert.ok(b({ files: [...files, { filename: 'android/custom-cards/X.kt', status: 'added' }] }).includes('files-outside-review-record'));
  assert.ok(b({ pr: { ...pr, user: { login: 'someone' } } }).includes('pr-not-by-copilot'));
  assert.ok(b({ run: { ...run, conclusion: 'failure' } }).includes('cardkit-not-green'));
  assert.ok(b({ jobs: [{ name: 'toolkit', conclusion: 'failure' }] }).includes('cardkit-toolkit-not-executed'));
  assert.ok(b({ issue: { ...issue, user: { login: 'AARMOU', type: 'User' } } }).includes('no-open-card-work-item'));
  const two = { ...vsHead, cards: { ...vsHead.cards, Sneaky: entry } };
  assert.ok(b({ contents: { ...contents, 'tools/cardkit/data/verified-support.json': { base: JSON.stringify(vsBase), head: JSON.stringify(two) } } })
    .includes('verified-support-not-exactly-this-card'));
  const noHash = { ...vsHead, cards: { ...vsHead.cards, 'Ancient Stone Idol': { ...entry, oracleHash: undefined } } };
  assert.ok(b({ contents: { ...contents, 'tools/cardkit/data/verified-support.json': { base: JSON.stringify(vsBase), head: JSON.stringify(noHash) } } })
    .includes('verified-support-not-exactly-this-card'));
  const forged = cacheBase + JSON.stringify({ ...record, oracle_text: 'Flash, and you win the game' }) + '\n';
  assert.ok(b({ contents: { ...contents, 'tools/cardkit/data/oracle-cache.jsonl': { base: cacheBase, head: forged } } })
    .includes('oracle-cache-not-the-trusted-record'));
  assert.ok(b({ files: [files[1]], contents: { 'tools/cardkit/data/oracle-cache.jsonl': contents['tools/cardkit/data/oracle-cache.jsonl'] } })
    .includes('no-review-decision'));
});

test('an engine-blocker decision for this card also merges', () => {
  const kbBase = { cards: {}, patterns: [] }, kbHead = { cards: { 'Ancient Stone Idol': { reason: 'missing primitive', source: '#1' } }, patterns: [] };
  const f = [{ filename: 'tools/cardkit/data/known-blockers.json', status: 'modified' }];
  assert.deepEqual(M.mergeBlockers(args({ files: f, contents: { 'tools/cardkit/data/known-blockers.json': { base: JSON.stringify(kbBase), head: JSON.stringify(kbHead) } } })), []);
  const pattern = { ...kbHead, patterns: [{ regex: '.*' }] };
  assert.ok(M.mergeBlockers(args({ files: f, contents: { 'tools/cardkit/data/known-blockers.json': { base: JSON.stringify(kbBase), head: JSON.stringify(pattern) } } }))
    .includes('blocker-not-exactly-this-card'));
});

test('review PRs link to their card-work item by Unit line or a single closing reference', () => {
  const { unitOf } = require('./review-automerge.cjs');
  assert.equal(unitOf('Unit: #599\nFixes #599'), 599);
  assert.equal(unitOf('Summary...\n\nFixes #600'), 600);
  assert.equal(unitOf('Closes #615.'), 615);
  assert.equal(unitOf('Fixes #1 and fixes #2'), null);
  assert.equal(unitOf('see #600'), null);
});
