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
//   no-checks         No checks observed on this head
//   checks-failed     Checks Failed
//   orphaned          Queue closed; PR unfinished; no verified destination receipt
//   repair-exhausted  Paid repair budget used; finished repair still leaves red checks
//   held              Explicit hold/dependency, or repair pickup cannot be verified
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
const A = require('./admission.cjs');
const { unitCards } = require('./feasibility.cjs');
const { contentWrittenAt, sessionsOf } = require('./units.cjs');
const { oracleSnapshot } = require('./oracle-handoff.cjs');
const { DIAGNOSIS_LABEL } = require('./dispatch-runner.cjs');
const { isCardCheck } = require('./card-checks.cjs');
const DECISION_MARKER = '<!-- arcana-owner-decision-v1 -->';

const unitOf = body => Number((String(body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]) || null;
const cardOf = title => String(title || '').replace(/^\s*(?:\[[^\]]*\]\s*)?(?:Implement|Add)\s+/i, '').trim().slice(0, 120);

// A title is a work description, not an Oracle lookup key. Never guess a name for
// multi-card/discovery units. Keep `card` for old Control consumers; T3 uses name/title.
function identityOf(unitBody) {
  const names = unitCards(unitBody);
  return { name: names.length === 1 ? names[0] : null };
}

async function offlineOracle({ github, owner, repo, unit, name }) {
  if (!name) return null;
  const ownerAuthored = String(unit.user?.login || '').toLowerCase() === owner.toLowerCase() && unit.author_association === 'OWNER';
  const feeder = A.isBot(unit) && String(unit.body || '').startsWith(A.AUTO_QUEUE_MARKER + '\n') &&
    String(unit.body).includes('source: trusted-main-queue-feeder');
  if (!ownerAuthored && !feeder) return null;
  if (feeder) {
    const written = await contentWrittenAt(github, owner, repo, unit.number);
    if (!Number.isFinite(written) || written !== Date.parse(unit.created_at)) return null;
  }
  const section = String(unit.body || '').match(/^## Offline Oracle record[^\r\n]*\r?\n([\s\S]*?)(?=^## |$(?![\s\S]))/m)?.[1] || '';
  for (const line of section.split(/\r?\n/)) {
    if (!/^ {4}\{/.test(line)) continue;
    try { return { source: 'trusted-queue-snapshot', issue: unit.number, record: JSON.parse(oracleSnapshot(line.trim(), name)) }; }
    catch { /* Invalid/mismatched snapshots are unavailable, never invented. */ }
  }
  return null;
}

// Classify by changed-file evidence, NOT PR title or linked queue unit alone.
// Missing/incomplete GitHub file evidence fails closed: it never becomes an approval.
function classifyChangeFiles(files, title = '') {
  if (!Array.isArray(files) || files.length >= 100) return { kind: 'unknown', hasTest: false, reason: 'Changed-file evidence unavailable or incomplete' };
  const source = files.some(f => /^android\/custom-cards\/src\/main\/.+\/cards\/[^/]+\.kt$/.test(f));
  const hasTest = files.some(f => /^android\/custom-cards\/src\/test\/.+Test\.kt$/.test(f));
  if (source) {
    // Integration takes only the card-only file set with the registration (scripts/card-integration.cjs).
    // A PR outside it can never land, so it must not be offered for Verify; say exactly why instead.
    const allowed = f => /^android\/custom-cards\/src\/main\/.+\/cards\/[^/]+\.kt$/.test(f) ||
      /^android\/custom-cards\/src\/test\/.+ScenarioTest\.kt$/.test(f) || f === I.REGISTRY || I.GENERATED.includes(f);
    const outside = files.filter(f => !allowed(f));
    const shapeProblem = !files.includes(I.REGISTRY) ? 'Not integrable: missing CustomCards.kt registration and regenerated card exports (Cardkit Finishing steps)'
      : outside.length ? 'Not integrable: changes files outside the card-only set: ' + outside.slice(0, 3).join(', ') : null;
    return { kind: 'card', hasTest, reason: hasTest ? shapeProblem : 'Executable card scenario test missing', ...(shapeProblem ? { shapeProblem } : {}) };
  }
  if (/\b(audit|coverage|inventory|documentation|docs)\b/i.test(title) || (files.length && files.every(f => /^(docs\/|README|\.github\/)/.test(f))))
    return { kind: 'audit', hasTest: false, reason: 'Audit/documentation, no card implementation' };
  return { kind: 'engine', hasTest: false, reason: files.length ? 'Engine or blocker changes; no card implementation' : 'No card implementation changes in this PR' };
}
// Drafts count once certified: the owner's Approve marks the PR ready for review (review Worker).
const isReadyForOwner = i => i?.kind === 'card' && i.hasTest === true && !i.shapeProblem &&
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
  const runs = checkRuns.filter(r => (r.head_sha === undefined || r.head_sha === head) && isCardCheck(r));
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
      : !runs.length ? 'no-checks'
        : pending ? 'checks-running'
        : failedRuns.length ? 'checks-failed'
          : certification === 'certified' ? 'certified' : 'awaiting-review';
  const problems = [
    ...failedRuns.map(r => `Check failed: ${r.name} (${r.conclusion})`),
    ...(owner.length && !onHead.length ? ['New commit since your last review: review again'] : []),
    ...(last?.state === 'CHANGES_REQUESTED' ? ['You requested a fix on this commit'] : []),
  ].slice(0, 8);
  if (change.reason) problems.push(change.reason);
  if (pr.draft && change.kind === 'card' && state !== 'certified') problems.push('Draft PR: not ready for owner approval');
  const decidedHere = last && String(last.body || '').startsWith(DECISION_MARKER) ? 'arcana-control' : last ? 'github' : null;
  if (!runs.length) problems.unshift('No card checks observed on the current commit');
  return { pr: pr.number, issue: unitOf(pr.body), card: cardOf(pr.title), title: pr.title, ...identityOf(unitBody), headSha: head, state, checks, checkRuns: checkRuns2,
    certification, abilities: abilitiesOf(unitBody), problems: problems.slice(0, 8), decidedIn: decidedHere, draft: !!pr.draft,
    // When the current stage began, for the stall report (a PR's updated_at ignores checks and reviews).
    certifiedAt: certification === 'certified' ? (runs.find(r => r.name === 'Arcana independent certification' && r.conclusion === 'success')?.completed_at || null) : null,
    approvedAt: last?.state === 'APPROVED' ? (last.submitted_at || null) : null,
    ...change, readyForOwner: isReadyForOwner({ ...change, draft: !!pr.draft, state, certification }),
    url: `https://github.com/${pr.base?.repo?.full_name || 'AARMOU/arcana_table'}/pull/${pr.number}/files`,
    updatedAt: pr.updated_at || null };
}

// Ready count is strictly actionable, never the number of green/draft/non-card PRs.
function inboxPayload(items, now = new Date(), integratedActivity = []) {
  const order = { certified: 0, 'awaiting-review': 1, 'checks-failed': 2, 'repair-exhausted': 2, orphaned: 2, held: 3,
    'changes-requested': 3, 'no-checks': 4, 'checks-running': 4, approved: 5, integrated: 6 };
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

// Reporting only. The ledger remains the sole repair counter; this never resets
// it, schedules work, or treats a comment/label as a worker pickup acknowledgement.
async function workEvidence({ github, owner, repo, unit, pr, now }) {
  let ledger;
  try {
    const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: unit.number, per_page: 100 });
    ledger = A.parseLedger(comments).filter(r => Number(r.issue) === unit.number);
  } catch { return { repairs: null, limit: A.CEILING.repair, pickup: 'unknown', halted: false }; }
  const repairs = ledger.filter(r => r.op === 'repair-request').length;
  const halted = ledger.some(r => r.op === 'halt');
  const evidence = { repairs, limit: A.CEILING.repair, pickup: 'unknown', halted };
  if (repairs < A.CEILING.repair) return evidence;
  try {
    const [unitEvents, prEvents] = await Promise.all([
      github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number: unit.number, per_page: 100 }),
      github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number: pr.number, per_page: 100 }),
    ]);
    const derived = A.deriveUnit({ number: unit.number, labels: [], ledger, sessions: sessionsOf([...unitEvents, ...prEvents]) }, now.getTime());
    evidence.pickup = derived.holdsSlot ? derived.reason || derived.state : 'finished';
  } catch { /* Budget is known; worker pickup is not. Keep those facts separate. */ }
  return evidence;
}

