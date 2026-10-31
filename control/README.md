# Arcana Control — mobile status dashboard with owner-gated card work

The dashboard displays **public read-only status** for `AARMOU/arcana_table`, with a single **owner-only card-work authorization** action through the existing encrypted OAuth session and separate Cloudflare Worker. Designed for an Android Fold and ordinary phone browsers. No GitHub tokens or billing secrets are shipped in the PWA.

## All Hands — Check In (owner only)

The Overview action beside Verify and Do It uses the existing encrypted GitHub OAuth owner session. Its `POST /v1/team-checkin` endpoint adds **one coordination-only owner comment on issue #81**, requesting separate status reports from Goosegeta, Claude, and Boneknuckles/GPT Work. The service checks the most recent #81 comments first and reuses a request from the last 30 minutes to prevent accidental spam.

The dashboard `GET /v1/team-checkin` shows the latest request and only properly keyed, trusted-author **self-reported** acknowledgments. It cannot prove agent presence or wake a closed ChatGPT/Claude session. Missing reports remain unconfirmed. Errors fail closed; if an HTTP write is uncertain, inspect #81 before retrying. Neither endpoint launches paid agents, edits billing, grants card authorization, merges, deploys or releases anything.

Deployment sequencing: merge requires peer review and green exact-head checks. The Pages site then publishes the new UI; the independent Cloudflare review Worker must be deployed **only with separate owner approval**. Until both are deployed, the button is not live.

## One-tap work authorization (owner only)

On the Overview screen, queued, genuine [Arcana Queue] issue units have an **I agree. Do it** button. You sign in once with the existing GitHub owner OAuth flow. The button asks the review Worker for the live issue and then, only if it is unchanged and genuinely actionable, posts one scoped label \`arcana:authorize-once\` to **that exact issue**. Duplicate clicks are idempotent. Non-owner requests, stale issues, untrusted submitters, invalid work units, already-assigned cards and conflicting outstanding authorizations fail closed.

**Authorized — waiting** means permission recorded, **NOT** that a worker started. The existing dispatcher remains authoritative for Cardkit feasibility, exact integration base CI, provenance, usage/credit reserve including pending liabilities, active capacity, and one-assignment/two-repair ceilings. A dedicated normal-mode selection guard makes an owner-authorized issue outrank the unrelated lowest-numbered candidate. Ordinary unlabeled normal-mode queueing remains unchanged. Oracle tests, positive and negative runtime scenarios, card registration/export and independent certification remain engineering gates after this authorization. This feature never merges, signs, builds or releases the Android app.

Deployment sequencing: this branch and its Worker endpoint must pass exact-head CI and receive explicit owner merge approval. GitHub Pages automatically updates on a later merge; **Cloudflare review Worker deployment is a separate explicit owner action** via \`.github/workflows/deploy-review-worker.yml\`. Before both are live, the UI is not an operational authorization button.

## Data and limits

On page load and on **Refresh**, the browser reads the public GitHub REST API:

- `GET /repos/AARMOU/arcana_table/pulls?state=open&sort=updated&direction=desc&per_page=100`
- `GET /repos/AARMOU/arcana_table/actions/runs?per_page=30`
- `GET /repos/AARMOU/arcana_table/issues/81/comments?per_page=100&page=1` (also the last page if more than 100 comments)
- `GET /repos/AARMOU/arcana_table/issues/78/comments?per_page=100&page=1` (also the last page) — only `github-actions[bot]` comments starting with `<!-- arcana-control-status-v1:billing -->` or `:dispatcher -->` are read, as JSON, and rendered as text.

PRs are displayed as open/draft, not "currently coding". Running workflows refer only to the latest 30 returned runs. The GitHub REST API can rate-limit anonymous visitors (normally 60 requests/hour per IP); the dashboard gracefully shows unavailable/partial data instead of inventing numbers. The user can manually refresh; no background polling is installed.

**Billing and dispatcher status (since 2026-10-27):** the Safeguards view shows the Copilot AI-credit usage level and band (never credit counts, allowance or percentages — the repository is public; exact figures stay in GitHub's private billing settings), estimated pending usage liabilities, the time of the last successful billing check, dispatcher readiness, the current blocker with its next step, and Copilot work units (running, pending pickup, queued, completed, blocked). These are copies published by the Billing Watch and Dispatcher workflows; they are as fresh as the timestamps shown (GitHub runs those schedules every few hours). Admission never reads them.

**Still not provided:** live ChatGPT or Claude session presence, GitHub Actions minutes, ChatGPT usage, branch protection configuration. These indicators are explicitly unknown and historical notes are labeled as such. Public visitor access is not suitable for viewing private usage data; any future private extension requires authentication, server-side access controls, and explicit authorization.

## Local preview

Open `control/index.html` in any browser, or serve with a static HTTP server. Public GitHub API calls require an internet connection.

## Tests

Run `node --test control/dashboard.test.cjs` from repository root. The 7 no-dependency static checks validate parsing, core views, public GET API endpoints and read-only guardrails.

A separate local browser simulation was executed with mocked GitHub responses at 320px, 393px and 1365px widths: all three views navigated, data populated, no JavaScript exceptions or horizontal overflow. **A successful mock/browser test does not establish real-browser network success**; verify the deployed page against GitHub on a phone.

## Publishing

This folder is only exposed through the existing GitHub Pages site after the website workflow change is merged and GitHub Pages deployment succeeds. Proposed URL: `https://aarmou.github.io/arcana_table/control/` — **do not describe it as live until deployed and checked.** No changes to Android gameplay, billing settings, or paid workers are included.
## Install as an Android app (PWA)

