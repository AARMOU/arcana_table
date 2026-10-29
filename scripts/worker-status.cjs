'use strict';
// Read-only worker observer. No dispatcher, budget API, or Copilot assignments.
const { loadUnits } = require('./units.cjs');
const { summarizeUnits, publishStatus, freshStatus } = require('./control-status.cjs');
// Event-driven refreshes within this window are skipped; schedule and manual runs always refresh.
const MIN_EVENT_INTERVAL_MS = 5 * 60 * 1000;

async function refreshWorkerStatus({ github, context, core, now = () => new Date(), load = loadUnits, publish = publishStatus,
  fresh = freshStatus, minIntervalMs = MIN_EVENT_INTERVAL_MS }) {
  const { owner, repo } = context.repo;
  const checkedAt = now();
  // Each refresh reads every open unit's timeline and comments (~100 API calls). Bursts of
  // workflow_run/issue events otherwise exhaust the repository's shared hourly token quota.
  if (!['schedule', 'workflow_dispatch'].includes(context.eventName) &&
      await fresh({ github, owner, repo, kind: 'workers', maxAgeMs: minIntervalMs, now: checkedAt.getTime() })) {
    core.info('Worker status refreshed within the last ' + minIntervalMs / 60000 + ' min; skipped to conserve API quota.');
    return { skipped: true };
  }
  // Same GitHub event/ledger observation as admission, but NO writes or spending.
  const data = await load({ github, owner, repo, now: checkedAt.getTime(), approvers: [owner] });
  const workers = summarizeUnits(data.units.map(u => u.derived));
  const payload = {
    checkedAt: checkedAt.toISOString(),
    runId: String(context.runId || ''),
    event: context.eventName || 'unknown',
    workers,
    source: 'read-only-worker-observer',
  };
  const published = await publish({
    github, owner, repo, kind: 'workers', payload,
    summary: [
      'GitHub-observed units: ' + workers.running + ' working, ' + workers.pendingPickup + ' pending, ' + workers.queued + ' queued.',
      'Checked: ' + payload.checkedAt + '. Not live Copilot telemetry.',
    ],
  });
  core.info('Worker status observer: ' + published + '; checked ' + payload.checkedAt);
  if (published.startsWith('failed')) throw Error('Worker status publication failed: ' + published);
  return { payload, published };
}
module.exports = { refreshWorkerStatus };