function applyWorkState(row, { unit, work, suite = null }) {
  const labels = (unit.labels || []).map(l => l?.name || l);
  row.work = work;
  if (suite) row.suite = suite;
  const original = row.state;
  const failedSuite = row.certification !== 'certified' && suite && ['checks-failed', 'repair-exhausted'].includes(suite.state);
  if (row.state === 'integrated') return row;
  if (row.integrationUnverified) {
    row.state = 'held';
    row.recovery = { who: 'Claude (automation)', next: 'retry integration evidence lookup; do not reopen or reassign this card', reason: 'Integration status unverified: receipt lookup unavailable' };
  } else if (unit.state === 'closed') {
    row.state = 'orphaned';
    row.recovery = { who: 'Work GPT / Goose (coordination)', next: 'reconcile the closed queue request with its unfinished PR and current owner; preserve the attempt ledger',
      reason: 'Queue request closed without a verified Android integration receipt' };
  } else if (labels.includes(A.HALTED_LABEL) || labels.includes('arcana:engine-gap') || work.halted) {
    row.state = 'held';
    row.recovery = labels.includes('arcana:engine-gap')
      ? { who: 'existing engine-lane owner', next: 'resolve the evidenced engine dependency before resuming card validation', reason: 'Queue labeled arcana:engine-gap' }
      : { who: 'Work GPT / Goose (coordination)', next: 'read the hold and reconcile with the current owner before resuming validation', reason: 'Queue halted by label or trusted ledger' };
  } else {
    const exhausted = failedSuite ? suite.work : row.state === 'checks-failed' ? work : null;
    if (exhausted?.repairs >= A.CEILING.repair && exhausted.pickup === 'finished') {
      row.state = 'repair-exhausted';
      row.recovery = { who: 'Work GPT / Goose (engineering)', next: `diagnose the failing ${failedSuite ? 'suite PR #' + suite.pr : 'card PR #' + row.pr} without another paid attempt`,
        reason: `${exhausted.repairs}/${A.CEILING.repair} paid repairs consumed; current checks still fail` };
    } else if (exhausted?.repairs >= A.CEILING.repair) {
      if (exhausted.pickup === 'unknown') row.state = 'held';
      row.recovery = { who: 'existing repair owner / Work GPT (coordination)',
        next: `reconcile ${failedSuite ? 'suite PR #' + suite.pr : 'card PR #' + row.pr} repair pickup (${exhausted.pickup}); do not schedule another paid attempt`,
        reason: `${exhausted.repairs}/${A.CEILING.repair} paid repairs consumed; current repair ${exhausted.pickup === 'unknown' ? 'pickup unverified' : 'still holds its slot'}` };
    }
  }
  if (!row.recovery && suite && row.certification !== 'certified') {
    row.recovery = { who: 'existing suite owner', next: `inspect suite PR #${suite.pr} (${suite.state}) and its exact-head premerge; preserve the card assertions`,
      reason: 'Certification depends on the existing suite PR' };
  }
  if (row.state !== original) {
    row.validationState = original;
    row.readyForOwner = false;
    row.problems.unshift(row.recovery.reason);
  }
  return row;
}

