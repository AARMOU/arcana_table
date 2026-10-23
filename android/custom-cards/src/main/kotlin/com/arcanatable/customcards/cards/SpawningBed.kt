package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule

/** Each Scion is a real 1/1 with its own immediate, stackless sacrifice-for-{C} mana ability. */
val SpawningBed = card("Spawning Bed") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{6}, {T}, Sacrifice this land: Create three 1/1 colorless Eldrazi Scion " +
        "creature tokens. They have \"Sacrifice this token: Add {C}.\""

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{6}"), Costs.Tap, Costs.SacrificeSelf)
        effect = Effects.CreateToken(
            count = 3,
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
                ),
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        artist = "Jason Felix"
    }
}
