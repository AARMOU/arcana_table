'use strict';
// Owner card-review inbox (#81, 2026-10-28). Lists Copilot card PRs and what each needs, published as
// the public #78 "review" status so the Arcana Table app can show a badge and list WITHOUT any token
// (one unauthenticated read). Approving happens on GitHub itself (the app opens the PR), so the
// owner's own login records the decision. This file only reads; it never approves, merges or builds.
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const OWNER = 'aarmou';
const MAX_ITEMS = 30;

// PR -> one inbox row. checkRuns: check runs on the PR head. reviews: the PR's reviews.
function classify(pr, checkRuns = [], reviews = []) {
  const head = pr.head?.sha || '';
  const runs = checkRuns.filter(r => r.head_sha === undefined || r.head_sha === head);
  const checks = Object.fromEntries(runs.map(r => [r.name, r.status === 'completed' ? (r.conclusion || 'unknown') : r.status]));
  const pending = runs.some(r => r.status !== 'completed');
  const failed = runs.some(r => r.status === 'completed' && !['success', 'skipped', 'neutral'].includes(r.conclusion));
  // Only the owner's decision on THIS head counts; a new commit needs a fresh review.
  const ownerReviews = reviews.filter(v => String(v.user?.login || '').toLowerCase() === OWNER && v.commit_id === head);
  const last = ownerReviews.filter(v => ['APPROVED', 'CHANGES_REQUESTED'].includes(v.state)).pop();
  const state = last?.state === 'APPROVED' ? 'approved'
    : last?.state === 'CHANGES_REQUESTED' ? 'changes-requested'
      : !runs.length || pending ? 'checks-running'
        : failed ? 'checks-failed'
          : 'awaiting-review';
  const fixes = String(pr.body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i);
  const card = String(pr.title || '').replace(/^\s*(?:\[[^\]]*\]\s*)?(?:Implement|Add)\s+/i, '').trim().slice(0, 120);
  return { pr: pr.number, issue: fixes ? Number(fixes[1]) : null, card, headSha: head, state, checks,
    url: `https://github.com/${pr.base?.repo?.full_name || 'AARMOU/arcana_table'}/pull/${pr.number}/files`,
    updatedAt: pr.updated_at || null };
}

function inboxPayload(items, now = new Date()) {
  const order = { 'awaiting-review': 0, 'checks-failed': 1, 'changes-requested': 2, 'checks-running': 3, approved: 4 };
  const sorted = [...items].sort((a, b) => order[a.state] - order[b.state] || a.pr - b.pr).slice(0, MAX_ITEMS);
  return { checkedAt: now.toISOString(), awaitingReview: sorted.filter(i => i.state === 'awaiting-review').length, items: sorted,
    note: 'Owner approval here is card sign-off only: no merge, build, signing or release follows from it. Checks passing is not rules certification.' };
}

function inboxSummary(p) {
  if (!p.items.length) return ['Card review inbox: no Copilot card PRs open.'];
  return [`Card review inbox: ${p.awaitingReview} awaiting owner review of ${p.items.length} Copilot card PR(s).`,
    ...p.items.map(i => `#${i.pr} ${i.card}: ${i.state}`)];
}

async function buildReviewInbox({ github, owner, repo, now }) {
  const prs = (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 }))
    .filter(p => COPILOT.has(String(p.user?.login || '').toLowerCase()));
  const items = [];
  for (const pr of prs.slice(0, MAX_ITEMS)) {
    // Card PRs only: the PR must fix an open [Arcana Queue] work unit (skips blocker notes and old work).
    const n = Number((String(pr.body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]);
    if (!n) continue;
    const unit = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
    if (unit.state !== 'open' || !String(unit.title || '').startsWith('[Arcana Queue]')) continue;
    const checkRuns = (await github.rest.checks.listForRef({ owner, repo, ref: pr.head.sha, per_page: 100 })).data.check_runs || [];
    const reviews = await github.paginate(github.rest.pulls.listReviews, { owner, repo, pull_number: pr.number, per_page: 100 });
    items.push(classify(pr, checkRuns, reviews));
  }
  return inboxPayload(items, now);
}

module.exports = { classify, inboxPayload, inboxSummary, buildReviewInbox };
