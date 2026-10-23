package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.RedirectZoneChange

val BlightsteelColossus = card("Blightsteel Colossus") {
    manaCost = "{12}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Phyrexian Golem"
    power = 11
    toughness = 11
    oracleText = "Trample, infect, indestructible\n" +
        "If Blightsteel Colossus would be put into a graveyard from anywhere, reveal Blightsteel Colossus and shuffle it into its owner's library instead."

    // The pinned Argentum SDK has no infect keyword implementation. This custom definition
    // keeps the supported trample, indestructible, and library-replacement behaviors.
    keywords(Keyword.TRAMPLE, Keyword.INDESTRUCTIBLE)

    replacementEffect(
        RedirectZoneChange(
            newDestination = Zone.LIBRARY,
            appliesTo = EventPattern.ZoneChangeEvent(to = Zone.GRAVEYARD),
            selfOnly = true,
            shuffleIntoLibrary = true,
            reveal = true,
        )
    )

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "99"
        artist = "Chris Rahn"
        imageUri = "https://cards.scryfall.io/normal/front/7/9/7928bb14-7631-4830-a756-26d1ea832ba2.jpg"
    }
}
