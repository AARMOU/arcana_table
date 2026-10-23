package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration

val Polyraptor = card("Polyraptor") {
    manaCost = "{6}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur"
    power = 5
    toughness = 5
    oracleText = "Enrage — Whenever this creature is dealt damage, create a token that's a copy of this creature."

    triggeredAbility {
        trigger = Triggers.self.isDealtDamage()
        effect = Effects.CreateTokenCopyOfSelf()
        description = "Whenever this creature is dealt damage, create a token that's a copy of this creature."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "156"
        imageUri = "https://cards.scryfall.io/normal/front/7/0/70c172ab-5263-4373-b0cc-cda763d451d8.jpg?1562560398"
    }
}
