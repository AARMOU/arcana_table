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
