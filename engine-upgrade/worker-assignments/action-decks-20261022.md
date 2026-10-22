# Argentum upgrade focused test repair — Actions and custom-decks assertions

**Base commit:** 1a42f8080842f765017ad09bd5f1241f3fd0ebb8
**Merge target:** `gpt/argentum-upgrade-20261022` (never `main`)
**CI failure:** https://github.com/AARMOU/arcana_table/actions/runs/39818196484
**Status:** Isolated investigation — tests failing, no fix or agent completion claimed.

## Scope and evidence
Existing CI output says treasure expected `Sacrifice for mana` but got `Sacrifice this source for mana`. Custom-deck test expected a hard-coded `Using Arcana's own versions of 15` string; fail at CustomDecksOfflineTest.kt:45. Verify actual library names/count with the replacement runtime; preserve user-facing action semantics and real card support. Do not simply weaken assertions or blanket-ignore engine behavior. Fix only the relevant production or test logic after establishing a behavioral baseline.

## Focused Gradle tests
Run with the pinned replacement Argentum runtime and the exact Arcana upgrade branch candidate; use a Gradle `--tests` filter instead of rerunning all upstream rules/card/AI suites after every edit:
- `./gradlew :app:testDebugUnitTest --tests 'com.arcanatable.Build97Test.treasuresShowAsSacrificeForMana'` from `android/` (after staging replacement runtime as documented in `engine-upgrade/UPGRADE-2026-10-22.md`)
- `./gradlew :app:testDebugUnitTest --tests 'com.arcanatable.CustomDecksOfflineTest.tokensWinUsesCustomCardsAndOnlyLeavesOutGoblinWelder'` from `android/` (after staging replacement runtime as documented in `engine-upgrade/UPGRADE-2026-10-22.md`)

## Safety and ownership
- Build97Test.kt; CustomDecksOfflineTest.kt; directly corresponding implementations only; do NOT edit UiTestSupport or UI screenshot tests.
- Never merge into main, release, alter signing keys, change AI decision depth, limit tokens or triggers, or substitute dummy card abilities.
- Baseline against existing runtime when necessary to distinguish pre-existing test/UI failures from upgrade regressions.
- Retain failures until evidence supports a real fix. Record exact test counts, command, artifacts, and commit SHA.
- Do not claim passing tests without execution proof. Full upgrade pipeline only after all focused work is integrated and passing.
