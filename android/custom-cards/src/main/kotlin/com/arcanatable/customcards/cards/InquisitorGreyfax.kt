package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val InquisitorGreyfax = card("Inquisitor Greyfax") {
    manaCost = "{1}{W}{U}{B}"
    colorIdentity = "WUB"
    typeLine = "Legendary Creature — Human Inquisitor"
    power = 3
    toughness = 3
    oracleText = "Vigilance\nUnquestionable Wisdom — Other creatures you control get +1/+0 and have vigilance.\n" +
        "Hunt for Heresy — {1}, {T}: Tap target creature an opponent controls. Investigate."

    keywords(Keyword.VIGILANCE)
    val otherCreatures = GroupFilter(GameObjectFilter.Creature.youControl(), excludeSelf = true)
    staticAbility { ability = ModifyStats(1, 0, otherCreatures) }
    staticAbility { ability = GrantKeyword(Keyword.VIGILANCE, otherCreatures) }
    activatedAbility {
        val creature = target(TargetFilter(GameObjectFilter.Creature.opponentControls()))
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Effects.Tap(creature) then Effects.Investigate()
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "3"
        artist = "Lie Setiawan"
    }
}
