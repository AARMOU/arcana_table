package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.DoubleDamage
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.PartnerKind

val RaphaelTheMuscle = card("Raphael, the Muscle") {
    manaCost = "{4}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Mutant Ninja Turtle"
    power = 4
    toughness = 4
    oracleText = "Double all damage that creatures you control with counters on them would deal. " +
        "When Raphael enters, create a Mutagen token. " +
        "Partner—Character select (You can have two commanders if both have this ability.)"

    replacementEffect(
        DoubleDamage(
            restrictions = emptyList(),
            appliesTo = EventPattern.DamageEvent(
                source = GameObjectFilter.Creature.youControl().withAnyCounter(),
            ),
            multiplier = 2,
        ),
    )

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateMutagenToken()
        description = "When Raphael enters, create a Mutagen token."
    }

    keywordAbility(KeywordAbility.Partner(PartnerKind.Named("Character select")))

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "4"
        artist = "Ryan Pancoast"
        imageUri = "https://api.scryfall.com/cards/tmc/4?format=image&version=normal"
    }
}
