# Usage-aware dispatcher rollout

The existing card dispatcher runs every five minutes and can assign two new issues per invocation. Its test_issue option bypasses normal capacity counting. The CI failure router may separately request up to three Copilot repairs per draft PR.

Before enabling more assignments, implement a shared budget gate for both entry points. Track Copilot premium requests, GitHub Actions billing, and ChatGPT usage as separate categories. Do not treat the GitHub Cloud Agent billing SKU as the same as premium requests.

Thresholds for verified consumption relative to allowance: 50 percent warn; 75 percent reduce dispatch; 90 percent block nonessential assignments. Unknown or stale usage should block new discretionary assignments. The test_issue override must not bypass budget checks.

The latest user-provided Copilot snapshot reported 338 of 1500 credits used, but it is not live data. Verify the personal-account billing API and authorization before relying on automated decisions. Do not store credentials in the repository.

Add daily reporting with timestamped usage sources, active jobs, completed PRs, failed checks, and remaining allowances where measurable. Do not claim ChatGPT Plus usage can be polled without a verified interface.

Changes remain isolated. No automatic merges, releases, or production modifications.
