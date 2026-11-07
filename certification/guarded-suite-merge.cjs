'use strict';
// Restricted, opt-in lane for brand-new, provenance-verified independent suite PRs.
// No card implementation, runtime, UI, workflow, budget or release PR can pass
// certification/suite-automerge.cjs's exact two-file suite guard.
const { mergeSuitePr } = require('./suite-automerge.cjs');

const START_AT = '2026-11-07T16:00:00Z';
const BLOCKING_LABELS = new Set([
  'hold', 'blocked', 'do-not-merge', 'needs-engine', 'needs-owner',
  'changes-requested', 'priority:hold',
]);

function gate({ enabled, report, pr, run, reviews = [], currentBaseSha, initialBaseSha, requireReady = false }) {
  const blocked = [];
  if (!enabled) blocked.push('feature-switch-disabled');
  if (!run || run.event !== 'pull_request' || run.conclusion !== 'success' ||
      run.name !== 'Arcana Card Certification Premerge' || !/^[a-f0-9]{40}$/.test(String(run.head_sha || '')))
    blocked.push('untrusted-or-failed-trigger');
  // This is only an assertion of the trusted reporter's *positive* result.
  // Its gate checks suite-issue provenance, exact two newly added files, all
  // scenario jobs, current head, and the target card's identity.
  if (report?.reason !== 'owner-merge-required' || report?.merged !== false)
    blocked.push('not-a-certified-suite');
  if (!pr || pr.state !== 'open' || pr.base?.ref !== 'main' ||
      pr.head?.sha !== run?.head_sha || pr.head?.ref !== run?.head_branch ||
      pr.head?.repo?.full_name !== pr.base?.repo?.full_name ||
      pr.head?.repo?.full_name !== run?.head_repository?.full_name)
    blocked.push('pr-not-current-main-head');
  if (!['copilot', 'copilot-swe-agent[bot]'].includes(String(pr?.user?.login || '').toLowerCase()))
    blocked.push('untrusted-pr-author');
  if (!pr?.created_at || Date.parse(pr.created_at) < Date.parse(START_AT))
    blocked.push('historical-pr-not-opted-in');
  if (!run?.created_at || Date.parse(run.created_at) < Date.parse(START_AT))
    blocked.push('historical-run-not-opted-in');
  if (/\b(hold|blocked|do not merge|needs owner)\b/i.test(String(pr?.title || '')) ||
      (pr?.labels || []).some(l => BLOCKING_LABELS.has(String(l.name || '').toLowerCase())))
    blocked.push('held-pr');
  if ((reviews || []).some(x => x.state === 'CHANGES_REQUESTED' || x.state === 'DISMISSED'))
    blocked.push('review-blocker');
  if (!currentBaseSha || !/^[a-f0-9]{40}$/.test(String(currentBaseSha)) ||
      pr?.base?.sha !== currentBaseSha || (initialBaseSha && initialBaseSha !== currentBaseSha))
    blocked.push('base-moved-or-unknown');
  if (requireReady && pr?.draft) blocked.push('still-draft');
  // GitHub recomputes mergeability asynchronously. Unknown and unstable
  // results are *not* permission to proceed.
  if (requireReady && (pr?.mergeable !== true || pr?.mergeable_state !== 'clean'))
    blocked.push('not-proven-clean');
  return blocked;
}

async function execute({ github, writer, owner, repo, run, enabled = false, core }) {
  if (!enabled || !writer) return { merged: false, reason: 'feature-switch-disabled' };
  const report = await mergeSuitePr({ github, owner, repo, run, core });
  if (report.reason !== 'owner-merge-required') return { ...report, merged: false };
  const current = async () => (await github.rest.pulls.get({ owner, repo, pull_number: report.pr })).data;
  const baseSha = async () => (await github.rest.repos.getBranch({ owner, repo, branch: 'main' })).data.commit.sha;
  const reviews = async () => github.paginate(github.rest.pulls.listReviews, {
    owner, repo, pull_number: report.pr, per_page: 100,
  });
  const pr = await current();
  const sha = await baseSha();
  let blockers = gate({ enabled, report, pr, run, reviews: await reviews(), currentBaseSha: sha });
  if (blockers.length) return { merged: false, pr: report.pr, blockers };
  if (pr.draft) {
    // Mark ready only once every read-only check has passed; never act on
    // arbitrary draft PRs or use the broad dispatcher token as a fallback.
    if (!pr.node_id || !writer.graphql) return { merged: false, pr: report.pr, blockers: ['cannot-mark-ready'] };
    try {
      const r = await writer.graphql('mutation($id: ID!) { markPullRequestReadyForReview(input: {pullRequestId: $id}) { pullRequest { isDraft } } }', { id: pr.node_id });
      if (r?.markPullRequestReadyForReview?.pullRequest?.isDraft !== false)
        return { merged: false, pr: report.pr, blockers: ['ready-not-confirmed'] };
    } catch (error) {
      core?.warning('Guarded suite ready transition rejected: ' + String(error?.message || error).slice(0, 160));
      return { merged: false, pr: report.pr, blockers: ['ready-rejected'] };
    }
  }
  // TOCTOU: redo trusted validation and read both target refs after draft
  // transition. GitHub's SHA lease is applied to the actual merge call.
  const freshReport = await mergeSuitePr({ github, owner, repo, run, core });
  const fresh = await current();
  const freshBaseSha = await baseSha();
  blockers = gate({ enabled, report: freshReport, pr: fresh, run,
    reviews: await reviews(), currentBaseSha: freshBaseSha,
    initialBaseSha: sha, requireReady: true });
  if (blockers.length) return { merged: false, pr: report.pr, blockers };
  try {
    const result = await writer.rest.pulls.merge({
      owner, repo, pull_number: report.pr, sha: run.head_sha, merge_method: 'squash',
    });
    if (result?.data?.merged !== true) return { merged: false, pr: report.pr, blockers: ['merge-not-confirmed'] };
  } catch (error) {
    core?.warning('Guarded suite merge rejected: ' + String(error?.message || error).slice(0, 160));
    return { merged: false, pr: report.pr, blockers: ['merge-rejected'] };
  }
  return { merged: true, pr: report.pr, request: freshReport.request,
    suite: freshReport.key, source_sha: run.head_sha, reason: 'guarded-suite-merged' };
}

module.exports = { gate, execute, START_AT };
