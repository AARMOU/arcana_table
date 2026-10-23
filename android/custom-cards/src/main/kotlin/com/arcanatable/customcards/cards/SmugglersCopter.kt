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

val SmugglersCopter = card("Smuggler's Copter") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 3
    toughness = 3
    oracleText = "Flying\nWhenever this Vehicle attacks or blocks, you may draw a card. If you do, discard a card.\n" +
        "Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an " +
        "artifact creature until end of turn.)"

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.or(Triggers.self.attacks(), Triggers.self.blocks())
        optional = true
        effect = Patterns.Hand.loot()
        description = "Whenever this Vehicle attacks or blocks, you may draw a card. If you do, discard a card."
    }

    keywordAbility(KeywordAbility.crew(1))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "235"
        imageUri = "https://cards.scryfall.io/normal/front/7/8/7832abb5-5107-4603-904e-491b221bd3e3.jpg?1783937147"
        artist = "Florian de Gesincourt"
    }
}
