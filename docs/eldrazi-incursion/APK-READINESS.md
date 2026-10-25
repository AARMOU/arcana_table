# Eldrazi Incursion APK readiness — 2026-10-25

**Verdict: NOT READY for an exact, fully playable original 100-card deck. No APK built or released.**

The original manifest and bundled picker asset agree on all 100 one-copy cards, including exactly
one Ulalek. Claude #171 (`4d4c3608`) has 90 indexed originals; completed Selective Obliteration #199
adds one, producing **91 indexed / 9 absent**. Index membership is not Oracle playability. The
candidate includes #197 Confluence regressions and the verified source tests from #187/#188.

The accompanying [100-card audit](COVERAGE.md) and [machine-readable evidence](coverage.json)
classify every original slot: **30 implemented with reviewed positive/negative acceptance scenarios;
60 implemented but missing direct acceptance evidence or required negative cases; 0 current
registered-card test failures observed; 1 missing implementation; 9 pinned-engine/runtime blocks.**
The nine include registered Chittering Dispatcher, so they are not nine additional absent names.
These are evidence classes, not a new 30% completion claim. Several of the 60 have upstream tests;
those have not been independently executed against this exact Arcana runtime in this audit.

## Executed checks

| Gate | Evidence | Result / limit |
|---|---|---|
| Exact original list | Manifest counted by quantity; picker asset compared as a multiset | PASS: 100 slots, no substitutions in the saved list |
| Precon picker | `TableController.kt` loads `precons/eldrazi_incursion.txt` with Ulalek | Source present; no new Fold/device interaction claimed |
| No substitutions in actual game setup | `OfflineGameSetup.build` drops unsupported names and fills the library to 99 with basics, adding a notice | FAIL: nine originals currently replaced. Notice does not make this the original deck |
| Selective Obliteration | Eight real-engine scenarios, registry and two byte-identical exports | PASS on unchanged #171 runtime; source implementation adds exactly one original slot |
| Confluence | Seven scenarios including four colored mana rejection and mixed payment acceptance | PASS locally; #197 Development 40212361451 SUCCESS on exact PR merge |
| Ulalek | 16 scenarios: all-colorless, all-colored and mixed hybrid payment; wrong-color rejection; optional strict {C}{C}; spell/ability copying, opponent exclusions, retargeting; command-zone first cast, return, rejected underpaid recast and taxed recast | PASS locally |
| Scions | Confluence, Spawnbed Protector, Spawning Bed, Warping Wail suites | PASS: actual sacrifice-for-{C} and relevant creation/target/cost negatives |
| Spawn | Skittering Invasion extended beyond ability-exists inspection | PASS: sacrifice immediately generates one spendable {C}; no stack; no summoning-sickness tap restriction; opponent cannot activate; no repeated sacrifice; actual Sol Ring cast consumes it |
| Ugin intake | #187/#188's 8 extra loyalty/Devoid/controller tests and second Spirit-link destruction regression applied to current source | PASS locally with existing seven Ugin scenarios |
| Combined custom-card suite | Actual repository Kotlin + Android AI/priority/cost source sets, committed jars, JDK 17, Kotlin 2.4.0, exact dependencies, standalone Gradle JVM harness | **603 executed / 0 failed / 0 errors / 0 skips**. Android app tests are separate |
| Cardkit | 58 unit tests, one existing skip; check/docs-check; diff check | PASS; 0 toolkit check errors/warnings |
| Android app suite | First #199 run 40213533557: custom-card task passed; 270 Android cases, 2 failed / 4 existing skips | Two exact inventory assertions still expected 90/10. Corrected to exact nine-name missing set and required Selective loading; corrected #199 head `ca3a01c3` passed Development 40214289212 + Cardkit 40214289234. No rules assertion weakened |
| Real original-deck match vs AI | Existing smoke test uses only indexed names, with a turn-progress assertion | NOT PROVEN: an incomplete filtered-deck smoke cannot certify the original 100-card deck |
| Fold/release exclusion | Parent #197 release-only scenario exclusion passed in 40212361451 | New combined exact-head release check and physical Fold testing still needed |

Core executed case names and counts are in [readiness-core-scenarios.json](evidence/readiness-core-scenarios.json).
The coverage JSON records source/test fingerprints and the exact four unchanged runtime hashes.
The standalone harness bypasses Android plugin configuration only; it does not mock or rebuild the
Argentum engine. Its full-suite command was `test` with the repository custom-card source sets.

