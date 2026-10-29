'use strict';
// Owner card requests: classify every requested card (not only the six precons), route cards Copilot
// cannot take yet to GPT as "[Card Work]" issues, and hand them back to the Copilot queue once
// Cardkit says they are ready. Also: stuck queue issues and repeat paid retries.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const Q = require('./queue-feeder.cjs');
const F = require('./feasibility.cjs');
const A = require('./admission.cjs');

const BASE = 'gpt/arcana-integration-next';
const SHA = '9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f';
const OWNER = { author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' } };
const BOT = { login: 'github-actions[bot]', type: 'Bot' };
const request = (number, names, extra = {}) => ({ number, ...OWNER, title: '[Card Request] ' + names.join(', '),
  created_at: '2026-10-28T14:00:00Z', body: '## Cards requested\n' + names.map(n => '- ' + n + ' — Manually entered').join('\n') + '\n', ...extra });
const gaps = (cards, { verified = true, claimed = {} } = {}) => ({
  dispatch: { claimsVerified: verified, dispatchable: verified, claimed,
    copilot: cards.filter(c => c.readiness === 'SUPPORTED' && c.route === 'copilot' && !claimed[c.name]).map(c => c.name) },
  cards });
const card = (name, readiness, route = null, extra = {}) => ({ name, readiness, route, reasons: ['reason for ' + name], ...extra });
const PRECONS = gaps([card('Celestine, the Living Saint', 'SUPPORTED', 'copilot')]);
const unitBody = name => ['## Cards', '- ' + name, '', '## Acceptance', '- ' + name + ': exact behavior',
  '- Exact current Oracle behavior (cost, types, every ability, targeting and timing), checked against authoritative card records',
  '- Executable regression scenarios for positive and negative behavior, registration and deterministic asset export',
  'Base: ' + BASE, ''].join('\n');

test('requested deck lists only owner-selected names, one per line, for a second Cardkit run', () => {
  const outsider = { ...request(9, ['Sol Ring']), author_association: 'NONE', user: { login: 'x', type: 'User' } };
  assert.equal(Q.requestedDeck([request(1, ['Broodlord', 'Ulalek, Fused Atrocity']), outsider]),
    '1 Broodlord\n1 Ulalek, Fused Atrocity\n');
  assert.equal(Q.requestedDeck([]), '');
});

test('a requested card outside the six precons becomes Copilot-eligible when Cardkit supports it', () => {
  const issues = [request(10, ['Darksteel Monolith'])];
  const before = Q.candidates({ gaps: PRECONS, openIssues: issues });
  assert.deepEqual(before.requested, []);
  assert.equal(before.blockedRequests[0].outcome, 'unclassified');
  const r = Q.candidates({ gaps: PRECONS, requestedGaps: gaps([card('Darksteel Monolith', 'SUPPORTED', 'copilot')]), openIssues: issues });
  assert.deepEqual(r.requested, [{ name: 'Darksteel Monolith', issue: 10 }]);
  assert.equal(r.names[0], 'Darksteel Monolith');
  assert.ok(r.eligibleNames.includes('Darksteel Monolith'));
  assert.deepEqual(r.resolvedRequests, ['Darksteel Monolith']);
});

test('unverified requested classification adds nothing: no eligibility and no GPT work', () => {
  const r = Q.candidates({ gaps: PRECONS, openIssues: [request(11, ['Darksteel Monolith', 'Broodlord'])],
    requestedGaps: gaps([card('Darksteel Monolith', 'SUPPORTED', 'copilot'), card('Broodlord', 'NEEDS_REVIEW', 'gpt')], { verified: false }) });
  assert.deepEqual(r.requested, []);
  assert.deepEqual(r.workRequests, []);
  assert.equal(r.requestedClassified, false);
  assert.ok(r.blockedRequests.every(b => b.outcome === 'unclassified'));
});

test('each blocked request gets a precise outcome; only engine and review cards go to GPT', () => {
  const issues = [request(12, ['Sol Ring', 'Abstruse Archaic', 'Ulalek, Fused Atrocity', 'Broodlord', 'Eldrazi Temple'])];
  const requested = gaps([
    card('Sol Ring', 'IMPLEMENTED_AND_TESTED'),
    card('Abstruse Archaic', 'SUPPORTED', 'copilot'),
    card('Ulalek, Fused Atrocity', 'NEEDS_REVIEW', 'claude', { reviewHint: 'possible-engine-gap' }),
    card('Broodlord', 'NEEDS_REVIEW', 'gpt'),
    card('Eldrazi Temple', 'ENGINE_BLOCKED', 'claude'),
  ], { claimed: { 'Abstruse Archaic': 'PR #344 (copilot/x)' } });
  const r = Q.candidates({ gaps: PRECONS, requestedGaps: requested, openIssues: issues });
  const by = Object.fromEntries(r.blockedRequests.map(b => [b.name, b]));
  assert.equal(by['Sol Ring'].outcome, 'implemented');
  assert.equal(by['Abstruse Archaic'].outcome, 'claimed');
  assert.match(by['Abstruse Archaic'].reason, /PR #344/);
  assert.equal(by['Ulalek, Fused Atrocity'].outcome, 'engine-work');
  assert.equal(by['Broodlord'].outcome, 'needs-review');
  assert.equal(by['Eldrazi Temple'].outcome, 'engine-work');
  assert.deepEqual(r.workRequests.map(w => w.name).sort(), ['Broodlord', 'Eldrazi Temple', 'Ulalek, Fused Atrocity']);
  assert.deepEqual(r.resolvedRequests.sort(), ['Abstruse Archaic', 'Sol Ring']);
  // None of them becomes a Copilot unit.
  assert.deepEqual(r.names, ['Celestine, the Living Saint']);
  const p = Q.queuePayload({ result: r, units: [], base: BASE, baseSha: SHA });
  assert.equal(p.workRequests.length, 3);
  assert.equal(p.requestedClassified, true);
  assert.match(Q.queueSummary(p).join('\n'), /Broodlord \(#12, needs-review\)/);
});

test('a waiting queue issue Copilot cannot take no longer freezes the automatic queue', () => {
  const stuckIssue = { number: 300, ...OWNER, title: '[Arcana Queue] Abstruse Archaic', body: unitBody('Abstruse Archaic'), assignees: [] };
  const r = Q.candidates({ gaps: PRECONS, openIssues: [stuckIssue],
    requestedGaps: null });
  assert.deepEqual(r.notReady, []); // unknown is not "not ready": stays conservative
  const claimedPrecons = gaps([card('Celestine, the Living Saint', 'SUPPORTED', 'copilot'), card('Abstruse Archaic', 'SUPPORTED', 'copilot')],
    { claimed: { 'Abstruse Archaic': 'PR #344 (copilot/x)' } });
  const r2 = Q.candidates({ gaps: claimedPrecons, openIssues: [stuckIssue] });
  assert.deepEqual(r2.notReady, ['Abstruse Archaic']);
  const p = Q.queuePayload({ result: r2, units: [{ name: 'Celestine, the Living Saint', body: unitBody('Celestine, the Living Saint') }], base: BASE, baseSha: SHA });
  const pick = Q.chooseAutoQueue(p, [stuckIssue]);
  assert.equal(pick.suggestion.name, 'Celestine, the Living Saint');
  assert.deepEqual(pick.stuck, [{ issue: 300, cards: ['Abstruse Archaic'] }]);
  // A waiting issue for a card that is still ready keeps the old behaviour (wake the dispatcher).
  const fine = { ...stuckIssue, number: 301, title: '[Arcana Queue] Celestine', body: unitBody('Celestine, the Living Saint') };
  assert.equal(Q.chooseAutoQueue(p, [stuckIssue, fine]).pendingIssue, 301);
});

test('a card whose earlier trusted queue issue closed without landing is held, not paid for again', () => {
  const r = Q.candidates({ gaps: PRECONS, openIssues: [] });
  const p = Q.queuePayload({ result: r, units: [{ name: 'Celestine, the Living Saint', body: unitBody('Celestine, the Living Saint') }], base: BASE, baseSha: SHA });
  const closedBot = { number: 340, user: BOT, author_association: 'NONE', state: 'closed',
    title: '[Arcana Queue] Celestine, the Living Saint — Cardkit SUPPORTED, unclaimed', body: A.AUTO_QUEUE_MARKER + '\n' + unitBody('Celestine, the Living Saint') };
  const held = Q.chooseAutoQueue(p, [], [closedBot]);
  assert.equal(held.suggestion, undefined);
  assert.deepEqual(held.held, [{ name: 'Celestine, the Living Saint', issue: 340 }]);
  // An outsider's closed lookalike cannot block a card.
  const outsider = { ...closedBot, user: { login: 'x', type: 'User' }, body: unitBody('Celestine, the Living Saint') };
  assert.equal(Q.chooseAutoQueue(p, [], [outsider]).suggestion.name, 'Celestine, the Living Saint');
});

const workPayload = (requests, resolved = []) => ({ ok: true, requestedClassified: true, base: BASE, baseSha: SHA,
  workRequests: requests, resolvedRequests: resolved });
const ULALEK = { name: 'Ulalek, Fused Atrocity', issue: 12, outcome: 'engine-work', readiness: 'NEEDS_REVIEW', route: 'claude',
  hint: 'possible-engine-gap', cardkitReasons: ['1 line(s) without a close implemented match (best 0.58)'] };
const KNUCKLES = { name: 'Knuckles the Echidna', issue: 350, outcome: 'needs-review', readiness: 'NEEDS_REVIEW', route: 'gpt',
  cardkitReasons: ['no cached Oracle record: run `tools/cardkit/cardkit oracle fetch` (needs Scryfall access)'] };
const workIssue = (number, r, state = 'open', user = BOT) => ({ number, state, user, title: Q.cardWorkTitle(r),
  body: Q.cardWorkBody(r, { runId: 1, baseSha: SHA, base: BASE }) });

test('one card-work issue per blocked card, ever; never for unclassified runs', () => {
  assert.equal(Q.planCardWork({ ...workPayload([ULALEK]), requestedClassified: false }).create, null);
  assert.equal(Q.planCardWork(workPayload([ULALEK])).create.name, ULALEK.name);
  // Existing open or closed bot issue for the card: nothing new.
  assert.equal(Q.planCardWork(workPayload([ULALEK]), [workIssue(400, ULALEK)]).create, null);
  assert.equal(Q.planCardWork(workPayload([ULALEK]), [workIssue(400, ULALEK, 'closed')]).create, null);
  // A human-made lookalike does not count as the bot issue and cannot suppress routing.
  assert.equal(Q.planCardWork(workPayload([ULALEK]), [workIssue(401, ULALEK, 'open', { login: 'someone', type: 'User' })]).create.name, ULALEK.name);
  // Next blocked card is picked once the first has an issue.
  assert.equal(Q.planCardWork(workPayload([ULALEK, KNUCKLES]), [workIssue(400, ULALEK)]).create.name, KNUCKLES.name);
});

test('open card work is capped so a flood of requests cannot flood GPT', () => {
  const many = Array.from({ length: 10 }, (_, k) => workIssue(500 + k, { ...KNUCKLES, name: 'Card ' + k }));
  const plan = Q.planCardWork(workPayload([ULALEK]), many);
  assert.equal(plan.create, null);
  assert.equal(plan.reason, 'card-work-limit-reached');
});

test('card work is closed and handed back once Cardkit says the card is ready', () => {
  const plan = Q.planCardWork(workPayload([], [ULALEK.name]), [workIssue(400, ULALEK), workIssue(401, KNUCKLES, 'closed')]);
  assert.deepEqual(plan.close, [{ issue: 400, name: ULALEK.name }]);
});

test('card-work body routes to GPT with real-engine rules, and never looks like a Copilot unit', () => {
  const body = Q.cardWorkBody(KNUCKLES, { runId: 9, baseSha: SHA, base: BASE });
  assert.ok(body.startsWith(Q.CARD_WORK_MARKER + '\n'));
  assert.match(body, /oracle fetch/);
  assert.match(body, /owner-request:#350/);
  assert.match(body, /No Copilot assignment/);
  assert.ok(!/@[A-Za-z0-9]/.test(body), 'no mentions');
  const engine = Q.cardWorkBody(ULALEK, { runId: 9, baseSha: SHA, base: BASE });
  assert.match(engine, /never as app-side workarounds/);
  // Not a queue unit: the dispatcher and feasibility gate never see it, nor the owner-priority parser.
  const issue = workIssue(400, ULALEK);
  assert.deepEqual(F.collectNames([issue]), []);
  assert.equal(A.unitActionable(issue.body, BASE).ok, false);
  assert.deepEqual(Q.ownerSelections([{ ...issue, ...OWNER }]), []);
});

test('syncCardWork closes resolved work, opens one new issue, and is inert when disabled', async () => {
  const calls = [];
  const github = {
    paginate: async (fn, args) => { calls.push(['list', args]); return [workIssue(400, KNUCKLES)]; },
    rest: { issues: {
      listForRepo: () => {},
      createComment: async a => { calls.push(['comment', a]); },
      update: async a => { calls.push(['update', a]); },
      create: async a => { calls.push(['create', a]); return { data: { number: 410 } }; },
    } },
  };
  const off = await Q.syncCardWork({ github, owner: 'AARMOU', repo: 'arcana_table', payload: workPayload([ULALEK]), runId: 5, enabled: false });
  assert.equal(off.reason, 'normal-auto-dispatch-not-enabled');
  assert.equal(calls.length, 0);
  const out = await Q.syncCardWork({ github, owner: 'AARMOU', repo: 'arcana_table', payload: workPayload([ULALEK], [KNUCKLES.name]), runId: 5, enabled: true });
  assert.deepEqual(out.closed, [{ issue: 400, name: KNUCKLES.name }]);
  assert.deepEqual(out.created, { issue: 410, name: ULALEK.name, outcome: 'engine-work' });
  assert.equal(calls[0][1].creator, 'github-actions[bot]');
  assert.equal(calls[0][1].state, 'all');
  const update = calls.find(c => c[0] === 'update')[1];
  assert.deepEqual([update.issue_number, update.state], [400, 'closed']);
  const created = calls.find(c => c[0] === 'create')[1];
  assert.equal(created.title, '[Card Work] Ulalek, Fused Atrocity — needs engine work');
  assert.equal(created.assignees, undefined);
  assert.equal(created.labels, undefined);
});

test('control site is redeployed only when the integration base actually moved', () => {
  const p = { ok: true, baseSha: SHA };
  assert.equal(Q.needsSiteRefresh({ baseSha: 'a'.repeat(40) }, p), true);
  assert.equal(Q.needsSiteRefresh({ baseSha: SHA }, p), false);
  assert.equal(Q.needsSiteRefresh(null, p), false);
  assert.equal(Q.needsSiteRefresh({ baseSha: 'a'.repeat(40) }, { ok: false, baseSha: SHA }), false);
});

test('outcome mapping never invents readiness', () => {
  assert.equal(Q.outcomeOf(undefined), 'unclassified');
  assert.equal(Q.outcomeOf({ readiness: 'SUPPORTED', route: 'gpt' }), 'unclassified');
  assert.equal(Q.outcomeOf({ readiness: 'IMPLEMENTED_AND_TESTED', claimedBy: 'PR #1' }), 'implemented');
});

test('feeder status counts verified Cardkit classes once per normalized card and names its exact source', () => {
  const source = gaps([
    card('Free Card', 'SUPPORTED', 'copilot'),
    card('Review Card', 'NEEDS_REVIEW', 'gpt'),
    card('Engine Card', 'ENGINE_BLOCKED', 'claude'),
    card('Landed Card', 'IMPLEMENTED_AND_TESTED'),
    card('Claimed Card', 'SUPPORTED', 'copilot'),
    card('Other Route', 'SUPPORTED', 'gpt'),
    card('Front // Back', 'SUPPORTED', 'copilot'),
  ], { claimed: { 'Claimed Card': 'PR #9' } });
  const requested = gaps([
    card('free card', 'SUPPORTED', 'copilot'),
    card('Front // Back', 'NEEDS_REVIEW', 'gpt'),
  ]);
  const result = Q.candidates({ gaps: source, requestedGaps: requested });
  assert.deepEqual(result.classificationCounts, {
    distinctNames: 7,
    counts: { supportedCopilotUnclaimed: 1, needsReview: 1, engineBlocked: 1,
      implementedAndTested: 1, claimed: 1, unclassified: 2 },
  });
  const p = Q.queuePayload({ result, base: BASE, baseSha: SHA, now: new Date('2026-10-29T16:00:00Z') });
  const summary = Q.queueSummary(p).join('\n');
  assert.match(summary, /not an index-gap or rules-playability count/);
  assert.match(summary, new RegExp(SHA));
  assert.match(summary, /checked 2026-10-29T16:00:00.000Z/);
  assert.match(summary, /SUPPORTED\/copilot\/unclaimed 1, NEEDS_REVIEW 1, ENGINE_BLOCKED 1, IMPLEMENTED_AND_TESTED 1, claimed 1, unclassified 2/);
  assert.equal(p.available, result.available);
});

test('incomplete claim evidence suppresses the diagnostic breakdown', () => {
  const incomplete = gaps([card('Unverified Claim', 'SUPPORTED', 'copilot')]);
  delete incomplete.dispatch.claimed;
  const result = Q.candidates({ gaps: incomplete });
  assert.equal(result.classificationCounts, null);
  const p = Q.queuePayload({ result, base: BASE, baseSha: SHA });
  assert.match(Q.queueSummary(p).join('\n'), /Cardkit classification breakdown unavailable/);
  assert.equal(result.eligibleNames.includes('Unverified Claim'), true);
});

test('feeder workflow classifies owner requests in the no-secret job and routes in the trusted publish job', () => {
  const fs = require('node:fs');
  const y = fs.readFileSync('.github/workflows/arcana-queue-feeder.yml', 'utf8');
  const suggest = y.slice(y.indexOf('  suggest:'), y.indexOf('  publish:'));
  const publish = y.slice(y.indexOf('  publish:'));
  assert.match(suggest, /requestedDeck\(openIssues,demand\)/);
  assert.match(suggest, /issue_number:DEMAND_ISSUE/);
  assert.match(suggest, /gaps --claims-from-github --deck \.\.\/requested\.txt/);
  assert.match(suggest, /requestedGaps,openIssues,demand,max\}\)/);
  assert.ok(!/\$\{\{\s*secrets\./.test(suggest), 'suggest job stays secret-free');
  assert.match(publish, /Q\.syncCardWork\(/);
  assert.match(publish, /workflow_id:'pages\.yml'/);
  assert.match(publish, /needsSiteRefresh\(previous,payload\)/);
});

test('control site lets the owner file blocked cards, shows routing, and still never dispatches', () => {
  const fs = require('node:fs');
  const js = fs.readFileSync('control/card-queue.js', 'utf8');
  assert.match(js, /function allVerified\(\)\{\n return !checking && chosen\.size>0 &&/);
  assert.match(js, /routed to GPT/);
  assert.match(js, /Preflight\.assessCard\(identity\.name,eligibility\)/);
  assert.ok(!/POST|PUT|PATCH|DELETE|createWorkflowDispatch|issues\.create/.test(js));
});

// ---- Public app demand (#321): votes reorder Copilot-ready cards only --------------------------------
const workerComment = (name, votes, extra = {}) => ({ author_association: 'OWNER', user: { login: 'AARMOU', type: 'User' },
  body: ['<!-- arcana-card-request-v1 -->', '**Requested card:** ' + name, '**Opt-in requests:** ' + votes,
    '**Status:** Needs library, duplicate-claim and rules-engine verification.'].join('\n'), ...extra });

test('public demand trusts only the Worker\'s owner-authored marker comments', () => {
  const d = Q.publicDemand([
    workerComment('Treasure Nabber', 2), workerComment('Shield Sphere', 5), workerComment('Arachnogenesis', 1),
    workerComment('Fake Card', 999, { author_association: 'NONE', user: { login: 'stranger', type: 'User' } }),
    { ...workerComment('No Marker', 50), body: '**Requested card:** No Marker\n**Opt-in requests:** 50' },
    workerComment('@someone', 9), workerComment('Bad Count', 'many'),
  ]);
  assert.deepEqual(d, [{ name: 'Shield Sphere', votes: 5 }, { name: 'Treasure Nabber', votes: 2 }, { name: 'Arachnogenesis', votes: 1 }]);
});

test('demand reorders only Copilot-ready cards, after the owner\'s own requests', () => {
  const precons = gaps([card('Celestine, the Living Saint', 'SUPPORTED', 'copilot'), card('Darksteel Monolith', 'SUPPORTED', 'copilot')]);
  const owner = request(20, ['Darksteel Monolith']);
  const demand = [{ name: 'Shield Sphere', votes: 9 }, { name: 'Celestine, the Living Saint', votes: 3 }, { name: 'Treasure Nabber', votes: 2 }];
  const requested = gaps([card('Darksteel Monolith', 'SUPPORTED', 'copilot'), card('Shield Sphere', 'SUPPORTED', 'copilot'),
    card('Treasure Nabber', 'NEEDS_REVIEW', 'gpt')]);
  const r = Q.candidates({ gaps: precons, requestedGaps: requested, openIssues: [owner], demand, max: 5 });
  // Owner first, then by votes; Treasure Nabber is not Copilot-ready and never becomes a unit.
  assert.deepEqual(r.names, ['Darksteel Monolith', 'Shield Sphere', 'Celestine, the Living Saint']);
  assert.deepEqual(r.publicRequests.map(x => [x.name, x.status]),
    [['Shield Sphere', 'copilot-ready'], ['Celestine, the Living Saint', 'copilot-ready'], ['Treasure Nabber', 'not-copilot-ready']]);
  // Demand never creates GPT work: only owner selections do.
  assert.deepEqual(r.workRequests, []);
  assert.ok(!r.blockedRequests.some(b => b.name === 'Treasure Nabber'));
  const p = Q.queuePayload({ result: r, units: [], base: BASE, baseSha: SHA });
  assert.equal(Q.planCardWork(p, []).create, null);
  assert.match(Q.queueSummary(p).join('\n'), /Shield Sphere ×9 \(ready\)/);
});

test('public demand is classified with the owner selections, capped and de-duplicated', () => {
  const many = Array.from({ length: 80 }, (_, i) => ({ name: 'Card ' + i, votes: 80 - i }));
  const deck = Q.requestedDeck([request(21, ['Card 0', 'Broodlord'])], many).trim().split('\n');
  assert.equal(deck[0], '1 Card 0');
  assert.equal(deck[1], '1 Broodlord');
  assert.equal(deck.length, 2 + 49); // owner's 2 + top 50 demand, minus the duplicate Card 0
});


test('repeated separate owner requests rank within owner lane, never behind public votes', () => {
  const ownerFirst = request(701, ['Owner A', 'Owner A', 'Owner B']);
  const ownerSecond = request(702, ['Owner B']);
  const ownerThird = request(703, ['Owner B'], { author_association: 'NONE', user: {login:'outsider',type:'User'} });
  const precons = gaps([card('Owner A', 'SUPPORTED', 'copilot'),
    card('Owner B', 'SUPPORTED', 'copilot'), card('Public C', 'SUPPORTED', 'copilot')]);
  const publicVotes = [{name:'Public C', votes:999}, {name:'Owner A', votes:5}];
  const r = Q.candidates({ gaps:precons, openIssues:[ownerFirst,ownerSecond,ownerThird],
    demand:publicVotes, max:3 });
  assert.deepEqual([...Q.ownerRequestCounts([ownerFirst,ownerSecond,ownerThird])], [['owner a',1],['owner b',2]]);
  assert.deepEqual(r.names, ['Owner B','Owner A','Public C']);
  assert.deepEqual(r.ownerDemand.map(d => [d.name,d.requests]), [['Owner B',2],['Owner A',1]]);
  const payload = Q.queuePayload({result:r,units:[],base:BASE,baseSha:SHA});
  assert.deepEqual(payload.ownerDemand,r.ownerDemand);
  assert.match(Q.queueSummary(payload).join('\n'), /Owner-requested priorities/);
});

test('duplicate owner card lines count once, blocked cards never become Copilot jobs', () => {
  const owner = request(704, ['Supported A','Supported A','Blocked B']);
  const precons = gaps([card('Supported A','SUPPORTED','copilot'),
    card('Blocked B','ENGINE_BLOCKED','claude')]);
  const r = Q.candidates({gaps:precons,openIssues:[owner],
    demand:[{name:'Blocked B',votes:999}],max:3});
  assert.deepEqual(r.names,['Supported A']);
  assert.equal(Q.ownerRequestCounts([owner]).get('supported a'),1);
  assert.equal(r.blockedRequests[0].outcome,'engine-work');
  assert.deepEqual(r.workRequests.map(w=>w.name),['Blocked B']);
});
