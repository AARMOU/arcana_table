'use strict';
// See .github/workflows/arcana-certification-backfill.yml. Re-runs CI only; certification
// itself still happens in the trusted independent certification workflow.
const P = require('./policy.cjs');
const { cardPr } = require('./premerge-plan.cjs');

const TRIGGERS = ['Arcana Cardkit', 'Arcana Development CI'];
const MAX_RERUNS = 6;
// A CI run that failed for infrastructure reasons (API quota, runner loss) would otherwise
// block its card until a manual re-run. Retry failed jobs once; a second failure is real.
const RETRYABLE = ['failure', 'timed_out'];

async function backfill({ github, owner, repo, root = '.', scheduled = false, report = false }) {
  const manifest = P.readManifest(root + '/certification/suites.json');
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  const out = [];
  if (report) {
    const list = await missingSuites({ github, owner, repo, manifest, prs });
    out.push({ missingSuites: list.map(m => m.pr), ...(await reportMissingSuites({ github, owner, repo, list })) });
  }
  for (const suite of manifest.suites) {
    if (out.filter(r => r.rerun).length >= MAX_RERUNS) break;
    const pr = cardPr(manifest, suite, prs);
    if (!pr) continue;
    const sha = pr.head.sha;
    const checks = await github.paginate(github.rest.checks.listForRef, { owner, repo, ref: sha, per_page: 100 });
    // A failed certification is retried: the merged change may be the corrected suite.
    if (checks.some(c => c.name === P.CHECK_NAME && c.head_sha === sha && c.app?.slug === 'github-actions' &&
        c.conclusion === 'success')) continue;
    const runs = (await github.rest.actions.listWorkflowRunsForRepo({ owner, repo, head_sha: sha, event: 'pull_request', per_page: 100 }))
      .data.workflow_runs || [];
    const run = runs.filter(r => TRIGGERS.includes(r.name) && r.head_sha === sha && r.conclusion === 'success')
      .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0];
    if (!run) {
      const failed = runs.filter(r => TRIGGERS.includes(r.name) && r.head_sha === sha && RETRYABLE.includes(r.conclusion))
        .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0];
      if (failed && (failed.run_attempt || 1) < 2) {
        await github.rest.actions.reRunWorkflowFailedJobs({ owner, repo, run_id: failed.id });
        out.push({ suite: suite.key, pr: pr.number, rerun: true, run: failed.id, workflow: failed.name, retry: 'failed-jobs' });
        continue;
      }
      out.push({ suite: suite.key, pr: pr.number, rerun: false, reason: failed ? 'CI failed after one retry' : 'no successful CI run on head' });
      continue;
    }
    // Scheduled runs only retry failures; re-running green CI hourly could loop on a failing suite.
    if (scheduled) continue;
    await github.rest.actions.reRunWorkflow({ owner, repo, run_id: run.id });
    out.push({ suite: suite.key, pr: pr.number, rerun: true, run: run.id, workflow: run.name });
  }
  return out;
}

// A Copilot card PR with card code but no registered suite can never be certified, so it would
// wait silently. List them (read-only) and route them to the suite authors on #81.
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const CARD_SOURCE = /^android\/custom-cards\/src\/main\/.+\/cards\/[^/]+\.kt$/;
const MARKER = '<!-- arcana-missing-certification-suites-v1 -->';
const MAX_LISTED = 25;

async function missingSuites({ github, owner, repo, manifest, prs }) {
  const covered = new Set(manifest.suites.map(s => s.issue));
  const out = [];
  for (const pr of prs) {
    if (out.length >= MAX_LISTED) break;
    if (!COPILOT.has(String(pr.user?.login || '').toLowerCase()) || pr.base?.ref !== manifest.base) continue;
    const issue = P.issueFromBody(pr.body);
    if (!issue || covered.has(issue)) continue;
    const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 });
    if (!files.some(f => CARD_SOURCE.test(f.filename))) continue;
    out.push({ pr: pr.number, issue, title: pr.title });
  }
  return out.sort((a, b) => a.pr - b.pr);
}

const missingBody = list => [MARKER, '### 🤖 Certification suites needed (automated, hourly)', '',
  list.length
    ? 'These Copilot card PRs contain card code but no registered certification suite, so they cannot be certified, verified or integrated. **Suite authors (Work GPT / Goose):** add `certification/suites/<key>.json` + test, as in #480. The backfill then re-runs CI and certification.'
    : 'Every open Copilot card PR with card code has a registered certification suite.',
  '', ...list.map(m => `- #${m.pr} (queue #${m.issue}): ${m.title.replace(/[\r\n]+/g, ' ').slice(0, 100)}`), '',
  '_Read-only detection by `certification/backfill.cjs`; posted only when this list changes. No dispatch, labels or paid work._'].join('\n');

// Posts a new note only when the list differs from the latest note on the newest page of #81
// (which Control shows); an unchanged list posts nothing.
async function reportMissingSuites({ github, owner, repo, list, issue = 81 }) {
  const body = missingBody(list);
  const first = await github.rest.issues.listComments({ owner, repo, issue_number: issue, per_page: 100, page: 1 });
  const lastPage = Number((String(first.headers?.link || '').match(/[?&]page=(\d+)[^>]*>;\s*rel="last"/) || [])[1] || 1);
  const page = lastPage > 1 ? (await github.rest.issues.listComments({ owner, repo, issue_number: issue, per_page: 100, page: lastPage })).data : first.data;
  const previous = [...(page || [])].reverse().find(c => c.user?.login === 'github-actions[bot]' && String(c.body || '').startsWith(MARKER));
  if (previous?.body === body) return { posted: false };
  if (!previous && !list.length) return { posted: false };
  await github.rest.issues.createComment({ owner, repo, issue_number: issue, body });
  return { posted: true };
}

module.exports = { backfill, missingSuites, missingBody, reportMissingSuites, MAX_RERUNS, MARKER };
