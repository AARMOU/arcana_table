package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private val monumentCreatures = GroupFilter(GameObjectFilter.Creature.youControl())

val EldraziMonument = card("Eldrazi Monument") {
    manaCost = "{5}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Creatures you control get +1/+1 and have flying and indestructible.\n" +
        "At the beginning of your upkeep, sacrifice a creature. If you can't, sacrifice Eldrazi Monument."

    staticAbility { ability = ModifyStats(powerBonus = 1, toughnessBonus = 1, filter = monumentCreatures) }
    staticAbility { ability = GrantKeyword(Keyword.FLYING, monumentCreatures) }
    staticAbility { ability = GrantKeyword(Keyword.INDESTRUCTIBLE, monumentCreatures) }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        effect = Effects.If(
            Conditions.YouControl(GameObjectFilter.Creature.youControl()),
            Effects.Sacrifice(GameObjectFilter.Creature.youControl(), 1, EffectTarget.Controller),
            Effects.SacrificeTarget(EffectTarget.Self),
        )
    }

    metadata { rarity = Rarity.MYTHIC; collectorNumber = "290"; artist = "Daarken" }
}
