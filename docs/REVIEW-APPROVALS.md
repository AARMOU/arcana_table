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
