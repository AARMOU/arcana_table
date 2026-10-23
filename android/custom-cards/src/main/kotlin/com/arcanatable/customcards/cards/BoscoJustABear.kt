package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*

val BoscoJustABear = card("Bosco, Just a Bear") {
    manaCost = "{4}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Bear"
    power = 4
    toughness = 4
    oracleText = "When Bosco enters, create a Food token for each legendary creature you control."

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateFood(
            DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature.legendary().youControl()).count()
        )
        description = "When Bosco enters, create a Food token for each legendary creature you control."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "132"
        artist = "Dmitry Burmak"
        imageUri = "https://cards.scryfall.io/normal/front/d/f/dfb85545-22d0-4265-94e2-8805d2ccf0f8.jpg?1783904817"
    }
}
