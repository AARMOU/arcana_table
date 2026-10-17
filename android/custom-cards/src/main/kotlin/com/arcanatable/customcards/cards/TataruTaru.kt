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

val TataruTaru = card("Tataru Taru") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Dwarf Advisor"
    power = 0
    toughness = 3
    oracleText = "When Tataru Taru enters, you draw a card and target opponent may draw a card.\n" +
        "Scions' Secretary — Whenever an opponent draws a card, if it isn't that player's turn, create a " +
        "tapped Treasure token. This ability triggers only once each turn."

    triggeredAbility {
        trigger = Triggers.self.enters()
        val opponent = target(Targets.Opponent)
        effect = Effects.DrawCards(1) then Effects.May(Effects.DrawCards(1, opponent))
        description = "When Tataru Taru enters, you draw a card and target opponent may draw a card."
    }

    triggeredAbility {
        trigger = Triggers.anOpponent.draws()
        interveningIf = Conditions.Not(Conditions.IsPlayersTurn(Player.TriggeringPlayer))
        oncePerTurn = true
        effect = Effects.CreateTreasure(1, tapped = true)
        description = "Whenever an opponent draws a card, if it isn't that player's turn, create a tapped " +
            "Treasure token. This ability triggers only once each turn."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "30"
        artist = "Livia Prima"
    }
}
