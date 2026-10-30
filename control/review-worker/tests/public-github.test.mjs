import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import worker from '../src/index.mjs';
import { publicGithub, isAllowedGithubPath } from '../src/public-github.mjs';

const W = 'https://arcana-review.example.workers.dev';
const PWA = 'https://aarmou.github.io';
const path = '/pulls?state=open&sort=updated&direction=desc&per_page=100';
const url = p => W + '/v1/public/github?path=' + encodeURIComponent(p);

test('only an explicit read-only set of public repo GitHub endpoints is allowed', () => {
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
});

test('public Github endpoint is CORS-allowlisted and never needs OAuth credentials', async () => {
  let calls = 0;
  const upstream = async (requestUrl, init) => {
    calls++;
    assert.equal(requestUrl, 'https://api.github.com/repos/AARMOU/arcana_table' + path);
    assert.ok(!init.headers.Authorization);
    return new Response(JSON.stringify([{ number: 372, title: 'Example' }]), {
      headers: { Link: '<https://api.github.com/page=2>; rel="next"' } });
  };
  const req = new Request(url(path), { headers: { Origin: PWA } });
  const res = await worker.fetch(req, {}, {}, upstream);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), [{ number: 372, title: 'Example' }]);
  assert.equal(res.headers.get('Access-Control-Allow-Origin'), PWA);
  assert.equal(res.headers.get('X-Arcana-Feed'), 'live');
  assert.equal(res.headers.get('Link'), '<https://api.github.com/page=2>; rel="next"');
  assert.ok(res.headers.get('Access-Control-Expose-Headers').includes('Cf-Cache-Status'));
  assert.equal(calls, 1);
  assert.equal((await worker.fetch(new Request(url(path), { method: 'POST' }), {}, {}, upstream)).status, 405);
});

test('native Workers Cache is configured and response uses 15 minute freshness, stale revalidation and stale-on-error', async () => {
  const toml = readFileSync(new URL('../wrangler.toml', import.meta.url), 'utf8');
  const workflow = readFileSync(new URL('../../../.github/workflows/deploy-review-worker.yml', import.meta.url), 'utf8');
  assert.match(toml, /compatibility_date = "2026-10-27"/);
  assert.match(toml, /\[cache\]\nenabled = true/);
  assert.match(workflow, /wrangler@4\.(?:69|[7-9]\d|\d{3,})\.\d+/);
  const res = await publicGithub(new Request(url(path)), {
    fetchImpl: async () => new Response('[{"id":78}]'),
    clock: () => new Date('2026-10-28T18:00:00Z'),
  });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('X-Arcana-Checked-At'), '2026-10-28T18:00:00.000Z');
  assert.equal(res.headers.get('Last-Modified'), 'Wed, 28 Oct 2026 18:00:00 GMT');
  const cc = res.headers.get('Cache-Control');
  assert.ok(cc.includes('public, max-age=900'), cc);
  assert.ok(cc.includes('stale-while-revalidate=3600'), cc);
  assert.ok(cc.includes('stale-if-error=86400'), cc);
  assert.ok(!cc.includes('s-maxage'), cc);
});

test('public feed denies arbitrary URL without upstream traffic', async () => {
  let calls = 0;
  const response = await publicGithub(new Request(url('https://evil.example/steal')), {
    fetchImpl: async () => { calls++; return new Response('bad'); } });
  assert.equal(response.status, 403);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(calls, 0);
});

test('API limits and invalid data return uncached errors with status clues', async () => {
  const req = new Request(url(path));
  const limited = await publicGithub(req, { fetchImpl: async () => new Response('limited', { status: 403 }) });
  assert.equal(limited.status, 503);
  assert.deepEqual(await limited.json(), { error: 'github-upstream-http-403' });
  assert.equal(limited.headers.get('Cache-Control'), 'no-store');
  const bad = await publicGithub(req, { fetchImpl: async () => new Response('<html>invalid</html>') });
  assert.equal(bad.status, 503);
  assert.deepEqual(await bad.json(), { error: 'public-github-feed-unavailable' });
  assert.equal(bad.headers.get('Cache-Control'), 'no-store');
});

test('no legacy caches.default calls: they cannot be relied on for workers.dev', () => {
  const source = readFileSync(new URL('../src/public-github.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source.replace(/^\/\/.*$/gm, ''), /globalThis\.caches\b|caches\.default|cache\.put\(/);
});

test('optional read token authenticates the upstream read and shortens the edge cache', async () => {
  let sent;
  const ok = async (_u, init) => { sent = init.headers; return new Response('[]', { status: 200 }); };
  const anon = await publicGithub(new Request(url(path)), { fetchImpl: ok });
  assert.equal(sent.Authorization, undefined);
  assert.match(anon.headers.get('Cache-Control'), /max-age=900\b/);
  const authed = await publicGithub(new Request(url(path)), { fetchImpl: ok, token: 'ghp_readonly' });
  assert.equal(sent.Authorization, 'Bearer ghp_readonly');
  assert.match(authed.headers.get('Cache-Control'), /max-age=60\b/);
  // The token never appears in the response.
  assert.doesNotMatch(JSON.stringify([...authed.headers]) + await authed.text(), /ghp_readonly/);
  // Disallowed paths are still refused before any upstream call.
  sent = null;
  assert.equal((await publicGithub(new Request(url('/user')), { fetchImpl: ok, token: 'ghp_readonly' })).status, 403);
  assert.equal(sent, null);
});
