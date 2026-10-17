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

val SaiMasterThopterist = card("Sai, Master Thopterist") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Creature — Human Artificer"
    power = 1
    toughness = 4
    oracleText = "Whenever you cast an artifact spell, create a 1/1 colorless Thopter artifact creature token with " +
        "flying.\n{1}{U}, Sacrifice two artifacts: Draw a card."

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Artifact)
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            creatureTypes = setOf("Thopter"),
            keywords = setOf(Keyword.FLYING),
            artifactToken = true,
        )
        description = "Whenever you cast an artifact spell, create a 1/1 colorless Thopter artifact creature token with flying."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}{U}"), Costs.SacrificeMultiple(2, GameObjectFilter.Artifact))
        effect = Effects.DrawCards(1)
        description = "{1}{U}, Sacrifice two artifacts: Draw a card."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "69"
        artist = "Adam Paquette"
    }
}
