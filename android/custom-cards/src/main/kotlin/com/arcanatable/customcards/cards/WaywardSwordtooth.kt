package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player

val WaywardSwordtooth = card("Wayward Swordtooth") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur"
    power = 5
    toughness = 5
    oracleText = "You may play an additional land on each of your turns.\nWayward Swordtooth can't attack or block unless you have the city's blessing."

    staticAbility {
        ability = GrantAdditionalLandDrop(count = 1, affected = Player.You)
    }
    staticAbility {
        ability = CantAttackUnless(Conditions.YouHaveCitysBlessing)
    }
    staticAbility {
        ability = CantBlockUnless(Conditions.YouHaveCitysBlessing)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "197"
        imageUri = "https://cards.scryfall.io/normal/front/4/3/43d4719a-6bee-4a5a-a09b-b87a557bb149.jpg?1562552117"
    }
}