// Join existing suite PRs to card PRs by their trusted request's explicit fields,
// never by a similar title or a guessed card name. Reuse the already fetched PR list.
async function suiteDependencies({ github, owner, repo, prs, now }) {
  const byCard = new Map();
  for (const pr of prs.filter(p => p.base?.ref === A.SUITE_BASE)) {
    const n = unitOf(pr.body);
    if (!n) continue;
    const request = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
    if (!A.isBot(request) || !String(request.body || '').startsWith(A.SUITE_MARKER + '\n') ||
        !String(request.title || '').startsWith(A.SUITE_TITLE)) continue;
    const cardPr = Number(String(request.body).match(/^Card PR: #(\d+)\s*$/m)?.[1]);
    const cardUnit = Number(String(request.body).match(/^Card unit: #(\d+)\s*$/m)?.[1]);
    if (!cardPr || !cardUnit) continue;
    const checks = (await github.rest.checks.listForRef({ owner, repo, ref: pr.head.sha, per_page: 100 })).data.check_runs || [];
    const row = classify(pr, checks);
    const work = row.state === 'checks-failed' ? await workEvidence({ github, owner, repo, unit: request, pr, now }) : null;
    const suite = { pr: pr.number, issue: n, cardUnit, headSha: pr.head.sha, state: row.state, work,
      url: `https://github.com/${owner}/${repo}/pull/${pr.number}`, checkRuns: row.checkRuns };
    if (!byCard.has(cardPr) || row.state === 'checks-failed') byCard.set(cardPr, suite);
  }
  return byCard;
}

async function buildReviewInbox({ github, owner, repo, now = new Date() }) {
  const prs = (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 }))
    .filter(p => COPILOT.has(String(p.user?.login || '').toLowerCase()));
  const items = [];
  const integratedActivity = [];
  const suites = await suiteDependencies({ github, owner, repo, prs, now });
  // The display cap must not prevent newly approved cards or verified integration
  // receipts from being considered. Source PRs may remain open on a legacy base.
  for (const pr of prs) {
    // Closed queue requests with unfinished PRs remain visible until reconciled.
    const n = unitOf(pr.body);
    if (!n) continue;
    const unit = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
    if (!String(unit.title || '').startsWith('[Arcana Queue]')) continue;
    // An integration automatically closes its queue issue. Keep the verified receipt in
    // the activity chart instead of dropping it on the very next inbox refresh.
    // A closed issue alone never proves integration.
    let integrationUnverified = false;
    if (unit.state === 'closed') {
      try {
        const integration = await I.receiptFor({ github, owner, repo, pr });
        if (integration?.status === 'integrated' && Number.isFinite(Date.parse(integration.at))) {
          const card = cardOf(pr.title);
          if (Date.parse(integration.at) >= now.getTime() - INTEGRATED_DAYS * 864e5)
            integratedActivity.push({ pr: pr.number, card, mergedAt: integration.at });
          items.push({
            pr: pr.number, issue: n, card, title: pr.title, ...identityOf(unit.body), headSha: pr.head.sha, state: 'integrated',
            checks: {}, checkRuns: [{ name: 'Android integration', result: 'success', url: integration.runUrl }],
            certification: 'certified', abilities: [], problems: [], decidedIn: null,
            draft: !!pr.draft, kind: 'card', hasTest: true, reason: null, readyForOwner: false,
            integration,
            url: `https://github.com/${owner}/${repo}/commit/${integration.destinationSha}`,
            updatedAt: integration.at,
          });
          continue;
        }
      } catch { integrationUnverified = true; }
    }
    if (!['open', 'closed'].includes(unit.state)) continue;
    const checkRuns = (await github.rest.checks.listForRef({ owner, repo, ref: pr.head.sha, per_page: 100 })).data.check_runs || [];
    const reviews = await github.paginate(github.rest.pulls.listReviews, { owner, repo, pull_number: pr.number, per_page: 100 });
    let changedFiles = null;
    try {
      changedFiles = (await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 }))
        .map(f => f.filename);
    } catch { /* Unknown is not an implementation: fail closed, without dropping the row. */ }
    const row = classify(pr, checkRuns, reviews, unit.body, changedFiles);
    row.integrationUnverified = integrationUnverified;
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
        row.integrationUnverified = true;
        row.problems.unshift('Android integration status could not be verified');
      }
    }
    const suite = suites.get(pr.number);
    const labels = (unit.labels || []).map(l => l?.name || l);
    const needsWork = row.state !== 'integrated' && (row.state === 'checks-failed' ||
      (suite?.cardUnit === n && suite.state === 'checks-failed' && row.certification !== 'certified') ||
      labels.includes(A.HALTED_LABEL) || labels.includes('arcana:engine-gap'));
    const work = needsWork ? await workEvidence({ github, owner, repo, unit: { ...unit, number: n }, pr, now }) : { repairs: null, limit: A.CEILING.repair, pickup: 'not-observed', halted: false };
    row.oracle = row.kind === 'card' && row.state !== 'integrated' ? await offlineOracle({ github, owner, repo, unit, name: row.name }) : null;
    items.push(applyWorkState(row, { unit, work, suite: suite?.cardUnit === n ? suite : null }));
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

