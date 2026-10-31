const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const SOURCE = fs.readFileSync(path.join(__dirname, 'team-checkin.js'), 'utf8');
const KEY = 'arcana-review-session-v1';
const tick = () => new Promise(resolve => setImmediate(resolve));
function storage(value) {
  const data = new Map(value ? [[KEY, value]] : []);
  return { getItem: key => data.get(key) || null, removeItem: key => data.delete(key), setItem: (key, value) => data.set(key, value) };
}
async function browser(responses, { local = 'expired', session = 'expired' } = {}) {
  const nodes = new Map();
  for (const id of ['allHandsCheckIn', 'allHandsStatus', 'allHandsAgents', 'allHandsIssue', 'refresh'])
    nodes.set(id, { textContent: 'Check in', handlers: {}, addEventListener(type, fn) { this.handlers[type] = fn; }, replaceChildren() {}, removeAttribute() {}, append() {} });
  const window = { localStorage: storage(local), sessionStorage: storage(session), location: { href: '' } };
  const calls = [];
  vm.runInNewContext(SOURCE, { window, document: { getElementById: id => nodes.get(id), createElement: () => ({ append() {} }) },
    fetch: async (url, options) => { calls.push({ url, ...options }); const next = responses.shift(); assert.ok(next, 'unexpected request'); return { status: next.status, json: async () => next.body }; } });
  await tick();
  return { window, calls, nodes, click: () => nodes.get('allHandsCheckIn').handlers.click() };
}
test('expired session from initial status read is removed so next tap opens sign-in', async () => {
  const b = await browser([{ status: 401 }]);
  await b.click();
  assert.match(b.window.location.href, /^https:\/\/arcana-review\.arcanamtg\.workers\.dev\/auth\/login\?/);
  assert.equal(b.window.localStorage.getItem(KEY), null);
  assert.equal(b.window.sessionStorage.getItem(KEY), null);
  assert.equal(b.calls.length, 1);
});
test('expired POST session can recover on next tap without sending another POST', async () => {
  const b = await browser([{ status: 200, body: { ok: true, request: null } }, { status: 401 }]);
  await b.click();
  assert.equal(b.nodes.get('allHandsCheckIn').disabled, false);
  await b.click();
  assert.match(b.window.location.href, /\/auth\/login\?/);
  assert.equal(b.calls.filter(c => c.method === 'POST').length, 1);
});
test('a rejected token never deletes a different session stored meanwhile', async () => {
  const b = await browser([{ status: 401 }], { local: 'expired', session: 'newer-session' });
  assert.equal(b.window.localStorage.getItem(KEY), null);
  assert.equal(b.window.sessionStorage.getItem(KEY), 'newer-session');
});
test('no session goes directly to owner login without an API write', async () => {
  const b = await browser([], { local: null, session: null });
  await b.click();
  assert.match(b.window.location.href, /\/auth\/login\?/);
  assert.equal(b.calls.length, 0);
});
