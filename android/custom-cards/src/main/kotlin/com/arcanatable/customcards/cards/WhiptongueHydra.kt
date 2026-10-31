package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Whiptongue Hydra — NEC #134.
 */
val WhiptongueHydra = card("Whiptongue Hydra") {
    manaCost = "{5}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Lizard Hydra"
    power = 4
    toughness = 4
    oracleText = "Reach\nWhen this creature enters, destroy all creatures with flying. Put a +1/+1 counter on this creature for each creature destroyed this way."

    keywords(Keyword.REACH)

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.Pipeline {
            val destroyed = runStoringCollection {
                Effects.DestroyAll(
                    GameObjectFilter.Creature.withKeyword(Keyword.FLYING),
                    storeDestroyedAs = it,
                )
            }
            run(
                Effects.AddDynamicCounters(
                    CounterType.PLUS_ONE_PLUS_ONE,
                    destroyed.count,
                    EffectTarget.Self,
                ),
            )
        }
        description = "When this creature enters, destroy all creatures with flying. Put a +1/+1 counter on this creature for each creature destroyed this way."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "134"
        artist = "Tomasz Jedruszek"
    }
}
