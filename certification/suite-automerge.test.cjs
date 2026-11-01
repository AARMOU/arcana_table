'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { mergeBlockers, mergeSuitePr, PREMERGE } = require('./suite-automerge.cjs');
const { suiteRequest } = require('./suite-requests.cjs');

const SHA = 'b'.repeat(40), REPO = 'AARMOU/arcana_table';
const req = suiteRequest({ pr: 518, issue: 509, name: 'Shield Sphere', oracle: { line: '{"name":"Shield Sphere","oracle_text":"Defender"}' } });
const TEST = 'certification/ShieldSphereIndependentCertificationTest.kt';
const good = () => ({
  run: { id: 9, name: PREMERGE, event: 'pull_request', conclusion: 'success', head_sha: SHA, head_branch: 'copilot/suite', head_repository: { full_name: REPO } },
  pr: { number: 610, state: 'open', draft: true, node_id: 'N', body: 'Unit: #600\nFixes #600', user: { login: 'Copilot' },
    base: { ref: 'main', repo: { full_name: REPO } }, head: { sha: SHA, repo: { full_name: REPO } } },
  request: { number: 600, state: 'open', title: req.title, body: req.body, user: { login: 'github-actions[bot]' } },
  files: [{ filename: 'certification/suites/shield-sphere.json', status: 'added' }, { filename: TEST, status: 'added' }],
  jobs: [{ name: 'Select suites with an open card PR', conclusion: 'success' }, { name: 'Execute trusted independent card scenarios (shield-sphere, 518)', conclusion: 'success' }],
  suite: { key: 'shield-sphere', name: 'Shield Sphere', issue: 509, source: TEST, tests: 4 },
});

test('a green premerge that executed exactly the requested suite has no blockers', () => {
  assert.deepEqual(mergeBlockers(good()), []);
});

test('every unsafe variation blocks the merge', () => {
  const cases = {
    'premerge-not-green': g => { g.run.conclusion = 'failure'; },
    'head-moved': g => { g.pr.head.sha = 'c'.repeat(40); },
    'pr-not-by-copilot': g => { g.pr.user.login = 'someone'; },
    'pr-not-open-on-main': g => { g.pr.base.ref = 'gpt/arcana-integration-next'; },
    'not-same-repository': g => { g.pr.head.repo.full_name = 'fork/x'; },
    'no-open-suite-request': g => { g.request.user.login = 'AARMOU'; },
    'files-not-exactly-the-new-suite': g => { g.files.push({ filename: '.github/workflows/x.yml', status: 'added' }); },
    'suite-registration-mismatch': g => { g.suite.issue = 1; },
    'suite-not-executed': g => { g.jobs = g.jobs.slice(0, 1); },
  };
  for (const [reason, mutate] of Object.entries(cases)) {
    const g = good(); mutate(g);
    assert.ok(mergeBlockers(g).includes(reason), reason + ' -> ' + mergeBlockers(g));
  }
  const modified = good(); modified.files[1].status = 'modified';
  assert.ok(mergeBlockers(modified).includes('files-not-exactly-the-new-suite'));
});

test('mergeSuitePr marks ready and squash-merges the exact head; ignores non-suite PRs', async () => {
  const g = good(); const calls = [];
  const github = {
    paginate: async (fn, a) => fn(a),
    graphql: async () => { calls.push('ready'); return { markPullRequestReadyForReview: { pullRequest: { isDraft: false } } }; },
    rest: {
      pulls: { list: () => [g.pr], listFiles: () => g.files, merge: async a => { calls.push(['merge', a.pull_number, a.sha, a.merge_method]); } },
      issues: { get: async () => ({ data: g.request }) },
      actions: { listJobsForWorkflowRun: () => g.jobs },
      repos: { getContent: async () => ({ data: { content: Buffer.from(JSON.stringify(g.suite)).toString('base64') } }) },
    },
  };
  const r = await mergeSuitePr({ github, owner: 'AARMOU', repo: 'arcana_table', run: g.run });
  assert.equal(r.merged, true);
  assert.deepEqual(calls, ['ready', ['merge', 610, SHA, 'squash']]);
  g.request = { ...g.request, title: '[Arcana Queue] something' }; calls.length = 0;
  const r2 = await mergeSuitePr({ github, owner: 'AARMOU', repo: 'arcana_table', run: g.run });
  assert.equal(r2.reason, 'not-a-suite-pr'); assert.deepEqual(calls, []);
});

test('a refused merge leaves one owner notice with the exact clicks, never a silent stall', async () => {
  const g = good(); const comments = [];
  const github = {
    paginate: async (fn, a) => fn(a),
    rest: {
      pulls: { list: () => [g.pr], listFiles: () => g.files },
      issues: { get: async () => ({ data: g.request }), listComments: () => comments,
        createComment: async a => comments.push({ user: { login: 'github-actions[bot]' }, body: a.body }) },
      actions: { listJobsForWorkflowRun: () => g.jobs },
      repos: { getContent: async () => ({ data: { content: Buffer.from(JSON.stringify(g.suite)).toString('base64') } }) },
    },
  };
  const writer = { graphql: async () => { throw Error('Resource not accessible by integration'); }, rest: { pulls: { merge: async () => {} } } };
  for (let i = 0; i < 2; i++) {
    const r = await mergeSuitePr({ github, writer, owner: 'AARMOU', repo: 'arcana_table', run: g.run });
    assert.equal(r.reason, 'merge-refused');
  }
  assert.equal(comments.length, 1);
  assert.match(comments[0].body, /Ready for review\*\*, then \*\*Squash and merge/);
  assert.match(comments[0].body, /ARCANA_SUITE_MERGE_TOKEN/);
});
