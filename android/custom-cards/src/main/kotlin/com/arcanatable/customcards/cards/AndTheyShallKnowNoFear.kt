package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ChooseCreatureTypeEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Choosing a type happens on resolution. The chosen group is restricted to
 * creatures you control when the spell resolves, not enemy creatures or
 * creatures entering afterward. Both benefits expire at end of turn.
 */
val AndTheyShallKnowNoFear = card("And They Shall Know No Fear") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Choose a creature type. Creatures you control of the chosen type " +
        "get +1/+0 and gain indestructible until end of turn."

    spell {
        effect = Effects.Pipeline {
            run(ChooseCreatureTypeEffect)
            run(Effects.ForEachInGroup(
                filter = GroupFilter(
                    baseFilter = GameObjectFilter.Creature.youControl(),
                    chosenSubtypeKey = "chosenCreatureType"
                ),
                effect = Effects.ModifyStats(1, 0, EffectTarget.IterationEntity) then
                    Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity)
            ))
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "9"
        artist = "Games Workshop"
    }
}
