'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const R = require('./review-inbox.cjs');
const S = require('./control-status.cjs');

const pr = (extra = {}) => ({ number: 360, title: 'Implement Celestine, the Living Saint', body: '- Fixes #358',
  head: { sha: 'a'.repeat(40) }, base: { repo: { full_name: 'AARMOU/arcana_table' } }, user: { login: 'Copilot' }, ...extra });
const run = (name, conclusion, status = 'completed', head_sha = 'a'.repeat(40)) => ({ name, status, conclusion, head_sha });
const certRun = (conclusion='success', head_sha='a'.repeat(40)) => ({
  name: 'Arcana independent certification', head_sha, status: 'completed', conclusion,
  app: { slug: 'github-actions', id: 15368 },
  output: { title: 'Resurrection Orb: independent rules scenarios passed',
    summary: '4 trusted-main JUnit scenarios passed against the exact current Copilot PR commit' }
});
const review = (state, commit_id = 'a'.repeat(40), login = 'AARMOU') => ({ state, commit_id, user: { login } });
const cardFiles = ['android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Celestine.kt',
  'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/CelestineScenarioTest.kt'];

test('states: running, failed, awaiting review, approved and changes requested', () => {
  assert.equal(R.classify(pr(), []).state, 'checks-running');
  assert.equal(R.classify(pr(), [run('toolkit', null, 'in_progress')]).state, 'checks-running');
  assert.equal(R.classify(pr(), [run('toolkit', 'success'), run('cards-and-android', 'failure')]).state, 'checks-failed');
  const green = [run('toolkit', 'success'), run('cards-and-android', 'success'), run('publish-oracle-cache', 'skipped')];
  const row = R.classify(pr(), green, [], '', cardFiles);
  assert.deepEqual([row.state, row.card, row.issue, row.url], ['awaiting-review', 'Celestine, the Living Saint', 358,
    'https://github.com/AARMOU/arcana_table/pull/360/files']);
  assert.equal(R.classify(pr(), green, [review('APPROVED')]).state, 'approved');
  assert.equal(R.classify(pr(), green, [review('APPROVED'), review('CHANGES_REQUESTED')]).state, 'changes-requested');
});

test('only the owner\'s review of the CURRENT head counts', () => {
  const green = [run('toolkit', 'success')];
  assert.equal(R.classify(pr(), green, [review('APPROVED', 'old-sha')]).state, 'awaiting-review');
  assert.equal(R.classify(pr(), green, [review('APPROVED', 'a'.repeat(40), 'stranger')]).state, 'awaiting-review');
  // Check runs from an older head are ignored.
  assert.equal(R.classify(pr(), [run('toolkit', 'failure', 'completed', 'old')]).state, 'checks-running');
});

test('payload puts cards needing the owner first and counts the badge', () => {
  const p = R.inboxPayload([
    { pr: 1, state: 'checks-running' }, { pr: 2, state: 'certified', certification: 'certified', kind: 'card', hasTest: true, draft: false },
    { pr: 3, state: 'approved' }, { pr: 4, state: 'certified', certification: 'certified', kind: 'card', hasTest: true, draft: false },
  ], new Date('2026-10-28T17:00:00Z'));
  assert.equal(p.awaitingReview, 2);
  assert.deepEqual(p.items.map(i => i.pr), [2, 4, 1, 3]);
  assert.match(R.inboxSummary({ ...p, items: p.items.map(i => ({ ...i, card: 'X' })) })[0], /2 verified card implementation/);
});

test('published as the public #78 review status the app can read without a token', () => {
  assert.ok(S.MARKERS.review);
  const body = S.formatStatus('review', { ...R.inboxPayload([]), kind: 'review' }, R.inboxSummary(R.inboxPayload([])));
  assert.ok(body.startsWith(S.MARKERS.review));
  assert.equal(S.parseStatus(body).kind, 'review');
});

