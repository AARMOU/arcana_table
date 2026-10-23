package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Venomthrope — Warhammer 40,000 Commander #147 (original printing). */
val Venomthrope = card("Venomthrope") {
    manaCost = "{1}{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Tyranid"
    power = 2
    toughness = 2
    oracleText = "Flying, deathtouch, hexproof"

    keywords(Keyword.FLYING, Keyword.DEATHTOUCH, Keyword.HEXPROOF)

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "147"
        artist = "Antonio Jose Manzanedo"
    }
}
