package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Rise of the Eldrazi #176. The upkeep trigger is optional, unlike the Spawn-creating spells. */
val AwakeningZone = card("Awakening Zone") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "At the beginning of your upkeep, you may create a 0/1 colorless Eldrazi Spawn " +
        "creature token. It has \"Sacrifice this token: Add {C}.\""

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        optional = true
        effect = Effects.CreateEldraziSpawn()
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "176"
        artist = "Johann Bodin"
    }
}
