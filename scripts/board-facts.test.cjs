'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const B = require('./board-facts.cjs');

const b64 = o => ({ data: { content: Buffer.from(JSON.stringify(o)).toString('base64') } });

function fake({ body, engine, runs, prs = [], apkRuns = {}, inbox949 = [], inbox950 = [] }) {
  const writes = [];
  const comments = [];
  const pages = [];
  const inbox = { 949: inbox949, 950: inbox950 };
  const github = {
    paginate: async (_fn, { issue_number }) => {
      if (issue_number === 78) return [{ user: { type: 'Bot' }, body: '<!-- arcana-control-status-v1:engine -->\n' +
        '```json\n' + JSON.stringify(engine) + '\n```' }];
      if (issue_number === 883) return comments;
      throw new Error('unexpected comment pagination ' + issue_number);
    },
    rest: {
      repos: { getContent: async ({ path, ref }) => {
        assert.equal(path, 'integration/policy.json');
        assert.equal(ref, 'main');
        return b64({ target: 'gpt/b141-candidate-20261101' });
      } },
      issues: {
        listComments: async ({ issue_number, per_page, page }) => {
          pages.push({ issue_number, per_page, page });
          const all = inbox[issue_number] || [];
          return { data: all.slice((page - 1) * per_page, page * per_page) };
        },
        get: async ({ issue_number }) => ({
          data: issue_number === 883 ? { body } : { comments: (inbox[issue_number] || []).length },
        }),
        update: async () => { writes.push('ISSUE BODY WRITE'); throw new Error('protected board overwritten'); },
        createComment: async ({ issue_number, body: content }) => {
          assert.equal(issue_number, 883);
          comments.push({ id: 12, user: { type: 'Bot' }, body: content });
          writes.push('created comment');
        },
        updateComment: async ({ comment_id, body: content }) => {
          assert.equal(comment_id, 12);
          comments[0].body = content;
          writes.push('updated comment');
        },
      },
      pulls: { list: async ({ base, state }) => {
        assert.equal(base, 'gpt/b141-candidate-20261101');
        assert.equal(state, 'open');
        return { data: prs };
      } },
      actions: {
        listWorkflowRuns: async ({ workflow_id }) => ({ data: {
          workflow_runs: workflow_id === 'argentum-weekly-upgrade.yml' ? runs : (apkRuns[workflow_id] || []),
        } }),
        listRepoWorkflows: async () => ({ data: { workflows: [
          { id: 41, path: '.github/workflows/b141-test-apk.yml' },
          { id: 42, path: '.github/workflows/b142-test-apk.yml' },
          { id: 9, path: '.github/workflows/pages.yml' },
        ] } }),
      },
    },
  };
  return { github, writes, comments, pages };
}
const decode = comment => JSON.parse(/```json\n([\s\S]*?)\n```/.exec(comment.body)[1]);

test('automated status uses one bot comment, keeps owner board intact and refreshes timestamp', async () => {
  const board = '**Updated:** x · **Revision 19**\n\n## 1. Owner decisions\n- D1 keep me\n';
  const f = fake({
    body: board,
    engine: { result: 'up_to_date', checkedAt: '2026-11-03T13:30:00+00:00', message: 'Already current.' },
    runs: [
      { event: 'workflow_dispatch', head_branch: 'main', created_at: '2026-11-03T13:25:00Z', conclusion: 'success', head_sha: 'aaa', html_url: 'https://x/run/1' },
    ],
    prs: [{ number: 921, title: 'Triarch Stalker | engine 0037', draft: false, html_url: 'https://x/pr/921' },
      { number: 5, title: 'draft', draft: true, html_url: 'https://x/pr/5' }],
    apkRuns: { 41: [{ created_at: '2026-11-03T02:06:29Z', html_url: 'https://x/apk/141' }],
      42: [{ created_at: '2026-11-03T00:46:07Z', html_url: 'https://x/apk/142' }] },
    inbox949: [{ id: 1, created_at: '2026-11-03T12:00:00Z',
      html_url: 'https://x/issues/949#comment1', body: '**Boneknuckles check-in**\nB15 green' }],
    inbox950: [{ id: 2, created_at: '2026-11-03T12:05:00Z',
      html_url: 'https://x/issues/950#comment2', body: '**Elmer status**\nEngine review' }],
  });
  const a = new Date('2026-11-03T13:40:00Z');
  assert.match(await B.reportBoard({ github: f.github, owner: 'o', repo: 'r', now: a }), /^created/);
  assert.equal(f.comments.length, 1);
  assert.ok(f.comments[0].body.startsWith(B.LIVE_MARKER));
  const first = decode(f.comments[0]);
  assert.equal(first.checkedAt, a.toISOString());
  assert.match(first.facts.join('\n'), /Engine upgrade: \*\*up_to_date\*\* at Nov 3, 6:30 AM MT/);
  assert.match(first.facts.join('\n'), /Last live upgrade run: Nov 3, 6:25 AM MT, success/);
  assert.match(first.facts.join('\n'), /Triarch Stalker engine 0037/);
  assert.doesNotMatch(first.facts.join('\n'), /draft/);
  assert.match(first.facts.join('\n'), /Latest B142 test app: Nov 2, 5:46 PM MT/);
  assert.match(first.inbox[0].entries[0].summary, /Boneknuckles check-in/);
  assert.equal(first.inbox[1].entries[0].url, 'https://x/issues/950#comment2');

  const b = new Date('2026-11-03T14:10:00Z');
  assert.match(await B.reportBoard({ github: f.github, owner: 'o', repo: 'r', now: b }), /^updated/);
  assert.equal(f.comments.length, 1, 'never spams new comments');
  assert.equal(decode(f.comments[0]).checkedAt, b.toISOString(), 'unchanged facts still update heartbeat');
  assert.deepEqual(f.writes, ['created comment', 'updated comment']);
});

test('only fetches the most recent page of a growing worker inbox', async () => {
  const old = Array.from({ length: 100 }, (_, i) => ({
    created_at: '2026-11-01T00:00:00Z', html_url: 'https://x/old/' + i, body: 'old',
  }));
  const f = fake({
    body: 'Protected orders', engine: { result: 'ok', checkedAt: '2026-11-03T00:00:00Z' },
    runs: [], inbox949: [...old, { created_at: '2026-11-05T12:00:00Z',
      html_url: 'https://x/new', body: 'LATEST CHECK-IN' }],
  });
  await B.reportBoard({ github: f.github, owner: 'o', repo: 'r' });
  assert.equal(f.pages.find(p => p.issue_number === 949).page, 2);
  assert.equal(decode(f.comments[0]).inbox[0].entries[0].summary, 'LATEST CHECK-IN');
});

test('place still replaces blocks without duplicating owner sections', () => {
  const first = B.place('top\n\n## 1. x\n', B.START + '\nA\n' + B.END);
  const second = B.place(first, B.START + '\nB\n' + B.END);
  assert.equal(second.split(B.START).length, 2);
  assert.match(second, /\nB\n/);
  assert.doesNotMatch(second, /\nA\n/);
});
