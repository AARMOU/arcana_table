'use strict';
// Read-only worker observer. No dispatcher, budget API, or Copilot assignments.
const { loadUnits } = require('./units.cjs');
const { summarizeUnits, publishStatus } = require('./control-status.cjs');

async function refreshWorkerStatus({ github, context, core, now = () => new Date(), load = loadUnits, publish = publishStatus }) {
  const { owner, repo } = context.repo;
  const checkedAt = now();
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
