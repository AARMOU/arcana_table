'use strict';
// Live facts block on the orders board (#883), refreshed by the review-inbox refresh (every ~15-30 min), so
// the board no longer depends on someone remembering to edit it (owner, 2026-11-03: "the board has been
// lagging behind"). Only the text between the two markers is ever touched; agents keep editing the rest.
// Facts only, read from GitHub itself: nothing here is anyone's claim. Edits notify nobody.
const START = '<!-- board-facts:start -->';
const END = '<!-- board-facts:end -->';
const ENGINE_MARKER = '<!-- arcana-control-status-v1:engine -->';
const UPGRADE = 'argentum-weekly-upgrade.yml';
const TEST_APK = /^b\d+-test-apk\.yml$/;

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
  const core = facts.map(f => '- ' + f).join('\n');
  const block = [START, '## 0. Live facts (refreshed automatically from GitHub every ~30 min; do not edit by hand)',
    `_Checked ${mt(now.toISOString())}._`, core, END].join('\n');
  const current = (await github.rest.issues.get({ owner, repo, issue_number: issue })).data.body || '';
  const a = current.indexOf(START), b = current.indexOf(END);
  if (a >= 0 && b > a && current.slice(a, b).includes(core)) return 'unchanged';
  await github.rest.issues.update({ owner, repo, issue_number: issue, body: place(current, block) });
  return `updated (${facts.length} facts)`;
}

module.exports = { reportBoard, place, START, END };