After merging and deploying the PWA files, open `https://aarmou.github.io/arcana_table/control/` in Chrome on Android. Tap **⋮ → Install app** (or **Add to Home screen** when install promotion is unavailable). The launcher entry is **Arcana Control**, separate from the Arcana Table game. It uses its own standalone window and receives website updates without APK rebuilds.

The service worker caches only the dashboard shell and install assets for offline launch. **GitHub data, worker state and billing/status comments are never cached by the worker**; they require a network connection and should remain unknown or show errors if unavailable. It does not grant permission to run workers, alter billing, merge PRs, or change game releases. PWA installation is not a Play Store / signed Android APK release.

PWA tests run alongside existing dashboard checks using `node --test control/dashboard.test.cjs`. Deployment is handled by the existing Pages workflow. Do not claim installability until the merged deployment is verified on an Android browser.

## Card Queue: select and request cards

The **Card Queue** screen is reachable from Arcana Control's desktop and Android PWA navigation or `/control/cards.html`. The existing `tools/deck-gap-audit.cjs` produces a public read-only `card-gaps.json` on site deployment, reading shipped personal Commander decks, six official precon manifests and both card indexes from the current `gpt/arcana-integration-next` source head. A source SHA and scan timestamp appear in the UI. The scanner reports *index presence*, not playable rules certification. The picker filters by deck, coverage and name; selects up to 8 cards per request; allows manual names; and retains only the draft selections in browser local storage. Users can view recently open card work issues.

**Submission remains owner-controlled:** the button opens a GitHub new-issue form with a prefilled `[Card Request]` title and selected cards. A signed-in owner submits it on GitHub. The trusted-main queue feeder gives owner-authored requests priority *only* for cards classified by Cardkit on the exact integration base as SUPPORTED / copilot and unclaimed. For eligible requests it may create one separate, immutable GitHub Actions-bot `[Arcana Queue]` unit on a later feeder run in enabled normal mode. Unsupported, unknown, already-claimed and engine-blocked requests never go into this path; the latest status lists blocked names and reasons. The existing dispatcher independently requires fresh matching Cardkit feasibility, CI, billing reserve and worker capacity before any paid assignment; automatic queue units cannot use a manual feasibility override. A request itself never triggers payment, worker launch or a release. The Card Queue screen displays the latest read-only public feeder status, which may be unavailable or stale.

The card scanner JSON and public GitHub issue feeds are not service-worker cached; if offline, the installed app displays its shell and lets the owner keep a draft or add manual entries, but cannot claim current coverage.

## Credit Budget: account-owner adjustment after a purchase

