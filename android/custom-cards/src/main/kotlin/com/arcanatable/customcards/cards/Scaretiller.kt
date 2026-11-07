package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.references.Player

/** Scaretiller — Throne of Eldraine #232. */
val Scaretiller = card("Scaretiller") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Scarecrow"
    power = 1
    toughness = 4
    oracleText = "Whenever Scaretiller becomes tapped, you may put a land card from your hand or graveyard onto the " +
        "battlefield tapped.\n{2}, {T}: Draw a card, then discard a card."

    triggeredAbility {
        trigger = Triggers.self.becomesTapped()
        effect = Effects.Pipeline {
            val lands = gather(
                CardSource.FromMultipleZones(
                    zones = listOf(Zone.HAND, Zone.GRAVEYARD),
                    player = Player.You,
                    filter = GameObjectFilter.Land,
                ),
                name = "scaretillerLands",
            )
            val chosenLand = chooseUpTo(
                1,
                from = lands,
                prompt = "Choose a land card from your hand or graveyard to put onto the battlefield tapped.",
            )
            move(
                chosenLand,
                CardDestination.ToZone(Zone.BATTLEFIELD, Player.You, ZonePlacement.Tapped),
            )
        }
        description = "Whenever Scaretiller becomes tapped, you may put a land card from your hand or graveyard " +
            "onto the battlefield tapped."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
        effect = Effects.DrawCards(1) then Effects.Discard(1)
        description = "{2}, {T}: Draw a card, then discard a card."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "232"
        artist = "Randy Vargas"
    }
}
