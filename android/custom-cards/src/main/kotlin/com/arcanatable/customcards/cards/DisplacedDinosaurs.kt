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

/**
 * Displaced Dinosaurs. The printed ability is a replacement ("As a historic permanent you control
 * enters, it becomes a 7/7 Dinosaur"); Arcana's pinned engine only runs "as this enters"
 * replacements for the entering card itself, so this is modelled as an immediate trigger with the
 * same permanent result. Difference: the change happens a moment after it enters (it can be
 * responded to), so "when this enters" abilities that check power see the old stats.
 */
val DisplacedDinosaurs = card("Displaced Dinosaurs") {
    manaCost = "{5}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur"
    power = 7
    toughness = 7
    oracleText = "As a historic permanent you control enters, it becomes a 7/7 Dinosaur creature in " +
        "addition to its other types. (Artifacts, legendaries, and Sagas are historic.)"

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Historic.youControl()).enters()
        effect = Effects.BecomeCreature(
            target = EffectTarget.TriggeringEntity,
            power = 7,
            toughness = 7,
            creatureTypes = setOf("Dinosaur"),
            duration = Duration.Permanent,
        )
        description = "Whenever another historic permanent you control enters, it becomes a 7/7 " +
            "Dinosaur creature in addition to its other types."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "100"
        imageUri = "https://cards.scryfall.io/normal/front/4/3/43319645-53b0-41f6-be90-9690e86e12d3.jpg?1783914644"
        artist = "Simon Dominic"
    }
}
