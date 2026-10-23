package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val DefenseOfTheHeart = card("Defense of the Heart") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "At the beginning of your upkeep, if an opponent controls three or more creatures, sacrifice " +
        "this enchantment, search your library for up to two creature cards, put those cards onto the " +
        "battlefield, then shuffle."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        interveningIf = Conditions.CompareAmounts(
            DynamicAmounts.battlefield(Player.EachOpponent, GameObjectFilter.Creature).count(),
            ComparisonOperator.GTE,
            3,
        )
        effect = Effects.SacrificeTarget(EffectTarget.Self) then
            Patterns.Library.searchLibrary(
                filter = GameObjectFilter.Creature,
                count = 2,
                destination = SearchDestination.BATTLEFIELD,
                shuffleAfter = true,
            )
        description = "At the beginning of your upkeep, if an opponent controls three or more creatures, " +
            "sacrifice this enchantment, search your library for up to two creature cards, put those " +
            "cards onto the battlefield, then shuffle."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "245"
        imageUri = "https://cards.scryfall.io/normal/front/9/e/9e93381f-627f-41b6-b1b6-45d712a44d8e.jpg?1783946231"
        artist = "Pete Venters"
    }
}
