// Owner-authenticated, coordination-only check-ins. Never starts agents or paid work.
export const ISSUE = 81;
export const MARKER = '<!-- arcana-all-hands-checkin-v1:';
export const ROLES = ['goosegeta', 'claude', 'boneknuckles'];
const TRUSTED = new Set(['OWNER', 'MEMBER', 'COLLABORATOR']);
const THROTTLE_MS = 30 * 60 * 1000;
const DISPLAY_MS = 24 * 60 * 60 * 1000;
const idFor = nowMs => new Date(nowMs).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
const idOf = comment => String(comment?.body || '').match(/^<!-- arcana-all-hands-checkin-v1:([0-9TZ]+) -->/)?.[1] || null;
const isOwner = comment => String(comment?.user?.login || '').toLowerCase() === 'aarmou' &&
  TRUSTED.has(String(comment?.author_association || ''));
const age = (comment, nowMs) => nowMs - Date.parse(comment.created_at || '');
const validAge = (ms, span) => Number.isFinite(ms) && ms >= 0 && ms <= span;
const githubUrl = id => 'https://github.com/AARMOU/arcana_table/issues/81#issuecomment-' + id;

async function recent(api) {
  const issue = await api('/issues/' + ISSUE);
  if (!issue.ok || issue.body?.number !== ISSUE || !Number.isSafeInteger(issue.body?.comments) || issue.body.comments < 0)
    return [503, { error: 'coordination-unavailable' }];
  const last = Math.max(1, Math.ceil(issue.body.comments / 100));
  const pages = last > 1 ? [last - 1, last] : [last];
  const rows = [];
  for (const page of pages) {
    const r = await api('/issues/' + ISSUE + '/comments?per_page=100&page=' + page);
    if (!r.ok || !Array.isArray(r.body)) return [503, { error: 'coordination-comments-unavailable' }];
    rows.push(...r.body);
  }
  return [200, rows];
}

function summarize(rows, nowMs) {
  const requests = rows.filter(c => idOf(c) && isOwner(c) && validAge(age(c, nowMs), DISPLAY_MS))
    .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || b.id - a.id);
  const last = requests[0];
  if (!last) return { ok: true, request: null, acknowledgments: Object.fromEntries(ROLES.map(role => [role, null])) };
  const token = idOf(last), acknowledgments = Object.fromEntries(ROLES.map(role => [role, null]));
  for (const c of rows) {
    if (c.id === last.id || !TRUSTED.has(String(c.author_association || '')) ||
        !Number.isSafeInteger(c.id) || c.id <= last.id || !Number.isFinite(Date.parse(c.created_at)) ||
        Date.parse(c.created_at) < Date.parse(last.created_at)) continue;
    for (const role of ROLES) {
      if (new RegExp('^CHECK-IN ' + token + ' ' + role + '(?:\\s|$)', 'i').test(String(c.body || '').trim()) &&
          (!acknowledgments[role] || c.id > acknowledgments[role].id))
        acknowledgments[role] = { id: c.id, url: githubUrl(c.id), at: c.created_at };
    }
  }
  return { ok: true, request: { id: last.id, token, url: githubUrl(last.id), at: last.created_at },
    acknowledgments };
}

export async function checkInStatus(api, nowMs = Date.now()) {
  const [status, result] = await recent(api);
  return status === 200 ? [200, summarize(result, nowMs)] : [status, result];
}

export async function requestAllHandsCheckIn(api, nowMs = Date.now()) {
  const [status, result] = await checkInStatus(api, nowMs);
  if (status !== 200) return [status, result]; // fail closed if existing requests cannot be checked
  if (result.request && validAge(nowMs - Date.parse(result.request.at), THROTTLE_MS))
    return [200, { ...result, duplicate: true }];
  const token = idFor(nowMs);
  const body = [
    MARKER + token + ' -->',
    '## 🪿 ALL HANDS — OWNER CHECK-IN (' + token + ')',
    'Requested by the owner from Arcana Control. This is **status-only coordination**, NOT a new engineering assignment.',
    '',
    '**Goosegeta / Project Goose, Claude, and Boneknuckles / GPT Work:** Each of you, on your next existing scheduled run or active session that reads #81, please post a separate reply here. Use the exact first line:',
    '- `CHECK-IN ' + token + ' goosegeta`',
    '- `CHECK-IN ' + token + ' claude`',
    '- `CHECK-IN ' + token + ' boneknuckles` (GPT Work)',
    '',
    'Then report: current claimed task, issue/PR and exact branch/head where applicable, last verified progress, blocker/owner, next action, and whether you are actually active or simply have a queued task. Explicitly say if nothing is running.',
    '',
    'Do not claim a check-in from another agent or infer live presence. Replies are self-reports, not telemetry.',
    '**No new paid sessions, worker dispatch, merges, deployments, build/sign/release, billing or policy changes are authorized by this request.**'
  ].join('\n');
  const write = await api('/issues/' + ISSUE + '/comments', { method: 'POST', body: JSON.stringify({ body }) });
  if (!write.ok || !Number.isSafeInteger(write.body?.id) || !String(write.body?.html_url || '').startsWith('https://github.com/AARMOU/arcana_table/issues/81#issuecomment-'))
    return [502, { error: 'post-unconfirmed', detail: 'Check issue #81 before retrying; a network failure may have occurred after the write.' }];
  return [200, { ok: true, duplicate: false,
    request: { id: write.body.id, token, at: write.body.created_at || new Date(nowMs).toISOString(),
      url: githubUrl(write.body.id) },
    acknowledgments: Object.fromEntries(ROLES.map(role => [role, null])) }];
}
