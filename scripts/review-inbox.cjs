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
// Passing CI never certifies rules. Require the exact trusted Github Actions\n// check identity and current PR SHA before displaying a card as certified.
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const OWNER = 'aarmou';
const MAX_ITEMS = 30;
const INTEGRATED_DAYS = 7;
const GREEN = new Set(['success', 'skipped', 'neutral']);
const V = require('./verified-certification.cjs');
const I = require('./card-integration.cjs');
const DECISION_MARKER = '<!-- arcana-owner-decision-v1 -->';

const unitOf = body => Number((String(body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]) || null;
const cardOf = title => String(title || '').replace(/^\s*(?:\[[^\]]*\]\s*)?(?:Implement|Add)\s+/i, '').trim().slice(0, 120);

// Classify by changed-file evidence, NOT PR title or linked queue unit alone.
// Missing/incomplete GitHub file evidence fails closed: it never becomes an approval.
function classifyChangeFiles(files, title = '') {
  if (!Array.isArray(files) || files.length >= 100) return { kind: 'unknown', hasTest: false, reason: 'Changed-file evidence unavailable or incomplete' };
  const source = files.some(f => /^android\/custom-cards\/src\/main\/.+\/cards\/[^/]+\.kt$/.test(f));
  const hasTest = files.some(f => /^android\/custom-cards\/src\/test\/.+Test\.kt$/.test(f));
  if (source) return { kind: 'card', hasTest, reason: hasTest ? null : 'Executable card scenario test missing' };
  if (/\b(audit|coverage|inventory|documentation|docs)\b/i.test(title) || (files.length && files.every(f => /^(docs\/|README|\.github\/)/.test(f))))
    return { kind: 'audit', hasTest: false, reason: 'Audit/documentation, no card implementation' };
  return { kind: 'engine', hasTest: false, reason: files.length ? 'Engine or blocker changes; no card implementation' : 'No card implementation changes in this PR' };
}
const isReadyForOwner = i => i?.kind === 'card' && i.hasTest === true && !i.draft &&
  i.state === 'certified' && i.certification === 'certified';


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
function classify(pr, checkRuns = [], reviews = [], unitBody = '', files = null) {
  const change = classifyChangeFiles(files, pr.title);
  const head = pr.head?.sha || '';
  const runs = checkRuns.filter(r => r.head_sha === undefined || r.head_sha === head);
  const checks = Object.fromEntries(runs.map(r => [r.name, r.status === 'completed' ? (r.conclusion || 'unknown') : r.status]));
  const checkRuns2 = runs.slice(0, 12).map(r => ({ name: String(r.name).slice(0, 80), result: r.status === 'completed' ? (r.conclusion || 'unknown') : r.status,
    url: typeof r.html_url === 'string' && r.html_url.startsWith('https://github.com/') ? r.html_url : null }));
  const pending = runs.some(r => r.status !== 'completed');
  const failedRuns = runs.filter(r => r.status === 'completed' && !GREEN.has(r.conclusion));
  const certification = V.certification(runs,head);
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
  if (change.reason) problems.push(change.reason);
  if (pr.draft && change.kind === 'card') problems.push('Draft PR: not ready for owner approval');
  const decidedHere = last && String(last.body || '').startsWith(DECISION_MARKER) ? 'arcana-control' : last ? 'github' : null;
  return { pr: pr.number, issue: unitOf(pr.body), card: cardOf(pr.title), headSha: head, state, checks, checkRuns: checkRuns2,
    certification, abilities: abilitiesOf(unitBody), problems: problems.slice(0, 8), decidedIn: decidedHere, draft: !!pr.draft,
    ...change, readyForOwner: isReadyForOwner({ ...change, draft: !!pr.draft, state, certification }),
    url: `https://github.com/${pr.base?.repo?.full_name || 'AARMOU/arcana_table'}/pull/${pr.number}/files`,
    updatedAt: pr.updated_at || null };
}

// Ready count is strictly actionable, never the number of green/draft/non-card PRs.
function inboxPayload(items, now = new Date(), integratedActivity = []) {
  const order = { certified: 0, 'awaiting-review': 1, 'checks-failed': 2, 'changes-requested': 3, 'checks-running': 4, approved: 5, integrated: 6 };
  const sorted = [...items].sort((a, b) => Number(isReadyForOwner(b)) - Number(isReadyForOwner(a)) ||
    Number(b.kind === 'card') - Number(a.kind === 'card') || (order[a.state] ?? 9) - (order[b.state] ?? 9) || a.pr - b.pr).slice(0, MAX_ITEMS);
  return { checkedAt: now.toISOString(), awaitingReview: sorted.filter(isReadyForOwner).length, items: sorted,
    // Separate from the 30-row review inbox: this timestamped sample powers a conservative daily merge count.
    integratedActivity: integratedActivity.slice(0, 100),
    note: 'Owner-approved cards may integrate automatically under the standing card-only policy after destination-engine checks pass. APK builds, signing and releases remain separately authorized. Checks passing is not rules certification.' };
}

