'use strict';
// Live GitHub status is published to one bot-owned comment on the protected orders board (#883).
// Keep the owner's issue body/decisions untouched: GitHub issue-body updates are not atomic.
// The comment carries an independent checkedAt heartbeat plus links to worker inbox evidence.
// Inbox snippets are reports, not proof that a worker is currently executing.
const START = '<!-- board-facts:start -->';
const END = '<!-- board-facts:end -->';
const ENGINE_MARKER = '<!-- arcana-control-status-v1:engine -->';
const UPGRADE = 'argentum-weekly-upgrade.yml';
const TEST_APK = /^b\d+-test-apk\.yml$/;
const LIVE_MARKER = '<!-- arcana-owner-status-v1 -->';

const mt = iso => {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 'unknown';
  return d.toLocaleString('en-US', { timeZone: 'America/Denver', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) + ' MT';
};
const clean = s => String(s || '').replace(/[|\r\n<>@]/g, ' ').replace(/\s+/g, ' ').trim();

async function currentBuild(github, owner, repo) {
  const f = (await github.rest.repos.getContent({ owner, repo, path: 'integration/policy.json', ref: 'main' })).data;
  const target = JSON.parse(Buffer.from(f.content, 'base64').toString()).target;
  if (!/^[A-Za-z0-9._/-]+$/.test(String(target))) throw Error('no current build in integration/policy.json');
  return target;
}

async function engineLine(github, owner, repo) {
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: 78, per_page: 100 });
  const mine = [...comments].reverse().find(c => c.user?.type === 'Bot' && String(c.body || '').startsWith(ENGINE_MARKER));
  const json = mine && /```json\n([\s\S]*?)\n```/.exec(mine.body);
  if (!json) return 'Engine upgrade: no status published yet.';
  const s = JSON.parse(json[1]);
  return `Engine upgrade: **${clean(s.result)}** at ${mt(s.checkedAt)}. ${clean(s.message).slice(0, 240)}`;
}

async function upgradeRuns(github, owner, repo) {
  const runs = (await github.rest.actions.listWorkflowRuns({ owner, repo, workflow_id: UPGRADE, per_page: 30 })).data.workflow_runs || [];
  const live = runs.find(r => r.event !== 'push');
  const drills = {};
  for (const r of runs) if (/^gpt\/argentum-weekly-fix-drill-/.test(r.head_branch) && !drills[r.head_branch]) drills[r.head_branch] = r;
  const lines = [live ? `Last live upgrade run: ${mt(live.created_at)}, ${clean(live.conclusion || live.status)} ([run](${live.html_url})).`
    : 'Last live upgrade run: none in the recent list.'];
  for (const [branch, r] of Object.entries(drills)) {
    lines.push(`Drill \`${clean(branch)}\`: latest check ${mt(r.created_at)}, **${clean(r.conclusion || r.status)}** on \`${String(r.head_sha).slice(0, 7)}\` ([run](${r.html_url})).`);
  }
  return lines;
}

async function waitingPrs(github, owner, repo, base) {
  const prs = (await github.rest.pulls.list({ owner, repo, base, state: 'open', per_page: 50 })).data || [];
  const ready = prs.filter(p => !p.draft);
  if (!ready.length) return [`Pull requests waiting to merge into \`${base.split('/').pop()}\`: none.`];
  return [`Pull requests waiting to merge into \`${base.split('/').pop()}\` (${ready.length}): ` +
    ready.slice(0, 8).map(p => `[#${p.number}](${p.html_url}) ${clean(p.title).slice(0, 60)}`).join('; ') + (ready.length > 8 ? '; …' : '') + '.'];
}

async function latestApks(github, owner, repo) {
  const flows = ((await github.rest.actions.listRepoWorkflows({ owner, repo, per_page: 100 })).data.workflows || [])
    .filter(w => TEST_APK.test(String(w.path).split('/').pop()));
  const lines = [];
  for (const w of flows.sort((a, b) => String(b.path).localeCompare(String(a.path))).slice(0, 2)) {
    const r = ((await github.rest.actions.listWorkflowRuns({ owner, repo, workflow_id: w.id, status: 'success', per_page: 1 })).data.workflow_runs || [])[0];
    if (r) lines.push(`Latest ${clean(String(w.path).split('/').pop().replace('-test-apk.yml', '').toUpperCase())} test app: ${mt(r.created_at)} ([run](${r.html_url})).`);
  }
  return lines;
}

function place(body, block) {
  const s = String(body || '');
  const a = s.indexOf(START), b = s.indexOf(END);
  if (a >= 0 && b > a) return s.slice(0, a) + block + s.slice(b + END.length);
  const anchor = s.indexOf('\n## 1.');
  return anchor >= 0 ? s.slice(0, anchor) + '\n' + block + '\n' + s.slice(anchor) : s + '\n\n' + block + '\n';
}

async function reportBoard({ github, owner, repo, issue = 883, now = new Date() }) {
  const build = await currentBuild(github, owner, repo);
  const facts = [await engineLine(github, owner, repo), ...await upgradeRuns(github, owner, repo),
    ...await waitingPrs(github, owner, repo, build), ...await latestApks(github, owner, repo)];
  // This status snapshot is owned by the automation, not by the protected owner orders.
  // GitHub's issue-body update has no compare-and-swap guard: do not rewrite #883.
  const inbox = await Promise.all([949, 950].map(async issueNumber => {
    const meta = (await github.rest.issues.get({ owner, repo, issue_number: issueNumber })).data;
    const count = Math.max(0, Number(meta.comments) || 0);
    const page = Math.max(1, Math.ceil(count / 100));
    const comments = (await github.rest.issues.listComments({
      owner, repo, issue_number: issueNumber, per_page: 100, page,
    })).data || [];
    return {
      issue: issueNumber,
      entries: comments.slice(-3).reverse().map(c => ({
        at: c.created_at,
        url: c.html_url,
        summary: clean(String(c.body || '').split('\n').find(l => l.trim()) || '').slice(0, 180),
      })),
    };
  }));
  const payload = {
    checkedAt: now.toISOString(),
    facts,
    inbox,
    // Neither an inbox comment nor a green PR proves an agent is actively running.
    note: 'Latest reported activity, not worker liveness. Owner decisions remain on #883.',
  };
  const body = LIVE_MARKER + '\n' + '## Automated Arcana status\n' +
    '_Checked ' + mt(now.toISOString()) + '._\n\n' +
    facts.map(f => '- ' + f).join('\n') + '\n\n' +
    'Latest inbox reports: [Boneknuckles #949](https://github.com/' + owner + '/' + repo + '/issues/949) · ' +
    '[Goose / Elmer #950](https://github.com/' + owner + '/' + repo + '/issues/950)\n\n' +
    '```json\n' + JSON.stringify(payload) + '\n```';
  const existing = await github.paginate(github.rest.issues.listComments, {
    owner, repo, issue_number: issue, per_page: 100,
  });
  const mine = existing.find(c => c.user?.type === 'Bot' && String(c.body || '').startsWith(LIVE_MARKER));
  if (mine) {
    await github.rest.issues.updateComment({ owner, repo, comment_id: mine.id, body });
    return 'updated existing live-status comment';
  }
  await github.rest.issues.createComment({ owner, repo, issue_number: issue, body });
  return 'created live-status comment';
}

module.exports = { reportBoard, place, START, END, LIVE_MARKER };
