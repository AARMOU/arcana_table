package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Necron Monolith — Warhammer 40,000 Commander #161 (Necron Dynasties). {7} Artifact — Vehicle, 7/7.
 *
 * Flying, indestructible. Eternity Gate — Whenever this Vehicle attacks, mill three cards. For each
 * creature card milled this way, create a 2/2 black Necron Warrior artifact creature token. Crew 4.
 *
 * Eternity Gate is Culmination of Studies' shape on a mill: the pipeline's `mill(3)` returns exactly the
 * cards milled this way (CR 701.17a), `filter(..., Creature)` keeps the creature cards among them, and
 * that count feeds the Night Scythe / Royal Warden Necron Warrior token. Creature cards already in the
 * graveyard are not in the milled collection, so they don't count; with fewer than three cards in the
 * library only the cards actually milled count. Crew 4 is `KeywordAbility.crew(4)` (CR 702.122a: tap
 * any number of untapped creatures with total power 4 or more).
 * No official rulings are published for this card (Scryfall/Gatherer, checked 2026-11-03).
 */
val NecronMonolith = card("Necron Monolith") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 7
    toughness = 7
    oracleText = "Flying, indestructible\nEternity Gate — Whenever this Vehicle attacks, mill three cards. " +
        "For each creature card milled this way, create a 2/2 black Necron Warrior artifact creature token.\n" +
        "Crew 4 (Tap any number of creatures you control with total power 4 or more: This Vehicle becomes " +
        "an artifact creature until end of turn.)"

    keywords(Keyword.FLYING, Keyword.INDESTRUCTIBLE)

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.Pipeline {
            val milled = mill(3)
            val creatures = filter(milled, GameObjectFilter.Creature)
            run(
                Effects.CreateToken(
                    count = creatures.count,
                    power = 2,
                    toughness = 2,
                    colors = setOf(Color.BLACK),
                    creatureTypes = setOf("Necron", "Warrior"),
                    name = "Necron Warrior",
                    artifactToken = true,
                )
            )
        }
        description = "Eternity Gate — Whenever this Vehicle attacks, mill three cards. For each creature card " +
            "milled this way, create a 2/2 black Necron Warrior artifact creature token."
    }
    keywordAbility(KeywordAbility.crew(4))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "161"
        artist = "Anthony Devine"
        imageUri = "https://cards.scryfall.io/normal/front/9/b/9b1ecad3-8cec-4a18-9401-6eebc7feeacc.jpg?1783920816"
    }
}
