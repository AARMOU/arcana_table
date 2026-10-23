package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
val Defile = card("Defile") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Target creature gets -1/-1 until end of turn for each Swamp you control."
    spell {
        val creature = target(TargetFilter.Creature)
        val swamps = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land.withSubtype(Subtype.SWAMP)).count()
        effect = Effects.ModifyStats(-swamps, -swamps, creature)
    }
    metadata { rarity = Rarity.COMMON; collectorNumber = "86"; artist = "Joe Slucher" }
}
