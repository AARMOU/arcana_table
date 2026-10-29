'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const O = require('./owner-fix.cjs');

const HEAD = 'c'.repeat(40);
const unit = (extra = {}) => ({ number: 358, title: '[Arcana Queue] Celestine — Cardkit SUPPORTED, unclaimed', labels: [{ name: O.FIX_LABEL }], ...extra });
const pr = (extra = {}) => ({ number: 360, user: { login: 'Copilot' }, body: '- Fixes #358', head: { sha: HEAD }, state: 'open', ...extra });
const fixReview = (extra = {}) => ({ id: 77, user: { login: 'AARMOU' }, commit_id: HEAD, state: 'CHANGES_REQUESTED',
  body: O.DECISION_MARKER + '\nsha:' + HEAD + '\n**Owner requested a fix** (Arcana Control):\n\ncost reduction ignores tokens', ...extra });

function fake({ units = [unit()], prs = [pr()], reviews = [fixReview()], labeledBy = 'AARMOU', comments = [], freshHead = HEAD } = {}) {
  const writes = [];
  const github = {
    paginate: async (fn, args) => {
      if (fn === github.rest.issues.listForRepo) return units;
      if (fn === github.rest.pulls.list) return prs;
      if (fn === github.rest.issues.listEventsForTimeline) return [{ event: 'labeled', label: { name: O.FIX_LABEL }, actor: { login: labeledBy } }];
      if (fn === github.rest.pulls.listReviews) return reviews;
      if (fn === github.rest.issues.listComments) return comments;
      throw Error('unexpected paginate');
    },
    rest: {
      issues: { listForRepo() {}, listEventsForTimeline() {}, listComments() {},
        removeLabel: async a => { writes.push(['unlabel', a.issue_number]); },
        createComment: async a => { writes.push(['comment', a.issue_number, a.body]); } },
      pulls: { list() {}, listReviews() {}, get: async () => ({ data: pr({ head: { sha: freshHead } }) }) },
    },
  };
  return { github, writes };
}
const allow = { allowed: true, attempt: 1, unit: 358, reason: 'ok', record: async () => { allow.recorded = (allow.recorded || 0) + 1; } };
const deny = { allowed: false, reason: 'budget: 10% reserve reached' };
const run = (f, decision, delivery = {
  preflight:async()=>({ok:true}),
  post:async x=>{f.writes.push(['comment',x.issue_number,x.body]);return {ok:true,id:99};},
}) => O.routeOwnerFixes({ github: f.github, owner: 'AARMOU', repo: 'arcana_table', runId: 4242,
  authorizer: async () => decision, delivery });

test('admitted fix request asks Copilot once, through the ledger, and consumes the label', async () => {
  allow.recorded = 0;
  const f = fake();
  const [r] = await run(f, allow);
  assert.equal(r.state, 'repair-requested');
  assert.equal(allow.recorded, 1);
  const comment = f.writes.find(w => w[0] === 'comment');
  assert.equal(comment[1], 360);
  assert.ok(comment[2].startsWith(O.ROUTED_MARKER) && comment[2].includes('@copilot') && comment[2].includes('cost reduction ignores tokens'));
  assert.deepEqual(f.writes.at(-1), ['unlabel', 358]);
});

test('budget, reserve or capacity refusal records an observation and starts no worker', async () => {
  const f = fake();
  const [r] = await run(f, deny);
  assert.equal(r.state, 'observed-only');
  const comment = f.writes.find(w => w[0] === 'comment')[2];
  assert.ok(!comment.includes('@copilot') && comment.includes('10% reserve') && comment.includes('no worker started'));
});

test('stale, duplicate, foreign and non-owner requests never reach Copilot', async () => {
  const cases = [
    [{ reviews: [fixReview({ commit_id: 'd'.repeat(40) })] }, 'no-current-fix-request'],
    [{ reviews: [fixReview(), { ...fixReview({ id: 78, state: 'APPROVED' }) }] }, 'no-current-fix-request'],
    [{ reviews: [fixReview({ body: 'typed on GitHub, not Arcana Control' })] }, 'no-current-fix-request'],
    [{ labeledBy: 'stranger' }, 'ignored-not-owner'],
    [{ units: [unit({ title: 'Some issue' })] }, 'ignored-not-a-card-unit'],
    [{ prs: [pr({ user: { login: 'someone' } })] }, 'no-open-copilot-pr'],
    [{ comments: [{ user: { login: 'github-actions[bot]', type: 'Bot' }, body: O.ROUTED_MARKER + '\nowner-review-id:77\nhead-sha:' + HEAD }] }, 'already-routed'],
    [{ freshHead: 'e'.repeat(40) }, 'stale-before-post'],
  ];
  for (const [opts, state] of cases) {
    allow.recorded = 0;
    const f = fake(opts);
    const [r] = await run(f, allow);
    assert.equal(r.state, state, JSON.stringify(opts));
    assert.equal(allow.recorded, 0);
    assert.ok(!f.writes.some(w => w[0] === 'comment'), state);
    assert.deepEqual(f.writes.at(-1), ['unlabel', 358]);
  }
});

test('owner fix delivery identity check fails closed without repair record',async()=>{
  allow.recorded=0;const f=fake();
  const result=await run(f,allow,{preflight:async()=>({ok:false,reason:'owner-delivery-identity-not-owner'}),
    post:async()=>{throw Error('should not post');}});
  assert.equal(result[0].state,'observed-only');assert.equal(allow.recorded,0);
  assert.ok(f.writes.some(w=>w[0]==='comment' && w[1]===360 && !w[2].includes('@copilot')));
});
test('uncertain owner delivery reserves only one attempt and reports uncertainty',async()=>{
  allow.recorded=0;const f=fake();
  const result=await run(f,allow,{preflight:async()=>({ok:true}),
    post:async()=>({ok:false,reason:'owner-delivery-post-http-403'})});
  assert.equal(result[0].state,'delivery-uncertain');assert.equal(allow.recorded,1);
  assert.deepEqual(f.writes.at(-1),['unlabel',358]);
  assert.ok(!f.writes.some(w=>w[0]==='comment'));
});
test('mentions in the owner note are neutralized; nothing to do without labels', async () => {
  const f = fake({ reviews: [fixReview({ body: O.DECISION_MARKER + '\nsha:' + HEAD + '\nask @someone' })] });
  await run(f, allow);
  assert.ok(!f.writes.find(w => w[0] === 'comment')[2].includes('@someone'));
  assert.deepEqual(await run(fake({ units: [] }), allow), []);
});
