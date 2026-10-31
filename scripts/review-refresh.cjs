'use strict';
// Arcana Review Refresh: gets finished Copilot review PRs merged without a person and without a paid session.
//
// Review PRs ([Card Work] items, see review-automerge.cjs) all edit the same Cardkit data files, so each merged
// review leaves the other open ones conflicted, and GitHub runs no pull_request workflow (no Arcana Cardkit, hence
// no auto-merge) on a conflicted PR. An engine upgrade also leaves older reviews bound to the previous runtime, and
// an item that was closed and reopened never sees another Cardkit completion. 2026-10-31: nine finished reviews sat
// unmerged for hours this way.
//
// Each pass picks at most ONE review PR and either
//   rebuild - scripts/review_refresh.py re-applies the PR's single-card decision on the current base (restamped onto
//             the adopted runtime by Cardkit's own upgrade check) as a merge commit pushed with the person token, so
//             Arcana Cardkit runs on it and Arcana Review Auto-merge validates and merges it as usual; or
//   rerun   - the PR is already on the current base and green: re-run its Cardkit run so auto-merge sees it again.
// One at a time on purpose: each merge conflicts the rest, and the auto-merge completion starts the next pass.
// Never merges, never spends Copilot credits, never touches a PR that is not exactly a review of an open item.
const A = require('./admission.cjs');
const { unitOf, cardOf, oracleOf, CARDKIT } = require('./review-automerge.cjs');

const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const TRAILER = 'Arcana-Review-Refresh:';
const DATA = 'tools/cardkit/data/';
const MAX_RERUN_ATTEMPT = 2;
const REVIEW_FILES = new Set([DATA + 'verified-support.json', DATA + 'known-blockers.json', DATA + 'oracle-cache.jsonl']);
const priorityOf = issue => Number((String(issue?.body || '').match(/priority:(\d{1,4})/) || [])[1]) || 0;

// Pure. One record per open Copilot PR on the base:
//   { pr, issue, run (latest Arcana Cardkit pull_request run on the exact head, or null), behind (head lacks the base
//     head), refreshedOnto (base SHA a Review Refresh commit at the head merged in, or null) }
// Returns { action: 'rebuild'|'rerun'|null, pr, ..., skipped: [{pr, reason}] }.
function plan(records, baseSha) {
  const skipped = [], rebuild = [], rerun = [];
  for (const r of records) {
    const { pr, issue, run } = r;
    const why = reason => skipped.push({ pr: pr.number, reason });
    if (!issue || issue.state !== 'open' || issue.user?.login !== 'github-actions[bot]' || issue.user?.type !== 'Bot' ||
        !String(issue.title || '').startsWith(A.CARD_WORK_TITLE) || !A.reviewActionable(issue.body).ok) { why('no-open-card-work-item'); continue; }
    if (!cardOf(issue.body) || !oracleOf(issue.body)) { why('item-has-no-card-or-oracle-record'); continue; }
    // A rebuild keeps only the card's data entry; never drop anything else a PR changed.
    if (!r.files?.length || r.files.some(f => !REVIEW_FILES.has(f))) { why('files-outside-review-record'); continue; }
    if (run && run.status !== 'completed') { why('cardkit-running'); continue; }
    if (r.refreshedOnto === baseSha) {
      // Already rebuilt on this exact base: wait for Cardkit, then auto-merge or a repair; never rebuild again.
      if (run?.conclusion === 'success' && (run.run_attempt || 1) < MAX_RERUN_ATTEMPT) rerun.push(r);
      else why(run ? 'refreshed-on-this-base: cardkit ' + run.conclusion : 'refreshed-on-this-base: waiting-for-cardkit');
      continue;
    }
    if (r.behind || !run || pr.mergeable_state === 'dirty') { rebuild.push(r); continue; }
    if (run.conclusion === 'success') {
      if ((run.run_attempt || 1) < MAX_RERUN_ATTEMPT) rerun.push(r); else why('green-but-not-merged: see Arcana Review Auto-merge');
      continue;
    }
    why('cardkit-' + run.conclusion + ' on the current base: left to the repair router');
  }
  const order = (a, b) => priorityOf(b.issue) - priorityOf(a.issue) || a.pr.number - b.pr.number;
  // A rerun is cheaper and closer to merging than a rebuild.
  const pick = rerun.sort(order)[0] || rebuild.sort(order)[0];
  if (!pick) return { action: null, skipped };
  return { action: rerun.includes(pick) ? 'rerun' : 'rebuild', pr: pick.pr.number, issue: pick.issue.number,
    card: cardOf(pick.issue.body), run: pick.run?.id || null, skipped,
    waiting: rerun.length + rebuild.length - 1 };
}

