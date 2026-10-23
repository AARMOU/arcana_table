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