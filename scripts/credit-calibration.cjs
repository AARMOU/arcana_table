'use strict';
// Auto-calibrated credits-per-session estimate (owner 2026-10-31: "Auto calibrate").
//
// Admission reserves an ESTIMATE for every active, recently finished and candidate Copilot session on top of
// authenticated usage. The fixed default (60) was ~4-9x the measured cost (owner billing: 1,514.73 credits over
// 98 sessions month-to-date = 15.5; 133.7 credits over 22 sessions in 8 h = 6), so phantom reservations held
// the system at one worker. The estimate is now measured: this month's authenticated Copilot usage (the same
// billing snapshot admission already uses) divided by this month's Copilot sessions (runs of GitHub's dynamic
// Copilot cloud agent workflow), with a safety margin, clamped to [FLOOR, CAP]. Billing can lag the sessions
// (counted up to now), which reads low; the 1.25 margin and the floor cover that.
//
// An explicitly set ARCANA_EST_CREDITS_PER_SESSION always wins. Too few sessions, no usage figure or any API
// error keeps the configured default (fail-safe: never lower than the default without evidence).
// Billing figures are private: callers log only the source, never the estimate or the usage.
const COPILOT_WORKFLOW_PATH = 'dynamic/copilot-swe-agent/copilot';
const FLOOR = 10;
const CAP = 60;
const MIN_SESSIONS = 10;
const MARGIN = 1.25;

function calibrate({ reportedUsed, sessions, floor = FLOOR, cap = CAP, minSessions = MIN_SESSIONS, margin = MARGIN }) {
  if (!Number.isFinite(reportedUsed) || reportedUsed < 0 || !Number.isSafeInteger(sessions) || sessions < minSessions) return null;
  return Math.min(cap, Math.max(floor, Math.ceil((reportedUsed / sessions) * margin)));
}

async function copilotSessionsThisMonth({ github, owner, repo, now = new Date() }) {
  const workflows = await github.paginate(github.rest.actions.listRepoWorkflows, { owner, repo, per_page: 100 });
  const wf = workflows.find(w => w.path === COPILOT_WORKFLOW_PATH);
  if (!wf) return null;
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString().replace('.000Z', 'Z');
  const end = now.toISOString().replace(/\.\d{3}Z$/, 'Z');
  const res = await github.rest.actions.listWorkflowRuns({ owner, repo, workflow_id: wf.id, created: `${start}..${end}`, per_page: 1 });
  const n = res?.data?.total_count;
  return Number.isSafeInteger(n) ? n : null;
}

async function calibrateReady({ github, owner, repo, env = {}, ready, snapshot, now = new Date(), count = copilotSessionsThisMonth }) {
  const set = v => v !== undefined && v !== null && String(v) !== '';
  if (!ready || ready.estSession === null) return { ready, source: 'invalid' };
  if (set(env.ARCANA_EST_CREDITS_PER_SESSION)) return { ready, source: 'variable' };
  // The billing snapshot is for the current UTC month; count sessions over the same month.
  if (!snapshot || !Number.isFinite(snapshot.reportedUsed) ||
      (snapshot.period && snapshot.period !== now.toISOString().slice(0, 7))) return { ready, source: 'default' };
  let sessions;
  try { sessions = await count({ github, owner, repo, now }); } catch { return { ready, source: 'default' }; }
  const est = calibrate({ reportedUsed: snapshot.reportedUsed, sessions });
  if (est === null) return { ready, source: 'default' };
  const estRepair = set(env.ARCANA_EST_CREDITS_PER_REPAIR) || ready.estRepair === null ? ready.estRepair : Math.min(ready.estRepair, est);
  return { ready: { ...ready, estSession: est, estRepair }, source: 'calibrated' };
}

module.exports = { calibrate, calibrateReady, copilotSessionsThisMonth, COPILOT_WORKFLOW_PATH, FLOOR, CAP, MIN_SESSIONS, MARGIN };
