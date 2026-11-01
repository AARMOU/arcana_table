'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const DP = require('./destination-preflight.cjs');

const S = c => c.repeat(40);
const bot = { id: 41898282, login: 'github-actions[bot]' };
const libs = jarSha => ({ data: DP.JARS.map(name => ({ name, sha: jarSha(name) })) });
const githubWith = byRef => ({ rest: { repos: { getContent: async ({ ref }) => libs(name => byRef[ref](name)) } } });
const receipt = (over = {}) => ({ schema: 1, pr: 662, sourceSha: S('a'), baseSha: S('b'), engineFingerprint: 'f'.repeat(64),
  status: 'blocked', reason: 'export: Command failed', runUrl: 'https://github.com/AARMOU/arcana_table/actions/runs/1', ...over });
const comment = r => ({ user: bot, body: DP.receiptBody(r) });

test('engine-bound reasons are recognized; ordinary test failures are not', () => {
  assert.ok(DP.engineBound('export: Command failed: ./gradlew :custom-cards:exportCards'));
  assert.ok(DP.engineBound('independent: java.lang.IllegalArgumentException: Card not found in registry: Koma, Cosmos Serpent'));
  assert.ok(DP.engineBound("PhyrexianTriniform.kt:27:5 Unresolved reference 'encore'"));
  assert.ok(!DP.engineBound('scenario: expected 3 permanents, got 1'));
  assert.ok(!DP.engineBound(''));
});

test('fingerprint depends only on the four engine JARs and fails closed when one is missing', async () => {
  const g = githubWith({ x: n => S('1'), y: n => S('1'), z: n => n === 'argentum-ai.jar' ? S('2') : S('1') });
  assert.equal(await DP.engineFingerprint({ github: g, owner: 'o', repo: 'r', ref: 'x' }), await DP.engineFingerprint({ github: g, owner: 'o', repo: 'r', ref: 'y' }));
  assert.notEqual(await DP.engineFingerprint({ github: g, owner: 'o', repo: 'r', ref: 'x' }), await DP.engineFingerprint({ github: g, owner: 'o', repo: 'r', ref: 'z' }));
  const missing = { rest: { repos: { getContent: async () => ({ data: [{ name: 'argentum-ai.jar', sha: S('1') }] }) } } };
  await assert.rejects(DP.engineFingerprint({ github: missing, owner: 'o', repo: 'r', ref: 'x' }));
});

test('receipts parse only from the bot and only bind to the same head and engine', () => {
  const r = receipt();
  assert.deepEqual(DP.parse(comment(r)), r);
  assert.equal(DP.parse({ user: { id: 1, login: 'someone' }, body: DP.receiptBody(r) }), null);
  assert.equal(DP.parse({ user: bot, body: 'hello' }), null);
  const comments = [comment(receipt({ status: 'blocked' })), comment(receipt({ status: 'passed' }))];
  assert.equal(DP.latestFor(comments, S('a'), 'f'.repeat(64)).status, 'passed');
  assert.equal(DP.latestFor(comments, S('c'), 'f'.repeat(64)), null, 'a new card commit needs a new preflight');
  assert.equal(DP.latestFor(comments, S('a'), 'e'.repeat(64)), null, 'a new target engine needs a new preflight');
});

test('integration skips an engine-blocked card only while the target engine is unchanged', async () => {
  const same = githubWith({ [S('b')]: () => S('1'), [S('d')]: () => S('1') });
  const changed = githubWith({ [S('b')]: () => S('1'), [S('d')]: () => S('9') });
  const args = (github, r, baseSha = S('d')) => ({ github, owner: 'o', repo: 'r', receipt: r, baseSha });
  assert.equal(await DP.engineUnchangedSince(args(same, receipt())), true);
  assert.equal(await DP.engineUnchangedSince(args(changed, receipt())), false, 'new engine: re-run');
  assert.equal(await DP.engineUnchangedSince(args(same, receipt({ reason: 'scenario: expected 3, got 1' }))), false, 'non-engine block keeps re-running');
  assert.equal(await DP.engineUnchangedSince(args(same, receipt({ status: 'integrated' }))), false);
  assert.equal(await DP.engineUnchangedSince(args(same, receipt(), S('b'))), false, 'same base is handled by the existing rule');
  const broken = { rest: { repos: { getContent: async () => { throw Object.assign(Error('x'), { status: 500 }); } } } };
  assert.equal(await DP.engineUnchangedSince(args(broken, receipt())), false, 'unknown never silences a card');
});

test('publish refuses a stale head and posts a well-formed receipt', async () => {
  const I = require('./card-integration.cjs');
  const plan = { schema: 1, pr: 690, sourceSha: S('a'), baseSha: S('b'), target: I.TARGET, engineFingerprint: 'f'.repeat(64), policyHash: 'p' };
  const crypto = require('node:crypto');
  const report = { schema: 1, status: 'blocked', planHash: crypto.createHash('sha256').update(JSON.stringify(plan)).digest('hex'),
    gates: I.GATES.map(name => ({ name, ok: name !== 'independent', detail: name === 'independent' ? 'Card not found in registry: Koma' : '' })) };
  const posted = [];
  const gh = head => ({ rest: { pulls: { get: async () => ({ data: { head: { sha: head }, state: 'open' } }) },
    issues: { createComment: async x => posted.push(x) } } });
  assert.equal((await DP.publish({ github: gh(S('c')), owner: 'o', repo: 'r', plan, report, runUrl: 'https://github.com/AARMOU/arcana_table/actions/runs/5' })).state, 'stale');
  assert.equal(posted.length, 0);
  const r = await DP.publish({ github: gh(S('a')), owner: 'o', repo: 'r', plan, report, runUrl: 'https://github.com/AARMOU/arcana_table/actions/runs/5' });
  assert.equal(r.status, 'blocked');
  assert.match(r.reason, /independent: Card not found in registry: Koma/);
  const parsed = DP.parse({ user: bot, body: posted[0].body });
  assert.equal(parsed.status, 'blocked');
  assert.match(posted[0].body, /held back from the owner's Verify list/);
});
