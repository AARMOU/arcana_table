'use strict';
// Loads work units from GitHub for admission control. All I/O lives here so that
// admission.cjs stays pure. One unit = one queue (or Copilot-assigned) issue plus its PRs.
const { parseLedger, deriveUnit, provenance, ONE_SHOT_LABEL, LEDGER, isBot } = require('./admission.cjs');

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

async function loadUnits({ github, owner, repo, now = Date.now(), approvers, lagMs }) {
  const open = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', per_page: 100 });
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  const unitIssues = open.filter(i => !i.pull_request &&
    (String(i.title).startsWith('[Arcana Queue]') || (i.assignees || []).some(isCopilot)));
  const numbers = new Set(unitIssues.map(i => i.number));
  const events = new Map();
  for (const issue of unitIssues) events.set(issue.number, await timeline(github, owner, repo, issue.number));
  // Each PR belongs to exactly one unit: explicit "Unit: #N" line, else GitHub's connected link,
  // else the lowest referenced unit issue (conservative fallback). A PR connected to several units
  // is ambiguous: it counts against every one of them and blocks their admission until fixed.
  const connectedTo = new Map();
  for (const [n, evs] of events) for (const pr of connectedPrs(evs)) connectedTo.set(pr, [...(connectedTo.get(pr) || []), n]);
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
    for (const pr of linked) sessions.push(...sessionsOf(await timeline(github, owner, repo, pr.number)));
    const labels = (issue.labels || []).map(l => l.name || l);
    const raw = { number: issue.number, title: issue.title, body: issue.body || '', labels,
      assignedToCopilot: (issue.assignees || []).some(isCopilot), ledger: parseLedger(comments), sessions,
      authorAssociation: issue.author_association, commenters: commentersOf(comments) };
    const oneShot = labels.includes(ONE_SHOT_LABEL);
    if (oneShot) raw.approval = approvalOf(events.get(issue.number), await contentWrittenAt(github, owner, repo, issue.number));
    units.push({ raw, derived: { ...deriveUnit(raw, now, lagMs === undefined ? undefined : { lagMs }), ambiguousLink: ambiguous.has(issue.number),
      provenance: provenance(raw, { needApproval: oneShot, approvers }) }, prs: linked });
  }
  return { units, prsByUnit, numbers };
}

module.exports = { unitOf, loadUnits, isCopilot, contentWrittenAt, approvalOf, commentersOf };
