package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val DruidsDeliverance = card("Druid's Deliverance") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Prevent all combat damage that would be dealt to you this turn.\nPopulate."

    spell {
        effect = Effects.PreventDamage(
            target = EffectTarget.Controller,
            combatOnly = true,
            direction = PreventionDirection.ToTarget,
        ) then Effects.CreateTokenCopyOfChosenPermanent(GameObjectFilter.Creature.token().youControl())
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "123"
        artist = "Nils Hamm"
        imageUri = "https://cards.scryfall.io/normal/front/8/3/83b35961-f4d5-4e14-a793-335147110627.jpg?1783940349"
    }
}