test('buildReviewInbox reads only Copilot PRs and never writes', async () => {
  const calls = [];
  const github = {
    paginate: async (fn, args) => { calls.push(fn.name || 'paginate'); return fn === github.rest.pulls.list
      ? [pr(), pr({ number: 9, user: { login: 'AARMOU' } }), pr({ number: 330, body: 'Fixes #173' }), pr({ number: 331, body: 'no link' })]
      : fn === github.rest.pulls.listFiles ? cardFiles.map(filename => ({ filename })) : [review('APPROVED')]; },
    rest: {
      pulls: { listFiles: function listFiles() {}, list: async function list() { return { data: [
        { number: 340, title: 'Implement Old Card', body: 'Fixes #300', user: { login: 'Copilot' }, merged_at: '2026-10-27T10:00:00Z', merge_commit_sha: 'm'.repeat(40) },
        { number: 341, title: 'Implement Ancient', body: 'Fixes #301', user: { login: 'Copilot' }, merged_at: '2026-10-01T10:00:00Z' },
        { number: 342, title: 'Closed unmerged', body: 'Fixes #302', user: { login: 'Copilot' }, merged_at: null }] }; }, listReviews: function listReviews() {} },
      checks: { listForRef: async () => ({ data: { check_runs: [run('toolkit', 'success')] } }) },
      issues: { get: async ({ issue_number }) => ({ data: issue_number === 358
        ? { state: 'open', title: '[Arcana Queue] Celestine, the Living Saint — Cardkit SUPPORTED, unclaimed' }
        : { state: 'open', title: 'Some other issue' } }) },
    },
  };
  const p = await R.buildReviewInbox({ github, owner: 'AARMOU', repo: 'arcana_table', now: new Date('2026-10-28T17:00:00Z') });
  assert.deepEqual(p.items.map(i => [i.pr, i.state]), [[360, 'approved'], [340, 'integrated']]);
  assert.deepEqual(p.integratedActivity, [{ pr: 340, card: 'Old Card', mergedAt: '2026-10-27T10:00:00Z' }]);
});

test('certification is only an explicit certification check, never plain CI', () => {
  const green = [run('toolkit', 'success'), run('cards-and-android', 'success')];
  assert.deepEqual([R.classify(pr(), green).state, R.classify(pr(), green).certification], ['awaiting-review', 'not-run']);
  const certified = R.classify(pr(), [...green, certRun()]);
  assert.deepEqual([certified.state, certified.certification], ['certified', 'certified']);
  const failedCert = R.classify(pr(), [...green, certRun('failure')]);
  assert.deepEqual([failedCert.state, failedCert.certification], ['checks-failed', 'failed']);
  assert.equal(R.inboxPayload([R.classify(pr(), [...green, certRun()], [], '', cardFiles),
    R.classify(pr({ number: 1 }), green, [], '', cardFiles)]).awaitingReview, 1);
});

test('details carry abilities, check links, problems and stale-review warnings', () => {
  const unitBody = ['## Cards', '- Celestine', '', '## Acceptance',
    '- Celestine, the Living Saint: Flying, lifelink; at end step, return creature card with MV <= life gained', '- Exact current Oracle behavior (cost)',
    '- Executable regression scenarios for positive and negative behavior', 'Base: x'].join('\n');
  const row = R.classify(pr(), [{ ...run('cards-and-android', 'failure'), html_url: 'https://github.com/AARMOU/arcana_table/runs/1' }],
    [review('APPROVED', 'old-sha')], unitBody);
  assert.deepEqual(row.abilities, ['Celestine, the Living Saint: Flying, lifelink; at end step, return creature card with MV <= life gained']);
  assert.deepEqual(row.checkRuns, [{ name: 'cards-and-android', result: 'failure', url: 'https://github.com/AARMOU/arcana_table/runs/1' }]);
  assert.deepEqual(row.problems, ['Check failed: cards-and-android (failure)', 'New commit since your last review: review again', 'Changed-file evidence unavailable or incomplete']);
});


test('daily activity is not truncated to the owner review inbox and excludes unmerged PRs', () => {
  const merged = Array.from({length: 42}, (_,i) => ({pr: 500+i, card:'Card '+i, mergedAt:'2026-10-28T12:00:00Z'}));
  const p = R.inboxPayload(merged.map(x=>({...x,state:'integrated'})), new Date('2026-10-28T18:00:00Z'), merged);
  assert.equal(p.items.length, 30);
  assert.equal(p.integratedActivity.length, 42);
  assert.equal(p.integratedActivity[0].pr, 500);
});

