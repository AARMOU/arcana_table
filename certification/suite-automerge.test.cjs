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

// Every mutation surface is a trap, including the legacy separately-authenticated writer.
function client(g, calls) {
  const mutation = async () => { calls.push('mutation'); throw Error('mutation forbidden'); };
  return {
    paginate: async (fn, a) => fn(a), graphql: mutation,
    rest: {
      pulls: { list: () => [g.pr], listFiles: () => g.files, merge: mutation, update: mutation },
      issues: { get: async () => ({ data: g.request }), createComment: mutation },
      actions: { listJobsForWorkflowRun: () => g.jobs, reRunWorkflow: mutation },
      repos: { getContent: async () => ({ data: { content: g.raw ?? Buffer.from(JSON.stringify(g.suite)).toString('base64') } }) },
    },
  };
}

test('green eligible draft and ready PRs require owner merge with zero mutations', async () => {
  for (const draft of [true, false]) {
    const g = good(), calls = []; g.pr.draft = draft;
    const github = client(g, calls);
    const result = await mergeSuitePr({ github, writer: github, owner: 'AARMOU', repo: 'arcana_table', run: g.run });
    assert.deepEqual(result, { merged: false, pr: 610, request: 600, key: 'shield-sphere', reason: 'owner-merge-required' });
    assert.deepEqual(calls, []);
  }
});

test('red, stale, foreign, unexecuted and malformed inputs remain blocked without mutations', async () => {
  const variations = [
    g => { g.run.conclusion = 'failure'; },
    g => { g.pr.head.sha = 'c'.repeat(40); },
    g => { g.run.head_repository.full_name = 'fork/repo'; },
    g => { g.jobs[1].conclusion = 'failure'; },
    g => { g.jobs = []; },
    g => { g.raw = Buffer.from('{bad json').toString('base64'); },
    g => { g.suite = null; },
    g => { g.run.head_sha = ''; },
    g => { g.run = null; },
  ];
  for (const mutate of variations) {
    const g = good(), calls = []; mutate(g);
    const github = client(g, calls);
    const result = await mergeSuitePr({ github, writer: github, owner: 'AARMOU', repo: 'arcana_table', run: g.run });
    assert.equal(result.merged, false);
    assert.ok(result.blockers?.length, JSON.stringify(result));
    assert.deepEqual(calls, []);
  }
});

test('unrelated PRs are left alone', async () => {
  const g = good(), calls = []; g.request.title = '[Arcana Queue] unrelated';
  const result = await mergeSuitePr({ github: client(g, calls), owner: 'AARMOU', repo: 'arcana_table', run: g.run });
  assert.equal(result.reason, 'not-a-suite-pr'); assert.deepEqual(calls, []);
});

test('workflow grants reads only and has no writer token or post-merge backfill', () => {
  const workflow = require('node:fs').readFileSync(require('node:path').join(__dirname, '../.github/workflows/arcana-suite-automerge.yml'), 'utf8');
  assert.doesNotMatch(workflow, /: write|MERGE_TOKEN|COPILOT_DISPATCH_TOKEN|new github.constructor|backfill|setOutput/);
  assert.match(workflow, /owner merge required/);
});
