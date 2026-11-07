# STUDIO-J12: UI replay baseline

Owner-approved Crayola assignment. #1002 is parked awaiting independent review.

## Scope

Measure the real MainActivity Table and Player surfaces using the same J10 synthetic four-player snapshot. No running game engine, AI, socket, APK build, production-source instrumentation, or change to Claude's branch is involved. This measures native Compose UI replay on Robolectric/JVM, not physical-device FPS or rules-engine throughput.

Pinned comparisons:

- No-Studio development: `01053b6024f748773e90eb63550094ef65688439`.
- Assigned Studio revision: `3ccba4ebf3e4e7dd411b8bd06520bade56d714ee`.
- Fixture: J10 draft #1032, `577d40f5bd6cc77ce5e0c06d3e8f572108277c6d`.

The pins contain other application differences, including different AI/SDK/rules-engine JAR blobs (tooling JAR is identical). Any ratio between these revisions describes the whole-revision difference, not a causal estimate of Studio alone. An additional same-Studio-revision unedited control separates the applied-edit overhead from other revision differences.

## Harness

`prepare.py baseline` and `prepare.py studio` extract committed Android source into separate ignored build directories. Only two new test files and the exact existing J10 fixture are overlaid. Existing source, runtime JARs and tests stay unchanged. Preparation refuses to overwrite an existing stage.

`J12PerformanceTest.kt` parses the same fixture, checks four players/six stack entries/18+ battlefield permanents, feeds the existing native UI controller, and freezes the game timers, disables automatic hand opening and asserts the drawer does not obscure the board. Parsed image URLs are preseeded with identical small placeholder bitmaps to exclude external card-art I/O. `studio.kt` applies one fill edit through the actual Studio runtime, then closes editing; it asserts precisely one saved edit. The baseline adapter performs no edit.

For each Table/Player screen:

- Settle the screen, perform 200 unmeasured life-total updates to warm layout/JIT, then measure 1,000 distinct updates, advancing one virtual Compose frame per update.
- Retain all 1,000 measured wall-clock samples, median (average of the two middle values) and nearest-rank p95.
- Record Recomposer changeCount delta across the 1,000-update measurement batch. This is recomposer change activity, not a count of individual composable executions.
- Collect 1ms-period JFR execution samples and GC events over that measured batch. Report inclusive stack-frame sample counts, including Studio methods when observed. Inclusive counts overlap and must not be added; zero samples do not establish zero cost. Sampling cannot establish exact per-call duration for `studioWidget`, `onGloballyPositioned` or `record`.
- Capture the rendered screen after measurement, outside the timed interval.

The measured time includes ActivityScenario dispatch, test clock and idling machinery. JFR adds overhead. Virtual 16ms clock progression is not a measured frame deadline. Rendering is the native Robolectric path, not Android GPU/phone hardware. Screen captures and CPU samples are supporting evidence, never device acceptance.

## Execution

Use JDK21 and the already-installed Android SDK. Set GRADLE_USER_HOME to this task's `build/gradle-home`, JAVA_HOME and ANDROID_HOME only in the process. Run one Gradle process at a time, after checking other workers' live process/claim evidence:

```
<stage>/android/gradlew.bat -p <stage>/android --no-daemon --max-workers=2 :app:testDebugUnitTest --tests com.arcanatable.ui.J12PerformanceTest
```

Results: `<stage>/android/app/build/j12/results.json`, `table.jfr`, `player.jfr`; images in the existing screenshot directory. A result is accepted only after successful Gradle exit and a current passing JUnit XML; stale JSON must not be used after a failed run. Record actual source SHA, fixture/hash, test-harness hash, JDK, CPU/memory, run order, workload, raw samples and limitations with results. Repeat comparisons if noise or failure warrants it.

For the same-Studio unedited control set process environment `J12_UNEDITED=1`; the adapter leaves catalog/editing inactive. Unset it for the applied case. Run the test again using `--rerun-tasks` or `--rerun` for the test task so Gradle cannot reuse a prior mode result. Archive each successful result before switching modes. Summarize host JFR recordings with `python investigation/studio-j12/summarize.py <stage>` using JAVA_HOME set to the existing JDK.

## Completed evidence

See [RESULTS.md](RESULTS.md) for the five final runs, limitations and source findings. All final long runs passed. Full baseline and last applied/unedited CPU recordings, raw timing arrays, screenshots and source/runtime byte audits are under `evidence/`. Exploratory trials are excluded.

After each successful run, execute `summarize.py <stage>` then `capture.py <baseline|studio> <unique-label> <log-name> <expected-mode>`. Capture checks current successful output and refuses existing labels. Run `audit_sources.py` to verify archived production/runtime/original-test bytes remain untouched.