The **Credit Budget** screen is reachable at `/control/credits.html`. It locally calculates the updated `ARCANA_AI_CREDIT_BUDGET` amount from confirmed *included* monthly AI credits plus any extra purchased credits confirmed by GitHub to be usable in that cycle. The new **total**, not the purchase delta, must be entered in the authenticated [GitHub Actions repository variables](https://github.com/AARMOU/arcana_table/settings/variables/actions). The page can copy the calculated value, and links to GitHub billing for confirmation and the Billing Watch for verification.

This page **does not** call the GitHub write API, handle credentials, purchase credits, read personal billing, change dispatcher readiness, or launch agents. Exact quantities remain in the device's form and are not published in the public website, issue comments, or browser storage. The existing private, authenticated billing feed and capacity/reserve safeguards stay authoritative. Premium requests, GitHub Actions minutes and ChatGPT usage are separate units.

Tests: `node --test control/dashboard.test.cjs control/card-queue.test.cjs`. Merging and deploying this PR does not change the Android game APK or release configuration.

## Mandatory pre-submission checks (Arcana Control)

The Card Queue picker now performs **two read-only checks before enabling Review verified request**: Scryfall exact-name lookup for each selected Magic card (with owner-confirmed canonical suggestions for non-exact entries), and the complete unclaimed Cardkit `SUPPORTED / copilot` allowlist posted by the trusted queue feeder on #78. The feeder status must be published by `github-actions[bot]`, complete, at most six hours old, and match the same exact 40-character integration-head SHA as the deck/library scanner. Any network error, stale/incomplete snapshot, mismatched SHA, invalid/ambiguous name, unknown/blocked/claimed card, or unconfirmed suggestion **blocks the GitHub request button**. All selected card names are sent to Scryfall for verification; there are no tokens, decklists, identities or credentials in those requests.

Cards that require GPT/Claude engine implementation are not sent to Copilot; they may be discussed separately in #321 / #81. The in-browser check is not a security boundary: the existing dispatcher still independently validates Cardkit at its current head, issues, provenance, CI, fresh billing and the 10% reserve. A preliminary `SUPPORTED` result does not certify gameplay correctness or guarantee Copilot success. No static allowlist or browser state is ever allowed to authorize paid work. If the queue feeder has not yet published `eligibleNames`, the UI fails closed until the new status is available.

## Dual card sources (Deck Needs and Argentum Library)

**Deck Needs** still uses the existing `tools/deck-gap-audit.cjs` report and the current 19 deck manifests. It shows unique scanned deck card names (the former 1,196 figure), unique missing index names, and front-face-only/DFA name-review candidates **separately**. An indexed deck card is not guaranteed playable; "not indexed" means missing from the two current index files, not proof the engine has no equivalent.

**Argentum Library** uses the *same integration-branch exact HEAD* via a new **read-only catalog exporter**, `tools/argentum-catalog.cjs`. It deduplicates names from `all-cards.index.tsv` and `custom-cards.index.tsv`, excludes token-only names by default and lists indexed card/basic names **even when none of the scanned decks contains them**. The library page supports searching, indexed names outside the scanned decks, and incremental 100-row pagination without creating thousands of DOM elements. It **cannot** infer that every indexed card has executable, tested Oracle rules. Selecting names from either view creates the same owner-reviewed GitHub request, never a paid worker assignment.

The Pages workflow builds `card-gaps.json` and `argentum-catalog.json` from the same integration-branch checkout and source SHA; neither JSON file is cached by the service worker. New integration changes require a new Pages deployment (manual site workflow dispatch is available). No importer, card implementation, Android game APK, billing or dispatcher behavior changes.

Tests: `node --test control/dashboard.test.cjs control/card-queue.test.cjs tools/tests/argentum-catalog.test.cjs`.

## Current Copilot assignment panel (overview)

On the opening **Overview** screen, the **Current Copilot assignment** card shows the latest evidence-backed card/issue, separate stages for **assigned / pickup unverified**, **PR opened**, **agent running**, and **agent finished**, and links directly to the issue, Copilot PR, and cloud-agent run when available. The card resolves one work unit from the latest trusted dispatcher status snapshot posted by github-actions[bot] to #78: an active worker first, the most recently confirmed assignment second, or a recently finished unit as a fallback. It does not automatically declare that an agent started merely because the dispatcher assigned Copilot.

For that issue, the public browser makes read-only GitHub GET requests to the issue, its timeline, and a Copilot-authored cross-referenced PR (if one exists). For a verified PR it checks **only that PR branch** for a GitHub Actions `Running Copilot cloud agent` run. An agent workflow that finished successfully means the **agent run** finished; it does **not** mean the card is playable, merged or validated. PR titles remain visible so blocker/scaffold PRs are not confused with card completion. Unknown or stale data remains explicitly labeled; no GitHub write capability, billing amount, private token, or worker trigger was added.

This panel refreshes on manual dashboard **Refresh**, as permitted by the public API rate limit, rather than assuming a continuous live connection. Its read-only status is based on the currently published dispatcher report and may lag an assignment until the next dispatcher snapshot.


## Reconciled request ranking (after routing PR #355)

Public app submissions tracked through #321 remain **votes only**: they reorder
verified Copilot-ready candidates, never authorize paid work or create a GPT
engine-work ticket. The owner's distinct, trusted GitHub `[Card Request]`
issues retain priority over public votes. Within that owner queue, a card
requested in more separate owner issues ranks first; repeated lines in one
issue count once. Cardkit and existing no-duplicate, billing, 10% reserve and
CI gates still control actual pickup. The read-only priority panel shows owner
issue counts and public request counts **separately**, with public card
eligibility labels, not a combined count that confuses voting with authority.

This follows PR #355's new outside-precon classification and GPT routing. It
supersedes obsolete PR #354's pre-#355 feeder/workflow changes; they must not be
merged wholesale. Counts are accepted submissions, not unique people or verified
rules certifications. No app-side GitHub token, write action, worker trigger,
merge, APK build or release is added.
