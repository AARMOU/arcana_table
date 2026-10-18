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

val ShorikaiGenesisEngine = card("Shorikai, Genesis Engine") {
    manaCost = "{2}{W}{U}"
    colorIdentity = "WU"
    typeLine = "Legendary Artifact — Vehicle"
    power = 8
    toughness = 8
    oracleText = "{1}, {T}: Draw two cards, then discard a card. Create a 1/1 colorless Pilot creature token with " +
        "\"This token crews Vehicles as though its power were 2 greater.\"\nCrew 8"

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Patterns.Hand.loot(draw = 2, discard = 1) then Effects.CreateToken(
            power = 1,
            toughness = 1,
            creatureTypes = setOf("Pilot"),
            staticAbilities = listOf(CrewSaddleContribution(modifier = 2, costs = setOf(CrewSaddleCost.CREW))),
        )
        description = "{1}, {T}: Draw two cards, then discard a card. Create a 1/1 colorless Pilot creature token " +
            "with \"This token crews Vehicles as though its power were 2 greater.\""
    }

    keywordAbility(KeywordAbility.crew(8))

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "224"
        imageUri = "https://cards.scryfall.io/normal/front/9/6/969ac7dd-f3aa-4888-9ff0-d16a31b5e7a9.jpg?1783923999"
        artist = "Paolo Parente"
    }
}
