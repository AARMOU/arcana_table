package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeywordToOwnSpells
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.TurnTracker

/**
 * Abaddon the Despoiler — Warhammer 40,000 Commander #2 (The Ruinous Powers face commander).
 *
 * Mark of Chaos Ascendant is a granted cascade with a dynamic mana-value cap (X = life opponents lost
 * this turn, read when the spell is cast) that applies only during Abaddon's controller's turn. Needs
 * engine patches 0008 (granted cascade) and 0009 (maxManaValue / duringYourTurnOnly).
 */
val AbaddonTheDespoiler = card("Abaddon the Despoiler") {
    manaCost = "{2}{U}{B}{R}"
    colorIdentity = "UBR"
    typeLine = "Legendary Creature — Astartes Warrior"
    power = 5
    toughness = 5
    keywords(Keyword.TRAMPLE)
    oracleText = "Trample\nMark of Chaos Ascendant — During your turn, spells you cast from your hand with mana value X or less " +
        "have cascade, where X is the total amount of life your opponents have lost this turn."
    staticAbility {
        ability = GrantKeywordToOwnSpells(
            keyword = Keyword.CASCADE,
            spellFilter = GameObjectFilter.Any,
            fromZone = Zone.HAND,
            maxManaValue = DynamicAmount.TurnTracking(Player.EachOpponent, TurnTracker.LIFE_LOST_AMOUNT),
            duringYourTurnOnly = true,
        )
    }
    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2"
        artist = "Johan Grenier"
    }
}
