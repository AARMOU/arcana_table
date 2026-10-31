'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { install, load } = require('./gh-etag-cache.cjs');

// Minimal Octokit stand-in: hook.wrap('request') + request.endpoint.parse, like @octokit/core.
function fakeGithub(server) {
  let wrapped = null;
  const core = async options => server(options);
  // Like before-after-hook: the inner method is pre-bound to the original options object, so a hook
  // must mutate it; a replacement object passed to request() is ignored (as in @octokit/core).
  const request = options => (wrapped ? wrapped(() => core(options), options) : core(options));
  request.endpoint = { parse: o => ({ url: 'https://api.github.com' + o.url.replace('{n}', o.n), headers: o.headers || {} }) };
  return { hook: { wrap: (name, fn) => { assert.equal(name, 'request'); wrapped = fn; } }, request };
}
const notModified = () => Object.assign(new Error('Not Modified'), { status: 304, response: { status: 304, url: 'u', headers: { 'x-ratelimit-remaining': '9' } } });
const tmp = () => path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'etag-')), 'c', 'cache.json.gz');

test('second run sends the stored ETag and serves the cached body on 304', async () => {
  const file = tmp();
  const seen = [];
  const server = o => {
    seen.push(o.headers?.['if-none-match'] || null);
    if (o.headers?.['if-none-match'] === 'W/"a"') throw notModified();
    return { status: 200, url: 'u', headers: { etag: 'W/"a"', link: '<next>; rel="next"' }, data: [{ id: 1 }] };
  };
  const g1 = fakeGithub(server);
  const c1 = install(g1, { file, saveOnExit: false });
  const r1 = await g1.request({ method: 'GET', url: '/repos/x/issues/{n}/timeline', n: 5, headers: {} });
  assert.deepEqual(r1.data, [{ id: 1 }]);
  c1.save();
  assert.equal(Object.keys(load(file)).length, 1);

  const g2 = fakeGithub(server);
  const c2 = install(g2, { file, saveOnExit: false });
  const r2 = await g2.request({ method: 'GET', url: '/repos/x/issues/{n}/timeline', n: 5, headers: {} });
  assert.deepEqual(seen, [null, 'W/"a"']);
  assert.equal(r2.status, 200);
  assert.deepEqual(r2.data, [{ id: 1 }]);
  assert.equal(r2.headers.link, '<next>; rel="next"'); // pagination keeps working from cache
  assert.deepEqual(c2.stats, { hits: 1, misses: 0, uncached: 0, fallback: 0, readToken: false });
});

test('changed data (200) replaces the entry; writes and other errors pass through untouched', async () => {
  const file = tmp();
  let version = 'a';
  const writes = [];
  const server = o => {
    if (o.method !== 'GET') { writes.push(o.headers?.['if-none-match'] ?? null); return { status: 201, headers: {}, data: {} }; }
    if (o.url.includes('missing')) throw Object.assign(new Error('nf'), { status: 404 });
    if (o.headers?.['if-none-match'] === `"${version}"`) throw notModified();
    return { status: 200, url: 'u', headers: { etag: `"${version}"` }, data: { v: version } };
  };
  const g = fakeGithub(server);
  const c = install(g, { file, saveOnExit: false });
  const get = () => g.request({ method: 'GET', url: '/repos/x/pulls/{n}', n: 1, headers: {} });
  assert.deepEqual((await get()).data, { v: 'a' });
  assert.deepEqual((await get()).data, { v: 'a' }); // 304 hit
  version = 'b';
  assert.deepEqual((await get()).data, { v: 'b' }); // GitHub says changed: fresh body
  assert.deepEqual((await get()).data, { v: 'b' });
  await g.request({ method: 'POST', url: '/repos/x/issues/{n}/comments', n: 1, headers: {} });
  assert.deepEqual(writes, [null]);
  await assert.rejects(g.request({ method: 'GET', url: '/repos/x/missing/{n}', n: 1, headers: {} }), e => e.status === 404);
  assert.deepEqual(c.stats, { hits: 2, misses: 2, uncached: 1, fallback: 0, readToken: false });
});

test('a 304 without a cached body is never fabricated; corrupt cache files are ignored', async () => {
  const file = tmp();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, 'not gzip');
  assert.deepEqual(load(file), {});
  const g = fakeGithub(() => { throw notModified(); });
  install(g, { file, saveOnExit: false });
  await assert.rejects(g.request({ method: 'GET', url: '/repos/x/pulls/{n}', n: 2, headers: {} }), e => e.status === 304);
});

test('install is idempotent and the store is pruned by age', async () => {
  const file = tmp();
  let t = 0;
  const g = fakeGithub(o => ({ status: 200, url: 'u', headers: { etag: '"e' + o.n + '"' }, data: o.n }));
  const c = install(g, { file, saveOnExit: false, now: () => t });
  assert.equal(install(g, { file, saveOnExit: false }), c);
  await g.request({ method: 'GET', url: '/r/{n}', n: 1, headers: {} });
  t = 4 * 864e5;
  await g.request({ method: 'GET', url: '/r/{n}', n: 2, headers: {} });
  c.save();
  assert.deepEqual(Object.values(load(file)).map(e => e.data), [2]);
});

test('with a read token, GETs go through the reader (own ETag scope); writes keep the job token', async () => {
  const file = tmp();
  const jobCalls = [], readCalls = [];
  const g = fakeGithub(o => { jobCalls.push(o.method); return { status: 201, headers: {}, data: {} }; });
  const reader = { request: async o => { readCalls.push(o.headers?.['if-none-match'] || null);
    if (o.headers?.['if-none-match'] === '"r"') throw notModified();
    return { status: 200, url: 'u', headers: { etag: '"r"' }, data: { via: 'reader' } }; } };
  const c = install(g, { file, saveOnExit: false, reader });
  const get = () => g.request({ method: 'GET', url: '/repos/x/issues/{n}', n: 3, headers: {} });
  assert.deepEqual((await get()).data, { via: 'reader' });
  assert.deepEqual((await get()).data, { via: 'reader' });
  await g.request({ method: 'POST', url: '/repos/x/issues/{n}/comments', n: 3, headers: {} });
  assert.deepEqual(readCalls, [null, '"r"']);
  assert.deepEqual(jobCalls, ['POST']);
  assert.equal(c.stats.readToken, true);
  c.save();
  assert.ok(Object.keys(load(file)).every(k => k.startsWith('read|')));
});

test('a read-token refusal falls back to the job token instead of failing the workflow', async () => {
  const jobCalls = [];
  const g = fakeGithub(o => { jobCalls.push(o.url); return { status: 200, url: 'u', headers: {}, data: { via: 'job' } }; });
  const reader = { request: async () => { throw Object.assign(new Error('Resource not accessible'), { status: 403 }); } };
  const c = install(g, { file: tmp(), saveOnExit: false, reader });
  const r = await g.request({ method: 'GET', url: '/repos/x/commits/{n}/check-runs', n: 'abc', headers: {} });
  assert.deepEqual(r.data, { via: 'job' });
  assert.equal(c.stats.fallback, 1);
  const boom = { request: async () => { throw Object.assign(new Error('server'), { status: 500 }); } };
  const g2 = fakeGithub(() => ({ status: 200, headers: {}, data: {} }));
  install(g2, { file: tmp(), saveOnExit: false, reader: boom });
  await assert.rejects(g2.request({ method: 'GET', url: '/r/{n}', n: 1, headers: {} }), e => e.status === 500);
});
