package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule

val WhispererOfTheWilds = card("Whisperer of the Wilds") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Shaman"
    power = 0
    toughness = 2
    oracleText = "{T}: Add {G}.\nFerocious — {T}: Add {G}{G}. Activate only if you control a creature with power 4 or greater."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN, 2)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(
                Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4))
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "187"
        artist = "David Gaillet"
    }
}
