# Corrupted Crossroads — exact Devoid mana restriction (candidate)

Issue: #178. Separate GPT lane while Claude fixes the weekly upgrader (#189/#190) and ShockFetch (#191).

**Do not register, ship, or count this card as playable yet.** The pinned runtime does not have an exact mana restriction for **spells with the Devoid keyword**. Existing `ColorlessSpellsOnly` incorrectly allows plain artifacts and face-down colorless spells.

- `patches/0001-devoid-spell-mana.patch`: isolated patch against upstream Argentum pin 61e71e586b100faa661f86cac827ac3c3bbb5a78, adding serializable `ManaRestriction.DevoidSpellsOnly`, fail-closed spell-context flag, and keyword projection from `CardComponent.baseKeywords`.
- `engine-tests/CorruptedCrossroadsDevoidRestrictionTest.kt`: six engine eligibility positive/negative assertions.
- `candidate/CorruptedCrossroads.kt`: two actual tap mana abilities; colored mode pays one life and produces restricted mana only.
- `candidate/CorruptedCrossroadsScenarioTest.kt`: six Android consumer scenarios, including Devoid casting and non-Devoid colored/colorless rejection.

Candidate workflow builds temporary source jars and performs temporary test-only card registration/export in its runner; no built JAR, app index, manifest or signing changes are committed. CI **must pass at this exact head** before source integrity is claimed; runtime integration and Android release packaging require separate approval. Verify impact on automatic mana tapping, saving/loading restricted mana and off-battlefield casts in integration review.

Oracle basis: https://mtg.wtf/card/m3c/332/Corrupted-Crossroads

Do not claim a higher Eldrazi Incursion readiness percentage from this isolated work alone.