// Owner alert on #78 when a card becomes ready to Verify. Only a PR at a head not already in the
// last alert is new, so refreshes every 15 minutes post nothing until something changes.
const READY_MARKER = '<!-- arcana-ready-to-verify-v1 -->';
async function alertReady({ github, owner, repo, payload, issue = 78 }) {
  const ready = (payload?.items || []).filter(isReadyForOwner).filter(i => Number.isSafeInteger(i.pr) && /^[0-9a-f]{40}$/.test(String(i.headSha)));
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: issue, per_page: 100 });
  const previous = [...comments].reverse().find(c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot' && String(c.body || '').startsWith(READY_MARKER));
  const seen = new Set((String(previous?.body || '').match(/^ready: (.*)$/m)?.[1] || '').split(' ').filter(Boolean));
  const fresh = ready.filter(i => !seen.has(i.pr + '@' + i.headSha));
  if (!fresh.length) return 'unchanged';
  const body = [READY_MARKER, 'ready: ' + ready.map(i => i.pr + '@' + i.headSha).join(' '),
    `@AARMOU **${fresh.length} card${fresh.length > 1 ? 's' : ''} ready to Verify** in Arcana Control:`,
    fresh.map(i => `- #${i.pr} ${String(i.card || 'card').replace(/[@\r\n]/g, ' ').slice(0, 80)} (head ${i.headSha.slice(0, 7)})`).join('\n'),
    'Open Control → Verify: https://aarmou.github.io/arcana_table/control/',
    'Independently certified with green checks; approval and integration still need your tap. No automatic merge or build.'].join('\n\n');
  await github.rest.issues.createComment({ owner, repo, issue_number: issue, body });
  return 'posted ' + fresh.length;
}

