'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { loadUnits, unitOf } = require('./units.cjs');

const T = m => new Date(Date.now() - m * 60000).toISOString();
// Mirrors the real #54 case: Copilot opened PR #59 and GitHub recorded a cross-reference,
// but the PR body never mentions #54.
function fake({ crossBy = 'Copilot', prBody = 'Refs #34 and #81', connected = true } = {}) {
  const timelines = {
    54: [{ event: 'assigned', created_at: T(600) },
      { event: 'cross-referenced', created_at: T(599), actor: { login: crossBy }, source: { issue: { number: 59, pull_request: {} } } },
      ...(connected ? [{ event: 'connected', created_at: T(599), subject: { type: 'pull_request', number: 59 } }] : [])],
    68: [{ event: 'cross-referenced', created_at: T(500), actor: { login: 'Copilot' }, source: { issue: { number: 59, pull_request: {} } } }],
    59: [{ event: 'copilot_work_started', created_at: T(300) }, { event: 'copilot_work_finished', created_at: T(240) }],
  };
  const data = {
    issues: [{ number: 54, title: '[Coding agent] Eldrazi', body: '', labels: [], assignees: [{ login: 'Copilot' }] },
      { number: 68, title: '[Arcana Queue] other', body: '', labels: [], assignees: [] }],
    prs: [{ number: 59, body: prBody }],
  };
  return { paginate: async (fn, a) => fn(a), rest: {
    issues: { listForRepo: () => data.issues, listComments: () => [], listEventsForTimeline: a => timelines[a.issue_number] || [] },
    pulls: { list: () => data.prs } } };
}

test('GitHub connected link assigns the PR to exactly one unit, even without a body reference', async () => {
  const d = await loadUnits({ github: fake(), owner: 'o', repo: 'r' });
  const u54 = d.units.find(u => u.raw.number === 54), u68 = d.units.find(u => u.raw.number === 68);
  assert.deepEqual(u54.prs.map(p => p.number), [59]);
  assert.deepEqual([u54.derived.state, u54.derived.holdsSlot], ['review', false]);
  // Mere cross-references (PR text mentioning another queue issue) do not link.
  assert.equal(u68.prs.length, 0);
});

test('without a connected link or reference, the stale assignment keeps its slot (safe direction)', async () => {
  const u = (await loadUnits({ github: fake({ connected: false }), owner: 'o', repo: 'r' })).units.find(x => x.raw.number === 54);
  assert.equal(u.prs.length, 0);
  assert.deepEqual([u.derived.state, u.derived.reason, u.derived.holdsSlot], ['halted', 'pickup-timeout', true]);
});

test('an explicit Unit line overrides the connected link', async () => {
  const d = await loadUnits({ github: fake({ prBody: 'Unit: #68' }), owner: 'o', repo: 'r' });
  assert.deepEqual(d.units.find(u => u.raw.number === 68).prs.map(p => p.number), [59]);
  assert.equal(d.units.find(u => u.raw.number === 54).prs.length, 0);
});

test('explicit Unit lines win over other references', () => {
  assert.equal(unitOf('Unit: #68\nsee #54', new Set([54, 68])), 68);
  assert.equal(unitOf('see #54 and #68', new Set([54, 68])), 54);
  assert.equal(unitOf('Unit: #12', new Set([54])), null);
});
