package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.references.Player

val QuicksilverAmulet = card("Quicksilver Amulet") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{4}, {T}: You may put a creature card from your hand onto the battlefield."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{4}"), Costs.Tap)
        effect = Effects.Pipeline {
            val creatureCards = gather(CardSource.FromZone(Zone.HAND, Player.You, GameObjectFilter.Creature))
            val chosen = chooseUpTo(1, from = creatureCards)
            move(chosen, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You))
        }
        description = "{4}, {T}: You may put a creature card from your hand onto the battlefield."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "214"
        artist = "Brad Rigney"
        imageUri = "https://cards.scryfall.io/normal/front/0/4/04c0357a-e98d-4c49-83ad-d7a8ebe7e2d1.jpg?1783941048"
    }
}
