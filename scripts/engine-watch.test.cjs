'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const W = require('./engine-watch.cjs');

test('upstream counter keeps one note, edits it in place only when an engine version changes', async () => {
  const sha = c => c.repeat(40), pin = s => ({ data: { content: Buffer.from(JSON.stringify({ upstream: s })).toString('base64') } });
  let head = sha('a'), adopted = sha('b'), b140 = sha('c'), compares = 0;
  const comments = [], writes = [];
  const github = { rest: {
    repos: { getCommit: async ({ ref }) => { assert.equal(ref, 'HEAD'); return { data: { sha: head, commit: { committer: { date: '2026-10-30T00:00:00Z' } } } }; },
      getContent: async ({ ref }) => pin(ref === W.BRANCHES.adopted ? adopted : b140),
      compareCommitsWithBasehead: async ({ basehead }) => { compares++; return { data: { ahead_by: basehead.startsWith(adopted) ? 12 : 300 } }; } },
    issues: { listComments() {}, createComment: async ({ body }) => { writes.push('create'); comments.push({ id: 7, user: { login: 'github-actions[bot]', type: 'Bot' }, body }); },
      updateComment: async ({ comment_id, body }) => { writes.push('update:' + comment_id); comments[0].body = body; } } },
    paginate: async () => comments };
  const go = () => W.reportUpstream({ github, owner: 'o', repo: 'r' });
  assert.match(await go(), /^created \(adopted 12 behind, B140 300 behind\)$/);
  assert.match(comments[0].body, /^<!-- arcana-engine-upstream-v1 -->/);
  assert.equal(await go(), 'unchanged'); assert.equal(compares, 2, 'no compare calls while nothing changed');
  head = sha('d');
  assert.match(await go(), /^updated/);
  assert.deepEqual(writes, ['create', 'update:7'], 'one note, edited in place, never a new post');
  adopted = head;
  assert.match(await go(), /adopted 0 behind/);
});
