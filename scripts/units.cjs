'use strict';
// Loads work units from GitHub for admission control. All I/O lives here so that
// admission.cjs stays pure. One unit = one queue (or Copilot-assigned) issue plus its PRs.
const { parseLedger, deriveUnit } = require('./admission.cjs');

const isCopilot = a => ['copilot', 'copilot-swe-agent[bot]'].includes(String(a?.login || '').toLowerCase());
const SESSION = { copilot_work_started: 'started', copilot_work_finished: 'finished' };

// Explicit "Unit: #N" wins; otherwise the lowest referenced unit issue number.
function unitOf(body, unitNumbers) {
  const text = String(body || '');
  const explicit = text.match(/^Unit:\s*#(\d+)\s*$/m);
  if (explicit && unitNumbers.has(Number(explicit[1]))) return Number(explicit[1]);
  const refs = [...text.matchAll(/(?:#|\/issues\/)(\d+)\b/g)].map(m => Number(m[1])).filter(n => unitNumbers.has(n));
  return refs.length ? Math.min(...refs) : null;
}

async function sessionsFor(github, owner, repo, issue_number) {
  const events = await github.paginate(github.rest.issues.listEventsForTimeline,
    { owner, repo, issue_number, per_page: 100 });
  return events.filter(e => SESSION[e.event]).map(e => ({ event: SESSION[e.event], at: Date.parse(e.created_at) }));
}

async function loadUnits({ github, owner, repo, now = Date.now() }) {
  const open = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', per_page: 100 });
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  const unitIssues = open.filter(i => !i.pull_request &&
    (String(i.title).startsWith('[Arcana Queue]') || (i.assignees || []).some(isCopilot)));
  const numbers = new Set(unitIssues.map(i => i.number));
  const prsByUnit = new Map();
  for (const pr of prs) {
    const n = unitOf(pr.body, numbers);
    if (n) prsByUnit.set(n, [...(prsByUnit.get(n) || []), pr]);
  }
  const units = [];
  for (const issue of unitIssues) {
    const comments = await github.paginate(github.rest.issues.listComments,
      { owner, repo, issue_number: issue.number, per_page: 100 });
    const sessions = await sessionsFor(github, owner, repo, issue.number);
    for (const pr of prsByUnit.get(issue.number) || []) sessions.push(...await sessionsFor(github, owner, repo, pr.number));
    const raw = { number: issue.number, title: issue.title, labels: (issue.labels || []).map(l => l.name || l),
      assignedToCopilot: (issue.assignees || []).some(isCopilot), ledger: parseLedger(comments), sessions };
    units.push({ raw, derived: deriveUnit(raw, now), prs: prsByUnit.get(issue.number) || [] });
  }
  return { units, prsByUnit, numbers };
}

module.exports = { unitOf, loadUnits, isCopilot };
