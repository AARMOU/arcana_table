package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val BarretAvalancheLeader = card("Barret, Avalanche Leader") {
    manaCost = "{2}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Legendary Creature — Human Rebel"
    power = 4
    toughness = 4
    oracleText = "Reach\n" +
        "Avalanche! — Whenever an Equipment you control enters, create a 2/2 red Rebel creature token.\n" +
        "At the beginning of combat on your turn, attach up to one target Equipment you control to target Rebel you control."

    keywords(Keyword.REACH)

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Artifact.withSubtype(Subtype.EQUIPMENT).youControl()).enters()
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.RED),
            creatureTypes = setOf("Rebel"),
        )
        description = "Whenever an Equipment you control enters, create a 2/2 red Rebel creature token."
    }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        val equipment = target(
            TargetFilter(GameObjectFilter.Artifact.withSubtype(Subtype.EQUIPMENT).youControl()),
            optional = true,
        )
        val rebel = target(TargetFilter(GameObjectFilter.Creature.withSubtype("Rebel").youControl()))
        effect = Effects.AttachTargetEquipmentToCreature(equipment, rebel)
        description = "At the beginning of combat on your turn, attach up to one target Equipment you control to target Rebel you control."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "79"
        artist = "Jana Schirmer & Matthias Verhasselt"
        imageUri = "https://cards.scryfall.io/normal/front/2/e/2e37fd36-84f0-4869-bfae-da4b97c9725d.jpg?1783906346"
    }
}
