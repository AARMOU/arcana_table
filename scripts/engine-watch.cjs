'use strict';
// Quiet upstream counter: one note on #78, edited in place (edits notify nobody), showing how far the
// upstream Argentum engine is ahead of the version adopted on the development line and of the one pinned in
// the current numbered build (main's integration/policy.json `target`, where approved cards go).
// Watching never builds, tests or merges anything; compatibility runs belong to deliberate adoption.
const MARKER = '<!-- arcana-engine-upstream-v1 -->';
const UPSTREAM = { owner: 'wingedsheep', repo: 'argentum-engine' };
const BRANCHES = { adopted: 'gpt/arcana-integration-next' };
const SHA = /^[0-9a-f]{40}$/;

async function pinOf(github, owner, repo, ref) {
  const f = (await github.rest.repos.getContent({ owner, repo, path: 'engine-upgrade/preview-config.json', ref })).data;
  const sha = JSON.parse(Buffer.from(f.content, 'base64').toString()).upstream;
  if (!SHA.test(String(sha))) throw Error('no engine pin on ' + ref);
  return sha;
}

async function currentBuild(github, owner, repo) {
  const f = (await github.rest.repos.getContent({ owner, repo, path: 'integration/policy.json', ref: 'main' })).data;
  const target = JSON.parse(Buffer.from(f.content, 'base64').toString()).target;
  if (!/^[A-Za-z0-9._/-]+$/.test(String(target))) throw Error('no current build in integration/policy.json');
  return target;
}

async function reportUpstream({ github, owner, repo, issue = 78, now = new Date() }) {
  // HEAD resolves to the upstream default branch, whatever it is named.
  const head = (await github.rest.repos.getCommit({ ...UPSTREAM, ref: 'HEAD' })).data;
  if (!SHA.test(String(head?.sha))) throw Error('upstream HEAD unavailable');
  const build = await currentBuild(github, owner, repo);
  const pins = { adopted: await pinOf(github, owner, repo, BRANCHES.adopted), current: await pinOf(github, owner, repo, build) };
  const state = `upstream:${head.sha} adopted:${pins.adopted} current:${build}@${pins.current}`;
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: issue, per_page: 100 });
  const mine = [...comments].reverse().find(c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot' && String(c.body || '').startsWith(MARKER));
  if (mine && String(mine.body).includes(state)) return 'unchanged';
  const behind = async base => base === head.sha ? 0 :
    (await github.rest.repos.compareCommitsWithBasehead({ ...UPSTREAM, basehead: base + '...' + head.sha })).data.ahead_by;
  const [adoptedBehind, currentBehind] = [await behind(pins.adopted), await behind(pins.current)];
  const label = build.split('/').pop();
  const short = s => '`' + s.slice(0, 10) + '`';
  const body = [MARKER, '<!-- ' + state + ' -->', '### Argentum engine versions (updated in place)', '',
    `| | Engine | Behind upstream |`, `|---|---|---|`,
    `| Upstream default branch | ${short(head.sha)} (${String(head.commit?.committer?.date || '').slice(0, 10)}) | — |`,
    `| Adopted on \`${BRANCHES.adopted}\` | ${short(pins.adopted)} | ${adoptedBehind} commits |`,
    `| Current build \`${label}\` (Android) | ${short(pins.current)} | ${currentBehind} commits |`, '',
    'Upstream movement alone triggers nothing. Adoption runs through the weekly upgrade (or a manual run) with its full checks; ' +
    'the daily early warning checks upstream against both every day. Checked ' + now.toISOString().slice(0, 16) + 'Z.'].join('\n');
  if (mine) await github.rest.issues.updateComment({ owner, repo, comment_id: mine.id, body });
  else await github.rest.issues.createComment({ owner, repo, issue_number: issue, body });
  return (mine ? 'updated' : 'created') + ` (adopted ${adoptedBehind} behind, ${label} ${currentBehind} behind)`;
}

module.exports = { reportUpstream, MARKER, BRANCHES };
