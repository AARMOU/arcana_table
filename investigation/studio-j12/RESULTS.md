# J12: UI replay baseline - November 7, 2026

**DONE for the bounded benchmark:** five fresh-JVM runs passed, totaling 10,000 measured native UI updates plus 2,000 warmup updates. This is synthetic UI replay, not a rules-engine match, AI benchmark, physical Fold test or Studio acceptance.

**Finding:** on the same Studio revision, one saved fill edit increased median update time from **1.95-2.04ms to 2.42-2.45ms on Table**, and **1.29-1.33ms to 2.12-2.19ms on Player**. Paired observed increases were **19-26%** and **59-70%**. Recomposer changeCount remained exactly 1,000 for 1,000 measured updates in every case: more work per update, without an observed extra recomposer-change loop. These descriptive runs are not statistical confidence intervals or phone frame times.

## Inputs and method

- No-Studio development: `01053b6024f748773e90eb63550094ef65688439`.
- Assigned Studio job pin: `3ccba4ebf3e4e7dd411b8bd06520bade56d714ee`, not a claim about latest PR #1013 behavior.
- Existing J10 fixture from draft #1032: `577d40f5bd6cc77ce5e0c06d3e8f572108277c6d`.
- Windows, Ryzen 7 5800X3D/16 logical CPUs/~32GiB RAM, Temurin21.0.12.1+1, Gradle8.13, Robolectric4.14.1 native graphics, SDK34, `w673dp-h841dp-port-xhdpi`.
- Identical fixture parses to four players, six stack entries and 18 local battlefield permanents. It is layout stress data, not a legal match. The test projects it directly into the real MainActivity controller, without running an engine/AI or game-server socket. Life stays at three digits. Hand auto-opening is disabled and the drawer asserted closed; timers stopped; known image URLs seeded with identical cached placeholder bitmaps.
- Each screen gets 200 warmup updates and 1,000 timed updates, changing own-player life/snapshot, advancing one virtual Compose frame and waiting for idling. Host elapsed time includes test dispatch/idling; virtual 16ms advance is not a measured frame deadline. Screenshots occur outside timing.
- Applied mode catalogs controls, applies one fill edit through the actual runtime, exits editing and asserts exactly one saved edit. Unedited mode leaves the same revision's runtime inactive.
- Run order: baseline, applied, unedited, applied repeat, unedited repeat. Both same-revision pairs show the same direction. JIT/GC/host variation remains.
- The development-versus-Studio comparison is **confounded by other app changes and different AI/SDK/rules-engine JAR blobs**. The tooling JAR matches. No binary was substituted between pins. Use the same-Studio applied/unedited cases to narrow that confounding.

## Results

Milliseconds per UI update, not phone FPS. Raw timing arrays, hashes, JUnit results, sample counts and GC counts are in the corresponding `evidence/*.json`.

| Run | Screen | Median ms | p95 ms | Recomposer changes | JFR samples | GC events |
|---|---|---:|---:|---:|---:|---:|
| baseline-long | Table | 1.842 | 5.124 | 1000 | 166 | 4 |
| baseline-long | Player | 1.236 | 1.791 | 1000 | 69 | 3 |
| studio-applied-long | Table | 2.421 | 6.843 | 1000 | 193 | 5 |
| studio-applied-long | Player | 2.185 | 2.905 | 1000 | 69 | 7 |
| studio-unedited-long | Table | 2.037 | 5.511 | 1000 | 176 | 4 |
| studio-unedited-long | Player | 1.285 | 1.762 | 1000 | 62 | 4 |
| studio-applied-repeat | Table | 2.454 | 6.789 | 1000 | 204 | 5 |
| studio-applied-repeat | Player | 2.121 | 2.890 | 1000 | 76 | 7 |
| studio-unedited-repeat | Table | 1.952 | 5.407 | 1000 | 171 | 4 |
| studio-unedited-repeat | Player | 1.332 | 1.709 | 1000 | 48 | 4 |

## Sampled CPU and source findings

JFR requested 1ms sampling, but the actual sample count is lower. **Do not multiply samples by 1ms and call that method duration.** Inclusive stack counts overlap and cannot be added. Zero samples do not prove zero cost. Full baseline, applied-repeat and unedited-repeat recordings are retained beside their summaries. The first applied/unedited pair retains hashes/counts/timing arrays; its original recordings were overwritten before retention was added.

### Table: 204 execution samples

| Application frame | Inclusive samples |
|---|---:|
| `com.arcanatable.MainActivityKt.TableScreenContent$lambda$4$7` | 24 |
| `com.arcanatable.StudioWidgetsKt.studioWidget` | 19 |
| `com.arcanatable.StudioWidgetsKt.StudioText-0bsmlHs` | 17 |
| `com.arcanatable.TableMainLayoutKt.TableMainLayout` | 12 |
| `com.arcanatable.TableMainLayoutKt.TableMainLayout$lambda$0$13$0$2$0$0` | 12 |
| `com.arcanatable.TableBoardViewsKt.PlayerOverview` | 12 |
| `com.arcanatable.TableOverlaysKt.TableOverlays` | 11 |
| `com.arcanatable.StudioControlsRuntime.style` | 6 |
| `com.arcanatable.TableEffectsKt.TableEffects$lambda$40` | 5 |
| `com.arcanatable.TableEffectsKt.TableEffects` | 5 |
| `com.arcanatable.TableMainLayoutKt.TableMainLayout$lambda$0$1` | 5 |
| `com.arcanatable.ArgentumUiKt.ArgentumSeatTab` | 5 |

