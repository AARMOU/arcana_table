const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const O = require('./orders-page.js');
const html = fs.readFileSync(require('node:path').join(__dirname, 'index.html'), 'utf8');
const start = html.indexOf('const directFeedCache=new Map();');
const end = html.indexOf('function statusForRun(run)', start);
const refreshStart = html.indexOf('async function refreshOrders()');
const refreshEnd = html.indexOf('async function refresh(){', refreshStart);
const LIVE_PATH = '/issues/883/comments?per_page=100';
const HOST = 'https://api.github.com/repos/AARMOU/arcana_table';
const payload = { checkedAt: '2026-11-05T16:00:00Z', facts: ['Repair review pending'], inbox: [] };
const comments = [{ user: { login: 'github-actions[bot]', type: 'Bot' },
  body: '<!-- arcana-owner-status-v1 -->\n```json\n' + JSON.stringify(payload) + '\n```' }];

function harness(fetchImpl) {
  let now = Date.parse('2026-11-05T16:05:00Z');
  class Clock extends Date { static now() { return now; } }
  const nodes = new Map();
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { style: {}, textContent: '' });
    return nodes.get(id);
  };
  const rendered = [];
  const ctx = vm.createContext({ Map, Date: Clock, REPO: 'AARMOU/arcana_table',
    PUBLIC_GITHUB: 'https://edge.example/?path=',
    staleFeeds: 0, cachedFeeds: 0, sourceTimes: [], staleSources: [],
    isStaleFeed: () => false, feedCheckedAt: () => now, feedStale: () => false,
    fetch: fetchImpl, document: {}, $: node, text: (id, value) => { node(id).textContent = value; },
    timeAgo: () => '5m ago', window: { ArcanaOrders: { ...O,
      render(doc, box, issue, open, at, live) {
        rendered.push({ issue, live });
        return { ok: !!issue, updated: issue?.updated_at, liveCheckedAt: live?.checkedAt,
          liveStale: !!live && at - Date.parse(live.checkedAt) > 90 * 60 * 1000 };
      } } },
  });
  vm.runInContext(html.slice(start, end) + html.slice(refreshStart, refreshEnd) +
    '\nthis.callApi=api; this.refreshOrders=refreshOrders;', ctx);
  return { ctx, rendered, nodes, advance(ms) { now += ms; } };
}

test('Work refresh reads the real public bot-comment array despite the edge route refusal', async () => {
  const { isAllowedGithubPath, publicGithub } = await import('./review-worker/src/public-github.mjs');
  assert.equal(isAllowedGithubPath(LIVE_PATH), false);
  const refusal = await publicGithub(new Request('https://edge.example/?path=' + encodeURIComponent(LIVE_PATH)));
  assert.equal(refusal.status, 403);
  assert.equal((await refusal.json()).error, 'github-path-not-allowed');
  let direct = 0;
  const h = harness(async (url, options) => {
    if (url === HOST + LIVE_PATH) {
      direct++;
      assert.equal(options.headers.Authorization, undefined);
      assert.equal(options.method, undefined);
      return new Response(JSON.stringify(comments));
    }
    const path = decodeURIComponent(String(url).split('?path=')[1]);
    assert.notEqual(path, LIVE_PATH, 'Work must bypass the undeployed comments route');
    return new Response(JSON.stringify(path === '/issues/883'
      ? { body: '## Current orders', updated_at: payload.checkedAt } : []));
  });
  await h.ctx.refreshOrders();
  assert.deepEqual(h.rendered[0].live, payload);
  assert.match(h.nodes.get('ordersMeta').textContent, /^Live checked/);
  await h.ctx.refreshOrders();
  assert.equal(direct, 1, 'Refresh reuses the bounded five-minute direct copy');
  h.advance(5 * 60 * 1000);
  await h.ctx.refreshOrders();
  assert.equal(direct, 2);
});

test('failed direct reads are coalesced and bounded, then recover after five minutes', async () => {
  let calls = 0;
  const h = harness(async () => {
    calls++;
    return calls === 1 ? new Response('{}', { status: 429 }) : new Response(JSON.stringify(comments));
  });
  const failed = await Promise.allSettled([h.ctx.callApi(LIVE_PATH), h.ctx.callApi(LIVE_PATH)]);
  assert.ok(failed.every(r => r.status === 'rejected'));
  await assert.rejects(h.ctx.callApi(LIVE_PATH), /retry delayed/);
  assert.equal(calls, 1);
  h.advance(5 * 60 * 1000);
  assert.deepEqual(O.parseLive((await h.ctx.callApi(LIVE_PATH)).data), payload);
  assert.equal(calls, 2);
});

test('a failed live read leaves the manual board available and reports live unavailable', async () => {
  const h = harness(async url => {
    if (url === HOST + LIVE_PATH) throw Error('offline');
    return new Response(JSON.stringify(String(url).includes('comments') ? [] :
      { body: '## Current orders', updated_at: payload.checkedAt }));
  });
  await h.ctx.refreshOrders();
  assert.ok(h.rendered[0].issue);
  assert.equal(h.rendered[0].live, null);
  assert.match(h.nodes.get('ordersMeta').textContent, /^Live status not available/);
});

test('malformed and non-bot comments cannot provide a heartbeat', async () => {
  for (const data of [{ message: 'wrong shape' }, [{ ...comments[0], user: { type: 'User' } }]]) {
    const h = harness(async () => new Response(JSON.stringify(data)));
    await h.ctx.refreshOrders();
    assert.equal(h.rendered[0].live, null);
  }
});

test('direct exception does not bypass other forbidden edge paths', async () => {
  let calls = 0;
  const h = harness(async url => {
    calls++;
    assert.ok(String(url).startsWith('https://edge.example/'));
    return new Response('{"error":"github-path-not-allowed"}', { status: 403 });
  });
  await assert.rejects(h.ctx.callApi('/issues/884/comments?per_page=100'), /HTTP 403/);
  assert.equal(calls, 1);
});
