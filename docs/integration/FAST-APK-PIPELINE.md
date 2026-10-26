# Arcana Table — Fast Test APK lane (manual only)

**Status:** Configured in isolated draft, **NOT RUN**. No seed, engine recompilation, APK, merge, signing, Play Store submission or Cloudflare deployment was started in this change. User specifically requested *make builds faster; do not start an APK yet*.

## Measured baseline (GitHub Actions)

The successful no-goose full Build 134 [run 40368920029](https://github.com/AARMOU/arcana_table/actions/runs/40368920029) lasted about **22 minutes** (17:28:18–17:50:43 UTC). Its expensive steps were:

| Step | Build 134 |
|---|---:|
| Recompile four Argentum engine/runtime JARs | 6m 12s |
| Run Ulalek/stack/restricted mana engine tests | 2m 34s |
| Compile/export/test custom cards | 2m 22s |
| Full Android debug unit tests | 3m 44s |
| Release variant unit tests | 1m 10s |
| Release lint + APK/AAB generation | 3m 05s |
| Final debug APK compilation | 1m 43s |

Timing comes from GitHub Actions individual step timestamps. Benchmarks for **fast** builds are not yet available. With a matching seeded engine and Gradle cache, a *goal* is single-digit minutes per repeated app/UI build, **not a measured guarantee**.

## Design

[Manual workflow](../../.github/workflows/fast-test-apk-manual.yml) exposes exactly two **manual workflow_dispatch choices**, neither runs from push, PR, schedule or polling:

1. **`seed-engine` (NO APK)**: Checks out exact validated Build136 source, executes its same CI runner integration and source smoke; fingerprints ALL engine patches, baseline JAR/card assets, current custom-card Kotlin/source inputs and build configuration; downloads the already SHA256-verified upgrade patch; checks out pinned Argentum upstream; executes the **same exact upstream patch application, full four-JAR rebuild, stack/Ulalek/colorless mana scenarios, custom-card export/tests, Azlask and 94-slot original Eldrazi gates** from the full release workflow. Archives four validated Java-17 runtime JARs, three full card data/index exports, and both custom-card export/index assets, plus an immutable SHA-256 manifest. No APK Gradle task can execute on this job.
2. **`fast-apk` (DEBUG test only)**: Requires the precise **successful seed-engine run ID**. Rejects non-successful/non-manual/wrong-repo runs, missing/expired artifacts, mismatched engine/card **source fingerprint**, wrong seed run id, wrong upstream and any tampered byte. Applies the same exact no-goose/AI/lobby/tutorial/Import new/Deck Editor/B121+ changelog/quiet-popup source joins **without rebuilding the engine**. Restores four verified JARs and five card assets. Builds only `assembleDebug` and seven focused Android/Compose/Deck/ReleaseFeatures/Tutorial/Resilience test suites. Verifies that all seven suites executed with no failures/errors/skips. Confirms the original Arcana logo, 20-chapter tutorial, exported card indexes, package ID/version, certificate, SHA-256 and uploads an installable **DEBUG TEST APK**.

The fast lane does **not** call `lintRelease`, `assembleRelease`, `bundleRelease`, `testReleaseUnitTest`, or run all gameplay scenarios every time; **the existing strict full release workflow still must pass on the eventual identical app/engine source before any release, signed AAB or Play upload.** Fast mode is a device-test acceleration, not a release sign-off.

### Source and cache safety

- Source joins are the same reviewed pinned B136 scripts and tests from [PR #290](https://github.com/AARMOU/arcana_table/pull/290), including Claude lobby from #283, quiet notices from #287, and fixed tutorial, no goose and original logo.
- The workflow does **not** merge Claude's in-progress, failing #289 deck-filter changes or replace Argentum JARs from unrelated development branches.
- Seed artifacts are only reusable when **exact content** of the relevant engine/patch/card/baseline dependencies matches; ordinary changes to Kotlin/Compose UI can reuse that seed. No unsafe fallback to unvalidated repo JARs.
- Snapshot artifacts expire after 21 days (GitHub retention policy may further restrict this), at which point repeat **seed-engine**.
- The GitHub Actions branch and code review remain isolated. Both manual modes must be **deliberately invoked in a later owner-approved action**; creating the workflow did not invoke them.
- Every new Argentum upstream version, engine patch, custom-card implementation, export format or rules change intentionally invalidates the seed and needs a fresh full engine/card validation.
- GitHub-hosted runners are ephemeral; Gradle dependency caches can further reduce warm-run times but are not guaranteed hits. No paid AI/Copilot worker is dispatched.

### Steps once the owner asks for a faster APK

Prerequisite: the workflow file must be present on the repository's **default branch** for normal GitHub Actions manual-dispatch UI availability. This draft is staged in an isolated PR and awaits **owner approval for any merge**. GitHub's UI will not necessarily list the manual job before the workflow exists on the default branch. Do not work around this by enabling push-triggered automatic APK builds.

1. Owner approves publishing the manually gated workflow on the default branch (as a reviewed docs/tools/workflow-only integration; no full release branch merge required).
2. In **Actions → Arcana Fast Test APK — manual only**, choose `seed-engine`; the job performs full engine/custom card checks, **not** an APK. Record the successful run ID.
3. Later, choose `fast-apk`, supply the seed run ID and an appropriate **incremented** version code (first fast code >=136). The run gives a debug-test APK artifact only when the source and all selected tests pass.
4. Install the APK on the Fold, test; repeat `fast-apk` when only app/UI code changes, using a newer version code as needed.
5. Run the full existing release validation and device acceptance before promoting source, signing with the correct Play upload key, or distributing a release variant.

### Non-goals and measurements

This PR deliberately does not refactor the existing strict full Build136 flow, deploy the Worker, trigger Build136, change existing source JARs, or use production signing. A seed test by itself is **not** real APK timing proof; after explicit authorization to run, log actual seed runtime, first fast-build runtime and warm fast-build runtime and compare against full Build134's 22-minute baseline.


## Build 136 activation

The manual workflow is registered on main but must be dispatched with branch `gpt/fast-build136-activation-20261026`, containing the unmerged Build 136 source. The exact Build 136 runner-only ports for the green #292 lobby, combat/commander tracker, advanced deck and Ugin are replayed after the B135 base. Snapshot execution now honors the original workflow step working directories, and includes Eldrazi subtype and Ugin regression gates. Fast debug runs ten focused Android test suites. A matching seeded engine snapshot is mandatory; neither mode is an actual release certification.
