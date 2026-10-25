# Angelic Aberration — base stat threshold preflight (unverified candidate)

**Original deck slot:** Angelic Aberration, M3C 39; still **UNSUPPORTED**. Do not register or export from this branch.

**Source pin:** wingedsheep/argentum-engine `c38f145359c342a04f951b7ba48f741bcb1627ac` (Build 133 upgraded upstream).

## Patch contract
- `0001-base-pt-at-most.patch` introduces `BasePowerAtMost` / `BaseToughnessAtMost`, a `basePowerOrToughnessAtMost(1)` filter builder, and matching support in the SDK, projected/LKI predicate evaluation, triggers, mana-cost filters and zone/cast filters. Null/noncreature base stats cannot qualify; a face-down 2/2 does not qualify for max=1.
- `0002-angelic-scenario-use-base-at-most.patch` changes only the existing Angelic Aberration **real-engine probe** to invoke the new primitive; preserve both original assertions. It should pass for a base-1/2 and base-0/2 creature. Later tests also must include creature with +1/+1 counters (base 0 or 1, modified P/T > 1), source with base 2/2 (negative), and true sacrifice count/Angel token output.
- These are separate data patches. Never apply the upstream patch to committed shared Android/engine JARs without the upgrade review.

## Required CI and acceptance (NOT YET RUN)
1. Check exact upstream SHA, verify each patch file against the correct repository tree, and apply with `git apply --check` / `git apply` on an **ephemeral** runner (no `--reject`, no skipped hunks).
2. Build Java-17-compatible coherent Argentum SDK/rules/tooling/AI as applicable; verify all exhaustive Kotlin branches compile, serialization and generated SDK index.
3. Inject second test-only patch into the isolated #217 Arcana test source on that same disposable runner. Run the two existing Angelic Aberration tests **without skip/expected-failure**, plus negative tests for current-stat modification and base 2/2 in a follow-up.
4. Run the full mandatory Arcana core patches, Ulalek/Temple/Crossroads/Azlask patches, Cardkit/custom-cards and Android regressions on one consistent upgraded engine.
5. Only after green test evidence: implement the **actual** Angelic Aberration card, register, export deterministically, recheck original 100-card inventory and full-game AI/Fold behavior. Until then this patch earns **zero** new indexed/playable cards.

**No** Build 133 APK replacement, shared branch merge, production signing, billing override, or paid Copilot/Claude dispatch.
Related: #81, #141, #150, #217, #235.