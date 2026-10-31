'use strict';
// Loads work units from GitHub for admission control. All I/O lives here so that
// admission.cjs stays pure. One unit = one queue (or Copilot-assigned) issue plus its PRs.
const { parseLedger, deriveUnit, provenance, ONE_SHOT_LABEL, LEDGER, isBot, SUITE_TITLE, SUITE_MARKER, SUITE_BASE } = require('./admission.cjs');
const { overrideOf } = require('./feasibility.cjs');

// writtenAt = created or last edited, whichever is later. Only the dispatcher's own ledger records
// (github-actions[bot] + ledger marker) are exempt from the after-approval rule.
const commentersOf = comments => comments.map(c => ({ association: c.author_association, login: c.user?.login, type: c.user?.type,
  ledger: isBot(c) && String(c.body || '').startsWith(LEDGER),
  writtenAt: Math.max(Date.parse(c.created_at), Date.parse(c.updated_at || c.created_at)) }));

// When the issue body was last written: GitHub's lastEditedAt, or createdAt if never edited.
// GraphQL is the only API exposing body edit times. Any failure returns NaN, which fails closed.
async function contentWrittenAt(github, owner, repo, number) {
  try {
    const r = await github.graphql('query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){issue(number:$n){createdAt lastEditedAt}}}',
      { o: owner, r: repo, n: number });
    const i = r?.repository?.issue;
    return Date.parse(i?.lastEditedAt || i?.createdAt);
  } catch { return NaN; }
}

// The one-shot approval is the latest time the label was applied. Title changes after it void it.
function approvalOf(events, contentAt) {
  const labeled = events.filter(e => e.event === 'labeled' && e.label?.name === ONE_SHOT_LABEL).pop();
  if (!labeled) return null;
  const labeledAt = Date.parse(labeled.created_at);
  return { labeledAt, actor: labeled.actor?.login || null, contentAt,
    renamedAfter: events.some(e => e.event === 'renamed' && Date.parse(e.created_at) >= labeledAt) };
}

const isCopilot = a => ['copilot', 'copilot-swe-agent[bot]'].includes(String(a?.login || '').toLowerCase());
// A failed Copilot session also ends the session (if GitHub emits this event); without it a failed run would look like it is still working.
const SESSION = { copilot_work_started: 'started', copilot_work_finished: 'finished', copilot_work_finished_failure: 'finished' };

