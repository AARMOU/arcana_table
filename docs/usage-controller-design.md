> **Superseded** by `docs/automation/ADMISSION-CONTROL-DESIGN.md`. Kept for history; parts below no longer match the code.

# Usage-aware dispatcher rollout

The existing card dispatcher runs every five minutes and can assign two new issues per invocation. Its test_issue option bypasses normal capacity counting. The CI failure router may separately request up to three Copilot repairs per draft PR.

Before enabling more assignments, implement a shared budget gate for both entry points. Track Copilot premium requests, GitHub Actions billing, and ChatGPT usage as separate categories. Do not treat the GitHub Cloud Agent billing SKU as the same as premium requests.

Thresholds for verified consumption relative to allowance: 50 percent warn; 75 percent reduce dispatch; 90 percent block nonessential assignments. Unknown or stale usage should block new discretionary assignments. The test_issue override must not bypass budget checks.

The latest user-provided Copilot snapshot reported 338 of 1500 credits used, but it is not live data. Verify the personal-account billing API and authorization before relying on automated decisions. Do not store credentials in the repository.

Add daily reporting with timestamped usage sources, active jobs, completed PRs, failed checks, and remaining allowances where measurable. Do not claim ChatGPT Plus usage can be polled without a verified interface.

Changes remain isolated. No automatic merges, releases, or production modifications.

## Implemented gates

`scripts/usage-gate.cjs` wraps `decideBudget` for both workflows. Usage comes from the `COPILOT_USAGE_SNAPSHOT` repository variable (`{"used":N,"allowance":N,"measuredAt":ISO}`), which must be refreshed by a verified source; missing, malformed, or stale (over 1 hour) data blocks new assignments.

- Dispatcher: 50% warns, 75% allows one assignment, 90% or unknown allows none. `test_issue` is capped at one and still gated. Its concurrency group serializes launches.
- Repair router: normal/warn allow the existing three retries, 75% allows only the first, 90% or unknown withholds repairs (a later failing run can retry once budget recovers; no attempt is recorded).
- Existing development workers are untouched. Actions billing and ChatGPT usage are not tracked here; ChatGPT usage is unavailable without a verified source.
