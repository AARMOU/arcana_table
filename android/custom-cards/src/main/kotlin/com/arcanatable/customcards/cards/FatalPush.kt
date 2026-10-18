package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.PermanentLeftBattlefieldThisTurn
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val FatalPush = card("Fatal Push") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Destroy target creature with mana value 2 or less. Revolt — Destroy that creature instead if a permanent you controlled left the battlefield this turn."

    spell {
        val maximumManaValue = DynamicAmount.Conditional(
            condition = PermanentLeftBattlefieldThisTurn(Player.You),
            ifTrue = DynamicAmount.Fixed(4),
            ifFalse = DynamicAmount.Fixed(2),
        )
        val creature = target(TargetFilter(GameObjectFilter.Creature.manaValueAtMostDynamic(maximumManaValue)))
        effect = Effects.Destroy(creature)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "57"
        imageUri = "https://api.scryfall.com/cards/named?exact=Fatal%20Push&format=image"
    }
}
