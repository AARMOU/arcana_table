package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.effects.SearchDestination

val SavageOrder = card("Savage Order") {
    manaCost = "{2}{G}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "As an additional cost to cast this spell, sacrifice a Dinosaur.\nSearch your library for a Dinosaur creature card, put it onto the battlefield, then shuffle."

    additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Creature.withSubtype("Dinosaur").youControl()))
    spell {
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Creature.withSubtype("Dinosaur"),
            count = 1,
            destination = SearchDestination.BATTLEFIELD,
            shuffleAfter = true,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "6"
        imageUri = "https://api.scryfall.com/cards/rex/6?format=image"
    }
}
