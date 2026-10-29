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