// Stall report on #78: card PRs stuck in one stage past a limit, measured from when the stage
// began (certification or approval time) or else the PR's last activity. Posted only when the set of stalled PR@head:stage keys changes, so ages ticking up
// never re-post. Read-only apart from that note.
const STALL_MARKER = '<!-- arcana-stalls-v1 -->';
const STALL_RULES = {
  certified: { hours: 24, text: 'certified, waiting for owner Verify', who: 'owner', next: 'Verify in Arcana Control' },
  approved: { hours: 2, text: 'approved, not integrated', who: 'Claude (automation)', next: 'read the #78 skip report or the integration receipt and fix the cause' },
  'awaiting-review': { hours: 24, text: 'checks green but not certified (suite missing or certification gate)', who: 'card lane (Work GPT / Goose)', next: 'inspect the existing suite request/premerge or add the missing independent suite; route only evidenced engine defects to the engine lane' },
  'checks-failed': { hours: 6, text: 'CI red on the current commit', who: 'card lane (auto-repair when enabled)', next: 'fix the failing check on the card PR' },
  'checks-running': { hours: 6, text: 'checks still running', who: 'Claude (automation)', next: 'find the stuck or cancelled run' },
  'no-checks': { hours: 6, text: 'no card checks observed on the current commit', who: 'Claude (automation)', next: 'inspect the missing CI trigger/check publication; no run is known to be executing' },
  orphaned: { hours: 0, text: 'queue closed while the PR remains unfinished', who: 'Work GPT / Goose (coordination)', next: 'reconcile the current owner, queue and receipt without replaying the assignment' },
  'repair-exhausted': { hours: 0, text: 'paid repair ceiling reached with failing checks', who: 'Work GPT / Goose (engineering)', next: 'diagnose without another paid attempt' },
  held: { hours: 0, text: 'held pending dependency or engineering review', who: 'existing recovery owner', next: 'inspect the hold evidence and acknowledge recovery' },
};
// A blocked weekly Argentum upgrade issue (bot-opened, "(needs work)") is engine-lane work: Claude owns it.
const ENGINE_STALL_HOURS = 24;
const isBlockedUpgrade = i => !i.pull_request && i.user?.login === 'github-actions[bot]' && i.user?.type === 'Bot' &&
  /^Weekly Argentum upgrade to [0-9a-f]+/.test(String(i.title || '')) && / \(needs work\)$/.test(String(i.title || ''));
