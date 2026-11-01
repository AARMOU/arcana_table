package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.references.Player

/** Anrakyr the Traveller — Warhammer 40,000 Commander #28. */
val AnrakyrTheTraveller = card("Anrakyr the Traveller") {
    manaCost = "{4}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Artifact Creature — Necron"
    power = 4
    toughness = 4
    oracleText = "Lord of the Pyrrhian Legions — Whenever Anrakyr the Traveller attacks, you may cast an artifact spell from your hand or graveyard by paying life equal to its mana value rather than paying its mana cost."

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Pipeline {
            val artifacts = gather(
                CardSource.FromMultipleZones(
                    zones = listOf(Zone.HAND, Zone.GRAVEYARD),
                    player = Player.You,
                    filter = GameObjectFilter.Artifact.withCardPredicate(CardPredicate.IsNonland),
                ),
            )
            val chosen = chooseUpTo(1, from = artifacts)
            run(Effects.CastFromCollectionByPaying(
                chosen,
                Costs.additional.PayLifeEqualToManaValueOfSpell,
            ))
        }
        description = "Whenever Anrakyr the Traveller attacks, you may cast an artifact spell from your hand or " +
            "graveyard by paying life equal to its mana value rather than paying its mana cost."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "28"
        artist = "L J Koh"
    }
}
