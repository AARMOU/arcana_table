# Arcana Copilot billing collector / proactive alerts

## Owner authorization (GitHub web UI only; never paste tokens into chat)

1. Create a fine-grained personal GitHub token under the AARMOU account with user **Plan: read** permission only. This collector is for a personally billed Copilot plan; an organization-paid plan needs a different endpoint and authorization.
2. Add the token as Actions secret **COPILOT_BILLING_READ_TOKEN** in https://github.com/AARMOU/arcana_table/settings/secrets/actions .
3. Set Actions repository variable **ARCANA_PREMIUM_REQUEST_BUDGET** to the verified monthly *premium request* allowance (positive integer) in https://github.com/AARMOU/arcana_table/settings/variables/actions . This must NOT be AI credits, dollars, Actions minutes, ChatGPT usage, or a guessed allowance. Optional variable **ARCANA_REQUEST_RESERVE** (number of requests) defaults to 10% of allowance, added before threshold calculations to cover billing lag/in-flight work conservatively.
4. Manually run "Arcana Copilot Billing Watch" in GitHub Actions and verify live data. If no personal premium SKU appears or API returns 403, do not set readiness; check whether Copilot is organization-billed.
5. Subscribe to GitHub issue #78 and enable @mention email/mobile notifications. The watch posts @AARMOU on stage changes (50% warn, 75% throttle, 90% pause, missing billing -> block, and recovery) but not every 30-minute check. Public issue comments contain **levels only, not private usage counts or credentials**.
6. Leave **ARCANA_AUTOMATION_READY** unset until actual API reads, billing-lag buffer, capacity/claims, exact-head CI and one controlled worker pickup are verified. The collector never changes readiness on its own.

## Behavior and limitations

- The scheduled billing watch runs twice hourly, independently of the dispatcher. Each dispatch or CI repair fetches fresh billing data instead of trusting an unverified repository variable. Any billing error fails closed.
- Data: monthly personal **Copilot Premium Request** SKU, **requests** unit, **grossQuantity** plus default 10% reserve. AI credits and ChatGPT/OpenAI allowance remain separate and unmeasured. GitHub Actions consumption requires a separately verified source.
- GitHub usage reports can lag ongoing worker charges: throttling cannot retroactively stop running agents and is not a guaranteed spending hard cap. Ongoing workers and claims must be reconciled separately.
- GitHub @mention delivery depends on account notification settings; scheduled GitHub Actions can be delayed. No repository secrets or user billing amounts are published.

Source: https://docs.github.com/en/rest/billing/usage
