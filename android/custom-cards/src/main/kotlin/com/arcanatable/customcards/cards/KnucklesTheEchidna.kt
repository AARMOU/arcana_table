package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

val KnucklesTheEchidna = card("Knuckles the Echidna") {
    manaCost = "{2}{R}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Echidna Warrior"
    power = 2
    toughness = 4
    oracleText = "Double strike, trample, haste\nWhenever one or more creatures you control deal combat damage to a player, create a Treasure token.\nTreasure Hunter — At the beginning of your upkeep, if you control thirty or more artifacts, you win the game."

    keywords(Keyword.DOUBLE_STRIKE, Keyword.TRAMPLE, Keyword.HASTE)

    triggeredAbility {
        trigger = Triggers.oneOrMore(GameObjectFilter.Creature).dealCombatDamageToAPlayer()
        effect = Effects.CreateTreasure()
    }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        interveningIf = Conditions.YouControlAtLeast(30, GameObjectFilter.Artifact)
        effect = Effects.WinGame()
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2082"
        artist = "Tyler Walpole"
    }
}
