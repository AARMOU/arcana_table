'use strict';
// Read-only worker observer. No dispatcher, budget API, or Copilot assignments.
const { loadUnits } = require('./units.cjs');
const { summarizeUnits, publishStatus, previousStatus } = require('./control-status.cjs');

// A duplicate event for the same underlying state must not trigger another ~100-call scan.
// The bounded schedule remains the recovery path for missed or newly visible GitHub state.
function eventInputKey(context = {}) {
  const p = context.payload || {};
  if (context.eventName === 'workflow_run') {
    const run = p.workflow_run || {};
    if (run.name !== 'Running Copilot cloud agent' || run.status !== 'completed') return null;
    return JSON.stringify({ type: 'copilot-completed', id: run.id || null,
      headSha: run.head_sha || null, conclusion: run.conclusion || null });
  }
  if (context.eventName === 'issues') {
    const issue = p.issue || {};
    if (!['opened', 'closed', 'assigned', 'unassigned', 'labeled', 'unlabeled'].includes(p.action)) return null;
    return JSON.stringify({ type: 'issue', number: issue.number || null, action: p.action,
      state: issue.state || null, updatedAt: issue.updated_at || null,
      labels: (issue.labels || []).map(label => label.name).sort() });
  }
  if (context.eventName === 'pull_request_target') {
    const pr = p.pull_request || {};
    if (!['opened', 'closed', 'reopened'].includes(p.action)) return null;
    return JSON.stringify({ type: 'pull-request', number: pr.number || null, action: p.action,
      state: pr.state || null, updatedAt: pr.updated_at || null, headSha: pr.head?.sha || null });
  }
  return null;
}

async function refreshWorkerStatus({ github, context, core, now = () => new Date(), load = loadUnits, publish = publishStatus,
  previous = previousStatus }) {
  const { owner, repo } = context.repo;
  const checkedAt = now();
  const inputKey = eventInputKey(context);
  // Read and retain the last key even on scheduled runs. Schedules always refresh as a bounded
  // fallback; event runs scan only when the relevant issue/PR/session state actually changed.
  const prior = await previous({ github, owner, repo, kind: 'workers' });
  if (!['schedule', 'workflow_dispatch'].includes(context.eventName)) {
    if (!inputKey) {
      core.info('No worker-status input for this event; skipped.');
      return { skipped: true, reason: 'irrelevant-event' };
    }
    if (prior?.lastInputKey === inputKey) {
      core.info('Worker-status input already observed; skipped duplicate event.');
      return { skipped: true, reason: 'duplicate-input' };
    }
  }
  // Same GitHub event/ledger observation as admission, but NO writes or spending.
  const data = await load({ github, owner, repo, now: checkedAt.getTime(), approvers: [owner] });
  const workers = summarizeUnits(data.units.map(u => u.derived));
  const payload = {
    checkedAt: checkedAt.toISOString(),
    runId: String(context.runId || ''),
    event: context.eventName || 'unknown',
    lastInputKey: inputKey || prior?.lastInputKey || null,
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
module.exports = { refreshWorkerStatus, eventInputKey };
