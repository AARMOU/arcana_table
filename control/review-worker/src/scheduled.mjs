// Reliable timer for read-only status workflows. GitHub's own schedule fires only every few
// hours in practice (the review inbox's */15 ran 4 times in its life), so Control's Verify
// report went hours old. Cloudflare Cron Triggers fire on time; each one only starts an
// allowlisted workflow on main. Every gate inside those workflows still applies. Without the
// optional GITHUB_ACTIONS_DISPATCH_TOKEN secret this does nothing.
const GH_REPO_API = 'https://api.github.com/repos/AARMOU/arcana_table';
// cron (must match wrangler.toml [triggers]) -> workflows it starts, with their inputs.
export const SCHEDULE = {
  '*/15 * * * *': [{ workflow: 'arcana-card-review-inbox-refresh.yml', inputs: {} }],
  // scheduled: the backfill then only retries failures and never re-runs green CI.
  '41 * * * *': [{ workflow: 'arcana-certification-backfill.yml', inputs: { scheduled: 'true' } }],
};

export async function runScheduled(cron, { token = '', fetchImpl = fetch } = {}) {
  if (!token) return [{ cron, skipped: 'no-dispatch-token' }];
  const out = [];
  for (const { workflow, inputs } of SCHEDULE[cron] || []) {
    try {
      const r = await fetchImpl(GH_REPO_API + '/actions/workflows/' + workflow + '/dispatches', {
        method: 'POST',
        headers: { 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
          'User-Agent': 'ArcanaControlScheduler/1.0', 'Authorization': 'Bearer ' + token,
          'Content-Type': 'application/json' },
        body: JSON.stringify({ ref: 'main', inputs }),
      });
      out.push({ workflow, status: r.status });
    } catch {
      out.push({ workflow, status: 'network-error' });
    }
  }
  return out;
}
