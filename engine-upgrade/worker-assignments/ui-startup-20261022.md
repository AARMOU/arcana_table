# Argentum upgrade focused test repair — Argentum UI resilience and shared startup

**Base commit:** 1a42f8080842f765017ad09bd5f1241f3fd0ebb8
**Merge target:** `gpt/argentum-upgrade-20261022` (never `main`)
**CI failure:** https://github.com/AARMOU/arcana_table/actions/runs/39818196484
**Status:** Isolated investigation — tests failing, no fix or agent completion claimed.

## Scope and evidence
Six UI resilience tests failed waiting for deck-check error dialog, needs-handling decision panel, combat damage board, declare attackers, action table, and spell composer. Investigate actual launcher/navigation state and shared helper `UiTestSupport.playWithArgentum`: some tests cannot find `play-start`. Verify FakeArgentumServer responses, quick tips, menus, timing, and whether baseline engine exhibits same failure. Repair actual navigation/setup or regressions; do not just increase timeouts.

## Focused Gradle tests
Run with the pinned replacement Argentum runtime and the exact Arcana upgrade branch candidate; use a Gradle `--tests` filter instead of rerunning all upstream rules/card/AI suites after every edit:
- `./gradlew :app:testDebugUnitTest --tests 'com.arcanatable.ui.ArgentumResilienceUiTest'` from `android/` (after staging replacement runtime as documented in `engine-upgrade/UPGRADE-2026-10-22.md`)

## Safety and ownership
- UiTestSupport.kt and ArgentumResilienceUiTest.kt; narrow production fixes only. Do NOT edit DesignPreviewTest or GameScreenSizesTest.
- Never merge into main, release, alter signing keys, change AI decision depth, limit tokens or triggers, or substitute dummy card abilities.
- Baseline against existing runtime when necessary to distinguish pre-existing test/UI failures from upgrade regressions.
- Retain failures until evidence supports a real fix. Record exact test counts, command, artifacts, and commit SHA.
- Do not claim passing tests without execution proof. Full upgrade pipeline only after all focused work is integrated and passing.
