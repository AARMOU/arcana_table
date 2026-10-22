# Android regression repair evidence

Candidate parent: `be527c95345bf1a7b275a0a142a5fab6796f95d1`.
Shipped Arcana baseline: `43b27a89f01e850ec651f85f9be775d7804cb0bd`.
Upstream target: `61e71e586b100faa661f86cac827ac3c3bbb5a78`.

## Causes established from source and failed CI

- `UiTestSupport.playWithArgentum` still follows Build 97's online menu. Both baseline and candidate `PlaySheet` park that option (since Build 113); it cannot select the server transport. Clicking Start therefore launches an unrelated embedded game, or fails to reach the off-screen Start control. All 16 server UI scenarios use this helper. Repair the helper to invoke the real retained `startArgentumPractice` path through the activity's current screen controller; keep the production menu parked and keep all protocol, lifecycle, interaction and layout assertions.
- `Build97Test.treasuresShowAsSacrificeForMana` expects `Sacrifice for mana`; baseline and candidate use `Sacrifice this source for mana`. `ManaReliability128Test` explicitly requires this distinction from Goose/Food. Update the stale exact expectation, retain classification and visibility checks.
- `CustomDecksOfflineTest` expects exactly 15 custom implementations. The upgrade deliberately prefers upstream definitions and removes redundant custom entries. Assert the registry contains all 15 previously custom cards, only Goblin Welder is unsupported, and the notice matches the actual custom-only set. This catches loss of implementations while allowing upstream ownership.

- Once server startup is reached, the resilience test deliberately injects malformed messages but never dismisses their recovery dialog before testing menus. Explicitly assert and acknowledge that recovery dialog.
- A local JVM thread dump proves the same test then spins inside Compose `performScrollTo` with the test clock paused. Permit Compose clock advancement only around that scroll, then restore manual stepping. This is a test harness hang, not a rules-engine stall. The focused workflow step has a 10-minute timeout so a future harness hang fails with retained reports instead of occupying the whole job indefinitely.

- Both baseline and replacement CI jobs at `9ff4f424` completed the same 24 tests with the same 9 failures (run `39831876339`), proving those remaining failures were not introduced by the replacement engine.
- In the full server-fixture layout, Robolectric coordinate taps on tagged tab wrappers do not invoke the control, while its real accessibility action correctly navigates. Layout tests now invoke the clickable control's accessibility action; their board, preview, hand, combat, and viewport assertions are retained. `ArgentumGestureUiTest` independently exercises real pointer taps and physical card hold/release plus preview tap dismissal, including all four screen configurations. All five physical-gesture tests passed locally; all six design-preview scenarios passed locally.
- The auto-pass screen-size test expected exact `PASS →`, although its recorded server fixture supplies `nextStopPoint: To Attackers`. Assert the complete `PASS → TO ATTACKERS` label and absence of the auto-passing label. All four screen-size cases passed locally after this correction.
- Build 128 deliberately retired the automatic-only mana hint when it added manual floating mana. Retain checks that the global action count excludes land mana and sends no unintended activation, and assert the retired hint stays absent. The existing Build 128 mana regression tests are included in the focused CI selection.

- Run `39836953924` passed all 29 focused tests on both runtimes. The replacement full suite then ran 210 tests: 205 passed, 1 failed, 4 skipped. The sole failure was `FloatManaTest.floatingManaIsSpentBeforeTappingMoreLands` at stage 0, before any mana-payment assertion; its random duel stalled for 60 seconds. This did not establish a payment-engine defect.
- The payment test previously chose random decks and left game shuffling unseeded, while only handling land play and priority passes. Combat or decision requests could prevent it reaching a payment scenario. Use Forest/Moon Sprite versus a passive Mountain deck and an explicit game seed instead; all four original payment assertions remain. The host accepts an optional seed with the production default unchanged. Timeout errors now include the last turn/step/actions, engine errors fail immediately, and the host closes in `finally`. All three FloatMana tests passed locally against the shipped runtime, including one floated mana, a two-mana cast, zero remaining pool, and exactly one additional land tapped. Include this class in both focused CI runtime comparisons (32 tests each).

## Verification

The focused CI workflow runs identical tests with two runtimes: shipped JARs/assets restored from the pinned baseline, and matching rebuilt replacement JARs/exports. Its replacement job then runs the entire app test suite, lint and debug compilation. Evidence artifacts include XML and HTML reports. Runtime binaries and APKs are not committed or published.

Final combined CI results are pending until the exact committed workflow finishes; separate local runs are not a claim that the full upgrade gate passed. No production merge or release is authorized by these changes.
