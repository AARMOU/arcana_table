package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** For the Emperor! — Warhammer 40,000 Commander #12. */
val ForTheEmperor = card("For the Emperor!") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Sorcery"
    oracleText = "Creatures you control get +2/+2 and gain vigilance and lifelink until end of turn."

    spell {
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.ModifyStats(2, 2, EffectTarget.IterationEntity) then
                Effects.GrantKeyword(Keyword.VIGILANCE, EffectTarget.IterationEntity) then
                Effects.GrantKeyword(Keyword.LIFELINK, EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "12"
        artist = "Games Workshop"
    }
}
