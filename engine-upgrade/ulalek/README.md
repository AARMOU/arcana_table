> **Moved (2026-10-25):** the six engine patches now live in `argentum-patches/0011-0016` and are applied by `engine-upgrade/audit_patches.py`, so the weekly upgrade carries them forward. `apply_patches.py` only confirms them, copies `engine-tests/` and writes `apply-report.json`. The patch numbers below refer to the old folder.

# Precon engine candidate: Ulalek, Zhulodok, Abaddon (Claude lane, #132 / #81)

Engine support needed by **Ulalek, Fused Atrocity** that the shipped Build 131 runtime lacks:

| Patch | What it adds |
|---|---|
| `0004-stack-object-copying.patch` | `CopyControlledStackObjects`: copy all spells, then all other activated/triggered abilities a player controls, as frozen batches the controller orders. Widens the former `CopyEachTargetSpellEffect` (same serial name, so existing card data such as Display of Power still decodes). |
| `0005-colorless-hybrid-sdk.patch` | `{C/W}`…`{C/G}` mana symbols (`ManaSymbol.ColorlessHybrid`) in the SDK. |
| `0006-colorless-hybrid-floating-payment.patch` | Paying those halves from floating mana. |
| `0007-colorless-hybrid-auto-tap.patch` | Auto-tap support for those halves. |
| `0008-granted-cascade.patch` | A *granted* CASCADE keyword (`GrantKeywordToOwnSpells`) becomes real cast triggers, one per grant instance, with mana value read as cast (X included). Needed by Zhulodok and, with further work, Abaddon. This is PR #60's `engine-patches/0001-granted-cascade.patch`, renamed. |

| `0009-granted-keyword-mana-value-cap.patch` | **New (Claude).** `GrantKeywordToOwnSpells` gains `maxManaValue: DynamicAmount?` and `duringYourTurnOnly`. Granted cascade evaluates the cap for the grant's controller at cast; every other reader of a capped grant fails closed. Both fields default to off and are omitted from JSON, so existing card data is unchanged. Docs and sdk-index were updated in the same patch. Needed by Abaddon the Despoiler. |

`engine-tests/` holds kotest specs that `apply_patches.py` drops into `rules-engine` tests: PR #60's `GrantedCascadeEngineTest` and the new `GrantedCascadeManaValueCapEngineTest`.

Patches 0004–0008 are PR #60's (`f159dea`), **byte-identical**, with their sha256 values pinned in `apply_patches.py`.
They apply exactly on `wingedsheep/argentum-engine@61e71e5` after the mandatory Arcana baseline
(`engine-upgrade/audit_patches.py`). GPT's PR #136 established this first.

`.github/workflows/ulalek-engine-candidate.yml` builds the candidate from source with the engine's
own Gradle build. It runs the engine suites, installs the rebuilt jars into the app **inside the CI
workspace only**, and runs the custom-card and Android suites against them. The committed runtime
jars are never replaced by this lane. That is a separate, owner-approved gate.

The registered Ulalek (`android/custom-cards/.../UlalekFusedAtrocity.kt`) and the regenerated
`custom-cards.{jsonl,index.tsv}` therefore need these patched jars. On the shipped jars the
custom-card module does not compile and the data line does not load.
