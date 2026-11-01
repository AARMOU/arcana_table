'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const R = require('./review.js');
const src = fs.readFileSync(path.join(__dirname, 'review.js'), 'utf8');
const SHA = 'a'.repeat(40);

test('all seven status categories have owner-facing labels', () => {
  assert.deepEqual(Object.values(R.LABELS).sort(), ['Awaiting Review', 'Changes Requested', 'Checks Failed', 'Checks Running',
    'Independently Certified', 'Integrated', 'Owner Approved'].sort());
});

test('only reviewable cards can be decided; approve needs independent certification', () => {
  const item = (s, certification) => ({ pr: 360, headSha: SHA, state: s, certification, kind: 'card', hasTest: true, draft: false,
    readyForOwner: true, preflight: { status: 'passed' } });
  assert.equal(R.canApprove(item('certified', 'certified')), true);
  assert.equal(R.approveBlocked(item('certified', 'certified')), null);
  // Green checks without certification (e.g. PR #344): missing, running, failed all fail closed.
  for (const c of [undefined, 'not-run', 'running', 'failed']) {
    assert.equal(R.canApprove(item('awaiting-review', c)), false, String(c));
    assert.match(R.approveBlocked(item('awaiting-review', c)), /independent certification[\s\S]*request a fix/, String(c));
    assert.equal(R.canDecide(item('awaiting-review', c)), true, 'Request Fix stays available');
  }
  assert.match(R.decisionMessage(422, { error: 'not-certified', certification: 'running' }).text, /not passed independent certification \(running\)/);
  for (const s of ['checks-failed', 'checks-running', 'approved', 'changes-requested']) {
    assert.equal(R.canApprove(item(s)), false, s);
    assert.equal(R.canDecide(item(s)), true, s);
  }
  assert.equal(R.canDecide(item('integrated')), false);
  assert.equal(R.canDecide({ pr: 360, headSha: 'short', state: 'awaiting-review' }), false);
  assert.equal(R.canApprove({ ...item('certified', 'certified'), draft: true }), true, 'Approve marks a certified draft ready');
  assert.equal(R.canApprove({ ...item('checks-running', 'not-run'), draft: true }), false);
  assert.equal(R.canApprove({ ...item('certified', 'certified'), hasTest: false }), false);
  assert.equal(R.canApprove({ ...item('certified', 'certified'), kind: 'audit' }), false);
  assert.equal(R.canDecide({ ...item('certified', 'certified'), kind: 'engine' }), false);
  assert.equal(R.isReadyForOwner({ ...item('certified', 'certified'), kind: undefined }), false);
});

test('decision outcomes: success, duplicate, auth failure, stale commit, failed checks', () => {
  assert.match(R.decisionMessage(200, { state: 'approved' }).text, /exact commit.*nothing was merged/i);
  assert.match(R.decisionMessage(200, { state: 'changes-requested' }).text, /only if budget, reserve and capacity allow/);
  assert.match(R.decisionMessage(200, { duplicate: true }).text, /Already recorded/);
  assert.deepEqual([R.decisionMessage(401, { error: 'sign-in-required' }).signIn, R.decisionMessage(401, {}).ok], [true, false]);
  const stale = R.decisionMessage(409, { error: 'stale-commit' });
  assert.equal(stale.stale, true); assert.match(stale.text, /Nothing was recorded/);
  assert.match(R.decisionMessage(422, { error: 'checks-not-green', checks: ['cards-and-android'] }).text, /cards-and-android/);
  assert.equal(R.decisionMessage(0, null).ok, false);
  assert.match(R.decisionMessage(422, { error: 'draft-pr' }).text, /draft/);
  assert.match(R.decisionMessage(502, { error: 'ready-for-review-failed' }).text, /no approval was recorded/);
  assert.match(R.decisionMessage(422, { error: 'not-a-verified-card-implementation' }).text, /No verified card source/);
});

test('sign-in session arrives in the URL fragment only and is then stripped', () => {
  assert.equal(R.sessionFromHash('#review-session=abc.def'), 'abc.def');
  assert.equal(R.sessionFromHash('#other=1'), null);
  assert.match(src, /history\.replaceState\(null, '', location\.pathname \+ location\.search\)/);
});

