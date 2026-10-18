package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*

val ConstrictingSliver = card("Constricting Sliver") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Sliver"
    power = 3
    toughness = 3
    oracleText = "Sliver creatures you control have \"When this creature enters, you may exile target creature an opponent controls until this creature leaves the battlefield.\""

    triggeredAbility {
        trigger = Triggers.self.enters()
        val creature = target(TargetFilter.CreatureOpponentControls)
        effect = Effects.May(Effects.ExileUntilLeaves(creature))
        description = "When this creature enters, you may exile target creature an opponent controls until this creature leaves the battlefield."
    }
    triggeredAbility {
        trigger = Triggers.self.leaves()
        effect = Effects.ReturnLinkedExileUnderOwnersControl()
        description = "When this creature leaves the battlefield, return the exiled card under its owner's control."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "818"
        artist = "Loïc Canavaggia"
        imageUri = "https://cards.scryfall.io/normal/front/6/f/6f7d8e62-9bc0-4064-81c5-919c076de086.jpg?1783915453"
    }
}
