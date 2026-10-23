package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val PrimarisChaplain = card("Primaris Chaplain") {
    manaCost = "{2}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Creature — Astartes Cleric"
    power = 3
    toughness = 3
    oracleText = "Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)\n" +
        "Rosarius — Whenever this creature attacks, it gains indestructible until end of turn."

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.attacking(), excludeSelf = true),
            Effects.ModifyStats(1, 0, EffectTarget.IterationEntity),
        )
    }
    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.Self)
    }

    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "137"; artist = "Diego Gisbert" }
}
