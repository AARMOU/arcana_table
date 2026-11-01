package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.MayCastWithoutPayingManaCost
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/** Darksteel Monolith — CMM #743. */
val DarksteelMonolith = card("Darksteel Monolith") {
    manaCost = "{8}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Indestructible\nOnce each turn, you may pay {0} rather than pay the mana cost for a colorless spell you cast from your hand."

    keywords(Keyword.INDESTRUCTIBLE)

    val colorlessSpells = GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless)
    staticAbility {
        ability = MayCastWithoutPayingManaCost(
            controllerOnly = true,
            firstSpellOfTurnOnly = false,
            spellFilter = colorlessSpells,
            oncePerTurn = true,
            fromZones = setOf(Zone.HAND),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "743"
        artist = "Sergey Glushakov"
        imageUri = "https://cards.scryfall.io/normal/front/f/5/f5b7d3df-4e9b-422b-a88c-43650a67a9c6.jpg?1783915478"
    }
}
