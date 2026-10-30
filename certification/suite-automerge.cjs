'use strict';
// Merges a Copilot suite PR that answers an [Arcana Suite] request, only after a green
// Arcana Card Certification Premerge run on its exact head actually executed the suite against
// the card PR. See .github/workflows/arcana-suite-automerge.yml. Never touches card PRs.
const { SUITE_TITLE, SUITE_MARKER, SUITE_BASE } = require('../scripts/admission.cjs');

const PREMERGE = 'Arcana Card Certification Premerge';
const SCENARIOS = 'Execute trusted independent card scenarios';
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const field = (body, name) => (String(body || '').match(new RegExp('^' + name + ': (\\S+)\\s*$', 'm')) || [])[1];

// Pure: every reason this PR must not merge (empty = merge).
function mergeBlockers({ run, pr, request, files, jobs, suite }) {
  const r = [];
  const repoName = pr?.base?.repo?.full_name;
  if (run?.name !== PREMERGE || run.event !== 'pull_request' || run.conclusion !== 'success') r.push('premerge-not-green');
  if (run?.head_repository?.full_name !== repoName || pr?.head?.repo?.full_name !== repoName) r.push('not-same-repository');
  if (!pr || pr.state !== 'open' || pr.base?.ref !== SUITE_BASE) r.push('pr-not-open-on-main');
  if (!COPILOT.has(String(pr?.user?.login || '').toLowerCase())) r.push('pr-not-by-copilot');
  if (pr?.head?.sha !== run?.head_sha) r.push('head-moved');
  if (!request || request.state !== 'open' || request.user?.login !== 'github-actions[bot]' ||
      !String(request.title || '').startsWith(SUITE_TITLE) || !String(request.body || '').startsWith(SUITE_MARKER + '\n')) r.push('no-open-suite-request');
  const key = field(request?.body, 'Suite key'), unit = Number(field(request?.body, 'Card unit')?.slice(1));
  const json = `certification/suites/${key}.json`;
  const names = (files || []).map(f => f.filename);
  const test = names.find(f => /^certification\/[A-Z][A-Za-z0-9]*IndependentCertificationTest\.kt$/.test(f));
  if (names.length !== 2 || !names.includes(json) || !test || (files || []).some(f => f.status !== 'added')) r.push('files-not-exactly-the-new-suite');
  if (!suite || suite.key !== key || suite.issue !== unit || suite.source !== test) r.push('suite-registration-mismatch');
  const ran = (jobs || []).filter(j => String(j.name || '').startsWith(SCENARIOS));
  if (!ran.length || ran.some(j => j.conclusion !== 'success') || (jobs || []).some(j => !['success', 'skipped'].includes(j.conclusion))) r.push('suite-not-executed');
  return r;
}

async function mergeSuitePr({ github, owner, repo, run, core }) {
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', base: SUITE_BASE, head: `${owner}:${run.head_branch}`, per_page: 100 });
  const pr = prs.find(p => p.head?.sha === run.head_sha) || prs[0];
  if (!pr) return { merged: false, reason: 'no-open-pr' };
  const n = Number(field(pr.body, 'Unit')?.slice(1)) || Number((String(pr.body || '').match(/(?:Fixes|Closes|Resolves) #(\d+)\b/i) || [])[1]);
  const request = n ? (await github.rest.issues.get({ owner, repo, issue_number: n })).data : null;
  // Only suite PRs are ours; anything else on main is left alone, silently.
  if (!String(request?.title || '').startsWith(SUITE_TITLE)) return { merged: false, pr: pr.number, reason: 'not-a-suite-pr' };
  const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 });
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, { owner, repo, run_id: run.id, per_page: 100 });
  let suite = null;
  const key = field(request.body, 'Suite key');
  try {
    const f = (await github.rest.repos.getContent({ owner, repo, path: `certification/suites/${key}.json`, ref: run.head_sha })).data;
    suite = JSON.parse(Buffer.from(f.content, 'base64').toString('utf8'));
  } catch {}
  const blockers = mergeBlockers({ run, pr, request, files, jobs, suite });
  if (blockers.length) { core?.warning(`Suite PR #${pr.number} not merged: ${blockers.join(', ')}`); return { merged: false, pr: pr.number, blockers }; }
  if (pr.draft) {
    const res = await github.graphql('mutation($id: ID!) { markPullRequestReadyForReview(input: {pullRequestId: $id}) { pullRequest { isDraft } } }', { id: pr.node_id });
    if (res?.markPullRequestReadyForReview?.pullRequest?.isDraft !== false) throw Error('GitHub did not confirm ready-for-review');
  }
  await github.rest.pulls.merge({ owner, repo, pull_number: pr.number, sha: run.head_sha, merge_method: 'squash' });
  return { merged: true, pr: pr.number, request: n, key };
}

module.exports = { mergeBlockers, mergeSuitePr, PREMERGE };
