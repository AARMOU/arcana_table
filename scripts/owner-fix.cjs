'use strict';
// Owner "Request Fix" decisions from Arcana Control -> the EXISTING guarded repair path (#81).
//
// The review Worker records the owner's CHANGES_REQUESTED review (pinned to a commit, never mentioning
// @copilot) and labels the card's [Arcana Queue] unit with FIX_LABEL. That label event wakes the trusted
// dispatcher, which calls routeOwnerFixes(): the same repair admission the CI repair router uses
// (createAuthorizer: master switch, ARCANA_REPAIR_MODE=request, work unit, trusted PR conversation,
// capacity, repair ceiling, fresh billing with the 10% reserve and pending liabilities). Only an admitted
// request posts the "@copilot" repair comment; otherwise the fix request is recorded as an observation
// and NO worker starts. The label is always consumed so a decision is processed exactly once.
const A = require('./admission.cjs');
const { createAuthorizer } = require('./failure-routing.cjs');

const FIX_LABEL = 'arcana:owner-fix-requested';
const DECISION_MARKER = '<!-- arcana-owner-decision-v1 -->';
const ROUTED_MARKER = '<!-- arcana-owner-fix-routed-v1 -->';
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const MAX_PER_RUN = 3;
const isOurs = c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot';
const neutralize = s => String(s || '').replace(/@/g, '＠').replace(/<!--|-->/g, '').slice(0, 1200);

// The owner's latest decision review on the PR's CURRENT head, if it is a fix request made from Arcana Control.
function ownerFixReview(reviews, head, owner) {
  const mine = (reviews || []).filter(r => String(r.user?.login || '').toLowerCase() === owner &&
    r.commit_id === head && ['APPROVED', 'CHANGES_REQUESTED'].includes(r.state));
  const last = mine[mine.length - 1];
  return last && last.state === 'CHANGES_REQUESTED' && String(last.body || '').startsWith(DECISION_MARKER) ? last : null;
}

async function routeOwnerFixes({ github, owner, repo, env, readBudget, runId, authorizer }) {
  const ownerLc = String(owner).toLowerCase();
  // Filter locally as well: never act on an issue that does not actually carry the label.
  const labeled = (await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', labels: FIX_LABEL, per_page: 100 }))
    .filter(i => (i.labels || []).some(l => (l?.name || l) === FIX_LABEL));
  if (!labeled.length) return [];
  const authorize = authorizer || createAuthorizer({ github, owner, repo, env, readBudget });
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  const results = [];
  for (const unit of labeled.filter(i => !i.pull_request).slice(0, MAX_PER_RUN)) {
    const n = unit.number;
    const done = async (state, extra = {}) => {
      try { await github.rest.issues.removeLabel({ owner, repo, issue_number: n, name: FIX_LABEL }); } catch { /* already gone */ }
      results.push({ unit: n, state, ...extra });
    };
    // Only the owner may request a fix this way; anyone else's label is ignored and removed.
    const events = await github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number: n, per_page: 100 });
    const applied = events.filter(e => e.event === 'labeled' && e.label?.name === FIX_LABEL).pop();
    if (String(applied?.actor?.login || '').toLowerCase() !== ownerLc) { await done('ignored-not-owner'); continue; }
    if (!String(unit.title || '').startsWith('[Arcana Queue]')) { await done('ignored-not-a-card-unit'); continue; }
    const pr = prs.find(p => COPILOT.has(String(p.user?.login || '').toLowerCase()) &&
      Number((String(p.body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]) === n);
    if (!pr) { await done('no-open-copilot-pr'); continue; }
    const reviews = await github.paginate(github.rest.pulls.listReviews, { owner, repo, pull_number: pr.number, per_page: 100 });
    const review = ownerFixReview(reviews, pr.head.sha, ownerLc);
    // A newer commit makes the request stale: the owner reviews the new head instead.
    if (!review) { await done('no-current-fix-request', { pr: pr.number }); continue; }
    const fingerprint = `owner-review-id:${review.id}\nhead-sha:${pr.head.sha}`;
    const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: pr.number, per_page: 100 });
    if (comments.some(c => isOurs(c) && String(c.body || '').startsWith(ROUTED_MARKER) && c.body.includes(fingerprint))) {
      await done('already-routed', { pr: pr.number }); continue;
    }
    const note = neutralize(String(review.body || '').split('\n').filter(l => !l.startsWith(DECISION_MARKER) && !l.startsWith('sha:')).join('\n').trim());
    const decision = await authorize({ pr, run: { id: runId, head_sha: pr.head.sha } });
    const allowed = decision.allowed === true;
    const body = [ROUTED_MARKER, fingerprint,
      allowed ? '@copilot The owner reviewed this exact commit in Arcana Control and requested changes. Fix exactly what is described below, keep every existing test assertion and exact Oracle behavior, and add a regression test for the problem.'
        : `Owner fix request recorded. Automatic repair withheld (${String(decision.reason || 'not admitted').replace(/[@\r\n]/g, ' ')}); no worker started.`,
      '', note || '(no description)', '',
      `Commit: \`${pr.head.sha}\``,
      allowed ? `Repair request ${decision.attempt} of ${A.CEILING.repair} for unit #${decision.unit}. Never merge, release, or change main, production or signing.`
        : 'This does not consume a repair attempt. The owner can re-request once capacity or budget allows.'].join('\n');
    // Re-check the head right before posting.
    const fresh = (await github.rest.pulls.get({ owner, repo, pull_number: pr.number })).data;
    if (fresh.state !== 'open' || fresh.head.sha !== pr.head.sha) { await done('stale-before-post', { pr: pr.number }); continue; }
    if (allowed) await decision.record();
    await github.rest.issues.createComment({ owner, repo, issue_number: pr.number, body });
    await done(allowed ? 'repair-requested' : 'observed-only', { pr: pr.number, reason: decision.reason || null });
  }
  return results;
}

module.exports = { routeOwnerFixes, ownerFixReview, FIX_LABEL, DECISION_MARKER, ROUTED_MARKER };
