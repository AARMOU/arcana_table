import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.mjs';
import { publicGithub, isAllowedGithubPath } from '../src/public-github.mjs';

const W = 'https://arcana-review.example.workers.dev';
const PWA = 'https://aarmou.github.io';
const path = '/pulls?state=open&sort=updated&direction=desc&per_page=100';
const url = p => W + '/v1/public/github?path=' + encodeURIComponent(p);

const fakeCache = () => {
  const data = new Map();
  return {
    data,
    async match(request) { return data.get(request.url)?.clone() || null; },
    async put(request, response) { data.set(request.url, response.clone()); },
  };
};

test('public feed only proxies a fixed set of AARMOU read-only GitHub endpoints', async () => {
  for (const p of [path, '/issues/78/comments?per_page=100',
    '/issues/81/comments?per_page=100&page=3', '/issues/150',
    '/issues/150/timeline?per_page=100', '/pulls/371',
    '/actions/runs?per_page=30', '/actions/runs?branch=gpt%2Ftest&per_page=20',
    '/actions/workflows/argentum-weekly-upgrade.yml/runs?per_page=10',
    '/issues?state=open&per_page=100']) assert.equal(isAllowedGithubPath(p), true, p);

  for (const p of ['https://evil.example/steal', '/user', '/repos/other/private',
    '/issues/150/comments?per_page=100', '/issues/150?token=secret',
    '/issues/../../admin', '/actions/runs?per_page=9999',
    '/issues/81/comments?per_page=100&page=0', '/issues/78/comments?per_page=100&foo=bar',
    '/issues?state=all&per_page=100', '/pulls/0']) assert.equal(isAllowedGithubPath(p), false, p);

  let calls = 0;
  const fetched = async () => { calls++; return new Response('[]'); };
  const blocked = await publicGithub(new Request(url('https://evil.example/steal'), { headers: { Origin: PWA } }), { fetchImpl: fetched });
  assert.equal(blocked.status, 403);
  assert.equal(calls, 0);
});

test('browser public feed is CORS-allowlisted, read-only and available without OAuth secrets', async () => {
  let calls = 0;
  const fetched = async (requestUrl, init) => {
    calls++;
    assert.equal(requestUrl, 'https://api.github.com/repos/AARMOU/arcana_table' + path);
    assert.ok(!init.headers.Authorization);
    return new Response(JSON.stringify([{ number: 371, title: 'Example' }]), { headers: { Link: '<https://api.github.com/page=2>; rel="next"' } });
  };
  const req = new Request(url(path), { headers: { Origin: PWA } });
  const res = await worker.fetch(req, {}, {}, fetched);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), [{ number: 371, title: 'Example' }]);
  assert.equal(res.headers.get('Access-Control-Allow-Origin'), PWA);
  assert.equal(res.headers.get('X-Arcana-Feed'), 'live');
  assert.ok(res.headers.get('Access-Control-Expose-Headers').includes('Link'));
  assert.equal(calls, 1);
  assert.equal((await worker.fetch(new Request(url(path), { method: 'POST' }), {}, {}, fetched)).status, 405);
});

test('edge cache serves repeated refreshes without repeat GitHub API traffic', async () => {
  const cache = fakeCache();
  let calls = 0;
  const fetched = async () => { calls++; return new Response(JSON.stringify({ workflow_runs: [1, 2, 3] })); };
  const req = new Request(url('/actions/runs?per_page=30'), { headers: { Origin: PWA } });
  const one = await publicGithub(req, { fetchImpl: fetched, cache });
  assert.equal(one.headers.get('X-Arcana-Feed'), 'live');
  const two = await publicGithub(req, { fetchImpl: fetched, cache });
  assert.equal(two.headers.get('X-Arcana-Feed'), 'cached');
  assert.deepEqual(await two.json(), { workflow_runs: [1, 2, 3] });
  assert.equal(calls, 1);
  assert.equal(cache.data.size, 2);
});

test('stale cache survives rate limits while displaying stale status, never invents fresh data', async () => {
  const cache = fakeCache();
  const req = new Request(url('/issues/78/comments?per_page=100'), { headers: { Origin: PWA } });
  const first = await publicGithub(req, { cache, fetchImpl: async () => new Response('[{"id":78}]') });
  assert.equal(first.status, 200);
  for (const key of cache.data.keys()) if (key.includes('/fresh?')) cache.data.delete(key);
  const old = await publicGithub(req, { cache, fetchImpl: async () => new Response('rate limited', { status: 403 }) });
  assert.equal(old.status, 200);
  assert.equal(old.headers.get('X-Arcana-Feed'), 'stale');
  assert.deepEqual(await old.json(), [{ id: 78 }]);
  const noCache = await publicGithub(req, { cache: fakeCache(), fetchImpl: async () => new Response('rate limited', { status: 403 }) });
  assert.equal(noCache.status, 503);
  assert.equal((await noCache.json()).error, 'public-github-feed-unavailable');
});

test('invalid upstream JSON never enters edge cache', async () => {
  const cache = fakeCache();
  const result = await publicGithub(new Request(url(path)), { cache, fetchImpl: async () => new Response('<html>bad gateway</html>') });
  assert.equal(result.status, 503);
  assert.equal(cache.data.size, 0);
});