function inboxSummary(p) {
  if (!p.items.length) return ['Card review inbox: no Copilot card PRs open.'];
  return [`Card review inbox: ${p.awaitingReview} verified card implementation(s) ready for owner approval of ${p.items.length} Copilot work PR(s).`,
    ...p.items.map(i => `#${i.pr} ${i.card}: ${i.state}`)];
}

async function buildReviewInbox({ github, owner, repo, now = new Date() }) {
  const prs = (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 }))
    .filter(p => COPILOT.has(String(p.user?.login || '').toLowerCase()));
  const items = [];
  const integratedActivity = [];
  // The display cap must not prevent newly approved cards or verified integration
  // receipts from being considered. Source PRs may remain open on a legacy base.
  for (const pr of prs) {
    // Card PRs only: the PR must fix an open [Arcana Queue] work unit (skips blocker notes and old work).
    const n = unitOf(pr.body);
    if (!n) continue;
    const unit = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
    if (unit.state !== 'open' || !String(unit.title || '').startsWith('[Arcana Queue]')) continue;
    const checkRuns = (await github.rest.checks.listForRef({ owner, repo, ref: pr.head.sha, per_page: 100 })).data.check_runs || [];
    const reviews = await github.paginate(github.rest.pulls.listReviews, { owner, repo, pull_number: pr.number, per_page: 100 });
    let changedFiles = null;
    try {
      changedFiles = (await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 }))
        .map(f => f.filename);
    } catch { /* Unknown is not an implementation: fail closed, without dropping the row. */ }
    const row = classify(pr, checkRuns, reviews, unit.body, changedFiles);
    if (row.state === 'approved') {
      try {
        const integration = await I.receiptFor({github,owner,repo,pr});
        if (integration) {
          row.integration = integration;
          row.checkRuns.unshift({name:'Android integration',result:integration.status==='integrated'?'success':'failure',url:integration.runUrl});
          if (integration.status === 'integrated') {
            row.state='integrated'; row.readyForOwner=false;
            row.updatedAt=integration.at;
            row.url=`https://github.com/${owner}/${repo}/commit/${integration.destinationSha}`;
            integratedActivity.push({pr:pr.number,card:row.card,mergedAt:integration.at});
          } else {
            row.problems.unshift('Integration blocked: '+String(integration.reason||'See destination test run').slice(0,1000));
          }
        }
      } catch {
        row.problems.unshift('Android integration status could not be verified');
      }
    }
    items.push(row);
  }
  // Recently integrated cards (merged Copilot card PRs), so an approval visibly reaches "Integrated".
  const since = now.getTime() - INTEGRATED_DAYS * 864e5;
  const closed = (await github.rest.pulls.list({ owner, repo, state: 'closed', sort: 'updated', direction: 'desc', per_page: 100 })).data || [];
  for (const pr of closed) {
    if (!COPILOT.has(String(pr.user?.login || '').toLowerCase()) || !pr.merged_at || Date.parse(pr.merged_at) < since || !unitOf(pr.body)) continue;
    // Daily card count must also exclude blocker/audit PRs linked to a card unit.
    let changedFiles = null;
    try {
      changedFiles = (await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 }))
        .map(f => f.filename);
    } catch { /* No evidence => not a counted card. */ }
    const change = classifyChangeFiles(changedFiles, pr.title);
    if (change.kind !== 'card' || !change.hasTest) continue;
    integratedActivity.push({ pr: pr.number, card: cardOf(pr.title), mergedAt: pr.merged_at });
    items.push({ pr: pr.number, issue: unitOf(pr.body), card: cardOf(pr.title), headSha: pr.merge_commit_sha || pr.head?.sha || '',
      state: 'integrated', checks: {}, checkRuns: [], certification: 'not-run', abilities: [], problems: [], decidedIn: null, draft: false,
      ...change, readyForOwner: false,
      url: `https://github.com/${pr.base?.repo?.full_name || 'AARMOU/arcana_table'}/pull/${pr.number}`, updatedAt: pr.merged_at });
  }
  return inboxPayload(items, now, integratedActivity);
}

module.exports = { classify, classifyChangeFiles, isReadyForOwner, inboxPayload, inboxSummary, buildReviewInbox, abilitiesOf };
