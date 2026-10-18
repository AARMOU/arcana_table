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

val TheFantasticar = card("The Fantasticar") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Legendary Artifact — Vehicle"
    power = 4
    toughness = 4
    oracleText = "Flying\nWhenever you cast a noncreature spell, you may have The Fantasticar become an artifact " +
        "creature until end of turn.\nWhenever you cast your fourth noncreature spell each turn, you may sacrifice " +
        "The Fantasticar. If you do, create four 4/4 colorless Construct artifact creature tokens with flying and haste."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Noncreature)
        optional = true
        effect = Effects.BecomeCreature(EffectTarget.Self, power = 4, toughness = 4, duration = Duration.EndOfTurn)
        description = "Whenever you cast a noncreature spell, you may have The Fantasticar become an artifact creature until end of turn."
    }

    triggeredAbility {
        trigger = Triggers.you.castsNth(4, GameObjectFilter.Noncreature)
        optional = true
        effect = Effects.SacrificeTarget(EffectTarget.Self) then Effects.CreateToken(
            power = 4,
            toughness = 4,
            creatureTypes = setOf("Construct"),
            keywords = setOf(Keyword.FLYING, Keyword.HASTE),
            count = 4,
            artifactToken = true,
        )
        description = "Whenever you cast your fourth noncreature spell each turn, you may sacrifice The Fantasticar. " +
            "If you do, create four 4/4 colorless Construct artifact creature tokens with flying and haste."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "104"
        imageUri = "https://cards.scryfall.io/normal/front/e/d/ed0beb69-8441-4104-9990-81bd82f81f9a.jpg?1786399169"
        artist = "Maxim Ruabtsev"
    }
}
