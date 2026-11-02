'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { refreshWorkerStatus, eventInputKey, eventIntervalMs } = require('./worker-status.cjs');
const ctx = { repo: { owner: 'AARMOU', repo: 'arcana_table' }, runId: 321, eventName: 'workflow_run',
  payload: { workflow_run: { id: 44, name: 'Running Copilot cloud agent', status: 'completed',
    head_sha: 'a'.repeat(40), conclusion: 'success' } } };
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

test('an unchanged event input does no scan; one changed input causes one scan and publish', async () => {
  const priorKey = eventInputKey(ctx);
  let loads = 0, publishes = 0;
  const run = async context => refreshWorkerStatus({
    github: {}, context: { ...ctx, ...context }, core, now,
    previous: async () => ({ lastInputKey: priorKey }),
    load: async () => { loads++; return { units: [] }; },
    publish: async () => { publishes++; return 'updated'; },
  });

  const duplicate = await run({});
  assert.deepEqual(duplicate, { skipped: true, reason: 'duplicate-input' });
  assert.equal(loads, 0);
  assert.equal(publishes, 0);

  const changedContext = { ...ctx, payload: { workflow_run: { ...ctx.payload.workflow_run, id: 45 } } };
  const changed = await run({ payload: changedContext.payload });
  assert.equal(loads, 1);
  assert.equal(publishes, 1);
  assert.equal(changed.payload.lastInputKey, eventInputKey(changedContext));
});

test('observer event keys track relevant state and ignore unrelated completions', () => {
  assert.equal(eventInputKey({ eventName: 'workflow_run', payload: { workflow_run: {
    name: 'Arcana Queue Feeder', id: 55, status: 'completed',
  } } }), null);
  assert.equal(eventInputKey({ eventName: 'issues', payload: { action: 'edited', issue: { number: 3 } } }), null);
  const issue = action => ({ eventName: 'issues', payload: { action, issue: {
    number: 3, state: 'open', updated_at: '2026-10-29T05:00:00Z', labels: [{ name: 'arcana:queue' }],
  } } });
  assert.equal(eventInputKey(issue('labeled')), eventInputKey(issue('labeled')));
  assert.notEqual(eventInputKey(issue('labeled')), eventInputKey(issue('unlabeled')));
  assert.notEqual(eventInputKey(issue('labeled')), eventInputKey({ ...issue('labeled'), payload: {
    ...issue('labeled').payload, issue: { ...issue('labeled').payload.issue, updated_at: '2026-10-29T05:01:00Z' },
  } }));
});

test('distinct issue events inside 15 minutes share one scan; Copilot completions keep the 5-minute window', async () => {
  let lastScanAt = null;
  let loads = 0;
  let priorKey = null;
  let checkedAt = null;
  const runIssueEvent = async (number, minutesAfterStart) => {
    const at = new Date(Date.parse('2026-10-29T05:00:00Z') + minutesAfterStart * 60_000);
    const context = { ...ctx, eventName: 'issues', payload: { action: 'labeled', issue: {
      number, state: 'open', updated_at: at.toISOString(), labels: [{ name: 'arcana:queue' }],
    } } };
    return refreshWorkerStatus({
      github: {}, context, core, now: () => at,
      previous: async () => ({ lastInputKey: priorKey, checkedAt }),
      load: async () => { loads++; lastScanAt = at.getTime(); return { units: [] }; },
      publish: async ({ payload }) => { priorKey = payload.lastInputKey; checkedAt = payload.checkedAt; return 'updated'; },
    });
  };

  assert.equal((await runIssueEvent(10, 0)).published, 'updated');
  assert.equal((await runIssueEvent(11, 3)).reason, 'fresh-status');
  assert.equal((await runIssueEvent(12, 14)).reason, 'fresh-status');
  assert.equal(loads, 1);
  assert.equal((await runIssueEvent(13, 16)).published, 'updated');
  assert.equal(loads, 2);
  assert.notEqual(lastScanAt, null);

  assert.equal(eventIntervalMs(ctx), 5 * 60_000);
  assert.equal(eventIntervalMs({ eventName: 'issues' }), 15 * 60_000);
});

test('scheduled and manual runs always refresh and preserve the last event key', async () => {
  const previousKey = eventInputKey(ctx);
  for (const eventName of ['schedule', 'workflow_dispatch']) {
    let loads = 0;
    const result = await refreshWorkerStatus({
      github: {}, context: { ...ctx, eventName }, core, now,
      previous: async () => ({ lastInputKey: previousKey }),
      load: async () => { loads++; return { units: [] }; },
      publish: async () => 'updated',
    });
    assert.equal(loads, 1);
    assert.equal(result.payload.lastInputKey, previousKey);
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
