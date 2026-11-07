# Guarded automatic merging — owner authorization Nov 7, 2026

**Goal:** remove routine PR bottlenecks without allowing a green test to act as release
authority. Authorized by the owner in conversation (“Ok do it”), recorded on
[#883](https://github.com/AARMOU/arcana_table/issues/883#issuecomment-6709783403).
This is a narrowly scoped exception to the historic owner-only merge default.
It does **not** authorize engine adoption, Studio/app merges, release, deployment,
workflow/security/budget/permissions changes or new paid work.

## Eligibility and activation

| Cohort | Automation | Gate |
| --- | --- | --- |
| Independent suites (exact two newly added files) | **Staged behind OFF-by-default switch** | New trusted bot suite issue; Copilot same-repo author; exact successful premerge run and executed independent scenarios; unchanged PR head; clean merge; no holds/requested changes; target main, no other changed files; dedicated merge token, squash SHA lease |
| Cardkit review decisions (trusted data-only) | **Existing separate narrow lane** | [`review-automerge.cjs`](../../scripts/review-automerge.cjs), exact Cardkit run and allowlisted review data; never card implementation |
| Pure docs / tests not matching the independent-suite cohort | **Manual until separately proven** | Markdown can contain instructions and tests can affect CI/trust; not a blanket path allowlist |
| Ordinary card implementations | **Manual until full integration pilot passes** | Full Android/engine regressions, exact-head independent certification, no changes outside card/registration/tests, trusted queue source, base/head compatibility, post-merge replay and rollback. Do not infer approval from a suite alone |
| Engine/JAR/rules, Studio/UI, security/workflows, budgets, deployments, APK/signing/releases, unresolved holds | **Owner-only** | No automatic override |

The suite merger is additive; the existing report-only workflow in PR #1029
remains read-only and supplies its eligibility evaluator. To activate after
both PRs have merged, an owner-authorized repository operator must set the
Actions repository variable `ARCANA_GUARDED_SUITE_MERGE=enabled` and provide
the dedicated least-privilege secret `ARCANA_SUITE_MERGE_TOKEN` (only repo
contents and PR write; not the dispatcher token). **Absence of either means no
automatic writes.** Activation is **not** implied by merging the PR.

Do not merge this stacked PR into main before #1029 is merged and verified.
The feature should first run in its disabled/report-only condition with exact
head CI passing. Activate with *one new* trusted suite PR as a canary; confirm
the workflow summary, target/main integrity and hourly certification backfill;
then permit further eligible suites.

## Defensive invariants

- Only new PRs and certification runs from **Nov 7, 2026 16:00 UTC onward**.
  No retroactive processing of historical backlog.
- A validated run must have succeeded on the PR's **exact current source SHA**.
  `mergeSuitePr` in the read-only report workflow independently verifies issue
  provenance, author, two-file diff, suite metadata and executable suite jobs.
- Disallow known hold/blocked labels or title, requested-change reviews, fork
  heads, ambiguous mergeability, non-main bases and base movement.
- Serialize this lane under `arcana-main-writer`. Re-fetch and re-evaluate after
  draft-to-ready transition and use GitHub's exact-head SHA lease for merge.
  Unknown/failed queries or API errors stop the attempt; no retry loop, secret
  fallback or optimistic merge.
- No CI, workflow, JAR, engine, UI, card implementation or settings files can
  pass the two-file suite allowlist. Do not broaden its patterns in a hotfix.
- Action summaries record merge/no-merge decisions; failed actions do not
  silently claim the PR integrated. Post-merge build certification remains
  independent.
- **Kill switch:** remove the repository variable or change it from
  `enabled`; new guarded merges stop without modifying any code or cancelling
  other worker schedules. Do not delete branches or overwrite history.

## Before any card-implementation auto-merge

Build a separate source-audited, fail-closed pipeline and demonstrate against
a *non-production pilot branch* that the exact candidate code and suites pass
both isolated and full integration tests, remain branch-current, preserve
private card state, handle a conflict, and can be reverted. Independent review
must show at least a genuine negative case (bad certification, changed SHA,
engine file, unsafe label, base drift, mid-flight CI failure). Turn it on for a
bounded canary cohort only after verifying current build/run evidence.
