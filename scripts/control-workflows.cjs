'use strict';
// Workflow entry points, kept here (not inline YAML) so they are covered by node --test.
const { readBudget: defaultReadBudget, alertChange } = require('./billing-monitor.cjs');
const { runDispatch } = require('./dispatch-runner.cjs');
const S = require('./control-status.cjs');

// Arcana Copilot Billing Watch: authenticated read, level-change alert on #78, status comment.
async function billingWatch({ github, context, core, env, readBudget = defaultReadBudget, now = () => new Date() }) {
  const { owner, repo } = context.repo;
  const result = await readBudget(env);
  core.info('Arcana AI credit usage state: ' + result.status.level + (result.error ? ' (' + result.error + ')' : ''));
  const period = now().toISOString().slice(0, 7);
  let alert;
  try { alert = await alertChange({ github, level: result.status.level, period, reason: result.error, env }); }
  catch (e) { alert = 'failed:' + (e?.status || 'error'); core.warning('Billing alert could not be posted: ' + alert); }
  core.info('Alert state: ' + alert);
  const previous = await S.previousStatus({ github, owner, repo, kind: 'billing' });
  const payload = S.billingPayload(result, { env, previous, now: now(), source: 'billing-watch' });
  const published = await S.publishStatus({ github, owner, repo, kind: 'billing', payload, summary: S.billingSummary(payload) });
  core.info('Status comment: ' + published);
  if (published.startsWith('failed')) core.warning('Arcana Control billing status could not be published (' + published + ').');
  await core.summary.addHeading('Arcana budget status').addRaw(S.billingSummary(payload).join('\n')).write();
  if (result.status.level === 'unknown') core.warning('Billing unavailable (' + result.error + '): new assignments stay blocked. ' + (payload.action || ''));
  return { result, alert, published, payload };
}

// Arcana Card Worker Dispatcher. `dry_run` on a manual run forces dry-run mode: it can only
// remove the ability to spend, never add it.
async function dispatcher({ github, context, core, env, readBudget = defaultReadBudget, now = () => new Date(), fetchImpl }) {
  const { owner, repo } = context.repo;
  const runEnv = { ...env };
  const forcedDry = context.eventName === 'workflow_dispatch' && String(context.payload?.inputs?.dry_run) === 'true';
  if (forcedDry) runEnv.ARCANA_DISPATCH_MODE = 'dry-run';
  const report = {};
  let result, failure;
  try {
    result = await runDispatch({ github, context, core, env: runEnv, readBudget, report, ...(fetchImpl ? { fetchImpl } : {}) });
  } catch (e) {
    failure = e;
    result = { state: 'error', reason: 'dispatcher exception: ' + String(e?.message || 'unknown').slice(0, 200) };
  }
  // Owner "Request Fix" decisions from Arcana Control: same repair admission as CI repairs; a forced
  // dry run never requests one. Failures here never change the assignment result above.
  let ownerFixes = [];
  try {
    const { routeOwnerFixes } = require('./owner-fix.cjs');
    ownerFixes = await routeOwnerFixes({ github, owner, repo, env: runEnv, readBudget, runId: context.runId,
      ...(forcedDry ? { authorizer: async () => ({ allowed: false, reason: 'manual dry run' }) } : {}) });
    if (ownerFixes.length) core.info('Owner fix requests: ' + JSON.stringify(ownerFixes));
  } catch (e) { core.warning('Owner fix requests not processed: ' + String(e?.message || e).slice(0, 200)); }
  // Display only: if the run never reached admission, read billing once so the dashboard stays fresh.
  let billing = report.billing;
  if (!billing) { try { billing = await readBudget(runEnv); } catch { billing = null; } }
  // The unit list was read before this run's assignment: show the unit it just started as pending pickup, so
  // Control never reports "N queued, 0 working" in the minute a worker starts (owner 2026-10-31).
  if (result?.state === 'attempted' && result?.result === 'confirmed' && Array.isArray(report.units))
    report.units = report.units.map(u => u.number === result.issue ? { ...u, state: 'pending-pickup', holdsSlot: true, eligible: false, skip: null } : u);
  const payload = S.dispatcherPayload({ result, report, ready: report.ready, now: now(), runId: context.runId, event: context.eventName });
  payload.forcedDryRun = forcedDry;
  payload.ownerFixes = ownerFixes.slice(0, 10);
  payload.billing = billing ? S.billingPayload(billing, { env: runEnv, now: now(), source: 'dispatcher' }) : null;
  if (payload.billing && !payload.billing.ok && result.state === 'denied' && String(result.reason).startsWith('budget'))
    payload.action = payload.billing.action || payload.action;
  const published = await S.publishStatus({ github, owner, repo, kind: 'dispatcher', payload, summary: S.dispatcherSummary(payload) });
  core.info('Status comment: ' + published);
  if (published.startsWith('failed')) core.warning('Arcana Control dispatcher status could not be published (' + published + ').');
  await core.summary.addHeading('Dispatcher status').addCodeBlock(JSON.stringify(payload, null, 1)).write();
  // Fill free slots now instead of one assignment per scheduled run (owner 2026-10-31). Each run still assigns at
  // most one unit through full admission; a confirmed assignment just starts the next run, which stops by itself
  // when capacity, budget or eligible work runs out. Runs are serialized by the workflow's concurrency group.
  let chained = false;
  if (continueFilling({ result, forcedDry, failure })) {
    try {
      await github.rest.actions.createWorkflowDispatch({ owner, repo, workflow_id: 'arcana-card-dispatcher.yml', ref: 'main', inputs: { dry_run: 'false' } });
      chained = true;
    } catch (e) { core.warning('Could not start the next dispatcher run: ' + String(e?.message || e).slice(0, 200)); }
  }
  if (failure) throw failure;
  return { result, payload, published, chained };
}

const continueFilling = ({ result, forcedDry = false, failure = null }) =>
  !failure && !forcedDry && result?.state === 'attempted' && result?.result === 'confirmed';

module.exports = { billingWatch, dispatcher, continueFilling };
