# Card review approvals in Arcana Control

Arcana Control's Overview has **Cards awaiting your review**. Tap a card to see its Oracle rules, the abilities Copilot was asked to implement, the automated checks on the exact commit, independent-certification status and open problems. Then tap **Verify / Approve** or **Request Fix**.

## How it works

- **Data:** the queue feeder publishes the public #78 `review` status every 15 minutes (`scripts/review-inbox.cjs`).
- **Decisions:** `control/review-worker` (Cloudflare Worker). You sign in with GitHub, and only the repository owner account is accepted. Your decision is recorded as a real GitHub review **by you**, pinned to the reviewed commit. The dashboard never holds a GitHub token, only the Worker's encrypted session, which expires after 12 hours.
- **Safety:**
  - A newer commit is refused, so you review again.
  - Repeating a decision is a no-op.
  - Approve is refused unless all checks pass.
- **Request Fix:** the review never mentions Copilot. The card's unit gets `arcana:owner-fix-requested`, which wakes the dispatcher. The dispatcher runs the same repair admission as CI repairs: master switch, `ARCANA_REPAIR_MODE=request`, capacity, two-repair ceiling, fresh billing with the 10% reserve. Only then does it ask Copilot to repair. Otherwise it records an observation and starts nothing.
- **Statuses:** Awaiting Review, Checks Running, Checks Failed, Independently Certified, Owner Approved, Changes Requested, Integrated.
  - A new commit resets approval.
  - "Independently Certified" appears only when a check whose name contains `certif` passes on that commit. That is the hook for the independent certification gate.
- **Approval is not** merge, build, signing or release approval.

## One-time owner setup

1. Create a GitHub OAuth App:
   - Settings → Developer settings → OAuth Apps → New.
   - Homepage: `https://aarmou.github.io/arcana_table/control/`.
   - Callback: `https://arcana-review.arcanamtg.workers.dev/auth/callback`.
2. In the repository, under Settings → Secrets and variables → Actions:
   - Add the variable `REVIEW_OAUTH_CLIENT_ID`.
   - Add the secrets `REVIEW_OAUTH_CLIENT_SECRET` and `REVIEW_SESSION_SECRET` (any random 32+ character string).
3. Run **Deploy Arcana Control review Worker (manual only)** on `main` with `DEPLOY_REVIEW_WORKER`.
4. Open Arcana Control, tap a card, then **Sign in with GitHub (owner)** once.

## GitHub public-feed caching

Arcana Control's read-only overview and card queue query the allowlisted
`/v1/public/github?path=...` endpoint on the review Worker, not the viewer's
unauthenticated GitHub REST API allowance. Native Cloudflare Workers Caching (Wrangler >=4.69, enabled on workers.dev) caches each allowlisted
public endpoint for 15 minutes. Cloudflare can serve an expired response during
background revalidation for up to 1 hour, or when the Worker returns a 5xx for
up to 24 hours. The dashboard labels data older than 15 minutes as stale. If no cache exists, the dashboard says
the feed is unavailable rather than fabricating data.

The cache contains only publicly visible repository API responses. It does not
use the owner's OAuth session or return OAuth tokens; private billing amounts
never enter the public cache. Cached statuses are informational **only**:
owner reviews still use the authenticated `/v1/decision` path, and dispatch/
admission decisions still use their existing authenticated, fail-closed checks.
Workers Caching uses an upper/lower tier and is available on workers.dev;
the older caches.default API does not reliably cache there and must not be used.
A newly deployed Worker may need a successful GitHub response to warm its new
cache version. All OAuth redirects, owner decision responses and errors explicitly
carry Cache-Control: no-store. Workers Cache can bill cached requests at the
standard Cloudflare Workers request rate; review that operational cost before deployment.

The worker is deployed **manually only**, via the owner-confirmed deployment
workflow; merging a site change does not deploy the Worker. Deploy the Worker
from the merged commit before expecting the site to use its cached endpoints.
