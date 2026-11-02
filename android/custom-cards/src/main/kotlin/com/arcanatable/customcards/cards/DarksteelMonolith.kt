package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.MayCastWithoutPayingManaCost
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/**
 * Darksteel Monolith — Commander Masters #743. {8} Artifact.
 *
 * "Once each turn, you may pay {0} rather than pay the mana cost for a colorless spell you cast
 * from your hand." Paying {0} instead of the mana cost is casting without paying the mana cost
 * (CR 118.9): additional costs still apply and X is 0. "Once each turn" includes opponents'
 * turns, so the permission uses `onAnyTurn = true` (engine patch 0026); it never changes timing,
 * so only an instant or a flash spell can use it on another player's turn. Each Monolith tracks
 * its own use; a spell paid for normally does not use it up.
 */
val DarksteelMonolith = card("Darksteel Monolith") {
    manaCost = "{8}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Indestructible\nOnce each turn, you may pay {0} rather than pay the mana cost for a colorless spell you cast from your hand."

    keywords(Keyword.INDESTRUCTIBLE)

    staticAbility {
        ability = MayCastWithoutPayingManaCost(
            controllerOnly = true,
            spellFilter = GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless),
            oncePerTurn = true,
            onAnyTurn = true,
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
