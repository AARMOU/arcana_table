package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnSourceTap
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TappedForManaType
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/**
 * Forsaken Monument — CMM #950 (Eldrazi Incursion, Eldrazi Unbound).
 *
 * - The anthem covers every colorless creature you control (no "other": the Monument is not a creature).
 * - The extra {C} is the engine's triggered mana ability: off the stack, one additional {C} per
 *   tap of ANY permanent you control that produced colorless mana (lands, Scion-free rocks, Temple).
 * - The life gain is a normal cast trigger on colorless spells you cast.
 */
// CARDKIT-SCAFFOLD — unregistered: implemented by Claude, awaiting CI scenario evidence before registration.
val ForsakenMonument = card("Forsaken Monument") {
    manaCost = "{5}"
    colorIdentity = ""
    typeLine = "Legendary Artifact"
    oracleText = "Colorless creatures you control get +2/+2.\nWhenever you tap a permanent for {C}, add an additional {C}.\nWhenever you cast a colorless spell, you gain 2 life."

    val colorless = GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless)

    staticAbility {
        ability = ModifyStats(
            powerBonus = 2,
            toughnessBonus = 2,
            filter = GroupFilter(GameObjectFilter.Creature.withCardPredicate(CardPredicate.IsColorless).youControl()),
        )
    }

    staticAbility {
        ability = AdditionalManaOnSourceTap(
            sourceFilter = GameObjectFilter.Permanent.youControl(),
            color = null,
            whenProducing = TappedForManaType.COLORLESS,
        )
    }

    triggeredAbility {
        trigger = Triggers.you.casts(colorless)
        effect = Effects.GainLife(2)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "950"
        artist = "Piotr Dura"
        imageUri = "https://cards.scryfall.io/normal/front/8/2/82dfdb91-aa69-45a1-adcc-9fcd85f84ccf.jpg?1783915407"
    }
}
