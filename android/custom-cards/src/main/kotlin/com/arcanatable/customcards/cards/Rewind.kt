package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*

val Rewind = card("Rewind") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell. Untap up to four lands."

    spell {
        target(TargetFilter.SpellOnStack)
        val lands = targets(TargetFilter.Land, count = 4, minCount = 0)
        effect = Effects.CounterSpell() then Effects.UntapEachTarget()
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "88"
        imageUri = "https://cards.scryfall.io/normal/front/0/5/05a7e5fc-ea55-4e04-915c-6cb4471ce655.jpg?1562901682"
    }
}
