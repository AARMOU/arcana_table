'use strict';
// Owner card-review inbox (#81). Lists Copilot card PRs and what each needs, published as the public #78
// "review" status. Arcana Control renders it (badge, list, card details) with one unauthenticated read;
// the owner's Approve / Request Fix goes through the review Worker (control/review-worker), which records
// a real GitHub review by the owner pinned to the reviewed commit. This file only reads.
//
// States, in Arcana Control's words:
//   awaiting-review   Awaiting Review          checks green, no owner decision on this commit
//   certified         Independently Certified  checks green AND an independent certification check passed
//   checks-running    Checks Running
//   checks-failed     Checks Failed
//   approved          Owner Approved           owner approved THIS commit (a new commit resets it)
//   changes-requested Changes Requested        owner requested a fix on THIS commit
//   integrated        Integrated               PR merged into the integration base (last 7 days)
// Passing CI is never shown as certification: only a check whose name contains "certif" counts, which is
// the hook for the independent certification gate (#81) once it exists.
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const OWNER = 'aarmou';
const MAX_ITEMS = 30;
const INTEGRATED_DAYS = 7;
const GREEN = new Set(['success', 'skipped', 'neutral']);
const CERT = /certif/i;
const DECISION_MARKER = '<!-- arcana-owner-decision-v1 -->';

const unitOf = body => Number((String(body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]) || null;
const cardOf = title => String(title || '').replace(/^\s*(?:\[[^\]]*\]\s*)?(?:Implement|Add)\s+/i, '').trim().slice(0, 120);

