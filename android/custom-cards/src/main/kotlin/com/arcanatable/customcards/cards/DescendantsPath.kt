package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.conditions.AllConditions
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private fun descendantsPathRevealEffect() = Effects.Pipeline {
    val topCard = gather(
        CardSource.TopOfLibrary(1),
        revealed = true,
        name = "descendantsPathTop",
    )
    run(
        Effects.If(
            condition = AllConditions(
                listOf(
                    Conditions.EntityMatches(EffectTarget.LibraryTop(), GameObjectFilter.Creature),
                    Conditions.YouControl(
                        GameObjectFilter.Creature.sharingCreatureTypeWith(EffectTarget.LibraryTop()),
                    ),
                ),
            ),
            then = Effects.IfYouDo(
                action = Effects.May(
                    Effects.CastFromCollectionWithoutPayingCost(
                        from = topCard.key,
                        storeCastTo = "descendantsPathCast",
                    ),
                ),
                then = Effects.Nothing,
                otherwise = MoveCollectionEffect(
                    from = topCard.key,
                    destination = CardDestination.ToZone(
                        Zone.LIBRARY,
                        Player.You,
                        ZonePlacement.Bottom,
                    ),
                ),
                successCriterion = SuccessCriterion.CollectionNonEmpty("descendantsPathCast"),
            ),
            otherwise = MoveCollectionEffect(
                from = topCard.key,
                destination = CardDestination.ToZone(
                    Zone.LIBRARY,
                    Player.You,
                    ZonePlacement.Bottom,
                ),
            ),
        ),
    )
}

/** Descendants' Path — Avatar: The Last Airbender Eternal #167. */
val DescendantsPath = card("Descendants' Path") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "At the beginning of your upkeep, reveal the top card of your library. If it's a " +
        "creature card that shares a creature type with a creature you control, you may cast it " +
        "without paying its mana cost. If you don't cast it, put it on the bottom of your library."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        effect = descendantsPathRevealEffect()
        description = "At the beginning of your upkeep, reveal the top card of your library. If it's a " +
            "creature card that shares a creature type with a creature you control, you may cast it " +
            "without paying its mana cost. If you don't cast it, put it on the bottom of your library."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "167"
        artist = "Pauline Voss"
        imageUri = "https://cards.scryfall.io/normal/front/6/e/6edb20a3-31f5-4bf9-a5e3-943d8d5a1def.jpg?1783904804"
    }
}
