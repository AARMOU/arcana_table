# Arcana Control — mobile dashboard (read-only v0.1)

This is a **public read-only** dashboard for the public `AARMOU/arcana_table` GitHub repository. Designed for an Android Fold and ordinary phone browsers. It is intentionally self-contained with no package installation, backend credentials, third-party assets, cookies or analytics.

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

**Submission remains owner-controlled:** the button opens a GitHub new-issue form with a prefilled `[Card Request]` title and selected cards. A signed-in owner submits it on GitHub. This is distinct from an `[Arcana Queue]` work unit and is not automatic worker dispatch, credit spend or authorization-label application. A later engineering triage must check open PR ownership, Oracle behavior, engine blockers and executable regressions before creating any dispatchable unit.

The card scanner JSON and public GitHub issue feeds are not service-worker cached; if offline, the installed app displays its shell and lets the owner keep a draft or add manual entries, but cannot claim current coverage.

## Credit Budget: account-owner adjustment after a purchase

The **Credit Budget** screen is reachable at `/control/credits.html`. It locally calculates the updated `ARCANA_AI_CREDIT_BUDGET` amount from confirmed *included* monthly AI credits plus any extra purchased credits confirmed by GitHub to be usable in that cycle. The new **total**, not the purchase delta, must be entered in the authenticated [GitHub Actions repository variables](https://github.com/AARMOU/arcana_table/settings/variables/actions). The page can copy the calculated value, and links to GitHub billing for confirmation and the Billing Watch for verification.

This page **does not** call the GitHub write API, handle credentials, purchase credits, read personal billing, change dispatcher readiness, or launch agents. Exact quantities remain in the device's form and are not published in the public website, issue comments, or browser storage. The existing private, authenticated billing feed and capacity/reserve safeguards stay authoritative. Premium requests, GitHub Actions minutes and ChatGPT usage are separate units.

Tests: `node --test control/dashboard.test.cjs control/card-queue.test.cjs`. Merging and deploying this PR does not change the Android game APK or release configuration.

## Dual card sources (Deck Needs and Argentum Library)

**Deck Needs** still uses the existing `tools/deck-gap-audit.cjs` report and the current 19 deck manifests. It shows unique scanned deck card names (the former 1,196 figure), unique missing index names, and front-face-only/DFA name-review candidates **separately**. An indexed deck card is not guaranteed playable; "not indexed" means missing from the two current index files, not proof the engine has no equivalent.

**Argentum Library** uses the *same integration-branch exact HEAD* via a new **read-only catalog exporter**, `tools/argentum-catalog.cjs`. It deduplicates names from `all-cards.index.tsv` and `custom-cards.index.tsv`, excludes token-only names by default and lists indexed card/basic names **even when none of the scanned decks contains them**. The library page supports searching, indexed names outside the scanned decks, and incremental 100-row pagination without creating thousands of DOM elements. It **cannot** infer that every indexed card has executable, tested Oracle rules. Selecting names from either view creates the same owner-reviewed GitHub request, never a paid worker assignment.

The Pages workflow builds `card-gaps.json` and `argentum-catalog.json` from the same integration-branch checkout and source SHA; neither JSON file is cached by the service worker. New integration changes require a new Pages deployment (manual site workflow dispatch is available). No importer, card implementation, Android game APK, billing or dispatcher behavior changes.

Tests: `node --test control/dashboard.test.cjs control/card-queue.test.cjs tools/tests/argentum-catalog.test.cjs`.
