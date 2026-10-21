# Cross-deck mechanics audit — preliminary (upstream b118c4b)

This is a **static source reconnaissance**, not a substitute for tests or proof that Arcana's pinned Build 124 JAR supports the same features.

| Mechanic | Upstream inspection | Required regression before shipping |
|---|---|---|
| Annihilator N | **Display-only numeric keyword** in some definitions; `Nulldrifter.kt` lowers its behavior to an explicit on-attack trigger. Merely adding `KeywordAbility.Numeric` does **not** implement sacrifices. | Attack trigger timing, defending-player selection, N distinct sacrifices, canceled combat/removed attacker, multiplayer defending player |
| Squad | `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/mechanics/Squad.kt` provides a linked additional-cost and enters trigger helper. | Extra payments, exact number of token copies, commander copying / ETB effects, simultaneous trigger handling |
| Ravenous | `RavenousDsl.kt` provides linked cast-time X / enters-with-counters / intervening-if draw. | X=0, X=4 vs X=5, cast vs put onto battlefield, token copies, counter replacement effects |
| Cascade | A `CascadeExecutor.kt` exists. Existence is not verified end-to-end deck compatibility. | Cast timing, mana-value comparison, may-cast decline, no eligible cards, spells copied/cast from exile |
| Colorless mana | Build 124 app uses a pinned earlier engine, while upstream has evolved. | Actual `{C}` payment, generic vs colorless distinction, Eldrazi mana rocks, Wastes |

## Work rules

- Implement exact Oracle behavior, not a visual/keyword placeholder.
- Do not promote an upstream helper existing in the current `main` source into a statement about the Android Build 124 runtime. The app uses independently pinned JARs.
- For each card, choose original canonical printing, plus separate `Printing` rows for requested precons.
- Keep scenario tests **one test file per card**, plus engine-level mechanics tests when the underlying mechanics change.
- No rules changes or AI behavior changes should go into the existing engine-delay or production branches as part of an unverified precon import.
- Android integration requires rebuilding all compatible engine libraries, exporting the new card names, and demonstrating the app actually loads and executes them.
