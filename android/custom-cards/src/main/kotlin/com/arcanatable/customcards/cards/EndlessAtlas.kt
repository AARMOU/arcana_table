package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator

val EndlessAtlas = card("Endless Atlas") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{2}, {T}: Draw a card. Activate only if you control three or more lands with the same name."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
        effect = Effects.DrawCards(1)
        restrictions = listOf(ActivationRestriction.OnlyIfCondition(
            Conditions.CompareAmounts(
                DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).largestSameNameGroup(),
                ComparisonOperator.GTE,
                3,
            )
        ))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "947"
        artist = "Titus Lunter"
    }
}
