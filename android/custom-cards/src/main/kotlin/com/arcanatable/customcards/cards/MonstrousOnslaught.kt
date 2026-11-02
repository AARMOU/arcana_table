package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val MonstrousOnslaught = card("Monstrous Onslaught") {
    manaCost = "{3}{G}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Monstrous Onslaught deals X damage divided as you choose among any number of target creatures, where X is the greatest power among creatures you control as you cast this spell."

    spell {
        val greatestPower = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature.youControl()).maxPower()
        captureAtCast("greatestPower", greatestPower)
        targets(
            TargetFilter.Creature,
            minCount = 0,
            unlimited = true,
            dynamicMaxCount = greatestPower,
        )
        effect = Effects.DividedDamage(
            total = 0,
            dynamicTotal = DynamicAmounts.capturedAtCast("greatestPower"),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "244"
        artist = "Slawomir Maniak"
    }
}
