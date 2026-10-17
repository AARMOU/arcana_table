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

val IronManBleedingEdge = card("Iron Man, Bleeding Edge") {
    manaCost = "{3}{U}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Artifact Creature — Human Hero"
    power = 3
    toughness = 5
    oracleText = "Flying\nWhenever you cast an artifact spell, you may copy it, except the copy isn't legendary. " +
        "Do this only once each turn. (The copy becomes a token.)"

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Artifact)
        optional = true
        effectOncePerTurn = true
        effect = Effects.CopyTargetSpell(EffectTarget.TriggeringEntity, removeLegendary = true)
        description = "Whenever you cast an artifact spell, you may copy it, except the copy isn't legendary. Do this only once each turn."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "626"
        artist = "Ryan Pancoast"
    }
}
