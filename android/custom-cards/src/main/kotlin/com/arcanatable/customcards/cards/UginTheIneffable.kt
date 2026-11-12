package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.FaceDownMode
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Ugin, the Ineffable — CMM #811 (Eldrazi Incursion). From GPT's #165/#176 candidate.
 *
 * Each +1 independently attaches its exiled card to its own newly created Spirit
 * token (NOT to Ugin). The token's leaves trigger reads its own linked collection.
 * Face-down exile must remain visible only to the exiling Ugin controller:
 * lookableInExile=true creates the explicit owner-only permission, not public reveal.
 */
val UginTheIneffable = card("Ugin, the Ineffable") {
    manaCost = "{6}"
    colorIdentity = ""
    typeLine = "Legendary Planeswalker — Ugin"
    startingLoyalty = 4
    oracleText = "Colorless spells you cast cost {2} less to cast.\n" +
        "+1: Exile the top card of your library face down and look at it. Create a 2/2 colorless Spirit creature token. When that token leaves the battlefield, put the exiled card into your hand.\n" +
        "−3: Destroy target permanent that's one or more colors."

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(
                GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless)
            ),
            modification = CostModification.ReduceGeneric(2)
        )
    }

    loyaltyAbility(+1) {
        description = "Exile the top card of your library face down and look at it. Create a 2/2 colorless Spirit creature token. When that token leaves the battlefield, put the exiled card into your hand."
        effect = Effects.Pipeline {
            val exiled = gather(CardSource.TopOfLibrary(1, Player.You))
            move(
                exiled,
                CardDestination.ToZone(Zone.EXILE),
                faceDown = FaceDownMode.HIDDEN,
                lookableInExile = true
            )
            run(Effects.CreateToken(
                name = "Spirit",
                power = 2,
                toughness = 2,
                colors = emptySet(),
                creatureTypes = setOf("Spirit"),
                triggeredAbilities = listOf(
                    TriggeredAbility.create(
                        trigger = Triggers.self.leaves(),
                        effect = Effects.Pipeline {
                            val linked = gather(CardSource.FromLinkedExile())
                            move(
                                linked,
                                CardDestination.ToZone(Zone.HAND),
                                underOwnersControl = true
                            )
                        },
                        descriptionOverride = "When this token leaves the battlefield, put the exiled card into your hand."
                    )
                )
            ))
            run(Effects.LinkCollectionToPermanent(exiled, EffectTarget.PipelineTarget(CREATED_TOKENS)))
        }
    }

    loyaltyAbility(-3) {
        val colored = target(TargetFilter(
            GameObjectFilter.Permanent.withCardPredicate(CardPredicate.IsColored)
        ))
        description = "Destroy target permanent that's one or more colors."
        effect = Effects.Destroy(colored)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "811"
        artist = "Daarken"
        imageUri = "https://cards.scryfall.io/normal/front/2/e/2eb7e322-aba0-42f5-80a1-267f3444ac4d.jpg?1783915455"
    }
}
