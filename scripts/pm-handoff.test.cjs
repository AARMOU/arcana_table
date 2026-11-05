'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { relayReviewHandoffs, requestsFrom, marker } = require('./pm-handoff.cjs');
const owner = 'AARMOU', repo = 'arcana_table';
const NOW = Date.parse('2026-11-05T18:00:00Z'), HEAD = 'a'.repeat(40);
function valid(pr = 933, sha = HEAD, id = 5) {
  return { id, created_at: '2026-11-05T17:00:00Z', author_association: 'OWNER',
    user: { login: owner },
    body: '<!-- arcana-handoff-v1 to=goose kind=pr-review pr=' + pr + ' sha=' + sha + ' -->' };
}
function mock(source = [valid()], target = [], options = {}) {
  const made = [], gets = [];
  const github = {
    rest: {
      issues: { listComments: () => {}, async createComment(args) {
        made.push(args);
        // Model real GITHUB_TOKEN behavior: the persisted comment is authored
        // by github-actions[bot], NOT by the repository owner.
        target.push({ id: 1000 + made.length, body: args.body,
          user: { login: 'github-actions[bot]', type: 'Bot' },
          author_association: 'NONE' });
        return { data: { id: 1000 + made.length } };
      } },
      pulls: { async get(args) {
        gets.push(args);
        return { data: { state: options.state || 'open', head: { sha: options.sha || HEAD },
          base: { repo: { full_name: options.fullName || 'AARMOU/arcana_table' } } } };
      } },
    },
    async paginate(_method, args, callback) {
      callback({ data: args.issue_number === 949 ? source : target }, () => {});
    },
  };
  return { github, made, gets };
}
const run = h => relayReviewHandoffs({ github: h.github, owner, repo, now: () => NOW });

test('valid owner marker produces an exact-head review request, not a paid action', async () => {
  const h = mock(), out = await run(h);
  assert.equal(out.sent, 1);
  assert.equal(h.gets[0].pull_number, 933);
  assert.equal(h.made[0].issue_number, 950);
  assert.ok(h.made[0].body.startsWith(marker(933, HEAD)));
  assert.match(h.made[0].body, /does not assert passing tests/);
  assert.doesNotMatch(h.made[0].body, /@copilot|approved to merge/);
});
test('existing matching receipt causes no duplicate write or extra PR query', async () => {
  const h = mock([valid(), valid(933, HEAD, 7)], [{ body: marker(933, HEAD) + '\nDelivered',
    user: { login: 'github-actions[bot]', type: 'Bot' }, author_association: 'NONE' }]);
  assert.equal((await run(h)).sent, 0);
  assert.equal(h.made.length, 0);
  assert.equal(h.gets.length, 0);
});
test('two actual scheduled runs post only one bot-authored receipt', async () => {
  const h = mock();
  const first = await run(h);
  assert.equal(first.sent, 1);
  assert.equal(h.made.length, 1);
  assert.equal(h.gets.length, 1);
  const second = await run(h);
  assert.equal(second.sent, 0, 'same PR and SHA must not be posted twice');
  assert.equal(h.made.length, 1, 'no second bot comment');
  assert.equal(h.gets.length, 1, 'no second PR query');
});
test('forged Actions-login without Bot type cannot suppress a real handoff', async () => {
  const h = mock([valid()], [{
    body: marker(933, HEAD), user: { login: 'github-actions[bot]', type: 'User' },
    author_association: 'OWNER',
  }]);
  assert.equal((await run(h)).sent, 1);
});
test('owner-authored receipt cannot impersonate GitHub Actions relay', async () => {
  const h = mock([valid()], [{
    body: marker(933, HEAD), user: { login: owner, type: 'User' },
    author_association: 'OWNER',
  }]);
  assert.equal((await run(h)).sent, 1);
});
test('forged receipt from unrelated account cannot suppress legitimate request', async () => {
  const h = mock([valid()], [{ body: marker(933, HEAD) + '\\nForged', user: { login: 'outsider' }, author_association: 'CONTRIBUTOR' }]);
  assert.equal((await run(h)).sent, 1);
  assert.equal(h.made.length, 1);
});
test('stale head, closed PR, or wrong repository is never relayed', async () => {
  for (const opts of [{ sha: 'b'.repeat(40) }, { state: 'closed' }, { fullName: 'other/repo' }]) {
    const h = mock([valid()], [], opts);
    assert.equal((await run(h)).sent, 0);
    assert.equal(h.made.length, 0);
  }
});
test('untrusted author, malformed marker, age and invalid IDs fail closed', () => {
  const c = valid();
  for (const x of [
    { ...c, user: { login: 'outsider' } },
    { ...c, author_association: 'CONTRIBUTOR' },
    { ...c, id: '5' },
    { ...c, body: '<!-- arcana-handoff-v1 to=goose kind=pr-review pr=933 sha=bad -->' },
    { ...c, created_at: '2026-11-02T17:00:00Z' },
    { ...c, created_at: '2026-11-06T18:00:00Z' },
  ]) assert.deepEqual(requestsFrom(x, { owner, nowMs: NOW }), []);
});
test('ordinary worker prose does not create a false assignment', async () => {
  const h = mock([{ ...valid(), body: 'B15 done, Goose re-review pending on PR #953' }]);
  assert.equal((await run(h)).sent, 0);
  assert.equal(h.gets.length, 0);
});
test('burst is bounded to three handoffs', async () => {
  const source = Array.from({ length: 7 }, (_, i) => valid(933 + i, HEAD, 80 + i));
  const h = mock(source);
  assert.equal((await run(h)).sent, 3);
  assert.equal(h.made.length, 3);
});
test('GitHub failure is propagated; no false completion', async () => {
  const h = mock();
  h.github.rest.pulls.get = async () => { throw Error('GitHub unavailable'); };
  await assert.rejects(run(h), /GitHub unavailable/);
  assert.equal(h.made.length, 0);
});
test('inbox scan cap fails closed', async () => {
  const h = mock(Array.from({ length: 500 }, () => valid()));
  await assert.rejects(run(h), /safe scan bound/);
  assert.equal(h.made.length, 0);
});
test('only Goose PR-review markers accepted; no arbitrary targets', () => {
  for (const target of ['elmer', 'owner', 'copilot']) {
    const c = { ...valid(), body: '<!-- arcana-handoff-v1 to=' + target +
      ' kind=pr-review pr=933 sha=' + HEAD + ' -->' };
    assert.deepEqual(requestsFrom(c, { owner, nowMs: NOW }), []);
  }
});
