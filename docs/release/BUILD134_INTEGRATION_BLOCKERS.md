# Build 134 isolated integration: exact-head blocker ledger (2026-10-28)

**Scope:** draft PR #346, branch `gpt/build134-integrated-source-20261028`, inspected source head `bbaf7ca8978ff750f6ac4eecf88264b77935d161`. This document records *failed* integration gates; it does not certify Build 134 or authorize a merge/build. First release remains two-player, premade-only, with deck import disabled. Frozen staged release: PR #254. Source-validation lineage: PR #260 and successful workflow #40678286737. Source integration: PR #346.

## 1. Bundled engine JAR/API mismatch — Android Development CI

[Exact-head run #40705576989](https://github.com/AARMOU/arcana_table/actions/runs/40705576989), job `122876709390`, failed at **Regenerate custom-card assets**, `:custom-cards:compileKotlin`, before app release-variant JUnit could execute. Kotlin diagnostics identify:

| Custom-card source | Missing authoritative Argentum SDK contract |
| --- | --- |
| `AbaddonTheDespoiler.kt:34-35` | `maxManaValue`, `duringYourTurnOnly` named parameters |
| `CorruptedCrossroads.kt:31` | `DevoidSpellsOnly` |
| `EldraziTemple.kt:35` | `ColorlessSubtypeSpellsOrAbilitiesOnly` |
| `UlalekFusedAtrocity.kt:7,26-27` | `StackObjectKind`, `CopyControlledStackObjects` |

The `Cannot infer type` errors at Ulalek are downstream of missing symbols; do **not** edit the card to fake stack copies or bypass authoritative rules. Both `android/app/build.gradle.kts` and `android/custom-cards/build.gradle.kts` load `android/app/libs/argentum-*.jar` from the checkout, so the successful source-only patch application does not rebuild or update the runtime SDK.

**Required engineering handoff:** Identify the exact Argentum source revision supplying *all* missing primitives; rebuild the four pinned runtime JARs from that source in an isolated validation lane; record source SHA, each JAR SHA256 and reproducible build commands; only then rerun custom-card compilation, positive/negative card scenario tests, Android debug and release tests. Do not commit unreviewed JARs or modify shared engine ownership.

## 2. Cardkit integrity — separate failure, not a cosmetic count

[Exact-head Cardkit run #40705576919](https://github.com/AARMOU/arcana_table/actions/runs/40705576919), job `122876876562`: 59 tests, **5 failures**. Two tests reject **Azlask, the Swelling Scourge** because the registry exposes a source file still marked `CARDKIT-SCAFFOLD/CARDKIT-TODO`. The six-precon audit expects Eldrazi Incursion **93/100**, but generated output claims **94/100**; this is a *coverage discrepancy*, not proof of an additional fully rules-certified card. Two other tests reject stale generated Cardkit SDK and pattern indexes.

**Ownership:** inspect existing Azlask implementation drafts #228 and #235; route the registry/scaffold resolution to that owner. Do not silently remove scaffold markers, waive the registered-scaffold assertion, or bump the committed 93/100 expectation just to turn CI green. After the authoritative implementation/registration decision, regenerate indexes via `tools/cardkit/cardkit sdk-index` and `tools/cardkit/cardkit patterns`, audit diffs, and rerun Cardkit.

## 3. Release acceptance still outstanding

The new `ReleaseVariantScopeTest.kt` has three permanent tests for release `BuildConfig` feature flags, persisted AI opponent count, and premade-only deck visibility without deleting saved data. Development CI invokes these with `:app:testReleaseUnitTest`, but the upstream custom-card compilation failure prevented execution on this head. A successful historical source-validation run does **not** substitute for integrated-source Android compilation, scenario tests, release-variant JUnit, tutorial tests, and packaging verification on the final exact head.

**No merge, APK/AAB, signing, Cloudflare deployment, paid Copilot worker, or release authorization follows from this ledger.**
