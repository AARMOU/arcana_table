'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { refreshWorkerStatus } = require('./worker-status.cjs');
const ctx = { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 321, eventName: 'workflow_run' };
const core = { info() {} };
const now = () => new Date('2026-10-29T05:00:00Z');

test('read-only observer publishes a fresh GitHub unit snapshot', async () => {
  let reads = 0, published;
  const result = await refreshWorkerStatus({
    github: {}, context: ctx, core, now,
    load: async args => {
      reads++;
      assert.equal(args.owner, 'AARMOU');
      assert.deepEqual(args.approvers, ['AARMOU']);
      assert.equal(args.now, Date.parse('2026-10-29T05:00:00Z'));
      return { units: [
        { derived: { number: 401, state: 'working', holdsSlot: true } },
        { derived: { number: 402, state: 'pending-pickup', holdsSlot: true } },
      ] };
    },
    publish: async args => { published = args; return 'updated'; },
  });
  assert.equal(reads, 1);
  assert.equal(published.kind, 'workers');
  assert.equal(published.payload.checkedAt, '2026-10-29T05:00:00.000Z');
  assert.equal(published.payload.workers.running, 1);
  assert.equal(published.payload.workers.pendingPickup, 1);
  assert.equal(published.payload.workers.list.length, 2);
  assert.equal(result.published, 'updated');
});

test('observer reports publication errors instead of claiming success', async () => {
  await assert.rejects(refreshWorkerStatus({
    github: {}, context: ctx, core, now,
    load: async () => ({ units: [] }),
    publish: async () => 'failed:403',
  }), /publication failed/);
});

test('read failure cannot overwrite last known status with invented zeros', async () => {
  let published = false;
  await assert.rejects(refreshWorkerStatus({
    github: {}, context: ctx, core, now,
    load: async () => { throw Error('GitHub unavailable'); },
    publish: async () => { published = true; return 'updated'; },
  }), /GitHub unavailable/);
  assert.equal(published, false);
});

test('event bursts skip the expensive reload while the published status is fresh', async () => {
  const run = async (eventName, isFresh, payload = {}) => {
    let loads = 0, asked;
    const r = await refreshWorkerStatus({
      github: {}, context: { ...ctx, eventName, payload }, core, now,
      fresh: async args => { asked = args; return isFresh; },
      load: async () => { loads++; return { units: [] }; },
      publish: async () => 'updated',
    });
    return { r, loads, asked };
  };
  const skipped = await run('workflow_run', true);
  assert.deepEqual(skipped.r, { skipped: true });
  assert.equal(skipped.loads, 0);
  assert.equal(skipped.asked.kind, 'workers');
  assert.equal(skipped.asked.maxAgeMs, 15 * 60 * 1000);
  // Only a finished Copilot session keeps the short window; dispatcher/feeder/router completions and
  // issue/PR churn wait 15 min (GITHUB_TOKEN has 1,000 requests/hour shared by every workflow).
  const agent = await run('workflow_run', true, { workflow_run: { name: 'Running Copilot cloud agent' } });
  assert.equal(agent.asked.maxAgeMs, 5 * 60 * 1000);
  for (const name of ['Arcana Card Worker Dispatcher', 'Arcana Queue Feeder', 'Arcana CI Failure Router']) {
    assert.equal((await run('workflow_run', true, { workflow_run: { name } })).asked.maxAgeMs, 15 * 60 * 1000);
  }
  assert.equal((await run('issues', true)).asked.maxAgeMs, 15 * 60 * 1000);
  assert.equal((await run('pull_request_target', true)).asked.maxAgeMs, 15 * 60 * 1000);
  assert.equal(skipped.asked.now, Date.parse('2026-10-29T05:00:00Z'));
  assert.equal((await run('issues', false)).loads, 1);
  // Scheduled and manual refreshes never consult freshness.
  for (const e of ['schedule', 'workflow_dispatch']) {
    const r = await run(e, true);
    assert.equal(r.loads, 1);
    assert.equal(r.asked, undefined);
  }
});

test('freshStatus reads the published checkedAt and treats unreadable status as stale', async () => {
  const { freshStatus, formatStatus } = require('./control-status.cjs');
  const bot = { login: 'github-actions[bot]', id: 41898282, type: 'Bot' };
  const gh = checkedAt => ({ rest: { issues: { listComments() {} } }, paginate: async () => [
    { user: bot, body: formatStatus('workers', { kind: 'workers', schema: 1, checkedAt }, []) }] });
  const at = Date.parse('2026-10-29T05:00:00Z');
  const args = { owner: 'AARMOU', repo: 'arcana_table', kind: 'workers', maxAgeMs: 300000 };
  assert.equal(await freshStatus({ ...args, github: gh('2026-10-29T04:57:00Z'), now: at }), true);
  assert.equal(await freshStatus({ ...args, github: gh('2026-10-29T04:54:00Z'), now: at }), false);
  assert.equal(await freshStatus({ ...args, github: gh('2026-10-29T05:01:00Z'), now: at }), false);
  assert.equal(await freshStatus({ ...args, github: gh('not a date'), now: at }), false);
  assert.equal(await freshStatus({ ...args, github: { paginate: async () => { throw Error('403'); } }, now: at }), false);
});
