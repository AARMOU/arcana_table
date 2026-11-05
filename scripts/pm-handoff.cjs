'use strict';
// Bounded inbox-to-inbox review relay. No review approval, worker launch, merge,
// deployment or paid action. Runs only in the existing scheduled observer.
const SOURCE_ISSUE = 949, GOOSE_INBOX = 950;
const MAX_AGE_MS = 48 * 60 * 60 * 1000, MAX_PER_RUN = 3;
const FORMAT = /<!-- arcana-handoff-v1 to=goose kind=pr-review pr=([1-9]\d{0,7}) sha=([a-f0-9]{40}) -->/g;
const RECEIPT_PREFIX = '<!-- arcana-handoff-relay-v1 ';

function requestsFrom(comment, { owner, nowMs }) {
  if (String(comment?.user?.login || '').toLowerCase() !== owner.toLowerCase() ||
      comment?.author_association !== 'OWNER' ||
      !Number.isSafeInteger(comment.id) || comment.id <= 0) return [];
  const createdAt = Date.parse(comment.created_at);
  if (!Number.isFinite(createdAt) || createdAt > nowMs + 5 * 60 * 1000 ||
      nowMs - createdAt < 0 || nowMs - createdAt > MAX_AGE_MS) return [];
  return [...String(comment.body || '').slice(0, 10000).matchAll(FORMAT)]
    .map(m => ({ pr: Number(m[1]), sha: m[2], commentId: comment.id }));
}

function marker(pr, sha) { return RECEIPT_PREFIX + 'pr=' + pr + ' sha=' + sha + ' -->'; }

async function allComments(github, owner, repo, issue_number) {
  // Only small dedicated worker inboxes, NEVER the giant #81.
  // Reject incomplete scans rather than duplicating old assignments.
  const rows = [];
  await github.paginate(github.rest.issues.listComments,
    { owner, repo, issue_number, per_page: 100 }, response => {
      const chunk = response.data || [];
      if (rows.length + chunk.length >= 500)
        throw Error('Inbox #' + issue_number + ' exceeds safe scan bound');
      rows.push(...chunk);
      return [];
    });
  return rows;
}

async function relayReviewHandoffs({ github, owner, repo, core, now = () => Date.now() }) {
  const nowMs = now();
  const source = await allComments(github, owner, repo, SOURCE_ISSUE);
  const target = await allComments(github, owner, repo, GOOSE_INBOX);
  const existing = new Set();
  for (const c of target) {
    // Ignore forged receipts from unrelated accounts in this public inbox.
    if (String(c?.user?.login || '').toLowerCase() !== owner.toLowerCase() ||
        c?.author_association !== 'OWNER') continue;
    const match = /^<!-- arcana-handoff-relay-v1 pr=([1-9]\d{0,7}) sha=([a-f0-9]{40}) -->/.exec(String(c.body || ''));
    if (match) existing.add(marker(Number(match[1]), match[2]));
  }
  const candidates = source.slice().reverse().flatMap(c => requestsFrom(c, { owner, nowMs }));
  const seen = new Set();
  let sent = 0, skipped = 0;
  for (const x of candidates) {
    const key = marker(x.pr, x.sha);
    if (seen.has(key) || existing.has(key)) { skipped++; continue; }
    seen.add(key);
    if (sent >= MAX_PER_RUN) break;
    const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: x.pr });
    if (pr?.state !== 'open' || pr.head?.sha !== x.sha ||
        String(pr.base?.repo?.full_name || '').toLowerCase() !== (owner + '/' + repo).toLowerCase()) {
      skipped++;
      continue;
    }
    const body = key + '\n**For Goose — PR review ready (automated handoff, not approval).**\n' +
      '[PR #' + x.pr + '](https://github.com/' + owner + '/' + repo + '/pull/' + x.pr + ') at exact head \`' + x.sha + '\`. ' +
      'Source: [worker inbox #949 comment](https://github.com/' + owner + '/' + repo + '/issues/949#issuecomment-' + x.commentId + ').\n\n' +
      'Review the actual diff and exact-head checks; report APPROVE / CHANGES NEEDED. ' +
      'This does not assert passing tests, start another worker, merge, deploy, or approve spending.';
    await github.rest.issues.createComment({ owner, repo, issue_number: GOOSE_INBOX, body });
    existing.add(key);
    sent++;
    core?.info?.('Relayed PR #' + x.pr + ' to Goose inbox at ' + x.sha.slice(0, 12));
  }
  return { sent, skipped, considered: candidates.length };
}
module.exports = { relayReviewHandoffs, requestsFrom, marker, allComments, MAX_PER_RUN };
