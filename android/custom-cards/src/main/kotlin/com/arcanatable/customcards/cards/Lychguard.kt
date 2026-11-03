package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Lychguard — Warhammer 40,000 Commander #39 (Necron Dynasties). {2}{B} Artifact Creature — Necron, 2/3.
 *
 * Guardian Protocols — {3}{B}, Sacrifice this creature: Return all legendary creature cards from your
 * graveyard to your hand.
 *
 * The cost is Memorial to Folly's shape without the tap: `Composite(Mana("{3}{B}"), SacrificeSelf)`.
 * The effect targets nothing (no "target"), so on resolution it gathers every legendary creature card
 * in **your** graveyard at that moment and moves them all to your hand (Port of Karfell's gather →
 * move, without a choice). Lychguard is sacrificed while the ability is activated (CR 602.2b, 601.2h),
 * so it is already in the graveyard on resolution; it is not legendary, so it stays there.
 * No official rulings are published for this card (Scryfall/Gatherer, checked 2026-11-03).
 */
val Lychguard = card("Lychguard") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 2
    toughness = 3
    oracleText = "Guardian Protocols — {3}{B}, Sacrifice this creature: Return all legendary creature cards from your graveyard to your hand."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{3}{B}"), Costs.SacrificeSelf)
        effect = Effects.Pipeline {
            val legends = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.Creature.legendary()))
            move(legends, CardDestination.ToZone(Zone.HAND))
        }
        description = "Guardian Protocols — Return all legendary creature cards from your graveyard to your hand."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "39"
        artist = "Games Workshop"
        imageUri = "https://cards.scryfall.io/normal/front/6/7/6737a276-b5e9-4c55-b995-ada96eca4e6e.jpg?1783920922"
    }
}
