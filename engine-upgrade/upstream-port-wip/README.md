# Upstream engine port: work in progress (NOT used by B141)

Saved 2026-11-01 so the later full upgrade resumes from here instead of starting over. B141 does not use this; it
ships the exact B140 engine plus targeted backports (`engine-upgrade/b141/`).

## What is done
- **Target upstream:** `wingedsheep/argentum-engine@c1c04ca3265c61df913af6db65460ad4ce409614` (int-base's weekly upgrade).
- `series/0001–0008`: int-base's rebased Arcana patches 0001–0008. They apply cleanly in order on `c1c04ca`.
- `series/0009`: B140's `0012-colorless-hybrid-sdk`, replayed cleanly on top.
- B140's exact engine reproduces as upstream `61e71e58` + the 19 manifest patches, in manifest order. 0004/0007
  must use `engine-upgrade/forward-ports/` for that upstream.

## What remains (3-way cherry-pick from the B140 series onto the above)
Conflicts measured each in isolation (some only conflict because a predecessor isn't applied yet):

| B140 patch | Conflicting files on `c1c04ca` |
|---|---|
| 0011 stack-object copying | `docs/sdk-index.md`, `CopyEachTargetSpellExecutor.kt` (deleted upstream), `CopyTargetSpellOrAbilityExecutor.kt`, `CopyTargetTriggeredAbilityExecutor.kt`, `SpellCopyTargets.kt`, `StackPlacement.kt` |
| 0013 colorless-hybrid floating payment | `ManaPool.kt` |
| 0014 colorless-hybrid auto-tap | `ManaSolver.kt` |
| 0015 granted cascade | `GrantedKeywordResolver.kt` |
| 0016 granted-keyword MV cap | `CastTriggers.kt`, `GrantedKeywordResolver.kt` |
| Temple 0001 | `ManaRestriction.kt`, `sdk-surface-baseline.txt`, `ManaPool.kt` |
| Devoid 0002 | `ManaPool.kt`, `PaymentSubtypes.kt` |
| build140 0001 Devoid SDK surface | `sdk-surface-baseline.txt` |
| build140 0002 Temple subtypes | `ManaPool.kt` |
| build140 0003 Azlask exact colors | `TriggerMatcher.kt` |

Per-row intent, existing tests and `upstream-native / port-needed / blocked` classification: Work/Codex's 1a
matrix on #81 (comment 6549165806). Int-base reconciliation (1b): #81 comment 6552272962.

## How to resume
```
git -C argentum-engine checkout c1c04ca3265c61df913af6db65460ad4ce409614
git -C argentum-engine am engine-upgrade/upstream-port-wip/series/*.patch
# build the B140 series (61e71e58 + 19 patches as commits), then for each remaining patch in manifest order:
git -C argentum-engine cherry-pick <b140-series-commit>   # resolve; prefer upstream-native where upstream now has it
```
Gate each patch on its embedded engine tests before the next.