test('no GitHub token, no raw HTML, one write endpoint, and it is the review Worker', () => {
  assert.doesNotThrow(() => new vm.Script(src));
  assert.ok(!/innerHTML|outerHTML|insertAdjacentHTML|document\.write/.test(src));
  assert.ok(!/ghp_|gho_|github_pat_|api\.github\.com\/repos\/[^'"]*\/(reviews|merge)/.test(src));
  const posts = src.match(/method: 'POST'/g) || [];
  assert.equal(posts.length, 1);
  assert.match(src, /fetch\(REVIEW_API \+ '\/v1\/decision', \{ method: 'POST'/);
  assert.match(src, /never a merge, build, signing or release|never merges, builds, signs or releases/);
});

test('owner sign-in button is actually hidden by dashboard CSS after authentication', () => {
  const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
  assert.match(html, /\.btn\[hidden\]\s*\{\s*display\s*:\s*none\s*!important\s*\}/);
  assert.match(src, /signIn\.hidden = ownerReady;/);
});

test('dashboard loads it, the shell caches it and Pages publishes it', () => {
  const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
  assert.ok(html.includes('<script src="./review.js" defer></script>'));
  assert.ok(html.includes('window.ArcanaReview.render(list,r,cardReviewChanged)'));
  assert.ok(fs.readFileSync(path.join(__dirname, 'sw.js'), 'utf8').includes("'./review.js'"));
  assert.ok(fs.readFileSync(path.join(__dirname, '../.github/workflows/pages.yml'), 'utf8').includes('cp control/review.js _site/control/review.js'));
});


test('descriptive PR titles use the verified identity and trusted offline Oracle without a network call', async () => {
  const item = {card:'Zephyrim with Squad and Miracle',name:'Zephyrim',issue:703,
    oracle:{source:'trusted-queue-snapshot',issue:703,record:{name:'Zephyrim',mana_cost:'{3}{W}',
      type_line:'Creature — Human Warrior',oracle_text:'Squad {2}\nFlying, vigilance\nMiracle {1}{W}'}}};
  let calls=0;
  const text = await R.loadOracle(item, async()=>{calls++;throw Error('offline');});
  assert.match(text,/Squad \{2\}/);
  assert.match(text,/Source: trusted queue Oracle snapshot/);
  assert.equal(calls,0);
});

test('Oracle fallback uses the exact single-card identity and refuses unrelated or untrusted snapshots', async () => {
  for (const oracle of [null,
    {source:'untrusted',issue:703,record:{name:'Zephyrim',type_line:'WRONG'}},
    {source:'trusted-queue-snapshot',issue:999,record:{name:'Zephyrim',type_line:'WRONG'}},
    {source:'trusted-queue-snapshot',issue:703,record:{name:'Other Card',type_line:'WRONG'}}]) {
    const text=await R.loadOracle({card:'Zephyrim with Squad and Miracle',name:'Zephyrim',issue:703,oracle},async url=>{
      assert.equal(url,'https://api.scryfall.com/cards/named?exact=Zephyrim');
      return new Response(JSON.stringify({name:'Zephyrim',type_line:'Creature',oracle_text:'Flying, vigilance'}));
    });
    assert.match(text,/Flying, vigilance/);
    assert.doesNotMatch(text,/WRONG/);
  }
});

test('missing identity, wrong-name Scryfall, and offline fallback never invent Oracle rules', async () => {
  let calls=0;
  assert.match(await R.loadOracle({card:'Implement any of two cards'},async()=>{calls++;}),/no single verified card name/);
  assert.equal(calls,0);
  assert.match(await R.loadOracle({name:'Zephyrim'},async()=>new Response(JSON.stringify({name:'Other',type_line:'WRONG'}))),/unavailable/);
  assert.match(await R.loadOracle({name:'Zephyrim'},async()=>{throw Error('offline');}),/offline/);
  assert.match(await R.loadOracle({name:'Plains'},async()=>new Response(JSON.stringify({name:'Plains',type_line:'Basic Land — Plains',oracle_text:''}))),/Basic Land/);
});

test('multi-face Oracle rules survive exact-name fallback', async () => {
  const text=await R.loadOracle({name:'Front // Back'},async()=>new Response(JSON.stringify({name:'Front // Back',type_line:'Creature // Creature',
    card_faces:[{name:'Front',oracle_text:'Flying'},{name:'Back',oracle_text:'Vigilance'}]})));
  assert.match(text,/Front: Flying\nBack: Vigilance/);
});

test('quota errors show a bounded recovery time and the failed approval stage', () => {
  const text=R.decisionMessage(503,{error:'github-rate-limited',upstreamStatus:403,stage:'checks',
    rateLimitResource:'core',retryAt:new Date(Date.now()+120000).toISOString()}).text;
  assert.match(text,/reading checks/);
  assert.match(text,/GitHub quota: core/);
  assert.match(text,/Retry in about [23] minute/);
  assert.match(text,/No approval recorded/);
  assert.doesNotMatch(R.decisionMessage(503,{error:'github-rate-limited',upstreamStatus:429,stage:'<private>',retryAt:'invalid'}).text,/<private>|NaN|Invalid Date/);
});

test('certified but destination preflight not passed is never ready to Verify (fails closed)', () => {
  const base = { pr: 360, headSha: SHA, state: 'certified', certification: 'certified', kind: 'card', hasTest: true, draft: false };
  for (const extra of [{}, { readyForOwner: false, preflight: { status: 'pending' } }, { readyForOwner: false, preflight: { status: 'blocked' } },
    { readyForOwner: false, preflight: { status: 'unknown' } }, { readyForOwner: true }, { readyForOwner: true, preflight: { status: 'blocked' } }]) {
    const i = { ...base, ...extra };
    assert.equal(R.isReadyForOwner(i), false, JSON.stringify(extra));
    assert.equal(R.canApprove(i), false, JSON.stringify(extra));
    assert.match(R.approveBlocked(i), /destination preflight/);
  }
  assert.equal(R.canApprove({ ...base, readyForOwner: true, preflight: { status: 'passed' } }), true);
});
