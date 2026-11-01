package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player

val SceptreOfEternalGlory = card("Sceptre of Eternal Glory") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Legendary Artifact"
    oracleText = "{T}: Add one mana of any color.\n{T}: Add three mana of any one color. Activate only if you control three or more lands with the same name."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddAnyColorMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddAnyColorMana(3)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(
                Conditions.CompareAmounts(
                    DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).largestSameNameGroup(),
                    ComparisonOperator.GTE,
                    3,
                )
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "166"
        artist = "Michał Miłkowski"
        imageUri = "https://cards.scryfall.io/normal/front/b/c/bc7f3009-c852-41e7-8e62-d2ee637b3998.jpg?1783920812"
    }
}
