import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, webcrypto } from 'node:crypto';
import { readyIds, extractReview, validSubscription, handleCardPush, runCardReadyPush,
  NOTIFICATION_TITLE } from '../src/card-ready-push.mjs';

globalThis.crypto ||= webcrypto;
const SHA = 'a'.repeat(40), NEXT = 'b'.repeat(40);
const now = Date.parse('2026-11-01T16:00:00Z');
const card = (sha=SHA) => ({ pr: 777, headSha: sha, kind: 'card', hasTest: true,
  state: 'certified', certification: 'certified', shapeProblem: null, readyForOwner: true, preflight: { status: 'passed' } });
const report = (items, clock=now) => ({ kind: 'review', checkedAt: new Date(clock).toISOString(), items });
const comments = value => [{ user: { login: 'github-actions[bot]', type: 'Bot' },
  body: '<!-- arcana-control-status-v1:review -->\n```json\n' + JSON.stringify(value) + '\n```' }];
const subscription = { endpoint: 'https://fcm.googleapis.com/fcm/send/test-123',
  keys: { p256dh: 'key', auth: 'auth' } };
const pairs = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const privateJwk = pairs.privateKey.export({ format: 'jwk' });
const publicKey = 'B' + 'A'.repeat(86); // Public-key syntax is not tested at push transport boundary.
function store() {
  const kv = new Map();
  return { async get(key){ return kv.has(key) ? JSON.parse(kv.get(key)) : null; },
    async put(key,value){kv.set(key,value);}, async delete(key){kv.delete(key);} };
}
const env = () => ({ CARD_PUSH: store(), VAPID_PUBLIC_KEY: publicKey,
  VAPID_PRIVATE_JWK: JSON.stringify(privateJwk), GITHUB_PUBLIC_READ_TOKEN: 'read-only-token' });

test('ready means independently certified exact-head card with tests; never green CI alone', () => {
  assert.deepEqual(readyIds(report([card(), { ...card(), pr: 778, state: 'awaiting-review' },
    { ...card(), pr: 779, hasTest: false }, { ...card(), pr: 780, certification: 'not-run' },
    { ...card(), pr: 781, kind: 'engine' }, { ...card(), pr: 782, shapeProblem: 'not-integrable' }]), now), ['777:' + SHA]);
  assert.equal(readyIds(report([card()], now - 21 * 60 * 1000), now), null);
  assert.equal(readyIds(report([card()], now + 2 * 60 * 1000), now), null);
  assert.equal(readyIds({ items: [card()] }, now), null);
  assert.equal(NOTIFICATION_TITLE, '🪿🪿🪿CARD READY FOR APPROVAL HONK🪿🪿🪿');
});
test('only the trusted #78 bot status marker is accepted', () => {
  assert.deepEqual(extractReview(comments(report([card()]))).items, [card()]);
  assert.equal(extractReview([{ ...comments(report([card()]))[0], user: { login: 'someone', type: 'User' } }]), null);
  assert.equal(extractReview([{ ...comments(report([card()]))[0], body: 'unmarked\n```json\n{}\n```' }]), null);
  assert.equal(extractReview([{ ...comments(report([card()]))[0], body: '<!-- arcana-control-status-v1:review -->\n```json\ninvalid\n```' }]), null);
});
test('subscription is owner-only on the route, URL restricted and server state may be disabled', async () => {
  assert.equal(validSubscription(subscription), true);
  assert.equal(validSubscription({ ...subscription, endpoint: 'http://localhost/internal' }), false);
  assert.equal(validSubscription({ ...subscription, endpoint: 'https://attacker.example/push' }), false);
  const e=env();
  const req=new Request('https://example.test', { method:'POST', body:JSON.stringify(subscription) });
  assert.deepEqual(await handleCardPush('POST', req, e), [200, { enabled: true }]);
  assert.deepEqual(await handleCardPush('GET', new Request('https://example.test'), e), [200, { enabled: true }]);
  assert.deepEqual(await handleCardPush('DELETE', new Request('https://example.test'), e), [200, { enabled: false }]);
});
test('push once per new certified PR head; stale reports and failures never mark as sent', async () => {
  const e=env();
  await handleCardPush('POST', new Request('https://example.test', { method:'POST', body:JSON.stringify(subscription) }), e);
  let current=report([card()]); let pushes=0; let fail=false;
  const fetchImpl=async (url, init) => {
    if (url.includes('/issues/78/comments?')) {
      assert.equal(init.headers.Authorization, 'Bearer read-only-token');
      return new Response(JSON.stringify(comments(current)), { status: 200 });
    }
    assert.equal(url, subscription.endpoint);
    assert.match(init.headers.Authorization, /^vapid t=.+, k=/);
    assert.equal(init.headers.TTL, '3600');
    pushes++;
    return new Response(null, { status: fail ? 503 : 201 });
  };
  assert.deepEqual(await runCardReadyPush(e, { fetchImpl, clock:now }), { ready:1, alerted:1 });
  assert.deepEqual(await runCardReadyPush(e, { fetchImpl, clock:now }), { ready:1, alerted:0 });
  current=report([card(NEXT)]);
  fail=true;
  assert.deepEqual(await runCardReadyPush(e, { fetchImpl, clock:now }), { skipped:'push-service-http-503' });
  fail=false;
  assert.deepEqual(await runCardReadyPush(e, { fetchImpl, clock:now }), { ready:1, alerted:1 });
  current=report([card(NEXT)], now - 25 * 60 * 1000);
  assert.deepEqual(await runCardReadyPush(e, { fetchImpl, clock:now }), { skipped:'review-feed-invalid-or-stale' });
  assert.equal(pushes,3);
});

test('no honk until destination preflight passed: pending, blocked, unknown, missing or held all fail closed', () => {
  for (const extra of [{ readyForOwner: false, preflight: { status: 'pending' } }, { readyForOwner: false, preflight: { status: 'blocked' } },
    { readyForOwner: false, preflight: { status: 'unknown' } }, { readyForOwner: true, preflight: undefined },
    { readyForOwner: undefined, preflight: { status: 'passed' } }, { readyForOwner: true, preflight: { status: 'blocked' } }])
    assert.deepEqual(readyIds(report([{ ...card(), ...extra }]), now), [], JSON.stringify(extra));
  assert.deepEqual(readyIds(report([card()]), now), ['777:' + SHA]);
});
