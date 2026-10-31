'use strict';
// Merges a Copilot review PR that answers a prioritized [Card Work] item (opened by the queue feeder, started by
// the dispatcher) into the integration base, only after a green Arcana Cardkit run on its exact head, and only if it
// is exactly a Cardkit decision for that one card: a reviewedLines entry in verified-support.json or an entry in
// known-blockers.json, plus that card's Oracle cache line equal to the trusted record in the item. Nothing else.
// A wrong review cannot ship a card: the card still needs its own implementation, tests and an independent
// certification suite before integration. See .github/workflows/arcana-review-automerge.yml.
const A = require('./admission.cjs');

const CARDKIT = 'Arcana Cardkit';
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const DATA = 'tools/cardkit/data/';
const ALLOWED = new Set([DATA + 'verified-support.json', DATA + 'known-blockers.json', DATA + 'oracle-cache.jsonl']);
const norm = s => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
// The card-work item a review PR answers: an explicit 'Unit: #N' line, else exactly one closing reference
// ('Fixes/Closes/Resolves #N'; Copilot often writes only that). Every other merge gate still applies.
const unitOf = body => {
  const text = String(body || '');
  const explicit = Number((text.match(/^Unit: #(\d+)\s*$/m) || [])[1]);
  if (explicit) return explicit;
  const refs = [...new Set([...text.matchAll(/\b(?:fix(?:e[sd])?|close[sd]?|resolve[sd]?)\s+#(\d+)\b/gi)].map(m => Number(m[1])))];
  return refs.length === 1 ? refs[0] : null;
};

function cardOf(issueBody) {
  const section = (String(issueBody || '').split(/^## Card\s*$/m)[1] || '').split(/^## /m)[0];
  const names = section.split('\n').filter(l => /^- \S/.test(l)).map(l => l.slice(2).trim());
  return names.length === 1 ? names[0] : null;
}
function oracleOf(issueBody) {
  const line = (String(issueBody || '').match(/^ {4}(\{.*"oracle_text".*\})\s*$/m) || [])[1];
  try { return line ? JSON.parse(line) : null; } catch { return null; }
}
const parse = text => { try { return JSON.parse(text); } catch { return null; } };
const changedKeys = (before = {}, after = {}) => [...new Set([...Object.keys(before), ...Object.keys(after)])]
  .filter(k => JSON.stringify(before[k]) !== JSON.stringify(after[k]));
const jsonl = text => new Map(String(text || '').split('\n').filter(l => l.trim()).map(l => { const d = parse(l); return [norm(d?.name), l]; }));
const sameRecord = (a, b) => !!a && !!b && JSON.stringify(Object.keys(a).sort().map(k => [k, a[k]])) === JSON.stringify(Object.keys(b).sort().map(k => [k, b[k]]));

// Pure: every reason this PR must not merge (empty = merge). contents: { path: { base, head } } file texts.
function mergeBlockers({ run, pr, issue, files, jobs, contents, base }) {
  const r = [];
  const repoName = pr?.base?.repo?.full_name;
  if (run?.name !== CARDKIT || run.event !== 'pull_request' || run.conclusion !== 'success') r.push('cardkit-not-green');
  if (run?.head_repository?.full_name !== repoName || pr?.head?.repo?.full_name !== repoName) r.push('not-same-repository');
  if (!pr || pr.state !== 'open' || !base || pr.base?.ref !== base) r.push('pr-not-open-on-integration-base');
  if (!COPILOT.has(String(pr?.user?.login || '').toLowerCase())) r.push('pr-not-by-copilot');
  if (pr?.head?.sha !== run?.head_sha) r.push('head-moved');
  if (!issue || issue.state !== 'open' || issue.user?.login !== 'github-actions[bot]' || issue.user?.type !== 'Bot' ||
      !String(issue.title || '').startsWith(A.CARD_WORK_TITLE) || !A.reviewActionable(issue.body).ok) r.push('no-open-card-work-item');
  if ((jobs || []).length === 0 || (jobs || []).some(j => !['success', 'skipped'].includes(j.conclusion)) ||
      !(jobs || []).some(j => j.name === 'toolkit' && j.conclusion === 'success')) r.push('cardkit-toolkit-not-executed');
  const names = (files || []).map(f => f.filename);
  if (!names.length || names.some(n => !ALLOWED.has(n)) || (files || []).some(f => f.status !== 'modified')) r.push('files-outside-review-record');
  const card = cardOf(issue?.body), key = norm(card);
  if (!card) return [...r, 'card-unknown'];
  const vs = { base: parse(contents?.[DATA + 'verified-support.json']?.base), head: parse(contents?.[DATA + 'verified-support.json']?.head) };
  const kb = { base: parse(contents?.[DATA + 'known-blockers.json']?.base), head: parse(contents?.[DATA + 'known-blockers.json']?.head) };
  let decided = false;
  if (names.includes(DATA + 'verified-support.json')) {
    const keys = vs.base && vs.head ? changedKeys(vs.base.cards, vs.head.cards) : null;
    const entry = keys?.length === 1 && norm(keys[0]) === key ? vs.head.cards[keys[0]] : null;
    const otherTopLevel = vs.base && vs.head && changedKeys({ ...vs.base, cards: 0 }, { ...vs.head, cards: 0 }).length;
    if (!entry || otherTopLevel || !Array.isArray(entry.reviewedLines) || !entry.reviewedLines.length ||
        entry.reviewedLines.some(l => !l?.line || !Array.isArray(l.primitives) || !l.primitives.length) ||
        !Array.isArray(entry.references) || !entry.references.length || !/^[a-f0-9]{40}$/.test(String(entry.baseSha || '')) ||
        !/^[a-f0-9]{64}$/.test(String(entry.oracleHash || '')) || !/^[a-f0-9]{64}$/.test(String(entry.runtimeHash || '')))
      r.push('verified-support-not-exactly-this-card');
    else decided = true;
  }
  if (names.includes(DATA + 'known-blockers.json')) {
    const keys = kb.base && kb.head ? changedKeys(kb.base.cards, kb.head.cards) : null;
    const other = kb.base && kb.head && changedKeys({ ...kb.base, cards: 0 }, { ...kb.head, cards: 0 }).length;
    if (!keys || keys.length !== 1 || norm(keys[0]) !== key || other || !kb.head.cards[keys[0]]?.reason) r.push('blocker-not-exactly-this-card');
    else decided = true;
  }
  if (!decided) r.push('no-review-decision');
  if (names.includes(DATA + 'oracle-cache.jsonl')) {
    const before = jsonl(contents?.[DATA + 'oracle-cache.jsonl']?.base), after = jsonl(contents?.[DATA + 'oracle-cache.jsonl']?.head);
    const changed = changedKeys(Object.fromEntries(before), Object.fromEntries(after));
    if (changed.length !== 1 || changed[0] !== key || !sameRecord(parse(after.get(key)), oracleOf(issue?.body)))
      r.push('oracle-cache-not-the-trusted-record');
  }
  return r;
}

async function text(github, owner, repo, path, ref) {
  try {
    const f = (await github.rest.repos.getContent({ owner, repo, path, ref })).data;
    return Buffer.from(f.content, 'base64').toString('utf8');
  } catch { return null; }
}

async function mergeReviewPr({ github, writer = github, owner, repo, run, base, core }) {
  const pr = (run.pull_requests || []).length
    ? (await github.rest.pulls.get({ owner, repo, pull_number: run.pull_requests[0].number })).data
    : (await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', head: `${owner}:${run.head_branch}`, per_page: 100 }))[0];
  if (!pr) return { merged: false, reason: 'no-open-pr' };
  const n = unitOf(pr.body);
  const issue = n ? (await github.rest.issues.get({ owner, repo, issue_number: n })).data : null;
  // Only review PRs answering a card-work item are ours; everything else is left alone, silently.
  if (!String(issue?.title || '').startsWith(A.CARD_WORK_TITLE)) return { merged: false, pr: pr.number, reason: 'not-a-review-pr' };
  const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: pr.number, per_page: 100 });
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, { owner, repo, run_id: run.id, per_page: 100 });
  const contents = {};
  for (const f of files.filter(f => ALLOWED.has(f.filename)))
    contents[f.filename] = { base: await text(github, owner, repo, f.filename, pr.base.sha), head: await text(github, owner, repo, f.filename, run.head_sha) };
  const blockers = mergeBlockers({ run, pr, issue, files, jobs, contents, base });
  if (blockers.length) { core?.warning(`Review PR #${pr.number} not merged: ${blockers.join(', ')}`); return { merged: false, pr: pr.number, blockers }; }
  try {
    if (pr.draft) {
      const res = await writer.graphql('mutation($id: ID!) { markPullRequestReadyForReview(input: {pullRequestId: $id}) { pullRequest { isDraft } } }', { id: pr.node_id });
      if (res?.markPullRequestReadyForReview?.pullRequest?.isDraft !== false) throw Error('GitHub did not confirm ready-for-review');
    }
    await writer.rest.pulls.merge({ owner, repo, pull_number: pr.number, sha: run.head_sha, merge_method: 'squash' });
  } catch (error) {
    const why = String(error?.message || error).replace(/[@\r\n]/g, ' ').slice(0, 200);
    core?.warning(`Review PR #${pr.number} is green but could not be merged automatically: ${why}`);
    return { merged: false, pr: pr.number, reason: 'merge-refused', why };
  }
  return { merged: true, pr: pr.number, item: n, card: cardOf(issue.body) };
}

module.exports = { mergeBlockers, mergeReviewPr, cardOf, oracleOf, unitOf, CARDKIT };
