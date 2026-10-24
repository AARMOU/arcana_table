# Ulalek engine candidate (Claude lane, #132)

Engine support needed by **Ulalek, Fused Atrocity** that the shipped Build 131 runtime lacks:

| Patch | What it adds |
|---|---|
| `0004-stack-object-copying.patch` | `CopyControlledStackObjects`: copy all spells, then all other activated/triggered abilities a player controls, as frozen batches the controller orders. Widens the former `CopyEachTargetSpellEffect` (same serial name, so existing card data such as Display of Power still decodes). |
| `0005-colorless-hybrid-sdk.patch` | `{C/W}`…`{C/G}` mana symbols (`ManaSymbol.ColorlessHybrid`) in the SDK. |
| `0006-colorless-hybrid-floating-payment.patch` | Paying those halves from floating mana. |
| `0007-colorless-hybrid-auto-tap.patch` | Auto-tap support for those halves. |

The patches are PR #60's (`f159dea`), **byte-identical**, with their sha256 values pinned in `apply_patches.py`.
They apply exactly on `wingedsheep/argentum-engine@61e71e5` after the mandatory Arcana baseline
(`engine-upgrade/audit_patches.py`). GPT's PR #136 established this first.

`.github/workflows/ulalek-engine-candidate.yml` builds the candidate from source with the engine's
own Gradle build. It runs the engine suites, installs the rebuilt jars into the app **inside the CI
workspace only**, and runs the custom-card and Android suites against them. The committed runtime
jars are never replaced by this lane. That is a separate, owner-approved gate.

The registered Ulalek (`android/custom-cards/.../UlalekFusedAtrocity.kt`) and the regenerated
`custom-cards.{jsonl,index.tsv}` therefore need these patched jars. On the shipped jars the
custom-card module does not compile and the data line does not load.