test('file evidence separates actual cards from audits and engine blockers and prevents draft approval', () => {
  const green = [run('toolkit', 'success'), certRun()];
  const real = R.classify(pr(), green, [], '', cardFiles);
  assert.deepEqual([real.kind, real.hasTest, real.readyForOwner], ['card', true, true]);
  const draft = R.classify(pr({ draft: true }), green, [], '', cardFiles);
  assert.deepEqual([draft.kind, draft.readyForOwner], ['card', false]);
  const audit = R.classify(pr({ title: 'Audit Eldrazi Incursion coverage', number: 95 }), green, [], '', []);
  const blocker = R.classify(pr({ title: 'Hold Ulamog pending engine support', number: 330 }), green, [], '', []);
  const notes = R.classify(pr({ title: 'Record cast-count blocker', number: 320 }), green, [], '', ['tools/cardkit/data/known-blockers.json']);
  assert.deepEqual([audit.kind, blocker.kind, notes.kind], ['audit', 'engine', 'engine']);
  assert.equal(R.inboxPayload([real, draft, audit, blocker, notes]).awaitingReview, 1);
  assert.equal(R.classify(pr(), green, [], '', null).readyForOwner, false);
  assert.equal(R.classify(pr(), green, [], '', cardFiles.slice(0, 1)).readyForOwner, false);
  assert.equal(R.classify(pr(), green, [], '', Array(100).fill(cardFiles[0])).readyForOwner, false);
});

test('look-alike check names or untrusted apps cannot certify the owner review inbox', () => {
  const green=[run('toolkit','success'),run('cards-and-android','success')];
  const forged=[
    {...certRun(),name:'fake-certification'},
    {...certRun(),app:{slug:'evil-agent',id:99}},
    {...certRun(),app:{slug:'github-actions',id:22}},
    {...certRun(),output:{title:'Unverified',summary:'not verified'}},
    {...certRun(),head_sha:'old'},
    {...certRun(),name:'Arcana independent certification (2)'},
  ];
  for(const c of forged){
    const row=R.classify(pr(), [...green,c], [],'',cardFiles);
    assert.equal(row.readyForOwner,false,JSON.stringify(c));
    assert.notEqual(row.certification,'certified');
  }
  const good=R.classify(pr(),[...green,certRun()],[],'',cardFiles);
  assert.equal(good.readyForOwner,true);
});

test('verified Android receipts distinguish integrated cards and destination blockers', async () => {
  const I=require('./card-integration.cjs');
  const receipt={schema:1,pr:360,sourceSha:'a'.repeat(40),baseSha:'b'.repeat(40),target:I.TARGET,
    status:'integrated',destinationSha:'d'.repeat(40),at:'2026-10-28T18:00:00Z',
    runUrl:'https://github.com/AARMOU/arcana_table/actions/runs/123456'};
  const github={rest:{
    pulls:{list:async args=>({data:args.state==='open'?[pr()]:[]}),listFiles:async()=>({data:cardFiles.map(filename=>({filename}))}),
      listReviews:async()=>({data:[review('APPROVED')]})},
    issues:{get:async()=>({data:{state:'open',title:'[Arcana Queue] Card',body:''}}),
      listComments:async()=>({data:[{user:{id:41898282,login:'github-actions[bot]'},body:I.receiptBody(receipt)}]})},
    checks:{listForRef:async()=>({data:{check_runs:[run('toolkit','success'),certRun()]}})},
    git:{getRef:async()=>({data:{object:{sha:'e'.repeat(40)}}})},
    repos:{compareCommitsWithBasehead:async()=>({data:{status:'ahead'}})}
  },paginate:async(fn,args)=>(await fn(args)).data};
  let payload=await R.buildReviewInbox({github,owner:'AARMOU',repo:'arcana_table'});
  assert.equal(payload.items[0].state,'integrated');
  assert.equal(payload.items[0].readyForOwner,false);
  assert.equal(payload.integratedActivity.length,1);
  receipt.status='blocked';receipt.reason='Independent test failed on destination engine';
  payload=await R.buildReviewInbox({github,owner:'AARMOU',repo:'arcana_table'});
  assert.equal(payload.items[0].state,'approved');
  assert.match(payload.items[0].problems[0],/Integration blocked: Independent test failed/);
  assert.equal(payload.integratedActivity.length,0);
});
