# Twins of Discord — exact-engine implementation contract

Status: **NOT IMPLEMENTED**. This is a rules/test contract, not a registered card, exported definition, or deck-coverage claim.
Owner lane: GPT Eldrazi Incursion. Base: `gpt/arcana-integration-next`. Do not modify Claude's weekly-upgrade system.

## Oracle / identity
- Name: Twins of Discord
- M3C #37; colorless Creature — Eldrazi; mana cost {7}; 8/6.
- Whenever you attack, choose odd or even. Creatures with mana value of that quality can't block this turn. (Zero is even.)
- Each other colorless creature you control has bloodthirst 2. (If an opponent was dealt damage this turn, that creature enters with two additional +1/+1 counters on it.)

## Required engine semantics
1. **Attack trigger** fires when its controller declares one or more attackers, even if Twins itself does not attack. It triggers once per declare-attackers event, not once per attacking creature.
2. **Choice on resolution:** controller selects odd or even as the trigger resolves. Do not hard-code even, choose randomly, or preselect on attack declaration.
3. **Blocking prohibition:** only creatures whose *current battlefield mana value* has the chosen parity cannot block this turn. Zero is even; face-down creatures and ordinary non-copy creature tokens generally have mana value zero. Other parity remains able to block if otherwise legal.
4. **Duration:** restriction expires at end of turn, not permanently; multiple triggers/effects coexist correctly.
5. **Bloodthirst grant:** continuous static ability grants bloodthirst 2 to **other colorless creatures controlled by Twins' controller**, including creatures entering while Twins is already on the battlefield. It does not grant to Twins itself, colored creatures, or opponents' creatures.
6. **ETB replacement timing:** if any opponent has been dealt damage this turn before the creature enters, that other colorless creature enters *with* two +1/+1 counters (not an ETB triggered counter placement).
7. **Stacking:** multiple Twins or pre-existing bloodthirst abilities stack their values; two Twins grant bloodthirst 2 + 2, giving four counters on qualifying entry. A Twins entering while another Twins is present gets the other one's bloodthirst grant.
8. **Negative:** damage to only yourself does not enable bloodthirst; no opponent damage this turn yields zero counters; Twins leaving battlefield removes its grant for future entries.

## Executable engine scenarios to implement before registration
| Scenario | Setup/action | Assert |
|---|---|---|
| attack-even | Twins stays untapped; another friendly creature attacks; choose even on resolution | Opponent's MV 0/2/4 creatures cannot block; MV 1/3 can block |
| attack-odd | One attacker; choose odd | Opponent's MV 1/3 cannot block; MV 0/2 can |
| trigger-once | Attack with 3 creatures | One choice trigger, not three |
| expire | Choose even; advance to next turn | Previous restriction gone |
| token-zero | Opponent controls ordinary creature token | Token prohibited only when even chosen |
| bloodthirst-positive | Opponent damaged this turn; then colorless creature enters under controller | Enters with exactly 2 +1/+1 counters |
| bloodthirst-negative | No opponent damaged; colorless creature enters | Enters with no added counters |
| bloodthirst-colored | Opponent damaged; colored creature enters | No counters from Twins |
| bloodthirst-opponent | Opponent damaged; creature enters under opponent | No counters from Twins |
| bloodthirst-self | Controller dealt damage to self only; colorless creature enters | No counters |
| bloodthirst-stack | Two Twins on battlefield; opponent damaged; third colorless creature enters | Enters with 4 counters |
| bloodthirst-exit | Twins leaves; opponent damaged; colorless creature enters | No counters from Twins |
| bloodthirst-twins | Single Twins enters after opponent damaged | Does not grant itself bloodthirst |

## Integration gates
- First inspect pinned Argentum engine for a dynamic *choice-based, parity-filtered can't-block* effect and a static *granted ETB replacement* for bloodthirst. **Do not approximate either effect.**
- Only after exact engine support: add `TwinsOfDiscord.kt`, register in `CustomCards.kt` with `M3C`, implement executable positive/negative engine tests, re-export deterministic JSONL/index, and verify matching runtime + Android CI.
- Until all gates pass, do **not** mark this original Eldrazi Incursion slot supported or substitute a fake vanilla 8/6.

## Pinned SDK inspection — 2026-10-25

Read `docs/agents/generated/sdk-api.md` from development. This index is generated from the committed Argentum SDK JAR, not from speculative upstream documentation.

**Available public primitives (verified in SDK index):**
- `GameObjectFilter.manaValueIsEven()` and `manaValueIsOdd()`
- `Effects.CantBlockGroup(GroupFilter, Duration)` and `CantBlock(EffectTarget, Duration, EffectTarget)`
- `Effects.GrantStaticAbility(StaticAbility, EffectTarget, Duration)`
- `Effects.GrantTriggeredAbility(TriggeredAbility, EffectTarget, Duration)`
- `Conditions.AmountIsEven(DynamicAmount)` / `AmountIsOdd(DynamicAmount)`
- `Triggers...attacks(...)` forms, but exact semantics of attack-event vs individual-creature attacks remain to be inspected.

**Missing from public SDK index:** No `Keyword.BLOODTHIRST`, no `bloodthirst(...)` builder, and no directly named parity-choice helper were found in the generated index. The SDK exposes general-purpose replacement effects, but this alone **does not establish** that a granted *entering-with-counters* replacement is expressible.

**Decision:** **ENGINE INVESTIGATION REQUIRED**. The even/odd filtering and group cannot-block primitives exist, but player choice and attack-event semantics are not yet verified. Bloodthirst ETB replacement granting is an apparent capability gap pending inspection of upstream engine internals. Do **not** substitute an ETB trigger (it differs in timing and counter interactions) or a plain counter-grant effect.

**Next exact-source investigation:** inspect the pinned Argentum implementation of `ReplacementEffect`, continuous static ability granting, `Triggers.you.attacks` semantics, and choice resolution. If the ETB grant is not representable, make a focused engine-source candidate with executable replacement/stacking tests; do not ship a partial card.
