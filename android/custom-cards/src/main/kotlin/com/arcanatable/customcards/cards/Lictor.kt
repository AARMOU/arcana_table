package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** The intervening condition reads turn history, including creatures that have since left. */
val Lictor = card("Lictor") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Tyranid"
    power = 3
    toughness = 3
    oracleText = "Flash\nPheromone Trail — When this creature enters, if a creature entered the battlefield " +
        "under an opponent's control this turn, create a 3/3 green Tyranid Warrior creature token with trample."
    keywords(Keyword.FLASH)

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.CreaturesEnteredThisTurn(player = Player.EachOpponent)
        effect = Effects.CreateToken(
            power = 3,
            toughness = 3,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Tyranid", "Warrior"),
            keywords = setOf(Keyword.TRAMPLE),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "94"
        artist = "Games Workshop"
    }
}
