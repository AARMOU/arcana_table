'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const B = require('./board-facts.cjs');

const b64 = o => ({ data: { content: Buffer.from(JSON.stringify(o)).toString('base64') } });

function fake({ body, engine, runs, prs = [], apkRuns = {} }) {
  const writes = [];
  let issueBody = body;
  const github = {
    paginate: async () => [{ user: { type: 'Bot' }, body: '<!-- arcana-control-status-v1:engine -->\n```json\n' + JSON.stringify(engine) + '\n```' }],
    rest: {
      repos: { getContent: async ({ path, ref }) => { assert.equal(path, 'integration/policy.json'); assert.equal(ref, 'main'); return b64({ target: 'gpt/b141-candidate-20261101' }); } },
      issues: { listComments() {}, get: async () => ({ data: { body: issueBody } }),
        update: async ({ issue_number, body: b }) => { assert.equal(issue_number, 883); writes.push(b); issueBody = b; } },
      pulls: { list: async ({ base, state }) => { assert.equal(base, 'gpt/b141-candidate-20261101'); assert.equal(state, 'open'); return { data: prs }; } },
      actions: {
        listWorkflowRuns: async ({ workflow_id }) => ({ data: { workflow_runs: workflow_id === 'argentum-weekly-upgrade.yml' ? runs : (apkRuns[workflow_id] || []) } }),
        listRepoWorkflows: async () => ({ data: { workflows: [{ id: 41, path: '.github/workflows/b141-test-apk.yml' }, { id: 42, path: '.github/workflows/b142-test-apk.yml' }, { id: 9, path: '.github/workflows/pages.yml' }] } }),
      },
    },
  };
  return { github, writes, body: () => issueBody };
}

test('the board gets one live facts block, refreshed in place, and the hand-written rest is kept', async () => {
  const board = '**Updated:** x · **Revision 19**\n\n**Every agent: read this first.**\n\n## 1. Owner decisions\n- D1 keep me\n';
  const f = fake({
    body: board,
    engine: { result: 'up_to_date', checkedAt: '2026-11-03T13:30:00+00:00', message: 'The engine is already up to date. Nothing to do.' },
    runs: [
      { event: 'push', head_branch: 'gpt/argentum-weekly-fix-drill-7q4k', created_at: '2026-11-03T06:13:13Z', conclusion: 'failure', head_sha: '422f4baaad', html_url: 'https://x/run/2' },
      { event: 'workflow_dispatch', head_branch: 'main', created_at: '2026-11-03T13:25:00Z', conclusion: 'success', head_sha: 'aaa', html_url: 'https://x/run/1' },
      { event: 'push', head_branch: 'gpt/argentum-weekly-fix-drill-7q4k', created_at: '2026-11-03T02:42:56Z', conclusion: 'failure', head_sha: '4b4f124', html_url: 'https://x/run/0' },
    ],
    prs: [{ number: 921, title: 'Necron Dynasties: Triarch Stalker | engine 0037', draft: false, html_url: 'https://x/pr/921' },
      { number: 5, title: 'draft thing', draft: true, html_url: 'https://x/pr/5' }],
    apkRuns: { 41: [{ created_at: '2026-11-03T02:06:29Z', html_url: 'https://x/apk/141' }], 42: [{ created_at: '2026-11-03T00:46:07Z', html_url: 'https://x/apk/142' }] },
  });
  const now = new Date('2026-11-03T13:40:00Z');
  assert.match(await B.reportBoard({ github: f.github, owner: 'o', repo: 'r', now }), /^updated/);
  const out = f.body();
  assert.ok(out.indexOf(B.START) < out.indexOf('## 1. Owner decisions'), 'facts sit above the decisions');
  assert.match(out, /- D1 keep me/);
  assert.match(out, /Engine upgrade: \*\*up_to_date\*\* at Nov 3, 6:30 AM MT/);
  assert.match(out, /Last live upgrade run: Nov 3, 6:25 AM MT, success/);
  assert.match(out, /Drill `gpt\/argentum-weekly-fix-drill-7q4k`: latest check Nov 2, 11:13 PM MT, \*\*failure\*\* on `422f4ba`/);
  assert.match(out, /\[#921\]\(https:\/\/x\/pr\/921\) Necron Dynasties: Triarch Stalker engine 0037/, 'pipes are stripped from titles');
  assert.doesNotMatch(out, /draft thing/);
  assert.match(out, /Latest B142 test app: Nov 2, 5:46 PM MT/);
  assert.equal(await B.reportBoard({ github: f.github, owner: 'o', repo: 'r', now: new Date('2026-11-03T14:10:00Z') }), 'unchanged',
    'only the time moved: no edit');
  assert.equal(f.writes.length, 1);
  assert.equal(out.split(B.START).length, 2, 'exactly one block');
});

test('place replaces an existing block and never duplicates it', () => {
  const first = B.place('top\n\n## 1. x\n', B.START + '\nA\n' + B.END);
  const second = B.place(first, B.START + '\nB\n' + B.END);
  assert.equal(second.split(B.START).length, 2);
  assert.match(second, /\nB\n/);
  assert.doesNotMatch(second, /\nA\n/);
});
