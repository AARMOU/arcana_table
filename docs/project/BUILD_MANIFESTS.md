# Arcana Table — Build and Release Provenance

**Purpose:** Stop confusing `implemented on branch` with `included in APK`. This is a **template and verified-facts register**, not a claim that all recent APKs were signed, released or tested.

## Recorded baselines (evidence only)

| Identifier | Code / provenance known | Evidence | Not established |
|---|---|---|---|
| `main` | `c533d0520feeab5a9c98d1d61ddb034b34672bf3`; historically Build 67–72 source and active GitHub Pages | [Main repository](https://github.com/AARMOU/arcana_table/tree/main), [Pages workflow](https://github.com/AARMOU/arcana_table/blob/main/.github/workflows/pages.yml) | That Android source reflects Build 131 or contains later work |
| `GPT-eating-glue` | `5cd24e874e55d92e2f0959e905daeebddf624707` | [Development branch](https://github.com/AARMOU/arcana_table/tree/GPT-eating-glue) | Inclusion in a particular signed app |
| B130 upgrade starting base | `43b27a89f01e850ec651f85f9be775d7804cb0bd` | [B130 branch](https://github.com/AARMOU/arcana_table/tree/gpt/arcana-b130-priority-three-preview) | That any subsequent upstream engine replaces shipped binaries |
| Build 131 *Argentum upgrade preview branch* | `83aa342e3fcd200710fb9188192189fc7cea7abf`; engine-upgrade workflow reported success | [Run #39844575500](https://github.com/AARMOU/arcana_table/actions/runs/39844575500), [upgrade provenance notes](https://github.com/AARMOU/arcana_table/blob/gpt/argentum-upgrade-preview-20261022/engine-upgrade/UPGRADE-2026-10-22.md) | All card worker PRs / game-over logging included; full behavioral, device, AI performance equivalence proven |
| B131 card compatibility branch | `89536604a175af241e45321f68ead6f1da633c1e` | [Focused Myriad gate #39857770885](https://github.com/AARMOU/arcana_table/actions/runs/39857770885) | Full branch merged into packaged B131 artifact |
| Six-precon worker integration | Saved `d3687c849dfc8b048d7b3551ff8d0bdb01848a47`; newer `a368c3f7f64d443a8a62b9fd62c60d4d54c4b97b` observed | [Worker run #39865218032](https://github.com/AARMOU/arcana_table/actions/runs/39865218032): **1 of 353 tests failed** | Ship readiness / compatibility with B131 |
| Pre-cleanup backup | Original 74 commits matched snapshot, bundle verified and restored from fresh mirror | [Backup run #39865508161](https://github.com/AARMOU/arcana_table/actions/runs/39865508161) | That the Android/Argentum suite passes (this validates Git history only) |

## Required manifest for EVERY new APK/AAB

Copy this template alongside the release candidate. Leave a field explicitly **UNVERIFIED** if there is no evidence; don't fill it from guesses.

```yaml
artifact:
  app: Arcana Table
  version_name: UNVERIFIED
  version_code: UNVERIFIED
  artifact_kind: APK_OR_AAB
  artifact_sha256: UNVERIFIED
  signing_key_fingerprint_public: UNVERIFIED
  produced_at_utc: UNVERIFIED
  build_run_url: UNVERIFIED
source:
  repo: AARMOU/arcana_table
  branch_at_build: UNVERIFIED
  exact_commit: UNVERIFIED
  merged_prs: []
  unmerged_features_deliberately_included: []
argentum:
  upstream_repo: wingedsheep/argentum-engine
  upstream_commit: UNVERIFIED
  local_engine_patch_ids: []
  jar_sha256:
    sdk: UNVERIFIED
    sdk_tooling: UNVERIFIED
    rules_engine: UNVERIFIED
    ai: UNVERIFIED
  card_export_sha256: UNVERIFIED
testing:
  sdk_compile: UNVERIFIED
  app_unit_tests_run_url: UNVERIFIED
  custom_card_tests_run_url: UNVERIFIED
  argentum_core_and_scenarios_run_url: UNVERIFIED
  ai_parity_run_url: UNVERIFIED
  token_trigger_stress_run_url: UNVERIFIED
  android_lint_run_url: UNVERIFIED
  device_install_and_launch: UNVERIFIED
  failures_or_waivers: []
delivery:
  release_tag_or_url: UNVERIFIED
  publishing_approved_by_owner: false
  explicitly_verified_included_features: []
  known_not_included_features: []
```

## Build gate

1. Pin app and exact Argentum binaries; compare local patches and generated card export to the source manifest.
2. Prove compile, full rules/AI/card scenarios, Android UI/unit tests, lint, stress tests, and device smoke checks using **commit-specific** evidence.
3. Calculate binary SHA-256 and, where applicable, verify the same signing certificate/fingerprint used for the intended install/update track.
4. Inspect all PR integration dependencies and document blockers/waivers.
5. Obtain explicit owner approval before production promotion, APK publication or modifying `main`.

**Explicitly out of scope for this docs-only step:** shipping or signing a new APK, force-merging an engine JAR, guessing release provenance from a branch name, or silently overriding broken tests.
