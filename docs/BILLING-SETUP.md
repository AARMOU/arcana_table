# Arcana Table Copilot AI credit billing setup

Copilot Pro October 2026: screenshot confirms 1,500 monthly included AI credits (471 used on October 23). Standard Copilot billing changed from premium requests to AI credits in June 2026.

1. Create GitHub fine-grained personal token with user permission Plan: Read: https://github.com/settings/personal-access-tokens/new . Never paste token in chat.
2. Add Actions repository secret COPILOT_BILLING_READ_TOKEN: https://github.com/AARMOU/arcana_table/settings/secrets/actions .
3. Add Actions repository variable ARCANA_AI_CREDIT_BUDGET with value 1500: https://github.com/AARMOU/arcana_table/settings/variables/actions . Do NOT use old ARCANA_PREMIUM_REQUEST_BUDGET.
4. Optional ARCANA_AI_CREDIT_RESERVE sets number of extra credits reserved for in-flight charges. Default = 10% allowance (150 credits).
5. Run Arcana Copilot Billing Watch manually: https://github.com/AARMOU/arcana_table/actions/workflows/arcana-copilot-billing-watch.yml . Confirm a verified normal/warn/slow/paused response. On 403, unknown or empty personal bill, leave workers paused.
6. Subscribe to https://github.com/AARMOU/arcana_table/issues/78 and enable GitHub mention notifications to receive level-change alerts.
7. Keep ARCANA_AUTOMATION_READY unset until live billing data, consumption lag, issue claims, exact CI and controlled pickup are verified. The monitor does NOT enable it.

The personal endpoint is GET https://api.github.com/users/AARMOU/settings/billing/ai_credit/usage with year/month. GitHub user Plan: Read required. Documented response product Copilot AI Credits, sku AI Credit, unitType ai-credits and grossQuantity. Wrong units, missing SKU, stale/invalid reports or unauthorized access block new discretionary work. Request counts, dollar amounts, GitHub Actions and ChatGPT usage are independent and not included.

Budget thresholds are 50% warning, 75% slowdown, 90% pause. Reserve causes early warning, not exact dashboard percent. Reporting can lag; already-running agents are not forcibly stopped, so no guaranteed hard cap.

Source: https://docs.github.com/en/rest/billing/usage ; https://docs.github.com/en/copilot/reference/copilot-billing/request-based-billing-legacy/what-changed-with-billing

## Status, freshness and pending usage (added 2026-10-27, issue #81)

- **Where to look:** Arcana Control → Safeguards (`https://aarmou.github.io/arcana_table/control/#safety`). It reads two status comments that `github-actions[bot]` keeps edited in place on issue #78 (`arcana-control-status-v1:billing` from Billing Watch, `arcana-control-status-v1:dispatcher` from the dispatcher). They show the usage **level** (normal / warn / slow / paused / unknown, already public through the #78 alerts), the time of the last successful billing check, the latest failure reason and the owner's next step. **No credit counts, allowance, reserve size or percentages are published** — the repository, its issues and its Actions job summaries are public. Exact figures: https://github.com/settings/billing (signed in as AARMOU).
- **Display only:** admission never reads the status comments. Every dispatch or repair decision makes its own authenticated billing read.
- **Freshness:** GitHub runs the 30-minute schedules only every few hours (observed 3.5–5 h gaps in October 2026). Billing Watch can be run manually at any time; the dispatcher now also refreshes billing status on every run and starts immediately when an owner applies `arcana:authorize-once`, `wu:resolve-interrupted` or `wu:halted`.
- **Pending (not yet billed) usage:** on top of authenticated usage and the 10% reserve, admission reserves `ARCANA_EST_CREDITS_PER_SESSION` (default 60) for every unit holding a slot and for every unit whose Copilot session finished within `ARCANA_BILLING_LAG_HOURS` (optional variable, default 24, 0–72, 0 disables), plus `ARCANA_EST_CREDITS_PER_REPAIR` (default 30) per pending repair. Estimates only ever add to authenticated usage.
- **Retired variable:** `ARCANA_PREMIUM_REQUEST_BUDGET` is ignored. If it is still set, status shows a note; delete it at https://github.com/AARMOU/arcana_table/settings/variables/actions .
- **Token type:** the configured token has authenticated successfully since 2026-10-23 (alerts moved unknown → normal → warn on #78). GitHub's REST reference lists `GET /users/{username}/settings/billing/ai_credit/usage`; GitHub's usage-reporting tutorial says billing usage endpoints do not support fine-grained tokens. If a replacement fine-grained token returns 403, use a classic personal access token from the AARMOU account instead and keep it read-only in purpose.
- **Failure reasons** are listed with their exact owner action in `scripts/billing-monitor.cjs` (`billingAction`). Transient GitHub 5xx errors and timeouts are retried once; 4xx answers are not retried.