// The card's own acceptance lines from its [Arcana Queue] unit (what Copilot was asked to implement).
function abilitiesOf(unitBody) {
  const out = [];
  let inAcc = false;
  for (const line of String(unitBody || '').replace(/<!--[\s\S]*?-->/g, '').split(/\r?\n/)) {
    const h = line.match(/^#{2,3}\s+(.+?)\s*$/);
    if (h) { inAcc = /^acceptance\b/i.test(h[1]); continue; }
    const b = inAcc && line.match(/^\s*[-*]\s+(.+?)\s*$/);
    if (b && !/^(Exact current Oracle|Executable regression|No placeholders|Start from)/i.test(b[1])) out.push(b[1].replace(/@/g, '＠').slice(0, 300));
  }
  return out.slice(0, 8);
}

// PR -> one inbox row. checkRuns: check runs on the PR head. reviews: the PR's reviews.
function classify(pr, checkRuns = [], reviews = [], unitBody = '') {
  const head = pr.head?.sha || '';
  const runs = checkRuns.filter(r => r.head_sha === undefined || r.head_sha === head);
  const checks = Object.fromEntries(runs.map(r => [r.name, r.status === 'completed' ? (r.conclusion || 'unknown') : r.status]));
  const checkRuns2 = runs.slice(0, 12).map(r => ({ name: String(r.name).slice(0, 80), result: r.status === 'completed' ? (r.conclusion || 'unknown') : r.status,
    url: typeof r.html_url === 'string' && r.html_url.startsWith('https://github.com/') ? r.html_url : null }));
  const pending = runs.some(r => r.status !== 'completed');
  const failedRuns = runs.filter(r => r.status === 'completed' && !GREEN.has(r.conclusion));
  const cert = runs.filter(r => CERT.test(r.name));
  const certification = !cert.length ? 'not-run' : cert.some(r => r.status !== 'completed') ? 'running'
    : cert.every(r => r.conclusion === 'success') ? 'certified' : 'failed';
  // Only the owner's decision on THIS head counts; a new commit needs a fresh review.
  const owner = reviews.filter(v => String(v.user?.login || '').toLowerCase() === OWNER && ['APPROVED', 'CHANGES_REQUESTED'].includes(v.state));
  const onHead = owner.filter(v => v.commit_id === head);
  const last = onHead[onHead.length - 1];
  const state = last?.state === 'APPROVED' ? 'approved'
    : last?.state === 'CHANGES_REQUESTED' ? 'changes-requested'
      : !runs.length || pending ? 'checks-running'
        : failedRuns.length ? 'checks-failed'
          : certification === 'certified' ? 'certified' : 'awaiting-review';
  const problems = [
    ...failedRuns.map(r => `Check failed: ${r.name} (${r.conclusion})`),
    ...(owner.length && !onHead.length ? ['New commit since your last review: review again'] : []),
    ...(last?.state === 'CHANGES_REQUESTED' ? ['You requested a fix on this commit'] : []),
  ].slice(0, 8);
  const decidedHere = last && String(last.body || '').startsWith(DECISION_MARKER) ? 'arcana-control' : last ? 'github' : null;
  return { pr: pr.number, issue: unitOf(pr.body), card: cardOf(pr.title), headSha: head, state, checks, checkRuns: checkRuns2,
    certification, abilities: abilitiesOf(unitBody), problems, decidedIn: decidedHere, draft: !!pr.draft,
    url: `https://github.com/${pr.base?.repo?.full_name || 'AARMOU/arcana_table'}/pull/${pr.number}/files`,
    updatedAt: pr.updated_at || null };
}

const ATTENTION = new Set(['awaiting-review', 'certified']);
function inboxPayload(items, now = new Date()) {
  const order = { certified: 0, 'awaiting-review': 1, 'checks-failed': 2, 'changes-requested': 3, 'checks-running': 4, approved: 5, integrated: 6 };
  const sorted = [...items].sort((a, b) => order[a.state] - order[b.state] || a.pr - b.pr).slice(0, MAX_ITEMS);
  return { checkedAt: now.toISOString(), awaitingReview: sorted.filter(i => ATTENTION.has(i.state)).length, items: sorted,
    note: 'Owner approval is card sign-off for integration review only: no merge, build, signing or release follows from it. Checks passing is not rules certification.' };
}

function inboxSummary(p) {
  if (!p.items.length) return ['Card review inbox: no Copilot card PRs open.'];
  return [`Card review inbox: ${p.awaitingReview} need owner attention of ${p.items.length} Copilot card PR(s).`,
    ...p.items.map(i => `#${i.pr} ${i.card}: ${i.state}`)];
}

async function buildReviewInbox({ github, owner, repo, now = new Date() }) {
  const prs = (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 }))
    .filter(p => COPILOT.has(String(p.user?.login || '').toLowerCase()));
  const items = [];
  for (const pr of prs.slice(0, MAX_ITEMS)) {
    // Card PRs only: the PR must fix an open [Arcana Queue] work unit (skips blocker notes and old work).
    const n = unitOf(pr.body);
    if (!n) continue;
    const unit = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
    if (unit.state !== 'open' || !String(unit.title || '').startsWith('[Arcana Queue]')) continue;
    const checkRuns = (await github.rest.checks.listForRef({ owner, repo, ref: pr.head.sha, per_page: 100 })).data.check_runs || [];
    const reviews = await github.paginate(github.rest.pulls.listReviews, { owner, repo, pull_number: pr.number, per_page: 100 });
    items.push(classify(pr, checkRuns, reviews, unit.body));
  }
  // Recently integrated cards (merged Copilot card PRs), so an approval visibly reaches "Integrated".
  const since = now.getTime() - INTEGRATED_DAYS * 864e5;
  const closed = (await github.rest.pulls.list({ owner, repo, state: 'closed', sort: 'updated', direction: 'desc', per_page: 30 })).data || [];
  for (const pr of closed) {
    if (!COPILOT.has(String(pr.user?.login || '').toLowerCase()) || !pr.merged_at || Date.parse(pr.merged_at) < since || !unitOf(pr.body)) continue;
    items.push({ pr: pr.number, issue: unitOf(pr.body), card: cardOf(pr.title), headSha: pr.merge_commit_sha || pr.head?.sha || '',
      state: 'integrated', checks: {}, checkRuns: [], certification: 'not-run', abilities: [], problems: [], decidedIn: null, draft: false,
      url: `https://github.com/${pr.base?.repo?.full_name || 'AARMOU/arcana_table'}/pull/${pr.number}`, updatedAt: pr.merged_at });
  }
  return inboxPayload(items, now);
}

module.exports = { classify, inboxPayload, inboxSummary, buildReviewInbox, abilitiesOf };
