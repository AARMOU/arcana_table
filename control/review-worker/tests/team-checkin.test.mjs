import test from 'node:test';
import assert from 'node:assert/strict';
import { requestAllHandsCheckIn, checkInStatus, MARKER } from '../src/team-checkin.mjs';
const NOW = Date.parse('2026-10-31T05:30:00.000Z');
const issue = { number: 81, comments: 102 };
const owner = { login: 'AARMOU' };
function mock(rows = [], override = {}) {
  const calls = [];
  const api = async (path, options) => {
    calls.push([path, options]);
    if (path === '/issues/81') return { ok: true, body: override.issue || issue };
    if (path === '/issues/81/comments?per_page=100&page=1') return { ok: true, body: [] };
    if (path === '/issues/81/comments?per_page=100&page=2') return { ok: true, body: rows };
    if (path === '/issues/81/comments') return override.write || { ok: true,
      body: { id: 12345, html_url: 'https://github.com/AARMOU/arcana_table/issues/81#issuecomment-12345', created_at: new Date(NOW).toISOString() } };
    throw Error('unexpected API ' + path);
  };
  return { api, calls };
}
const request = (when, id = '20261031T052000Z') => ({
  id: 123, user: owner, author_association: 'OWNER', created_at: when,
  body: MARKER + id + ' -->\n## all hands'
});
test('one authenticated request posts a single status-only comment with three distinct acknowledgments', async () => {
  const m = mock();
  const [status, out] = await requestAllHandsCheckIn(m.api, NOW);
  assert.equal(status, 200); assert.equal(out.request.id, 12345);
  const post = m.calls.filter(([path, cfg]) => path === '/issues/81/comments' && cfg?.method === 'POST');
  assert.equal(post.length, 1);
  const body = JSON.parse(post[0][1].body).body;
  for (const role of ['goosegeta', 'claude', 'boneknuckles']) assert.match(body, new RegExp('CHECK-IN .+ ' + role));
  assert.match(body, /No new paid sessions/);
  assert.doesNotMatch(body, /@copilot|@claude|workflow_dispatch/i);
});
test('recent request is reused, not spammed, even with a second tap', async () => {
  const m = mock([request('2026-10-31T05:20:00.000Z')]);
  const [status, out] = await requestAllHandsCheckIn(m.api, NOW);
  assert.equal(status, 200); assert.equal(out.duplicate, true);
  assert.equal(m.calls.filter(([path]) => path === '/issues/81/comments').length, 0);
});
test('untrusted lookalike comment does not suppress owner request', async () => {
  const fake = {...request('2026-10-31T05:20:00.000Z'), author_association: 'NONE'};
  const m = mock([fake]);
  const [status, out] = await requestAllHandsCheckIn(m.api, NOW);
  assert.equal(status, 200); assert.equal(out.duplicate, false);
});
test('read-only status attributes replies to request id; unrelated or older reports do not count', async () => {
  const m = mock([
    request('2026-10-31T05:20:00.000Z'),
    {id: 124, author_association: 'OWNER', created_at: '2026-10-31T05:22:00.000Z',
      body: 'CHECK-IN 20261031T052000Z goosegeta\nClaim ...'},
    {id: 125, author_association: 'NONE', created_at: '2026-10-31T05:23:00.000Z',
      body: 'CHECK-IN 20261031T052000Z claude'},
    {id: 126, author_association: 'OWNER', created_at: '2026-10-31T05:24:00.000Z',
      body: 'CHECK-IN old boneknuckles'}
  ]);
  const [status, out] = await checkInStatus(m.api, NOW);
  assert.equal(status, 200);
  assert.equal(out.acknowledgments.goosegeta.id, 124);
  assert.equal(out.acknowledgments.claude, null);
  assert.equal(out.acknowledgments.boneknuckles, null);
  assert.equal(m.calls.some(([_, cfg]) => cfg?.method === 'POST'), false);
});
test('comment read failure prevents write (fail closed)', async () => {
  const m = mock([], { issue: {number:81, comments:'unknown'} });
  const [status] = await requestAllHandsCheckIn(m.api, NOW);
  assert.equal(status, 503); assert.equal(m.calls.length, 1);
});
test('uncertain GitHub write does not pretend the request was delivered', async () => {
  const m = mock([], { write: { ok:false, status:502, body:null } });
  const [status, out] = await requestAllHandsCheckIn(m.api, NOW);
  assert.equal(status, 502); assert.equal(out.error, 'post-unconfirmed');
});
