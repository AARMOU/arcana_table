// Owner-only permission for one existing, actionable [Arcana Queue] work unit.
// This endpoint never starts a worker, bypasses admission, merges, approves PRs, or changes billing.
// The owner GitHub OAuth token is held only inside the encrypted Worker session.
export const AUTHORIZE_LABEL = 'arcana:authorize-once';
const TRUSTED = new Set(['OWNER', 'MEMBER', 'COLLABORATOR']);
const AUTO_MARKER = '<!-- arcana-auto-queue-v1 -->';
const STAMP = /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/;
const allowedIssue = n => Number.isSafeInteger(n) && n > 0 && n < 10000000;
const labelsOf = issue => (issue.labels || []).map(l => typeof l === 'string' ? l : l.name);
const copilotAssigned = issue => (issue.assignees || []).some(x => ['copilot', 'copilot-swe-agent[bot]'].includes(String(x.login || '').toLowerCase()));

export async function authorizationInfo(api, issueNumber) {
  if (!allowedIssue(issueNumber)) return [400, { error: 'bad-issue' }];
  const r = await api('/issues/' + issueNumber);
  if (!r.ok) return [r.status === 404 ? 404 : 502, { error: 'issue-unavailable' }];
  const i = r.body;
  if (i.pull_request || i.state !== 'open' || !String(i.title || '').startsWith('[Arcana Queue]'))
    return [422, { error: 'not-open-card-unit' }];
  const body = String(i.body || '');
  const fromBot = i.user?.login === 'github-actions[bot]' && i.user?.type === 'Bot' && body.startsWith(AUTO_MARKER + '\n');
  if (!TRUSTED.has(i.author_association) && !fromBot)
    return [422, { error: 'untrusted-issue-origin' }];
  if (!/^Base: [A-Za-z0-9._/-]+$/m.test(body) || !/## Cards\b/.test(body) ||
      !/## Acceptance\b/.test(body) || !body.includes('Exact current Oracle behavior') ||
      !body.includes('Executable regression scenarios'))
    return [422, { error: 'unit-not-actionable' }];
  if (!STAMP.test(String(i.updated_at || ''))) return [502, { error: 'issue-timestamp-unavailable' }];
  return [200, { ok: true, issue: issueNumber, title: String(i.title).slice(0,200),
    updatedAt: i.updated_at, authorized: labelsOf(i).includes(AUTHORIZE_LABEL),
    claimed: copilotAssigned(i), state: 'queued-or-checking' }];
}

export async function authorizeUnit(api, { issue, expectedUpdatedAt } = {}) {
  if (!allowedIssue(issue) || !STAMP.test(String(expectedUpdatedAt || '')))
    return [400, { error: 'bad-request' }];
  const [status, info] = await authorizationInfo(api, issue);
  if (status !== 200) return [status, info];
  // Label is an idempotent, immutable authorization marker. Never add an audit comment
  // AFTER labeling: the dispatcher treats later human comments as changed instructions.
  if (info.authorized) return [200, { ok: true, duplicate: true, issue, state: 'authorized-waiting' }];
  if (info.claimed) return [409, { error: 'already-assigned', issue }];
  if (info.updatedAt !== expectedUpdatedAt)
    return [409, { error: 'stale-issue', issue }];

  // Only one explicit approval may be pending. This must stay aligned with the dispatcher
  // to ensure normal mode cannot accidentally select the lowest-numbered OTHER card.
  const existing = await api('/issues?state=open&labels=arcana%3Aauthorize-once&per_page=100');
  if (!existing.ok || !Array.isArray(existing.body))
    return [502, { error: 'pending-authorizations-unavailable' }];
  const other = existing.body.filter(x => !x.pull_request && x.number !== issue);
  if (other.length || existing.body.length === 100)
    return [409, { error: 'another-card-authorized', issue: other[0]?.number || null }];

  const result = await api('/issues/' + issue + '/labels', {
    method: 'POST', body: JSON.stringify({ labels: [AUTHORIZE_LABEL] })
  });
  if (!result.ok) return [502, { error: 'authorization-not-recorded', githubStatus: result.status }];
  return [200, { ok: true, issue, state: 'authorized-waiting', duplicate: false,
    message: 'Permission recorded. Existing dispatcher must still check card feasibility, CI, billing, reserve and capacity.' }];
}
