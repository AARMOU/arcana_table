# Eldrazi Incursion: five-card verification

**14 added scenarios, 23 focused cases, full 626/626 suite: zero failed, errored or skipped.**
The shipped engine jars remain unchanged. This review found and fixed two card-definition defects;
it does not certify the entire original deck. Updated categories are **37 reviewed acceptance /
53 imported definitions awaiting direct acceptance evidence / 0 current observed failures /
2 missing implementations / 8 qualified engine-runtime blockers**. Still 91 original names indexed.

| Card | Cases | Verified acceptance scope |
|---|---:|---|
| All Is Dust | 3 | Both players sacrifice colored permanents; colored artifacts/enchantments, multicolor, indestructible and hexproof do not escape; colorless/Devoid survive; generic {7} accepts colored mana; a dying observer sees simultaneous friendly and opposing deaths |
| Drowner of Hope | 5 | Actual ETB creates two Scions; sacrifice cost taps a creature; reject opposing Scions, ordinary creatures, Eldrazi Spawn and Scions lacking Eldrazi; reject noncreature target before paying cost; allow a noncreature Kindred Eldrazi Scion permanent as cost, including while Drowner is tapped and summoning sick |
| Oblivion Sower | 5 | Cast trigger exiles four, can choose zero or old exiled lands; own lands and nonlands excluded; opponent-owned lands remain eligible in another exile bucket; caster chooses and controls returned lands while ownership is preserved; entering without casting does nothing |
| Ulamog's Crusher | 5 | Annihilator sacrifices exactly two from four, only defending player; able Crusher cannot be omitted; tapped/summoning-sick Crusher cannot attack and creates no annihilator trigger |
| Shrine of the Forsaken Gods | 5 | Six vs seven lands; actual colorless spell payment; colored generic costs cannot consume restricted mana; Devoid colorless spell with a printed colored cost can; an activated ability cannot spend spell-only restricted mana; failed payments preserve the pool |

## Repaired defects

**Drowner:** `Costs.Sacrifice(GameObjectFilter.Creature...)` wrongly rejected a Kindred Enchantment
with both Eldrazi and Scion subtypes. Change only its type gate to `GameObjectFilter.Permanent`;
retain both subtype requirements and controller validation. The subtype-only Oracle description
means a permanent, and Kindred shares creature subtypes (CR 109.2, 205.3m, 308.2 in the official
[September 25, 2026 rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt)).
The unregistered fixture isolates that rule; it is not a new catalog card. The before-fix test fails
with `Cannot pay ability cost`; the same acceptance case now passes on the same runtime.

**Sower:** `CardSource.FromZone(EXILE, Player.Any, ownedByTargetPlayer())` resolves a single player
bucket. `Player.Any` is not an all-player collection operator. Change it to `Player.Each`, retaining
the target-opponent ownership predicate in gathering and selection. An opponent-owned land in the
caster's exile bucket was previously missed and produced no optional land decision; the same
case now offers exactly that land to the caster, excludes their own Forest/nonland cards and
puts it under the caster's control with opponent ownership intact.

Historical red JUnit is preserved under `investigation/eldrazi-incursion/five-card-verification/`;
current executable tests remain in the normal suite. No failing test was skipped or weakened.
Initial fixture mistakes are not reported as card defects: with no legal attackers the engine can
already declare the empty attack; and ability auto-payment can tap other available Plains, so the
Shrine restriction test now taps those unrelated sources before making its negative assertion.

## Reproduction and integration

Run the five named `*ScenarioTest` suites with `:custom-cards:test`, then the full custom-card suite
and `:custom-cards:exportCards`. Local verification used the existing standalone Gradle JVM harness
with repository sources, the exact dependencies and four unchanged committed jars; no engine mocks.
Two exports are byte-identical. Only two exported definitions change, the 250-name index is
unchanged, and all audit source/test/runtime hashes are verified. SDK filter/methods are in the
generated API index; multi-player references are documented in the vendored language reference
and confirmed by the committed-runtime execution.

Cardkit: 58 Python tests (one existing missing-javap skip), zero check/docs-check errors; pattern
references regenerated after the source/cache changes. Four nonfatal wording warnings concern
omitted reminder text, not mechanical substitutions. Existing registrations are reused.

Parent #210 is green in Development 40218625281 and Cardkit 40218625363, including #209's undo
fixture. This follow-up requires its own combined CI. No runtime replacement, main merge, weekly
workflow change, paid dispatch, signing or APK release occurred. See COVERAGE.md, coverage.json,
evidence/readiness-core-scenarios.json and evidence/five-card-verification.json.
