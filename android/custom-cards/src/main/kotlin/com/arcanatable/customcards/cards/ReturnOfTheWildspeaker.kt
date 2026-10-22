package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Both modes exclude Humans; max projected power is determined only as the draw mode resolves. */
val ReturnOfTheWildspeaker = card("Return of the Wildspeaker") {
    manaCost = "{4}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Choose one —\n" +
        "• Draw cards equal to the greatest power among non-Human creatures you control.\n" +
        "• Non-Human creatures you control get +3/+3 until end of turn."

    val nonHumans = GameObjectFilter.Creature.youControl().notSubtype("Human")
    spell {
        effect = ModalEffect.chooseOne(
            Mode.noTarget(
                effect = Effects.DrawCards(
                    DynamicAmounts.battlefield(Player.You, nonHumans).maxPower()
                ),
                description = "Draw cards equal to the greatest power among non-Human creatures you control."
            ),
            Mode.noTarget(
                effect = Effects.ForEachInGroup(
                    GroupFilter(nonHumans),
                    Effects.ModifyStats(3, 3, EffectTarget.IterationEntity),
                ),
                description = "Non-Human creatures you control get +3/+3 until end of turn."
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "242"
        artist = "Chris Rallis"
    }
}
