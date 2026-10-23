package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

/** Ancient Copper Dragon — roll a d20 on combat damage to a player, then that many Treasures. */
val AncientCopperDragon = card("Ancient Copper Dragon") {
    manaCost = "{4}{R}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Elder Dragon"
    power = 6
    toughness = 5
    oracleText = "Flying\nWhenever Ancient Copper Dragon deals combat damage to a player, roll a d20. " +
        "You create a number of Treasure tokens equal to the result."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        // The engine counts rolls that land in each result set, so one set per face gives the
        // result: exactly one face's count is 1, and that face's Treasures are created.
        effect = Effects.Pipeline {
            val faces = rollDice(sides = 20, resultSets = (1..20).map { setOf(it) })
            faces.forEachIndexed { i, face -> run(Effects.CreateTreasure(face.amount * (i + 1))) }
        }
        description = "Whenever Ancient Copper Dragon deals combat damage to a player, roll a d20. " +
            "You create a number of Treasure tokens equal to the result."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "161"
        imageUri = "https://cards.scryfall.io/normal/front/3/8/3836dddd-a7e4-499f-ad49-ce298aa65720.jpg?1783922745"
        artist = "Simon Dominic"
    }
}
