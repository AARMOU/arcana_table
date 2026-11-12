# Broodlord — rules review and executable acceptance matrix

**Ticket:** #359 (owner request #350); **classification source:** `gpt/arcana-integration-next@9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f`.
**Status:** review in progress; **NOT implemented, NOT tested, NOT Cardkit SUPPORTED**. This document is not permission for paid Copilot pickup, merge, or a release. The B140 test-APK baseline (`49d0b8c`) is a separate, diverged branch; do not port or count this review as a B140 change.

## Exact rules to preserve

Broodlord (40K #89) is a 3/3 Tyranid creature with cost `{X}{3}{G}`. It has Ravenous and an enters-the-battlefield trigger that distributes **X +1/+1 counters among any number of other target creatures you control**.

Ravenous places X counters on Broodlord **as it enters** (not later), and its draw trigger applies only when the **cast X is at least five**. The other-creature counter distribution is a separate ETB trigger. Targets and their nonzero allocations must be announced when the trigger is put on the stack; allocations cannot be reassigned after a target becomes illegal. A copied *spell* carries its X, but copying an already-existing battlefield permanent does not import its cast X.

## Pinned engine source inspection — building blocks found, composition unproven

The Cardkit integration base's `tools/cardkit/cardkit_lib/repo.py` pins `wingedsheep/argentum-engine@7fb073337e4389cfabe2ebf4c6416633a7fc63d2`. Its committed `docs/agents/generated/sdk-api.md` exposes `ravenous(CardBuilder)`, `DynamicAmounts.castX()`, `DistributeCountersAmongTargets(DynamicAmount, String, Int)` and `targets(... dynamicMaxCount ...)`.

* Arcana `Zoanthrope.kt` + `ZoanthropeScenarioTest.kt` already exercise Ravenous and `DynamicAmounts.castX()` in a separate ETB trigger, including X=3 and X=5 draw behavior.
* Upstream `ElusiveOtter.kt` (Grove's Bounty) uses `targets(TargetFilter.CreatureYouControl, unlimited = true, dynamicMaxCount = DynamicAmounts.xValue())` and `Effects.DistributeCountersAmongTargets(DynamicAmounts.xValue())` for a chosen X pool.
* Upstream `QuirionBeastcaller.kt` uses dynamic max target count on a trigger and `DistributeCountersAmongTargets` for counters with last-known information.
* Upstream `FelidarSavior.kt` uses `TargetFilter.OtherCreatureYouControl` to exclude the ETB source.
* Upstream `DistributeCountersAmongTargetsExecutor.kt` explicitly retains announced distribution and discards counters assigned to targets that become illegal.

**Hypothesis to test, not certified implementation:** combine `ravenous()` with a `Triggers.self.enters()` trigger, `targets(TargetFilter.OtherCreatureYouControl, unlimited = true, dynamicMaxCount = DynamicAmounts.castX())`, and `Effects.DistributeCountersAmongTargets(DynamicAmounts.castX())`. The essential uncertainty is whether the chosen **cast X** is still available when the ETB target cap and distribution are evaluated (and whether the split is announced at trigger time). Do not replace this with a resolution-time arbitrary allocation or use an app-side rule approximation.

## Mandatory executable positive/negative scenarios

1. **X=0:** pay `{3}{G}`; Broodlord enters as 3/3, no draw, no counters distributed, no required target; no invalid zero-counter target.
2. **X=2, one friendly target:** Broodlord enters as 5/5; one other creature receives exactly two counters; no draw.
3. **X=2, two friendly targets:** choose both, announce 1+1, and verify each receives one. Attempting **three** targets must be rejected (each needs at least one).
4. **X=5:** Broodlord enters as 8/8, Ravenous draws **one**, and the separate trigger distributes **five** counters. Draw and distribution must not double-trigger.
5. **Illegal targets:** announce 2+1 on two friendly creatures; make one illegal before resolution. The surviving creature keeps only its originally allocated share; the missing share is lost, not reassigned.
6. **Target restriction:** Broodlord itself and opposing creatures must not be legal recipients; controller may choose zero targets.
7. **Cast-X persistence:** X in both ETB abilities must be the paid cast value, not zero from a battlefield card's printed `{X}` cost, nor the count of counters after replacement effects.
8. **Copy behavior:** a copied Broodlord *spell* retains X; a copy of an existing permanent does not inherit a prior cast X. Test if supported by current harness; otherwise record as an explicit unverified edge case, not a pass.

Use **real Argentum scenario tests**, not just source snapshots, index presence, or Cardkit similarity. Verify legal decisions, priority, and opponent responses. Record exact source SHA, test count, failed/skipped counts, and CI run. No card should be registered/shipped until positive and negative cases pass.

## Handoff / routing

Cardkit's current `NEEDS_REVIEW → gpt` verdict (ticket #359) is correct until evidence is produced. There is **no verified new engine primitive gap yet**: the required component APIs appear present. If an executable scenario demonstrates missing X propagation, isolate that **general-purpose authoritative engine** defect with a minimal failing test; do not invent a Broodlord-specific workaround.

After positive/negative tests establish full behavior, rerun `tools/cardkit/cardkit classify --verbose -- "Broodlord"` on the **current feeder integration base**. Only route back to Copilot when it actually reports `SUPPORTED / copilot`; otherwise keep the ticket open with the exact remaining blocker. Do not edit the owner's original request #350.
