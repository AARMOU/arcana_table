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
  // Display only: if the run never reached admission, read billing once so the dashboard stays fresh.
  let billing = report.billing;
  if (!billing) { try { billing = await readBudget(runEnv); } catch { billing = null; } }
  const payload = S.dispatcherPayload({ result, report, ready: report.ready, now: now(), runId: context.runId, event: context.eventName });
  payload.forcedDryRun = forcedDry;
  payload.billing = billing ? S.billingPayload(billing, { env: runEnv, now: now(), source: 'dispatcher' }) : null;
  if (payload.billing && !payload.billing.ok && result.state === 'denied' && String(result.reason).startsWith('budget'))
    payload.action = payload.billing.action || payload.action;
  const published = await S.publishStatus({ github, owner, repo, kind: 'dispatcher', payload, summary: S.dispatcherSummary(payload) });
  core.info('Status comment: ' + published);
  if (published.startsWith('failed')) core.warning('Arcana Control dispatcher status could not be published (' + published + ').');
  await core.summary.addHeading('Dispatcher status').addCodeBlock(JSON.stringify(payload, null, 1)).write();
  if (failure) throw failure;
  return { result, payload, published };
}

module.exports = { billingWatch, dispatcher };
