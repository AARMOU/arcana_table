'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { gate, execute } = require('./guarded-suite-merge.cjs');
const { suiteRequest } = require('./suite-requests.cjs');

const SHA = 'b'.repeat(40), BASE = 'a'.repeat(40), REPO = 'AARMOU/arcana_table';
const REQUEST = suiteRequest({ pr: 518, issue: 509, name: 'Shield Sphere',
  oracle: { line: '{"name":"Shield Sphere","oracle_text":"Defender"}' } });
const source = 'certification/ShieldSphereIndependentCertificationTest.kt';
const fresh = '2026-11-08T12:00:00Z';

function fixture() {
  const run = { id: 9, name: 'Arcana Card Certification Premerge', event: 'pull_request',
    conclusion: 'success', head_sha: SHA, head_branch: 'copilot/suite',
    created_at: fresh, head_repository: { full_name: REPO } };
  const pr = { number: 610, state: 'open', draft: true, node_id: 'N', mergeable: true,
    mergeable_state: 'draft', created_at: fresh, title: 'Add Shield Sphere suite',
    labels: [], body: 'Unit: #600\nFixes #600', user: { login: 'Copilot' },
    base: { ref: 'main', sha: BASE, repo: { full_name: REPO } },
    head: { ref: 'copilot/suite', sha: SHA, repo: { full_name: REPO } } };
  const request = { number: 600, state: 'open', title: REQUEST.title, body: REQUEST.body,
    user: { login: 'github-actions[bot]' } };
  const files = [{ filename: 'certification/suites/shield-sphere.json', status: 'added' },
    { filename: source, status: 'added' }];
  const jobs = [{ name: 'Select suites with an open card PR', conclusion: 'success' },
    { name: 'Execute trusted independent card scenarios (shield-sphere, 518)', conclusion: 'success' }];
  const suite = { key: 'shield-sphere', name: 'Shield Sphere', issue: 509, source, tests: 4 };
  return { run, pr, request, files, jobs, suite };
}

test('default closed, historical, foreign, changed, held, review-blocked, and unclean never qualify', () => {
  const base = fixture();
  const report = { merged: false, reason: 'owner-merge-required' };
  const args = { enabled: true, report, pr: base.pr, run: base.run,
    currentBaseSha: BASE };
  assert.deepEqual(gate(args), []);
  const cases = [
    [{ enabled: false }, 'feature-switch-disabled'],
    [{ report: { merged: false, reason: 'merge-blocked' } }, 'not-a-certified-suite'],
    [{ pr: { ...base.pr, created_at: '2026-11-06T01:00:00Z' } }, 'historical-pr-not-opted-in'],
    [{ run: { ...base.run, created_at: '2026-11-06T01:00:00Z' } }, 'historical-run-not-opted-in'],
    [{ pr: { ...base.pr, head: { ...base.pr.head, sha: 'f'.repeat(40) } } }, 'pr-not-current-main-head'],
    [{ pr: { ...base.pr, user: { login: 'stranger' } } }, 'untrusted-pr-author'],
    [{ pr: { ...base.pr, labels: [{ name: 'hold' }] } }, 'held-pr'],
    [{ reviews: [{ state: 'CHANGES_REQUESTED' }] }, 'review-blocker'],
    [{ currentBaseSha: 'd'.repeat(40) }, 'base-moved-or-unknown'],
    [{ requireReady: true }, 'still-draft'],
    [{ pr: { ...base.pr, draft: false, mergeable: null },
      requireReady: true }, 'not-proven-clean'],
    [{ pr: { ...base.pr, draft: false, mergeable_state: 'unstable' },
      requireReady: true }, 'not-proven-clean'],
  ];
  for (const [override, name] of cases)
    assert.ok(gate({ ...args, ...override }).includes(name), name);
});

