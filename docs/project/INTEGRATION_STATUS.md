# Arcana Table — integration control board

**As-of:** 2026-10-22; source: actual branch refs, GitHub PRs, CI logs, and verified backup inventory. This is a **tracking document**, not authorization for a merge, code modification or release.

## Repositories / baselines

| Role | Branch / source | Known state | Next gate |
|---|---|---|---|
| Public website and default branch | `main@c533d0520feeab5a9c98d1d61ddb034b34672bf3` | Site deployment from main; Android source/docs are historically Build 67–72-era. Main includes website commits absent from app branches. | Preserve Pages, privacy, and testing/signup changes before reconciling main. |
| Old app development trunk | `GPT-eating-glue@5cd24e874e55d92e2f0959e905daeebddf624707` | Contains app evolution not in main; diverged from main. | Do not fast-forward main assuming histories are identical. |
| Build 130 candidate base | `gpt/arcana-b130-priority-three-preview@43b27a89f01e850ec651f85f9be775d7804cb0bd` | Foundation for the Argentum upgrade. | Capture suite-specific evidence and runtime provenance. |
| Argentum upgrade preview | `gpt/argentum-upgrade-preview-20261022@83aa342e3fcd200710fb9188192189fc7cea7abf` | Packaging workflow passed at that SHA, but full integration of all custom cards not established. | Engine behavior parity, app regressions, exact JAR/card export manifests. |
| Precon implementation | `gpt/argentum-six-precons@ff5e2b719159904b51e3a46d479afa7018d9f4c9` | Card worker PRs isolated from Argentum upgrade. | Full custom-card suite, exact engine compatibility, integration review. |
| Card worker integration | `gpt/six-precon-worker-integration-20261022` (live head changes) | Draft PR #43; latest inspected full test 353/354? **353 total, 1 failure** at `a368c3f7`. | Solve Forge of Heroes loyalty-counter failure and rerun full regressions at fixed SHA. |
| Outcome diagnostics | `gpt/game-over-cause-logging-20261022@273df437383d6766d0f2f8858382361412acb3a8` | Draft PR #42; Kotlin compile failed on nullable viewer ID. | Resolve type error and run outcome tests. |
| Governance rules/docs | `gpt/repository-governance-20261022` | This isolated documentation PR; app behavior unchanged. | Review governance docs for merging into main separately. |

**Correction:** The card-worker run reported **353 tests completed, 1 failed** (not 354). The above status must be checked against any newer worker commit/run before integration.

## Dependency map

```text
main (website/default)  [DIVERGED FROM ANDROID DEVELOPMENT]
    |
    +--> governance docs PR (docs-only; separate review)
    |
GPT-eating-glue
    |
    +--> older performance/build work -> B129 -> B130 preview
                                           |
                                           +--> Argentum upgrade target
                                                  +--> targeted CI / repairs
                                                  +--> Build 131 upgrade preview
                                                         +--> outcome logging PR #42 (red)
    |
    +--> precon card export work -> gpt/argentum-six-precons
                                     +--> isolated cards #32-34, #37-41
                                     +--> worker integration PR #43 (red)
    |
    +--> mana reliability #11 -> table refinement #26 -> graveyard/exile #27
```

The diagram is a **workstream / PR dependency sketch**, not a proof that every item shares a direct ancestry or is already integrated. Re-check `git merge-base` and actual PR diffs for the exact intended merge route.

## Integration gates

1. **Preserve:** Keep the verified backup manifest and independent Git bundle safe, and re-snapshot changed live worker heads before later destructive operations. Do not archive or delete work as a prerequisite for organization.
2. **Reconcile website:** Keep `main`-only site, privacy, and signup commits. Compare the Android integration candidate with `main` using both ancestry and file-level diffs.
3. **Resolve pending worker conflicts separately:** Each currently open PR remains draft/protected where applicable until its own tests pass. No worker branch is retargeted automatically.
4. **Engine/card compatibility:** Establish exact Argentum upstream commit, forward port list, runtime JAR checksums, custom-card registry and export hashes. Verify all added cards with actual SDK engine scenarios.
5. **Behavioral equivalence:** Run rule-sensitive trigger/token/AI/priority cases, the full custom-card suite, Android tests, lint, and release packaging where appropriate. Do not trade correctness or AI ability for timing.
6. **Integration candidate:** In a new, explicitly assigned *reviewed* branch, combine only validated work. Treat that combination as a separate test target; previous green isolated tests are necessary but not sufficient.
7. **Promotion:** Publish a reviewed, exact build manifest and obtain explicit user approval before merging into `main`, changing the app's released artifact or releasing it.

## Current workstream backlog (one owner/branch per area)

| Area | Representative source | Evidence state | Next action |
|---|---|---|---|
| Massive token/trigger performance | Engine-delay branches + B131 diagnostic | Real-world B131 end-step stall; no verified general fix | Reproduce, profile and optimize with no legitimate token/trigger caps; preserve AI |
| Background process/game loss | Offline checkpoint code in B129/B130 | Manual save/resume code exists, not proven automatic restore after process death | Capture Android exit reason; automated recovery fixture |
| Game-over reason logging | PR #42 | **compile-red** | Nullability fix isolated to owner workflow |
| Six-precon card integration | PR #43 plus worker PRs | **test-red** | Forge of Heroes loyalty counter regression |
| Argentum engine upgrade | Upgrade preview & #28-31, #35 | Targeted packaging success, not full merged-proof | Compatibility and regression gates |
| UI features | B129/B130 and older draft PR #11/#26/#27 | Various implemented features, uncertain installed coverage | Verify precise integrated SHAs and device behavior |
| Library and compatibility-oriented importer | B131 compatibility and import code | Existing unsupported-card review, full new library not implemented | Separate approved design/build track |
| Build manifests / release tracking | This governance branch | Template authored, releases not automatically certified | Backfill real signed output hashes and provenance before next APK |

## Task state vocabulary

`proposed` → `implemented` → `tested` → `integrated` → `released`

Every promotion needs exact SHA and associated evidence. **Never call something shipped solely because its implementation and tests exist on another branch.**

## Protected-scope notice

No deletion, rebasing, force-pushing, main changes, integration merges, APK publishing, production release or unrequested active-worker source modifications are authorized by this control board.
