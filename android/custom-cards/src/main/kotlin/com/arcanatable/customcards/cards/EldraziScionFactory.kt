package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Real Eldrazi Scion token: sacrifice-as-cost mana ability, usable without the stack. */
internal fun createEldraziScions(count: Int): CreateTokenEffect = createEldraziScions(DynamicAmount.Fixed(count))

/** Same Scion token, for "a Scion for each …" counts evaluated at resolution. */
internal fun createEldraziScions(count: DynamicAmount): CreateTokenEffect =
    Effects.CreateToken(
        count = count,
        power = 1,
        toughness = 1,
        creatureTypes = setOf("Eldrazi", "Scion"),
        name = "Eldrazi Scion",
        activatedAbilities = listOf(
            ActivatedAbility(
                id = AbilityId.next(),
                cost = Costs.SacrificeSelf,
                effect = Effects.AddColorlessMana(1),
                isManaAbility = true,
                timing = TimingRule.ManaAbility,
            )
        ),
    )