// Base SHA merged in by a Review Refresh commit, from its trailer; null for any other commit.
function refreshedOnto(commit) {
  const m = String(commit?.commit?.message || '').match(new RegExp('^' + TRAILER + ' base=([a-f0-9]{40})\\s*$', 'm'));
  return m && (commit.parents || []).some(p => p.sha === m[1]) ? m[1] : null;
}

async function gather({ github, owner, repo, base, baseSha }) {
  const prs = (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', base, per_page: 100 }))
    .filter(p => COPILOT.has(String(p.user?.login || '').toLowerCase()) && p.head?.repo?.full_name === `${owner}/${repo}`);
  const records = [];
  for (const listed of prs) {
    const n = unitOf(listed.body);
    if (!n) continue;
    const issue = (await github.rest.issues.get({ owner, repo, issue_number: n })).data;
    if (!String(issue.title || '').startsWith(A.CARD_WORK_TITLE)) continue;  // not a review PR: not ours
    const pr = (await github.rest.pulls.get({ owner, repo, pull_number: listed.number })).data;  // mergeable_state
    const runs = (await github.rest.actions.listWorkflowRunsForRepo({ owner, repo, head_sha: pr.head.sha, event: 'pull_request', per_page: 30 }))
      .data.workflow_runs.filter(r => r.name === CARDKIT && r.head_sha === pr.head.sha)
      .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at));
    const cmp = (await github.rest.repos.compareCommitsWithBasehead({ owner, repo, basehead: `${baseSha}...${pr.head.sha}`, per_page: 1 })).data;
    const head = (await github.rest.repos.getCommit({ owner, repo, ref: pr.head.sha, per_page: 1 })).data;
    const files = (await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 })).map(f => f.filename);
    records.push({ pr, issue, files, run: runs[0] || null, behind: cmp.behind_by > 0, refreshedOnto: refreshedOnto(head) });
  }
  return records;
}

async function fileAt(github, owner, repo, path, ref) {
  try {
    const f = (await github.rest.repos.getContent({ owner, repo, path, ref })).data;
    return Buffer.from(f.content, 'base64').toString('utf8');
  } catch (e) { if (e.status === 404) return null; throw e; }
}

// Writes review_refresh.py's inputs for the chosen PR into dir.
async function prepare({ github, owner, repo, pr, issue, dir, fs = require('fs'), path = require('path') }) {
  const files = path.join(dir, 'files');
  fs.mkdirSync(files, { recursive: true });
  let reviewBase = null;
  for (const f of ['verified-support.json', 'known-blockers.json']) {
    for (const [side, ref] of [['base', pr.base.sha], ['head', pr.head.sha]]) {
      const text = await fileAt(github, owner, repo, DATA + f, ref);
      if (text !== null) fs.writeFileSync(path.join(files, f + '.' + side), text);
      if (side === 'head' && f === 'verified-support.json' && text) {
        try { reviewBase = Object.entries(JSON.parse(text).cards || {}).find(([k]) => k === cardOf(issue.body))?.[1]?.baseSha || null; } catch { /* refused later */ }
      }
    }
  }
  fs.writeFileSync(path.join(dir, 'oracle.json'), JSON.stringify(oracleOf(issue.body)));
  fs.writeFileSync(path.join(dir, 'card'), cardOf(issue.body));
  // The engine index the review was made against, for Cardkit's restamp check.
  if (/^[a-f0-9]{40}$/.test(String(reviewBase || ''))) {
    // Without it, a review on an older runtime is refused (never restamped blind).
    const sdk = await fileAt(github, owner, repo, 'docs/agents/generated/sdk-api.json', reviewBase).catch(() => null);
    if (sdk) fs.writeFileSync(path.join(dir, 'previous-sdk.json'), sdk);
  }
}

module.exports = { plan, refreshedOnto, gather, prepare, priorityOf, TRAILER, MAX_RERUN_ATTEMPT };