// GPT engine lane: feeder-opened [Card Work] issues and queue units labeled arcana:engine-gap (Copilot could
// not implement them). Owner Work GPT, deadline LANE_HOURS from when the work arrived in the lane.
const LANE_HOURS = 48;
const inGptLane = i => !i.pull_request && (String(i.title || '').startsWith('[Card Work] ') ||
  (String(i.title || '').startsWith('[Arcana Queue]') && (i.labels || []).some(l => (l?.name || l) === 'arcana:engine-gap')));
// Owner build trigger: B140 has changes no published release contains, for longer than BUILD_HOURS.
const B140_BRANCH = 'gpt/b140-card-request-test-apk-20261028';
const BUILD_HOURS = 72;
async function unreleasedBuild({ github, owner, repo, now }) {
  const head = (await github.rest.repos.getBranch({ owner, repo, branch: B140_BRANCH })).data.commit;
  let release = null;
  try { release = (await github.rest.repos.getLatestRelease({ owner, repo })).data; } catch {}
  const releasedAt = Date.parse(release?.published_at || '');
  const since = Number.isFinite(releasedAt) ? releasedAt : Date.parse(head.commit?.committer?.date || '');
  const changedAt = Date.parse(head.commit?.committer?.date || '');
  if (!(changedAt > (Number.isFinite(releasedAt) ? releasedAt : -Infinity))) return null;
  const hours = (now - since) / 36e5;
  return hours >= BUILD_HOURS ? { sha: head.sha, hours, release: release?.tag_name || null } : null;
}
function stalls(payload, now = Date.now()) {
  return (payload?.items || []).filter(i => i?.kind === 'card' && STALL_RULES[i.state] && Number.isSafeInteger(i.pr))
    .map(i => ({ ...i, hours: (now - Date.parse((i.state === 'certified' && i.certifiedAt) || (i.state === 'approved' && i.approvedAt) || i.updatedAt || '')) / 36e5 }))
    .filter(i => Number.isFinite(i.hours) && i.hours >= STALL_RULES[i.state].hours)
    .sort((a, b) => a.pr - b.pr).slice(0, 20);
}
async function reportStalls({ github, owner, repo, payload, issue = 78, now = Date.now() }) {
  const list = stalls(payload, now);
  const open = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', per_page: 100 });
  const diagnosis = open.filter(i => !i.pull_request && String(i.title || '').startsWith('[Arcana Queue]') &&
    (i.labels || []).some(l => (l?.name || l) === DIAGNOSIS_LABEL)).sort((a,b) => a.number-b.number).slice(0,20);
  const engine = open.filter(isBlockedUpgrade).map(i => ({ n: i.number, title: i.title, hours: (now - Date.parse(i.created_at || '')) / 36e5 }))
    .filter(e => Number.isFinite(e.hours) && e.hours >= ENGINE_STALL_HOURS).sort((a, b) => a.n - b.n);
  // Lane age: when the engine-gap label was applied is not in the issue list; the issue's creation (or last
  // label change, via updated_at as an upper bound) is used, so a lane item is never reported early.
  const lane = open.filter(inGptLane).map(i => ({ n: i.number, title: i.title, hours: (now - Date.parse(i.updated_at || i.created_at || '')) / 36e5 }))
    .filter(e => Number.isFinite(e.hours) && e.hours >= LANE_HOURS).sort((a, b) => a.n - b.n).slice(0, 20);
  let build = null;
  try { build = await unreleasedBuild({ github, owner, repo, now }); } catch {}
  const keys = [...diagnosis.map(i => 'zero-delivery#' + i.number), ...engine.map(e => 'engine#' + e.n), ...lane.map(e => 'lane#' + e.n), ...(build ? ['build@' + build.sha.slice(0, 12)] : []),
    ...list.map(i => i.pr + '@' + String(i.headSha).slice(0, 12) + ':' + i.state +
      (i.suite ? ':suite#' + i.suite.pr + '@' + String(i.suite.headSha).slice(0, 12) + ':' + i.suite.state : ''))].join(' ');
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: issue, per_page: 100 });
  const previous = [...comments].reverse().find(c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot' && String(c.body || '').startsWith(STALL_MARKER));
  const before = String(previous?.body || '').match(/^stalls: (.*)$/m)?.[1] ?? null;
  if (before === keys || (before === null && !keys)) return 'unchanged';
  const body = [STALL_MARKER, 'stalls: ' + keys, '### 🤖 Stalled work (automated)', '',
    keys ? 'Work stuck past its stage limit. Each line names the stage, who owns the next step, and what it is.'
      : 'Nothing is stalled.', '',
    ...diagnosis.map(i => `- zero-delivery: #${i.number} — Copilot finished without a PR; unclassified, needs diagnosis. **Owner:** Work GPT / Goose (engineering). **Next:** inspect the session/output and Oracle handoff, then route the evidenced cause; preserve the ledger and existing retry ceiling. A label is not worker pickup.`),
    ...engine.map(e => `- #${e.n} ${String(e.title).replace(/[@\r\n]/g, ' ').slice(0, 70)}: blocked engine upgrade for ${Math.floor(e.hours)}h. ` +
      '**Owner:** Claude (engine lane). **Next:** fix on a `gpt/argentum-weekly-fix-*` branch per `engine-upgrade/HANDOFF.md`, dry run green, then merge into `gpt/arcana-integration-next`.'),
    ...lane.map(e => `- #${e.n} ${String(e.title).replace(/[@\r\n]/g, ' ').slice(0, 70)}: in the GPT engine lane with no activity for ${Math.floor(e.hours)}h (deadline ${LANE_HOURS}h). ` +
      '**Owner:** Work GPT (engine lane). **Next:** add the engine support the card needs on `gpt/arcana-integration-next`, then remove `arcana:engine-gap` (or close the [Card Work] issue) so the card returns to Copilot; or comment why it cannot be done.'),
    ...(build ? [`- B140 \`${build.sha.slice(0, 10)}\` has integrated changes not in any release${build.release ? ' (latest: ' + build.release + ')' : ''} for ${Math.floor(build.hours)}h (trigger ${BUILD_HOURS}h). ` +
      '**Owner:** AARMOU (build). **Next:** cut the next numbered build from B140 per the B141 checklist in `docs/stabilization/CONTROL-PLAN.md` and publish it as a GitHub release, which clears this line.'] : []),
    ...list.map(i => `- #${i.pr} ${String(i.card || 'card').replace(/[@\r\n]/g, ' ').slice(0, 60)}: ${STALL_RULES[i.state].text} for ${Math.floor(i.hours)}h. ` +
      (i.recovery ? `**Owner:** ${i.recovery.who}. **Next:** ${i.recovery.next}`
        : i.shapeProblem ? '**Owner:** card lane (Copilot / Work GPT / Goose). **Next:** complete the PR to the card-only file set'
        : `**Owner:** ${STALL_RULES[i.state].who}. **Next:** ${STALL_RULES[i.state].next}`) +
      (i.problems?.[0] ? ` (${String(i.problems[0]).replace(/[@\r\n]/g, ' ').slice(0, 140)})` : '') + '.')].join('\n');
  await github.rest.issues.createComment({ owner, repo, issue_number: issue, body });
  return 'posted ' + list.length;
}

module.exports = { classify, classifyChangeFiles, isReadyForOwner, inboxPayload, inboxSummary, buildReviewInbox, abilitiesOf, alertReady, READY_MARKER,
  stalls, reportStalls, STALL_MARKER, inGptLane, unreleasedBuild, LANE_HOURS, BUILD_HOURS, identityOf, offlineOracle, applyWorkState };
