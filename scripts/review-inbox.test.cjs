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
  'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/CelestineScenarioTest.kt',
  'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt'];

test('states: running, failed, awaiting review, approved and changes requested', () => {
  assert.equal(R.classify(pr(), []).state, 'no-checks');
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
  assert.equal(R.classify(pr(), [run('toolkit', 'failure', 'completed', 'old')]).state, 'no-checks');
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

test('file evidence separates actual cards from audits and engine blockers; certified drafts are ready', () => {
  const green = [run('toolkit', 'success'), certRun()];
  const real = R.classify(pr(), green, [], '', cardFiles);
  assert.deepEqual([real.kind, real.hasTest, real.readyForOwner], ['card', true, true]);
  const draft = R.classify(pr({ draft: true }), green, [], '', cardFiles);
  assert.deepEqual([draft.kind, draft.readyForOwner], ['card', true], 'certified draft is ready: Approve marks it ready');
  assert.doesNotMatch(draft.problems.join(' '), /Draft PR/);
  const uncertifiedDraft = R.classify(pr({ draft: true }), [run('toolkit', 'success')], [], '', cardFiles);
  assert.equal(uncertifiedDraft.readyForOwner, false);
  const audit = R.classify(pr({ title: 'Audit Eldrazi Incursion coverage', number: 95 }), green, [], '', []);
  const blocker = R.classify(pr({ title: 'Hold Ulamog pending engine support', number: 330 }), green, [], '', []);
  const notes = R.classify(pr({ title: 'Record cast-count blocker', number: 320 }), green, [], '', ['tools/cardkit/data/known-blockers.json']);
  assert.deepEqual([audit.kind, blocker.kind, notes.kind], ['audit', 'engine', 'engine']);
  assert.equal(R.inboxPayload([real, draft, uncertifiedDraft, audit, blocker, notes]).awaitingReview, 2);
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

test('auto-closed queue issues preserve only authenticated Android integration history', async () => {
  const I = require('./card-integration.cjs');
  const when = '2026-10-30T12:53:31Z';
  const receipt = {schema:1, pr:360, sourceSha:'a'.repeat(40), baseSha:'b'.repeat(40),
    target:I.TARGET, status:'integrated', destinationSha:'d'.repeat(40), at:when,
    runUrl:'https://github.com/AARMOU/arcana_table/actions/runs/41056966638'};
  let author = {id:41898282, login:'github-actions[bot]'};
  let ancestry = 'ahead';
  const github = {rest:{
    pulls:{list:async ({state})=>({data:state==='open'?[pr()]:[]}), listReviews:async()=>({data:[]}), listFiles:async()=>({data:cardFiles.map(filename=>({filename}))})},
    checks:{listForRef:async()=>({data:{check_runs:[]}})},
    issues:{
      get:async()=>({data:{state:'closed',title:'[Arcana Queue] Celestine, the Living Saint — Cardkit SUPPORTED, unclaimed'}}),
      listComments:async()=>({data:[{user:author,body:I.receiptBody(receipt)}]})
    },
    git:{getRef:async()=>({data:{object:{sha:'e'.repeat(40)}}})},
    repos:{compareCommitsWithBasehead:async()=>({data:{status:ancestry}})}
  }, paginate:async(fn,args)=>(await fn(args)).data};
  const get=now=>R.buildReviewInbox({github,owner:'AARMOU',repo:'arcana_table',now:new Date(now)});
  const live=await get('2026-10-30T13:00:00Z');
  assert.deepEqual(live.integratedActivity,[{pr:360,card:'Celestine, the Living Saint',mergedAt:when}]);
  assert.equal(live.items[0].state,'integrated');
  assert.equal(live.items[0].readyForOwner,false);
  assert.equal(live.items[0].integration.destinationSha,'d'.repeat(40));
  // Only a trusted GitHub Actions integration receipt with a live destination ancestor counts.
  author={id:12345,login:'someone'};
  const forged=await get('2026-10-30T13:00:00Z');
  assert.deepEqual(forged.integratedActivity,[]);
  assert.equal(forged.items[0].state,'orphaned');
  assert.equal(forged.items[0].readyForOwner,false);
  author={id:41898282,login:'github-actions[bot]'};
  ancestry='diverged';
  const invalid=await get('2026-10-30T13:00:00Z');
  assert.deepEqual(invalid.integratedActivity,[]);
  assert.equal(invalid.items[0].state,'orphaned');
  ancestry='ahead';
  // The seven-day graph rolls by calendar window; historical cards stay marked integrated.
  const old=await get('2026-11-09T13:00:00Z');
  assert.deepEqual(old.integratedActivity,[]);
  assert.equal(old.items[0].state,'integrated');
});

test('control-plane feeder/observer runs do not hold a certified card in checks-running', () => {
  const { classify } = require('./review-inbox.cjs');
  const head = 'b'.repeat(40);
  const pr = { number: 360, title: 'Implement Celestine, the Living Saint', body: 'Fixes #358', draft: false, head: { sha: head } };
  const cert = { name: 'Arcana independent certification', status: 'completed', conclusion: 'success', head_sha: head,
    app: { slug: 'github-actions', id: 15368 }, output: { title: 'Celestine, the Living Saint: independent rules scenarios passed',
      summary: '7 trusted-main JUnit scenarios passed against the exact current Copilot PR commit' } };
  const green = ['toolkit', 'scaffold', 'cards-and-android'].map(name => ({ name, status: 'completed', conclusion: 'success', head_sha: head }));
  const files = ['android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/CelestineTheLivingSaint.kt',
    'android/custom-cards/src/test/kotlin/com/arcanatable/customcards/CelestineTheLivingSaintScenarioTest.kt',
    'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt'];
  const noisy = [...green, cert, { name: 'suggest', status: 'in_progress', head_sha: head },
    { name: 'publish', status: 'completed', conclusion: 'failure', head_sha: head }];
  const row = classify(pr, noisy, [], '', files);
  assert.equal(row.state, 'certified');
  assert.equal(row.readyForOwner, true);
  const real = classify(pr, [...noisy, { name: 'cards-and-android', status: 'in_progress', head_sha: head }], [], '', files);
  assert.equal(real.state, 'checks-running');
});

test('owner is mentioned once per newly ready card head, never on unchanged refreshes', async () => {
  const card = (n, h) => ({ pr: n, card: 'Card ' + n, headSha: h.repeat(40), kind: 'card', hasTest: true, draft: false,
    state: 'certified', certification: 'certified', checks: {} });
  const comments = [];
  const github = { rest: { issues: { listComments() {}, createComment: async ({ body }) => {
    comments.push({ user: { login: 'github-actions[bot]', type: 'Bot' }, body }); } } },
    paginate: async () => comments };
  const go = items => R.alertReady({ github, owner: 'o', repo: 'r', payload: { items } });
  assert.equal(R.isReadyForOwner(card(1, 'a')), true, 'fixture is a ready card');
  assert.equal(await go([]), 'unchanged');
  assert.equal(await go([card(1, 'a')]), 'posted 1');
  assert.match(comments[0].body, /^<!-- arcana-ready-to-verify-v1 -->/);
  assert.match(comments[0].body, /@AARMOU \*\*1 card ready to Verify\*\*/);
  assert.equal(await go([card(1, 'a')]), 'unchanged', 'same head is not re-alerted');
  assert.equal(await go([card(1, 'a'), card(2, 'b')]), 'posted 1', 'only the new card is announced');
  assert.match(comments[1].body, /#2 Card 2/);
  assert.doesNotMatch(comments[1].body, /- #1 /);
  assert.equal(await go([card(1, 'c')]), 'posted 1', 'a new head of the same PR is new');
  assert.equal(await go([{ ...card(3, 'd'), state: 'checks-running' }]), 'unchanged', 'not-ready items never alert');
  // A look-alike from another account never suppresses an alert.
  comments.push({ user: { login: 'someone', type: 'User' }, body: R.READY_MARKER + '\nready: 4@' + 'e'.repeat(40) });
  assert.equal(await go([card(4, 'e')]), 'posted 1');
});

test('stall report lists card PRs past each stage limit and posts only when the set changes', async () => {
  const now = Date.parse('2026-10-30T12:00:00Z'), h = n => new Date(now - n * 36e5).toISOString();
  const item = (pr, state, hours, extra = {}) => ({ pr, card: 'Card ' + pr, headSha: String(pr).repeat(40).slice(0, 40), kind: 'card', state, updatedAt: h(hours), ...extra });
  const items = [item(1, 'certified', 25), item(2, 'certified', 23), item(8, 'certified', 200, { certifiedAt: h(1) }), item(3, 'approved', 3, { problems: ['Integration blocked: cardkit'] }),
    item(4, 'checks-failed', 7), item(5, 'integrated', 99), item(6, 'awaiting-review', 30), { ...item(7, 'certified', 99), kind: 'audit' }];
  assert.deepEqual(R.stalls({ items }, now).map(i => i.pr), [1, 3, 4, 6], 'stage time beats stale PR activity (#8 certified 1h ago)');
  const comments = [];
  let openIssues = [];
  const github = { rest: { issues: { listComments() {}, listForRepo() {}, createComment: async ({ body }) => comments.push({ user: { login: 'github-actions[bot]', type: 'Bot' }, body }) } },
    paginate: async fn => fn === github.rest.issues.listForRepo ? openIssues : comments };
  const go = (its, t = now) => R.reportStalls({ github, owner: 'o', repo: 'r', payload: { items: its }, now: t });
  assert.equal(await go([]), 'unchanged', 'nothing stalled and nothing posted before: quiet');
  assert.equal(await go(items), 'posted 4');
  assert.match(comments[0].body, /#3 Card 3: approved, not integrated for 3h\. \*\*Owner:\*\* Claude \(automation\)\. \*\*Next:\*\* .* \(Integration blocked: cardkit\)\./);
  assert.equal(await go(items, now + 18e5), 'unchanged', 'ages ticking up never re-post');
  assert.equal(await go(items, now + 36e5), 'posted 5', 'a PR newly crossing its limit is reported');
  assert.equal(await go(items.filter(i => i.pr !== 1)), 'posted 3', 'one resolved');
  assert.equal(await go([]), 'posted 0', 'a cleared list is reported once');
  assert.equal(await go([]), 'unchanged');
  // A bot-opened blocked weekly upgrade older than 24h is listed with Claude as owner; a fresh one is not.
  const bot = { login: 'github-actions[bot]', type: 'Bot' };
  openIssues = [{ number: 312, title: 'Weekly Argentum upgrade to 621552754f (needs work)', user: bot, created_at: h(72) },
    { number: 313, title: 'Weekly Argentum upgrade to 7777777777 (needs work)', user: bot, created_at: h(2) },
    { number: 314, title: 'Weekly Argentum upgrade to 8888888888 (needs work)', user: { login: 'someone', type: 'User' }, created_at: h(99) }];
  assert.equal(await go([]), 'posted 0');
  assert.match(comments.at(-1).body, /^stalls: engine#312$/m);
  assert.match(comments.at(-1).body, /#312 Weekly Argentum upgrade to 621552754f \(needs work\): blocked engine upgrade for 72h\. \*\*Owner:\*\* Claude \(engine lane\)/);
  assert.equal(await go([]), 'unchanged');
});

test('a certified card PR integration cannot take is not offered for Verify, with the reason', () => {
  const green = [run('toolkit', 'success'), certRun()];
  const unregistered = R.classify(pr(), green, [], '', cardFiles.filter(f => !f.endsWith('CustomCards.kt')));
  assert.equal(unregistered.readyForOwner, false);
  assert.match(unregistered.reason, /missing CustomCards\.kt registration/);
  const extra = R.classify(pr(), green, [], '', [...cardFiles, 'tools/cardkit/tests/test_cardkit.py']);
  assert.equal(extra.readyForOwner, false);
  assert.match(extra.reason, /outside the card-only set: tools\/cardkit\/tests\/test_cardkit\.py/);
  assert.equal(R.classify(pr(), green, [], '', cardFiles).readyForOwner, true, 'a complete card PR is still ready');
  const now = Date.now();
  const row = { ...unregistered, state: 'certified', certifiedAt: new Date(now - 30 * 36e5).toISOString() };
  assert.deepEqual(R.stalls({ items: [row] }, now).map(i => i.pr), [row.pr], 'a certified but unintegrable card still stalls');
});

test('stall report times the GPT engine lane (48h) and the B140 build trigger (72h), with owners', async () => {
  const now = Date.parse('2026-10-30T12:00:00Z'), h = n => new Date(now - n * 36e5).toISOString();
  const comments = [];
  const open = [
    { number: 50, title: '[Card Work] Nullifier — engine-work', updated_at: h(49) },
    { number: 51, title: '[Arcana Queue] Foo — Cardkit SUPPORTED, unclaimed', labels: [{ name: 'arcana:engine-gap' }], updated_at: h(60) },
    { number: 52, title: '[Arcana Queue] Bar — Cardkit SUPPORTED, unclaimed', labels: [{ name: 'arcana:engine-gap' }], updated_at: h(10) },
    { number: 53, title: '[Arcana Queue] Baz — Cardkit SUPPORTED, unclaimed', labels: [], updated_at: h(99) },
  ];
  let release = { tag_name: 'B140', published_at: h(100) }, headDate = h(80);
  const github = { rest: {
      issues: { listComments() {}, listForRepo() {}, createComment: async ({ body }) => comments.push({ user: { login: 'github-actions[bot]', type: 'Bot' }, body }) },
      repos: { getBranch: async () => ({ data: { commit: { sha: 'f'.repeat(40), commit: { committer: { date: headDate } } } } }),
        getLatestRelease: async () => ({ data: release }) } },
    paginate: async fn => fn === github.rest.issues.listForRepo ? open : comments };
  assert.equal(await R.reportStalls({ github, owner: 'o', repo: 'r', payload: { items: [] }, now }), 'posted 0');
  const body = comments[0].body;
  assert.match(body, /^stalls: lane#50 lane#51 build@ffffffffffff$/m);
  assert.match(body, /#50 .*\*\*Owner:\*\* Work GPT \(engine lane\)/);
  assert.match(body, /B140 `ffffffffff` .*latest: B140.*\*\*Owner:\*\* AARMOU \(build\)/);
  // A release newer than B140's head clears the build line.
  release = { tag_name: 'B141', published_at: h(1) };
  assert.equal(await R.reportStalls({ github, owner: 'o', repo: 'r', payload: { items: [] }, now }), 'posted 0');
  assert.match(comments[1].body, /^stalls: lane#50 lane#51$/m);
});

// Durable unit/PR/check/ledger joins, without workflow writes or an extra worker.
function lifecycleFixture({ state = 'open', labels = [], checks = [run('toolkit', 'success')], repairs = [],
  events = [], suite = false, suiteChecks = [run('premerge', 'failure')], suiteRepairs = [], body = '## Cards\n- Surge Conductor\n' } = {}) {
  const now = new Date('2026-10-31T02:00:00Z');
  const card = pr({ title: 'Add Surge Conductor artifact-entry proliferation', base: { ref: 'gpt/arcana-integration-next' } });
  const unit = { number: 358, state, labels, body, title: '[Arcana Queue] Surge Conductor',
    user: { login: 'AARMOU', type: 'User' }, author_association: 'OWNER' };
  const suitePr = pr({ number: 535, body: 'Unit: #534', title: 'Add independent Surge Conductor certification', base: { ref: 'main' } });
  const suiteUnit = { number: 534, state: 'open', title: '[Arcana Suite] Surge Conductor',
    user: { login: 'github-actions[bot]', type: 'Bot' },
    body: '<!-- arcana-suite-request-v1 -->\nCard unit: #358\nCard PR: #360\nSuite key: surge-conductor\n' };
  const ledger = records => records.map((at, i) => ({ user: { login: 'github-actions[bot]', type: 'Bot' },
    body: require('./admission.cjs').formatLedger({ op: 'repair-request', issue: records === repairs ? 358 : 534, run: String(i), at }) }));
  const github = { rest: {
    pulls: { list: async ({state}) => ({data: state === 'open' ? [card, ...(suite ? [suitePr] : [])] : []}),
      listFiles: async () => ({data: cardFiles.map(filename => ({filename}))}), listReviews: async () => ({data: []}) },
    issues: { get: async ({issue_number}) => ({data: issue_number === 358 ? unit : suiteUnit}),
      listComments: async ({issue_number}) => ({data: issue_number === 358 ? ledger(repairs) : issue_number === 534 ? ledger(suiteRepairs) : []}),
      listEventsForTimeline: async () => ({data: events}) },
    checks: { listForRef: async () => ({data:{check_runs: checks}}) },
  }, paginate: async (fn, args) => (await fn(args)).data };
  // The suite gets a distinct head so the check responses cannot be confused.
  suitePr.head = {sha: 'b'.repeat(40)};
  github.rest.checks.listForRef = async ({ref}) => ({data:{check_runs: ref === suitePr.head.sha ? suiteChecks.map(c => ({...c, head_sha: ref})) : checks}});
  return { github, unit, suiteUnit, go: () => R.buildReviewInbox({github, owner:'AARMOU', repo:'arcana_table', now}) };
}

test('no-checks is distinct from a running job, including control-only and previous-head observations', () => {
  for (const checks of [[], [run('observe', 'success')], [run('toolkit','success','completed','old')]]) {
    const row = R.classify(pr(), checks, [], '', cardFiles);
    assert.equal(row.state, 'no-checks');
    assert.equal(row.readyForOwner, false);
    assert.match(row.problems[0], /No card checks observed/);
  }
});

test('closed unfinished card requests stay orphaned and never appear ready or integrated', async () => {
  const f = lifecycleFixture({state:'closed', checks:[run('toolkit','success'),certRun()]});
  const p = await f.go();
  assert.equal(p.items[0].state,'orphaned');
  assert.equal(p.items[0].validationState,'certified');
  assert.match(p.items[0].recovery.next,/preserve the attempt ledger/);
  assert.equal(p.awaitingReview,0);
  assert.deepEqual(p.integratedActivity,[]);
});

test('existing holds override a certified ready row without replacing certification evidence', async () => {
  for (const label of ['wu:halted','arcana:engine-gap']) {
    const p = await lifecycleFixture({labels:[{name:label}], checks:[run('toolkit','success'),certRun()]}).go();
    assert.equal(p.items[0].state,'held');
    assert.equal(p.items[0].certification,'certified');
    assert.equal(p.awaitingReview,0);
    assert.ok(p.items[0].recovery.who);
  }
});

const exhaustedRepairs = ['2026-10-31T00:00:00Z','2026-10-31T00:20:00Z'];
const finishedRepair = [{event:'copilot_work_started',created_at:'2026-10-31T00:21:00Z'},
  {event:'copilot_work_finished',created_at:'2026-10-31T00:50:00Z'}];
test('trusted two-repair ledger plus finished session and red checks exposes engineering escalation', async () => {
  const p = await lifecycleFixture({checks:[run('toolkit','failure')],repairs:exhaustedRepairs,events:finishedRepair}).go();
  const row = p.items[0];
  assert.equal(row.state,'repair-exhausted');
  assert.equal(row.work.repairs,2);
  assert.equal(row.work.limit,2);
  assert.equal(row.work.pickup,'finished');
  assert.match(row.recovery.next,/without another paid attempt/);
  assert.equal(row.readyForOwner,false);
});

test('exhausted budget does not imply a live repair finished; lost timeline reads remain unknown', async () => {
  const f = lifecycleFixture({checks:[run('toolkit','failure')], repairs:exhaustedRepairs,events:finishedRepair.slice(0,1)});
  let row = (await f.go()).items[0];
  assert.equal(row.state,'checks-failed');
  assert.notEqual(row.work.pickup,'finished');
  f.github.rest.issues.listEventsForTimeline = async () => {throw Error('unavailable');};
  row = (await f.go()).items[0];
  assert.equal(row.work.pickup,'unknown');
  assert.equal(row.work.repairs,2);
  assert.notEqual(row.state,'repair-exhausted');
});

test('a failing existing suite PR is named as the recovery destination, including its own exhausted budget', async () => {
  const f = lifecycleFixture({suite:true,suiteRepairs:exhaustedRepairs,events:finishedRepair});
  let row = (await f.go()).items[0];
  assert.equal(row.state,'repair-exhausted');
  assert.equal(row.suite.pr,535);
  assert.equal(row.work.repairs,0,'card repair ledger is not the suite ledger');
  assert.equal(row.suite.work.repairs,2);
  assert.match(row.recovery.next,/suite PR #535/);
  f.suiteUnit.user.login = 'someone';
  row = (await f.go()).items[0];
  assert.equal(row.suite,undefined,'untrusted request cannot bind a dependency');
  assert.equal(row.state,'awaiting-review');
});

test('canonical Oracle identity comes from the queue and never from descriptive PR titles', async () => {
  const record = {name:'Surge Conductor',type_line:'Artifact Creature',oracle_text:'When an artifact enters, proliferate.',scryfall_uri:'https://scryfall.com/card/xx/1/surge-conductor'};
  const body = '## Cards\n- Surge Conductor\n\n## Offline Oracle record (trusted feeder snapshot)\n\n    ' + JSON.stringify(record);
  const f = lifecycleFixture({body});
  let row = (await f.go()).items[0];
  assert.equal(row.name,'Surge Conductor');
  assert.equal(row.title,'Add Surge Conductor artifact-entry proliferation');
  assert.equal(row.oracle.record.name,'Surge Conductor');
  assert.equal(row.oracle.source,'trusted-queue-snapshot');
  f.unit.user.login = 'stranger';
  assert.equal((await f.go()).items[0].oracle,null);
  f.unit.user.login = 'AARMOU';
  f.unit.body = body.replace('"name":"Surge Conductor"','"name":"Different Card"');
  assert.equal((await f.go()).items[0].oracle,null,'wrong-name snapshots never reach Control');
  assert.equal(R.identityOf('## Cards\n- A\n- B\n').name,null,'ambiguous names never fall back to title');
});

test('feeder Oracle data requires an unedited trusted snapshot and failed provenance is unavailable', async () => {
  const f = lifecycleFixture();
  f.unit.user = {login:'github-actions[bot]',type:'Bot'};
  f.unit.created_at = '2026-10-30T22:00:00Z';
  const record = JSON.stringify({name:'Surge Conductor',type_line:'Artifact',oracle_text:'Proliferate.',scryfall_uri:'https://scryfall.com/card/xx/1'});
  f.unit.body = '<!-- arcana-auto-queue-v1 -->\nsource: trusted-main-queue-feeder\n## Cards\n- Surge Conductor\n## Offline Oracle record\n    ' + record;
  f.github.graphql = async () => ({repository:{issue:{createdAt:f.unit.created_at,lastEditedAt:null}}});
  assert.equal((await f.go()).items[0].oracle.record.name,'Surge Conductor');
  f.github.graphql = async () => ({repository:{issue:{createdAt:f.unit.created_at,lastEditedAt:'2026-10-31T00:00:00Z'}}});
  assert.equal((await f.go()).items[0].oracle,null);
});

test('stall text points to the failing suite and changes once when its exact head changes', async () => {
  const now = Date.parse('2026-10-31T02:00:00Z');
  const row = (await lifecycleFixture({suite:true}).go()).items[0];
  row.updatedAt = new Date(now - 26*36e5).toISOString();
  const comments = [];
  const github = {rest:{issues:{listComments(){},listForRepo(){},createComment:async ({body})=>comments.push({body,user:{login:'github-actions[bot]',type:'Bot'}})}},
    paginate:async fn=>fn===github.rest.issues.listComments?comments:[]};
  const go = () => R.reportStalls({github,owner:'AARMOU',repo:'arcana_table',payload:{items:[row]},now});
  assert.equal(await go(),'posted 1');
  assert.match(comments[0].body,/suite PR #535 \(checks-failed\)/);
  assert.doesNotMatch(comments[0].body,/add the certification suite, or label/);
  assert.equal(await go(),'unchanged');
  row.suite.headSha='c'.repeat(40);
  assert.equal(await go(),'posted 1');
});
