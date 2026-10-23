package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

/**
 * Space Marine Scout — Warhammer 40,000 Commander #15.
 * "If an opponent controls more lands" is an intervening-if condition; a
 * player may decline the library search entirely.
 */
val SpaceMarineScout = card("Space Marine Scout") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Astartes Scout"
    power = 2
    toughness = 1
    oracleText = "First strike, vigilance\nConcealed Position — When this creature enters, " +
        "if an opponent controls more lands than you, you may search your library for a Plains " +
        "card, put it onto the battlefield tapped, then shuffle."

    keywords(Keyword.FIRST_STRIKE, Keyword.VIGILANCE)

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.OpponentControlsMoreLands
        optional = true
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Land.withSubtype(Subtype.PLAINS),
            count = 1,
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true,
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "15"
        artist = "Games Workshop"
    }
}
