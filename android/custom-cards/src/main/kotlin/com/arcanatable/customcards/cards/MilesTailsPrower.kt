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

val MilesTailsPrower = card("Miles \"Tails\" Prower") {
    manaCost = "{1}{W}{U}"
    colorIdentity = "WU"
    typeLine = "Legendary Creature — Fox Artificer"
    power = 3
    toughness = 1
    oracleText = "Flash\nFlying\nWhenever a Vehicle you control enters, draw a card if it has flying. " +
        "Otherwise, put a flying counter on it."

    keywords(Keyword.FLASH, Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Artifact.withSubtype("Vehicle").youControl()).enters()
        effect = Effects.If(
            Conditions.EntityMatches(EffectTarget.TriggeringEntity, GameObjectFilter.Permanent.withKeyword(Keyword.FLYING)),
            Effects.DrawCards(1),
            Effects.AddCounters(CounterType.FLYING, 1, EffectTarget.TriggeringEntity),
        )
        description = "Whenever a Vehicle you control enters, draw a card if it has flying. Otherwise, put a flying counter on it."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2085"
        imageUri = "https://cards.scryfall.io/normal/front/7/d/7d4bd7e1-9861-4f8a-9ad5-a41aeaee1928.jpg?1783906091"
        artist = "John Thacker"
    }
}
