# Upstream engine port (catch-up)

Goal: put Arcana on current upstream Argentum once, so the Monday weekly upgrade can carry the app forward from then
on. B141 still ships the B140 engine plus targeted backports (`engine-upgrade/b141/`) until this port is validated
and the owner approves the swap.

## State (2026-11-01)
- **Upstream:** `UPSTREAM` = `wingedsheep/argentum-engine@c1c04ca3265c61df913af6db65460ad4ce409614`. It already
  contains Encore, so the B141 Encore backport is not needed on this base.
- **`series/`:** all 19 B140 manifest patches, ported in manifest order. They apply cleanly with `git am` on `UPSTREAM`.
- **Local checks done:** `:rules-engine:compileKotlin` and `:rules-engine:compileTestKotlin` pass. The 0011
  stack-copy engine tests pass (60/60), as does `StackObjectCopySdkTest`.
- **CI:** `.github/workflows/engine-port-validate.yml` runs the full mtg-sdk and rules-engine suites, builds the four
  JARs, and runs every Arcana custom-card test on them. It is validation only: no staging and no APK.

## How conflicts were resolved
In every case Arcana's behaviour is kept, built on upstream's newer code. Where upstream already provides a
capability, upstream's version is used.

| Patch | Resolution |
|---|---|
| 0011 stack-object copying | Upstream deleted `CopyEachTargetSpellExecutor`; Twinning Staff now goes through `CopyTargetSpellExecutor`. Kept upstream's `triggerCause` and departed-ability last-known info, added Arcana's `emitTriggerEvent` and multi-object copy. |
| 0013 colorless-hybrid pool | Built on upstream's `spendAsAnyColor` / snow `allocatesExactly`. `{C/x}` added to `substitutes` and the partial-pay options. |
| 0014 colorless-hybrid auto-tap | Built on upstream's generic-barred retry pass, `remainingSources`, `colorlessPays` and snow stand-in. |
| 0015 / 0016 granted cascade, MV cap | Upstream's projected spell `colors` (Mycosynth Lattice) is threaded alongside Arcana's cast-time mana value and `grantActive`. |
| Temple / Devoid / build140 0001–0002 | Added next to upstream's `ColoredSpellsOnly` and `isClassLevelUpActivation`. Devoid and colorless checks read the projected spell colors. SDK surface baseline is the sorted union. |
| build140 0003 Azlask | `HasExactlyColors` uses upstream's own `colors()` helper (layer 5, off-battlefield, then printed), so the patch is three lines. |

## Card sources that change with the engine
`cards/` mirrors `android/`. It holds card files that must be swapped in together with the new JARs, because
upstream retired an SDK type they use:
- `PrimalAmulet.kt`: `ManaSpellRider.CopySpellWhenSpent` became `TriggersWhenSpent(filter, CopyTargetSpell(TriggeringEntity))`.
  This is upstream #3746, which is how upstream migrated Pyromancer's Goggles. Taken from int-base (6ea1936).

## Reproduce
```
git -C argentum-engine checkout $(cat engine-upgrade/upstream-port-wip/UPSTREAM)
git -C argentum-engine am --3way engine-upgrade/upstream-port-wip/series/*.patch
```

## Next
1. CI green on the engine suites and the card tests. Fix any failures in the series.
2. Point the weekly upgrade's manifest and pin at this series and `UPSTREAM`, so Monday runs start from here.
3. With owner approval, stage these JARs on the B141 candidate in place of the B140-plus-backports JARs.
