package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

val AcolyteHybrid = card("Acolyte Hybrid") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Tyranid Human"
    power = 2
    toughness = 2
    oracleText = "Heavy Rock Cutter — Whenever this creature attacks, destroy up to one target artifact. If an artifact is destroyed this way, its controller draws a card."

    triggeredAbility {
        trigger = Triggers.self.attacks()
        target(TargetFilter.Artifact, optional = true)
        effect = Effects.Pipeline {
            val targeted = gather(CardSource.ChosenTargets)
            val destroyed = moveTracked(
                targeted,
                CardDestination.ToZone(Zone.GRAVEYARD),
                moveType = MoveType.Destroy,
            )
            ifNotEmpty(destroyed) {
                run(Effects.ForEachPlayer(Player.ControllerOf("target artifact"), Effects.DrawCards(1)))
            }
        }
        description = "Whenever this creature attacks, destroy up to one target artifact. If an artifact is destroyed this way, its controller draws a card."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "70"
        artist = "Slawomir Maniak"
        imageUri = "https://cards.scryfall.io/normal/front/b/2/b229697e-b6ff-450b-8020-45c454da9b3e.jpg?1783920895"
    }
}
