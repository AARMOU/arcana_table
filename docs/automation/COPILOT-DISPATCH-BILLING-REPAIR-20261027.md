# Copilot dispatch and billing repair — 2026-10-27

**Author:** Claude · **Coordination:** issue #81 · **Baseline:** `main@1523e9bd61677aba08bd0a68a472621c584338dc`
Nothing here enables paid work. `ARCANA_AUTOMATION_READY`, dispatch/repair modes, worker limits and the 50/75/90 + 10% reserve policy are unchanged.

## Evidence gathered (read-only)

| Check | Result |
|---|---|
| Billing Watch runs | All green; latest 2026-10-27 23:43 UTC (run 40604088463) with no "billing unavailable" annotation. #78 alerts: `unknown` (10-23 19:13) → `normal` (10-23 19:35) → `warn` (10-24 16:55), none since. The configured token and `ARCANA_AI_CREDIT_BUDGET` authenticate and parse. |
| Official endpoint | `GET /users/{username}/settings/billing/ai_credit/usage` in GitHub's OpenAPI description (`github/rest-api-description`, 2026-03-10 version): product `Copilot AI Credits`, unit `ai-credits`, `grossQuantity`. Matches the collector. Separate `premium_request/usage` endpoint exists and is not mixed in. |
| Schedule cadence | Dispatcher (`*/30`) actually ran 2026-10-27 at 05:27, 10:52, 16:21, 20:53, 10-28 00:22 UTC; Billing Watch similar. GitHub delays/drops scheduled runs. |
| One-shot latency | #245 labeled 00:08 UTC, dispatched by the next scheduled run at 01:37 (89 min). |
| Live units (local read-only `loadUnits`) | 18 units: 12 `review`, 6 `queued`, **0 holding slots**. Capacity was not the blocker. |
| Integration base | `gpt/arcana-integration-next@9dd5fc9c` (weekly-upgrade merge, 10-26 16:31). Its only "Arcana Development CI" run, 40359905589, is `action_required` (pull_request started by `github-actions[bot]`). No push run exists: pushes made with `GITHUB_TOKEN` do not start workflows. |

## Confirmed root causes

1. **Dispatch blocked since 2026-10-26 16:31 UTC by `ci-not-ready`.** The base head has no successful Development CI run; the run that exists waits for approval. The dispatcher returned `ci-not-ready` with no explanation outside raw job logs.
2. **Head-of-line blocking from a heading parser bug.** In `normal` mode the lowest-numbered eligible unit is the candidate. #127 is complete but titles its section `## Acceptance — complete rules behavior, not registration-only`; the parser keyed sections by the full heading, so it was always "not actionable". Every run would then have denied #127 and never considered later units.
3. **Decisions were invisible.** Dispatcher outcomes, billing figures and blockers lived only in job logs/step summaries; Arcana Control said "No verified billing feed". Billing Watch published only a level, and only on change.
4. **Owner actions waited for unreliable schedules** (see cadence above).
5. **Stuck slots had no release path.** A `pickup-timeout` (e.g. an assignment API `error`) held a slot until the issue was closed, and capacity denials did not say which unit held it. A Copilot session that never reported finishing held its slot as `working` forever.
6. **Pending usage gap.** Credits of a just-finished session can be billed late; admission counted only slot-holding units on top of the flat reserve.
7. **Billing robustness.** No retry for transient 5xx/timeouts; unparseable JSON surfaced as the generic `billing-unavailable`; alerts did not say what to do.

Not reproduced: completed workers counted as active (0 slot holders observed), duplicate assignments, router permission failures (router runs green since the #83 fix).

## Repairs

| # | Change | Files |
|---|---|---|
| 1 | `ci-not-ready` now reports the runs GitHub has for the base SHA and the exact owner action (approve run N / run CI manually). | `dispatch-runner.cjs`, `control-status.cjs` |
| 2 | Section headings ignore qualifiers after a dash/colon/parenthesis; normal-mode selection skips incomplete units and reports why. Admission still re-checks actionability. | `admission.cjs`, `dispatch-runner.cjs` |
| 3 | Status comments on #78 (billing, dispatcher), edited in place by `github-actions[bot]`; Arcana Control renders them. Display only — admission never reads them. **Level and freshness only: no credit counts, allowance, reserve size or percentages in comments or job summaries** (public repository). | `control-status.cjs`, `control-workflows.cjs`, `control/index.html` |
| 4 | Dispatcher also runs on `issues: labeled` for `arcana:authorize-once`, `wu:resolve-interrupted`, `wu:halted` (main only, same serialized group). Manual `dry_run` input forces dry-run. | `arcana-card-dispatcher.yml` |
| 5 | `wu:halted` releases a slot held only by `pickup-timeout`/`session-stale`; a real session start takes it back. Sessions open >3 h become `session-stale` (still holding). `copilot_work_finished_failure` (if GitHub emits it) also ends a session. Capacity denials name the holders. | `admission.cjs`, `units.cjs` |
| 6 | Units finished within `ARCANA_BILLING_LAG_HOURS` (default 24) are pending liabilities; the liability breakdown is published. | `admission.cjs` |
| 7 | One retry for 5xx/timeouts; `billing-timeout`, `billing-network-error`, `invalid-billing-response` reasons; every reason maps to an owner/engineer action; legacy `ARCANA_PREMIUM_REQUEST_BUDGET` flagged. | `billing-monitor.cjs` |

## Behaviour change the owner must know

With the parser fix, #127 (The Swarmlord) becomes the first eligible unit. If `ARCANA_AUTOMATION_READY=true`, `ARCANA_DISPATCH_MODE=normal` and the base CI is green, the next run after merge would assign #127. In `authorize-once` mode nothing changes: only the labeled issue is eligible. #172 and #173 remain not actionable (no Cards/Acceptance/Base sections).
