'use strict';
// Quiet upstream counter: one note on #78, edited in place (edits notify nobody), showing how far the
// upstream Argentum engine is ahead of the version adopted on the card branch and pinned in B140.
// Watching never builds, tests or merges anything; compatibility runs belong to deliberate adoption.
const MARKER = '<!-- arcana-engine-upstream-v1 -->';
const UPSTREAM = { owner: 'wingedsheep', repo: 'argentum-engine' };
const BRANCHES = { adopted: 'gpt/arcana-integration-next', b140: 'gpt/b140-card-request-test-apk-20261028' };
const SHA = /^[0-9a-f]{40}$/;

async function pinOf(github, owner, repo, ref) {
  const f = (await github.rest.repos.getContent({ owner, repo, path: 'engine-upgrade/preview-config.json', ref })).data;
  const sha = JSON.parse(Buffer.from(f.content, 'base64').toString()).upstream;
  if (!SHA.test(String(sha))) throw Error('no engine pin on ' + ref);
  return sha;
}

async function reportUpstream({ github, owner, repo, issue = 78, now = new Date() }) {
  // HEAD resolves to the upstream default branch, whatever it is named.
  const head = (await github.rest.repos.getCommit({ ...UPSTREAM, ref: 'HEAD' })).data;
  if (!SHA.test(String(head?.sha))) throw Error('upstream HEAD unavailable');
  const pins = { adopted: await pinOf(github, owner, repo, BRANCHES.adopted), b140: await pinOf(github, owner, repo, BRANCHES.b140) };
  const state = `upstream:${head.sha} adopted:${pins.adopted} b140:${pins.b140}`;
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: issue, per_page: 100 });
  const mine = [...comments].reverse().find(c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot' && String(c.body || '').startsWith(MARKER));
  if (mine && String(mine.body).includes(state)) return 'unchanged';
  const behind = async base => base === head.sha ? 0 :
    (await github.rest.repos.compareCommitsWithBasehead({ ...UPSTREAM, basehead: base + '...' + head.sha })).data.ahead_by;
  const [adoptedBehind, b140Behind] = [await behind(pins.adopted), await behind(pins.b140)];
  const short = s => '`' + s.slice(0, 10) + '`';
  const body = [MARKER, '<!-- ' + state + ' -->', '### Argentum engine versions (updated in place)', '',
    `| | Engine | Behind upstream |`, `|---|---|---|`,
    `| Upstream default branch | ${short(head.sha)} (${String(head.commit?.committer?.date || '').slice(0, 10)}) | — |`,
    `| Adopted on \`${BRANCHES.adopted}\` | ${short(pins.adopted)} | ${adoptedBehind} commits |`,
    `| B140 (Android) | ${short(pins.b140)} | ${b140Behind} commits |`, '',
    'Upstream movement alone triggers nothing. Adoption runs through the weekly upgrade (or a manual run) with its full checks; ' +
    'a numbered build follows the B141 checklist in `docs/stabilization/CONTROL-PLAN.md`. Checked ' + now.toISOString().slice(0, 16) + 'Z.'].join('\n');
  if (mine) await github.rest.issues.updateComment({ owner, repo, comment_id: mine.id, body });
  else await github.rest.issues.createComment({ owner, repo, issue_number: issue, body });
  return (mine ? 'updated' : 'created') + ` (adopted ${adoptedBehind} behind, B140 ${b140Behind} behind)`;
}

module.exports = { reportUpstream, MARKER, BRANCHES };
