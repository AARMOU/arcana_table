package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val HomingSliver = card("Homing Sliver") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Sliver"
    power = 2
    toughness = 2
    oracleText = "Each Sliver card in each player's hand has slivercycling {3}. ({3}, Discard this card: Search your library for a Sliver card, reveal it, and put it into your hand. Then shuffle.)"

    val allSliverCards = GroupFilter(GameObjectFilter.Any.withSubtype("Sliver"))
    val slivercycling = ActivatedAbilityBuilder().apply {
        cost = Costs.Composite(Costs.Mana("{3}"), Costs.DiscardSelf)
        activateFromZone = Zone.HAND
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Any.withSubtype("Sliver"),
            count = 1,
            destination = SearchDestination.HAND,
            shuffleAfter = true,
            reveal = true,
        )
        description = "Slivercycling {3} ({3}, Discard this card: Search your library for a Sliver card, reveal it, and put it into your hand. Then shuffle.)"
    }.build()
    staticAbility {
        ability = GrantActivatedAbility(
            ability = slivercycling,
            filter = allSliverCards,
            recipientZone = Zone.HAND,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "171"
        imageUri = "https://cards.scryfall.io/normal/front/a/7/a7f2073b-bc1f-4611-9bdf-73cb86892886.jpg?1783927795"
    }
}
