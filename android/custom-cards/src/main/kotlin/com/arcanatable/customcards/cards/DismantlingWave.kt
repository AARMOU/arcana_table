package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val DismantlingWave = card("Dismantling Wave") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Sorcery"
    oracleText = "For each opponent, destroy up to one target artifact or enchantment that player controls.\n" +
        "Cycling {6}{W}{W} ({6}{W}{W}, Discard this card: Draw a card.)\n" +
        "When you cycle this card, destroy all artifacts and enchantments."

    spell {
        targets(
            filter = TargetFilter(GameObjectFilter.ArtifactOrEnchantment.opponentControls()),
            minCount = 0,
            dynamicMaxCount = DynamicAmounts.playerCount(Player.EachOpponent),
            differentControllers = true,
        )
        effect = Effects.ForEachTarget(Effects.Destroy(EffectTarget.ContextTarget(0)))
    }

    keywordAbility(KeywordAbility.cycling("{6}{W}{W}"))

    triggeredAbility {
        trigger = Triggers.self.isCycled()
        effect = Effects.DestroyAll(GameObjectFilter.ArtifactOrEnchantment)
        description = "When you cycle this card, destroy all artifacts and enchantments."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "25"
        artist = "Raoul Vitale"
        imageUri = "https://api.scryfall.com/cards/named?exact=Dismantling%20Wave&format=image"
    }
}