Subsequent evidence: #201 exact head `78528e2` Development [40214937451](https://github.com/AARMOU/arcana_table/actions/runs/40214937451) and Cardkit 40214937485 are now SUCCESS. The current land follow-up adds eight scenarios; its own combined CI is separate. [Land acceptance details](LAND-MANA-ACCEPTANCE.md) cover Unclaimed Territory and Tendo Ice Bridge.

## Exact remaining unindexed originals

| Card | Actual next step / blocker |
|---|---|
| Azlask, the Swelling Scourge | Earlier #171 colorless dies-filter failure reported in Cardkit. Independently reproduce and repair the trigger matcher; do not treat that old report as freshly certified |
| Benthic Anomaly | #203 primitive probe: direct stored projected power works; chosen-collection accumulation loses numeric outputs (2+3 becomes 0). No documented chosen-collection power/toughness sum. This blocks the tested composition, not every possible engine extension. Remains unimplemented/unregistered |
| Angelic Aberration | Documented filter API lacks base power/toughness <=1. It has equality and current-stat comparison only. Extend exact base-stat filtering, then variable sacrifice/token tests |
| Hideous Taskmaster | **Fresh reproduced blocker #200: 9 pass / 1 fail**. A selected target changing between opponents is incorrectly stolen. Preserve per-target original-controller binding through resolution/copies/retargeting |
| Twins of Discord | #196 is a contract/investigation only. Prove external conditional bloodthirst replacement plus parity attack restriction before implementation; not an implemented card |
| Mutated Cultist | Dynamic next-spell generic discount based on removed counters lacks a documented primitive. Requires exact next-spell scope/expiry and counter-removal tests |
| Corrupted Crossroads | #192 exact Devoid-only source candidate: 6 engine + 6 Android scenarios passed at `1bf22a66`, but runtime/registry/export absent from integration |
| Eldrazi Temple | #158/#162 exact restriction source candidate: 5 engine + 8 Android scenarios passed at `fb60a660`, but runtime/registry/export absent from integration |
| Opal Palace | Commander-specific mana provenance and command-zone cast-count-dependent entering counters need an exact mana rider/replacement primitive |

**Additional indexed blocker:** Chittering Dispatcher's three-player Myriad defender-choice regression
remains blocked (#34/#36/#181/#183). Its two-player cases passing cannot erase that rules defect.
**Additional acceptance backlog:** All Is Dust, Drowner of Hope, Oblivion Sower, Ruins of Oran-Rief,
Sifter of Skulls, Ulamog's Crusher and Shrine of the Forsaken Gods
need the specific missing negative/cost/timing cases listed in the 100-row audit. Imported upstream
cards stay evidence-unverified rather than being counted playable from their names.

## Integration intake and reversibility

- Base: Claude #171; #197 adds Confluence regressions. #199 contains the one new registered card,
  deterministic assets and corrected app inventory. Neither parent branch was edited by card work.
- #200 is an isolated unregistered blocker reproducer, with a runner that temporarily injects two
  candidate sources and cleans up on failure. It must remain outside normal runtime registration.
- #187/#188 intake is **test files only**, preserving their exact source and adding no duplicate Ugin
  implementation. The existing Ugin source is reused and all combined tests pass locally.
- Temple #158/#162 and Crossroads #192 remain source/runner candidates. Their original patches
  conflict in `ManaRestriction.kt` and `ManaPool.kt`; the clean-pin applicability check reproduced it.
  The other GPT lane now owns #202 (`20091368`), an additive source resolution with a 25-case
  combined engine/consumer gate. This report does not certify that gate or #171 runtime compatibility.
  Review serializers and every spell/ability payment context on the full Ulalek patch stack before
  runtime intake. No committed jar replacement is authorized or performed.
- Myriad #183's source patch targets a different upstream context. Reconcile it with #171's pinned
  patch stack and prove three-player controller/planeswalker defender choices before counting Dispatcher.
- Duplicate historical Eldrazi Temple candidates #158/#161/#162 represent **one card**, not three.
  Prefer #162's eight Android scenarios over the older six/seven-case variants. #169/#168/#167 are
  older integration stacks, not extra implementations to replay into #171.
- The integration preparation branch is isolated and reversible. **It is not a 100/100 release-ready
  branch.** No merge to main, runtime replacement, weekly-upgrade change, paid worker dispatch,
  signing, release or APK publication occurred.