### Player: 76 execution samples

| Application frame | Inclusive samples |
|---|---:|
| `com.arcanatable.StudioWidgetsKt.StudioText-0bsmlHs` | 7 |
| `com.arcanatable.TableBoardViewsKt.PlayerBoard$lambda$13` | 3 |
| `com.arcanatable.StudioWidgetsKt.studioWidget` | 3 |
| `com.arcanatable.TableMainLayoutKt.TableMainLayout` | 3 |
| `com.arcanatable.StudioControlsRuntime.project` | 2 |
| `com.arcanatable.StudioControlsRuntime.getEditing` | 2 |
| `com.arcanatable.TableMainLayoutKt.TableMainLayout$lambda$0$13$0$BoardFor$3` | 2 |
| `com.arcanatable.ArgentumUiKt$ArgentumSeatTab$1$1.invokeSuspend` | 2 |
| `com.arcanatable.TutorialCoachKt.tutorialTarget$lambda$0` | 2 |
| `com.arcanatable.TableBoardViewsKt.PlayerBoard` | 2 |
| `com.arcanatable.TableController.getPlayersState` | 2 |
| `com.arcanatable.TableController.getPlayers` | 2 |


Requested methods:

- `studioWidget` is present in applied CPU stacks. At [StudioWidgets.kt:39](https://github.com/AARMOU/arcana_table/blob/3ccba4ebf3e4e7dd411b8bd06520bade56d714ee/android/app/src/main/java/com/arcanatable/StudioWidgets.kt#L39), registration depends on `runtime.active`. [StudioControls.kt:149](https://github.com/AARMOU/arcana_table/blob/3ccba4ebf3e4e7dd411b8bd06520bade56d714ee/android/app/src/main/java/com/arcanatable/StudioControls.kt#L149) makes any saved edit activate wrappers. This is a source-supported lead for extra registration/style work across other composed controls, not proof of one exact bottleneck.
- `record` is called by the registration SideEffect at [StudioWidgets.kt:54](https://github.com/AARMOU/arcana_table/blob/3ccba4ebf3e4e7dd411b8bd06520bade56d714ee/android/app/src/main/java/com/arcanatable/StudioWidgets.kt#L54); implementation [StudioControls.kt:159](https://github.com/AARMOU/arcana_table/blob/3ccba4ebf3e4e7dd411b8bd06520bade56d714ee/android/app/src/main/java/com/arcanatable/StudioControls.kt#L159). Sampling is sparse; **exact per-call or aggregate duration UNKNOWN**.
- `onGloballyPositioned`: [StudioWidgets.kt:113](https://github.com/AARMOU/arcana_table/blob/3ccba4ebf3e4e7dd411b8bd06520bade56d714ee/android/app/src/main/java/com/arcanatable/StudioWidgets.kt#L113) returns before attaching the callback when not editing. The measured applied case has exited editing. Editing-mode cost is outside this benchmark; no duration is claimed.

Claude should rerun the harness on the current Studio head and inspect active-wrapper/style/registration cost, preserving saved styles and callbacks. Device frame/jank testing remains separate. No optimization is included here.

## Validation and boundaries

- Five final long `J12PerformanceTest` invocations PASS, one test each. Native screenshot review verified the board is not obscured by the hand. Short 100-update exploratory trials are superseded and excluded above.
- Initial harness issues corrected: Android compile bootclasspath omitted JFR; platform-loader reflection fixed it. Fresh runtime dependencies were downloaded into the isolated Gradle home after an offline cache miss. An automatic hand opening was caught by screenshot/assertion and disabled before final measurements. Existing tests were not weakened.
- `audit_sources.py` verified 148 original committed baseline files and 253 Studio files, including production source/assets, existing tests and all runtime JARs: every byte equals its pinned Git blob. Only two new opt-in test files and the existing J10 resource are overlaid. See `evidence/source-audit.json`.
- Cardkit 67 tests/one existing skip PASS; check zero errors/41 warnings; docs-check zero errors; diff-check PASS. A Cardkit test rewrote verified-support.json line endings; normalized equality was checked before restoring exact original bytes.
- Negative checks: `capture.py` refused superseded stale results; `prepare.py` refused to overwrite an existing stage. Final capture requires successful build, fresh passing JUnit/JSON, matching harness/mode and exact sample count; evidence labels cannot be overwritten.
- Waited for Red Bull's actual live app-test processes to exit, then ran serially with capacity checks. Separate Crayola stages/Gradle home, process-local tool paths. No other worker files, jobs, caches, timers or scripts changed.
- No production code change, runtime adoption, APK, merge, release, spending or schedule change. #1002 remains parked awaiting independent review; owner reassignment did not approve its merge/adoption.

Estimate 1-2 hours; actual approximately 27 minutes including setup and capacity wait. Deliverable is a reproducible baseline for review, not independent certification of all Studio behavior.