function clients(f, calls, opts = {}) {
  let reads = 0;
  const github = {
    paginate: async (fn, a) => fn(a),
    rest: {
      pulls: {
        list: () => [f.pr],
        get: () => {
          reads++;
          if (opts.moveHeadOnSecondRead && reads === 2) f.pr.head.sha = 'd'.repeat(40);
          return { data: { ...f.pr, head: { ...f.pr.head }, base: { ...f.pr.base } } };
        },
        listFiles: () => f.files,
        listReviews: () => opts.reviews || [],
      },
      repos: {
        getBranch: () => ({ data: { commit: { sha: opts.moveBase ? 'd'.repeat(40) : BASE } } }),
        getContent: () => ({ data: { content: Buffer.from(JSON.stringify(f.suite)).toString('base64') } }),
      },
      issues: { get: () => ({ data: f.request }) },
      actions: { listJobsForWorkflowRun: () => f.jobs },
    },
  };
  const writer = {
    graphql: async () => {
      calls.push('ready'); f.pr.draft = false; f.pr.mergeable_state = 'clean';
      return { markPullRequestReadyForReview: { pullRequest: { isDraft: false } } };
    },
    rest: { pulls: { merge: async a => { calls.push(['merge', a.pull_number, a.sha, a.merge_method]); return { data: { merged: true } }; } } },
  };
  return { github, writer };
}

test('disabled or absent writer does not even query a PR', async () => {
  const f = fixture(), calls = [];
  const { github, writer } = clients(f, calls);
  assert.equal((await execute({ github, writer, owner: 'AARMOU', repo: 'arcana_table', run: f.run })).reason,
    'feature-switch-disabled');
  assert.equal((await execute({ github, owner: 'AARMOU', repo: 'arcana_table', run: f.run, enabled: true })).reason,
    'feature-switch-disabled');
  assert.deepEqual(calls, []);
});

test('only newly trusted two-file suite transitions ready and merges exactly the validated SHA', async () => {
  const f = fixture(), calls = [];
  const { github, writer } = clients(f, calls);
  const result = await execute({ github, writer, owner: 'AARMOU', repo: 'arcana_table',
    run: f.run, enabled: true });
  assert.equal(result.merged, true);
  assert.equal(result.source_sha, SHA);
  assert.deepEqual(calls, ['ready', ['merge', 610, SHA, 'squash']]);
});

test('stale head, a third changed file, or a held label never merge', async () => {
  for (const mutate of [
    f => f.files.push({ filename: '.github/workflows/evil.yml', status: 'added' }),
    f => { f.pr.labels.push({ name: 'hold' }); },
    f => { f.pr.created_at = '2026-11-06T00:00:00Z'; },
  ]) {
    const f = fixture(), calls = []; mutate(f);
    const { github, writer } = clients(f, calls);
    const result = await execute({ github, writer, owner: 'AARMOU', repo: 'arcana_table',
      run: f.run, enabled: true });
    assert.equal(result.merged, false);
    assert.deepEqual(calls, []);
  }
});

test('JIT revalidation prevents merging a head moved after ready', async () => {
  const f = fixture(), calls = [];
  const { github, writer } = clients(f, calls, { moveHeadOnSecondRead: true });
  const result = await execute({ github, writer, owner: 'AARMOU', repo: 'arcana_table',
    run: f.run, enabled: true });
  assert.equal(result.merged, false);
  assert.deepEqual(calls, ['ready']);
});

test('workflow is feature-gated, serialized, trusted-code checkout, and has no broad dispatcher token', () => {
  const fs = require('node:fs'), path = require('node:path');
  const source = fs.readFileSync(path.join(__dirname, '../.github/workflows/arcana-guarded-suite-merge.yml'), 'utf8');
  assert.match(source, /ARCANA_GUARDED_SUITE_MERGE/);
  assert.match(source, /arcana-main-writer/);
  assert.match(source, /github\.workflow_sha/);
  assert.match(source, /ARCANA_SUITE_MERGE_TOKEN/);
  assert.doesNotMatch(source, /COPILOT_DISPATCH_TOKEN|\sactions: write|\sissues: write/);
});
