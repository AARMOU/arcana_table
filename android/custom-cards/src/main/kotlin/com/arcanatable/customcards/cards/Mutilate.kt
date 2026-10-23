package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
val Mutilate = card("Mutilate") {
    manaCost = "{2}{B}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "All creatures get -1/-1 until end of turn for each Swamp you control."
    spell {
        val swamps = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land.withSubtype(Subtype.SWAMP)).count()
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature),
            Effects.ModifyStats(-swamps, -swamps, EffectTarget.IterationEntity)
        )
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "73"; artist = "Eric Peterson" }
}
