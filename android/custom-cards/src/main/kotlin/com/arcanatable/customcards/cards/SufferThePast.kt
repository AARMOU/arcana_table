package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

val SufferThePast = card("Suffer the Past") {
    manaCost = "{X}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Exile X target cards from target player's graveyard. That player loses 1 life and you gain 1 life for each card exiled this way."

    spell {
        val player = target(Targets.Player)
        targets(
            TargetFilter(GameObjectFilter.Any.ownedByTargetPlayer(), zone = Zone.GRAVEYARD),
            exactly = DynamicAmounts.castX(),
        )
        effect = Effects.Pipeline {
            val chosenCards = gather(CardSource.ChosenTargets, name = "sufferThePastTargets")
            move(
                chosenCards,
                CardDestination.ToZone(Zone.EXILE, Player.TargetPlayer),
            )
            val exiledCount = DynamicAmounts.distinctEntitiesIn(chosenCards)
            run(Effects.LoseLife(exiledCount, player))
            run(Effects.GainLife(exiledCount))
        }
    }

    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "128"; artist = "Trevor Claxton" }
}