// Explicit "Unit: #N" wins; otherwise the lowest referenced unit issue number.
function unitOf(body, unitNumbers) {
  const text = String(body || '');
  const explicit = text.match(/^Unit:\s*#(\d+)\s*$/m);
  if (explicit && unitNumbers.has(Number(explicit[1]))) return Number(explicit[1]);
  const refs = [...text.matchAll(/(?:#|\/issues\/)(\d+)\b/g)].map(m => Number(m[1])).filter(n => unitNumbers.has(n));
  return refs.length ? Math.min(...refs) : null;
}

async function timeline(github, owner, repo, issue_number) {
  return github.paginate(github.rest.issues.listEventsForTimeline, { owner, repo, issue_number, per_page: 100 });
}
const sessionsOf = events => events.filter(e => SESSION[e.event]).map(e => ({ event: SESSION[e.event], at: Date.parse(e.created_at) }));
// A missing PR is not a diagnosis. Engine routing requires an explicit engineer
// confirmation with a source/run link; ordinary Copilot comments cannot supply it.
function engineEvidenceOf(comments, owner, repo) {
  return comments.filter(c => c.user?.type === 'User' && ['OWNER', 'MEMBER', 'COLLABORATOR'].includes(c.author_association))
    .map(c => {
      const reason = String(c.body || '').match(/^Engine gap confirmed:\s*(\S[^\r\n]{9,299})\s*$/m)?.[1];
      const evidence = String(c.body || '').match(/^Evidence:\s*(https:\/\/github\.com\/\S+)\s*$/m)?.[1];
      let url;
      try { url = new URL(evidence); } catch { return null; }
      if (!reason || url.origin !== 'https://github.com' || url.search || url.hash || url.username || url.password ||
          !url.pathname.startsWith(`/${owner}/${repo}/`) ||
          !/\/(?:actions\/runs\/\d+|blob\/[a-f0-9]{40}\/.+)$/.test(url.pathname)) return null;
      return { reason, evidence, comment: c.html_url || null, at: Math.max(Date.parse(c.created_at), Date.parse(c.updated_at || c.created_at)) };
    }).filter(e => e && Number.isFinite(e.at)).sort((a, b) => b.at - a.at)[0] || null;
}
// GitHub's development link: `connected` names exactly one PR (`subject`); `disconnected` undoes it.
function connectedPrs(events) {
  const linked = new Set();
  for (const e of events) {
    if (e.subject?.type !== 'pull_request') continue;
    if (e.event === 'connected') linked.add(e.subject.number);
    if (e.event === 'disconnected') linked.delete(e.subject.number);
  }
  return [...linked];
}

// Only repository-authored suite requests are retained after closure. Do not use a time
// cutoff: a PR can emit a late session start/finish without updating its closed issue.
const isSuiteRequest = i => !i.pull_request && isBot(i) &&
  String(i.title || '').startsWith(SUITE_TITLE) && String(i.body || '').startsWith(SUITE_MARKER + '\n');
const field = (body, name) => (String(body || '').match(new RegExp('^' + name + ': (\\S+)\\s*$', 'm')) || [])[1];

// A durable GitHub merge plus the exact requested files/registration is the completion
// record. Never accept a PR body, comment, closing keyword, or issue state as proof.
async function suiteCompletion({ github, owner, repo, issue, pr, now }) {
  const repository = owner + '/' + repo;
  const explicit = String(pr.body || '').match(/^Unit:\s*#(\d+)\s*$/m);
  const key = field(issue.body, 'Suite key');
  const cardUnit = Number(field(issue.body, 'Card unit')?.slice(1));
  const mergedAt = Date.parse(pr.merged_at);
  if (!isSuiteRequest(issue) || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(key || '') ||
      !Number.isSafeInteger(cardUnit) || cardUnit <= 0 ||
      (explicit && Number(explicit[1]) !== issue.number) || !isCopilot(pr.user) ||
      pr.state !== 'closed' || pr.merged !== true || !Number.isFinite(mergedAt) || mergedAt > now ||
      !/^[a-f0-9]{40}$/.test(pr.merge_commit_sha || '') || !/^[a-f0-9]{40}$/.test(pr.head?.sha || '') ||
      pr.base?.ref !== SUITE_BASE || pr.base?.repo?.full_name !== repository || pr.head?.repo?.full_name !== repository)
    return null;
  const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 });
  const json = `certification/suites/${key}.json`;
  const test = files.find(f => /^certification\/[A-Z][A-Za-z0-9]*IndependentCertificationTest\.kt$/.test(f.filename));
  if (files.length !== 2 || !files.some(f => f.filename === json) || !test || files.some(f => f.status !== 'added')) return null;
  // Read the merged commit, not a mutable branch or an untrusted completion comment.
  const f = (await github.rest.repos.getContent({ owner, repo, path: json, ref: pr.merge_commit_sha })).data;
  const suite = JSON.parse(Buffer.from(f.content, 'base64').toString('utf8'));
  if (suite.key !== key || suite.issue !== cardUnit || suite.source !== test.filename) return null;
  const runs = await github.paginate(github.rest.actions.listWorkflowRuns, { owner, repo,
    workflow_id: 'arcana-card-certification-premerge.yml', head_sha: pr.head.sha, event: 'pull_request', status: 'success', per_page: 100 });
  const cardPr = Number(field(issue.body, 'Card PR')?.slice(1));
  for (const run of runs) {
    if (run.name !== 'Arcana Card Certification Premerge' || run.event !== 'pull_request' || run.conclusion !== 'success' ||
        run.head_sha !== pr.head.sha || run.head_repository?.full_name !== repository ||
        !(Date.parse(run.updated_at) <= mergedAt)) continue;
    const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, { owner, repo, run_id: run.id, per_page: 100 });
    const expected = `Execute trusted independent card scenarios (${key}, ${cardPr}, ${cardUnit}, `;
    if (!jobs.some(j => String(j.name).startsWith(expected) && j.conclusion === 'success' &&
        (j.steps || []).some(s => s.name === 'Verify every registered independent test passed using the shared runner' && s.conclusion === 'success')) ||
        jobs.some(j => !['success', 'skipped'].includes(j.conclusion))) continue;
    return { pr: pr.number, key, headSha: pr.head.sha, mergeSha: pr.merge_commit_sha, mergedAt, run: run.id };
  }
  return null;
}

async function loadUnits({ github, owner, repo, now = Date.now(), approvers, lagMs }) {
  const open = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', per_page: 100 });
  const closed = await github.paginate(github.rest.issues.listForRepo,
    { owner, repo, state: 'closed', creator: 'github-actions[bot]', per_page: 100 });
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  const unitIssues = open.filter(i => !i.pull_request &&
    (String(i.title).startsWith('[Arcana Queue]') || String(i.title).startsWith(SUITE_TITLE) || (i.assignees || []).some(isCopilot)));
  const seen = new Set(unitIssues.map(i => i.number));
  for (const issue of closed.filter(isSuiteRequest)) if (!seen.has(issue.number)) {
    seen.add(issue.number); unitIssues.push(issue);
  }
  const numbers = new Set(unitIssues.map(i => i.number));
  const events = new Map();
  for (const issue of unitIssues) events.set(issue.number, await timeline(github, owner, repo, issue.number));
  // Each PR belongs to exactly one unit: explicit "Unit: #N" line, else GitHub's connected link,
  // else the lowest referenced unit issue (conservative fallback). A PR connected to several units
  // is ambiguous: it counts against every one of them and blocks their admission until fixed.
  const connectedTo = new Map();
  for (const [n, evs] of events) for (const pr of connectedPrs(evs)) connectedTo.set(pr, [...(connectedTo.get(pr) || []), n]);
  // Recover only explicitly connected suite PRs missing from the open list. This
  // avoids scanning every historical PR and excludes mere cross-references.
  const present = new Set(prs.map(p => p.number));
  for (const issue of unitIssues.filter(isSuiteRequest)) {
    for (const n of connectedPrs(events.get(issue.number))) if (!present.has(n)) {
      const pr = (await github.rest.pulls.get({ owner, repo, pull_number: n })).data;
      if (pr.number !== n || pr.base?.repo?.full_name !== owner + '/' + repo ||
          pr.head?.repo?.full_name !== owner + '/' + repo || pr.base?.ref !== SUITE_BASE || !isCopilot(pr.user))
        throw Error('Unverified connected suite PR #' + n);
      present.add(n); prs.push(pr);
    }
  }
  const prsByUnit = new Map();
  const ambiguous = new Set();
  for (const pr of prs) {
    const explicit = String(pr.body || '').match(/^Unit:\s*#(\d+)\s*$/m);
    const connected = connectedTo.get(pr.number) || [];
    let targets;
    if (explicit && numbers.has(Number(explicit[1]))) targets = [Number(explicit[1])];
    else if (connected.length > 1) { targets = connected; connected.forEach(n => ambiguous.add(n)); }
    else if (connected.length === 1) targets = connected;
    else { const n = unitOf(pr.body, numbers); targets = n ? [n] : []; }
    for (const n of targets) prsByUnit.set(n, [...(prsByUnit.get(n) || []), pr]);
  }
  const units = [];
  for (const issue of unitIssues) {
    const comments = await github.paginate(github.rest.issues.listComments,
      { owner, repo, issue_number: issue.number, per_page: 100 });
    const linked = prsByUnit.get(issue.number) || [];
    const sessions = sessionsOf(events.get(issue.number));
    let completion = null;
    for (const pr of linked) {
      sessions.push(...sessionsOf(await timeline(github, owner, repo, pr.number)));
      if (isSuiteRequest(issue) && pr.merged === true && !ambiguous.has(issue.number)) {
        const proof = await suiteCompletion({ github, owner, repo, issue, pr, now });
        if (proof && (!completion || proof.mergedAt > completion.mergedAt)) completion = proof;
      }
    }
    const labels = (issue.labels || []).map(l => l.name || l);
    const raw = { number: issue.number, title: issue.title, body: issue.body || '', labels,
      closed: issue.state === 'closed', suiteCompletion: completion,
      assignedToCopilot: (issue.assignees || []).some(isCopilot), ledger: parseLedger(comments), sessions,
      authorAssociation: issue.author_association, authorLogin: issue.user?.login,
      authorType: issue.user?.type, commenters: commentersOf(comments) };
    raw.engineGapEvidence = engineEvidenceOf(comments, owner, repo);
    if (raw.authorLogin === 'github-actions[bot]' && raw.authorType === 'Bot') {
      // Comment and assignment timestamps do not constitute body edits.
      const writtenAt = await contentWrittenAt(github, owner, repo, issue.number);
      raw.autoQueueUnedited = Number.isFinite(writtenAt) && writtenAt === Date.parse(issue.created_at);
    }
    raw.feasibilityOverride = overrideOf(events.get(issue.number), labels);
    const oneShot = labels.includes(ONE_SHOT_LABEL);
    if (oneShot) raw.approval = approvalOf(events.get(issue.number), await contentWrittenAt(github, owner, repo, issue.number));
    units.push({ raw, derived: { ...deriveUnit(raw, now, lagMs === undefined ? undefined : { lagMs }), ambiguousLink: ambiguous.has(issue.number),
      provenance: provenance(raw, { needApproval: oneShot, approvers }) }, prs: linked });
  }
  return { units, prsByUnit, numbers };
}

module.exports = { unitOf, loadUnits, isCopilot, contentWrittenAt, approvalOf, commentersOf, sessionsOf, engineEvidenceOf };
