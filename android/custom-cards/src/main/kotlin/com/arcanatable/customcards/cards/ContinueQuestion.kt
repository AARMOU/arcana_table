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

val ContinueQuestion = card("Continue?") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Choose up to four target creature cards in your graveyard that were put there from the battlefield " +
        "this turn. Return them to the battlefield."

    spell {
        val returning = targets(
            TargetFilter(GameObjectFilter.Creature.ownedByYou().putIntoGraveyardFromBattlefieldThisTurn(), zone = Zone.GRAVEYARD),
            count = 4,
            minCount = 0,
        )
        effect = returning.map { Effects.Move(it, Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD) as Effect }.reduce { a, b -> a then b }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "7"
        artist = "Irina Nordsol"
    }
}
