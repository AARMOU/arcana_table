package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.OpponentsMustAttack

/**
 * Seeker of Slaanesh — Warhammer 40,000 Commander #85. {3}{R} Creature — Demon 3/3.
 *
 * "Allure of Slaanesh — Each opponent must attack with at least one creature each combat if able."
 * A requirement on each opponent as attacking player (CR 508.1d), not on any creature and not aimed
 * at anyone: any one attack satisfies it, and it lapses when none of their creatures could attack
 * without paying a cost. Uses `OpponentsMustAttack` from engine patch 0027.
 */
val SeekerOfSlaanesh = card("Seeker of Slaanesh") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Demon"
    power = 3
    toughness = 3
    oracleText = "Haste\nAllure of Slaanesh — Each opponent must attack with at least one creature each combat if able."

    keywords(Keyword.HASTE)

    staticAbility {
        ability = OpponentsMustAttack
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "85"
        artist = "Games Workshop"
        imageUri = "https://cards.scryfall.io/normal/front/9/f/9f07e21c-a775-43bb-90a2-545a5e1b909b.jpg?1783920882"
    }
}
