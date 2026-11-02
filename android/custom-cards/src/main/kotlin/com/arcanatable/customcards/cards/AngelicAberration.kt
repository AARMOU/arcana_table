package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Angelic Aberration — Modern Horizons 3 Commander #39. {5}{W} Creature — Eldrazi Angel 4/4.
 *
 * Devoid (patch 0019 surface: the card has no color, colour identity stays W), flying, vigilance.
 *
 * The enter trigger is one resolution: `SacrificeAnyNumber` offers every creature you control whose
 * **base** power or **base** toughness is 1 or less (`basePowerOrToughnessAtMost(1)`, engine patch
 * 0028; either half qualifies, so base 0/2, 1/5 and 5/1 all do) and you choose zero to all of them;
 * then `CreateToken` makes one 4/4 colorless Eldrazi Angel with flying and vigilance per creature
 * actually sacrificed (`permanentsSacrificedThisWay`). Base P/T is the layer 1–7b value, so counters
 * and pumps never disqualify a creature and a copy or set-base-P/T effect counts. The filter does
 * not exclude this creature: it is sacrificed only if it qualifies (normally it is a 4/4) and is
 * chosen. A creature that can't be sacrificed is never offered.
 */
val AngelicAberration = card("Angelic Aberration") {
    manaCost = "{5}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Eldrazi Angel"
    power = 4
    toughness = 4
    oracleText = "Devoid (This card has no color.)\nFlying, vigilance\nWhen this creature enters, sacrifice any " +
        "number of creatures each with base power or toughness 1 or less. Create that many 4/4 colorless " +
        "Eldrazi Angel creature tokens with flying and vigilance."

    keywords(Keyword.DEVOID, Keyword.FLYING, Keyword.VIGILANCE)

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.SacrificeAnyNumber(
            GameObjectFilter.Creature.basePowerOrToughnessAtMost(1)
        ) then Effects.CreateToken(
            count = DynamicAmounts.permanentsSacrificedThisWay(),
            power = 4,
            toughness = 4,
            creatureTypes = setOf("Eldrazi", "Angel"),
            keywords = setOf(Keyword.FLYING, Keyword.VIGILANCE),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "39"
        artist = "Helge C. Balzer"
        imageUri = "https://cards.scryfall.io/normal/front/c/7/c79a5bbe-7b70-4b05-a336-9b2bcb2f2b8f.jpg?1783911427"
    }
}
