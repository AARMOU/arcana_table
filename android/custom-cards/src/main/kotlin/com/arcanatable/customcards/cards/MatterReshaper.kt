package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private fun matterReshaperDeathEffect() = Effects.Pipeline {
    val topCard = gather(
        CardSource.TopOfLibrary(1),
        revealed = true,
        name = "matterReshaperTop",
    )
    run(
        Effects.If(
            condition = Conditions.EntityMatches(
                EffectTarget.LibraryTop(),
                GameObjectFilter.Permanent.manaValueAtMost(3),
            ),
            then = Effects.IfYouDo(
                action = Effects.May(
                    MoveCollectionEffect(
                        from = topCard.key,
                        destination = CardDestination.ToZone(Zone.BATTLEFIELD, Player.You),
                        revealed = true,
                        storeMovedAs = "matterReshaperPut",
                    ),
                ),
                then = Effects.Nothing,
                otherwise = MoveCollectionEffect(
                    from = topCard.key,
                    destination = CardDestination.ToZone(Zone.HAND, Player.You),
                ),
                successCriterion = SuccessCriterion.CollectionNonEmpty("matterReshaperPut"),
            ),
            otherwise = MoveCollectionEffect(
                from = topCard.key,
                destination = CardDestination.ToZone(Zone.HAND, Player.You),
            ),
        ),
    )
}

/** Matter Reshaper — Oath of the Gatewatch #6. */
val MatterReshaper = card("Matter Reshaper") {
    manaCost = "{2}{C}"
    colorIdentity = ""
    typeLine = "Creature — Eldrazi"
    power = 3
    toughness = 2
    oracleText = "Devoid (This card has no color.)\nWhen Matter Reshaper dies, reveal the top card of your " +
        "library. You may put that card onto the battlefield if it's a permanent card with mana value 3 or less. " +
        "Otherwise, put that card into your hand."

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.self.dies()
        effect = matterReshaperDeathEffect()
        description = "When Matter Reshaper dies, reveal the top card of your library. You may put that card onto " +
            "the battlefield if it's a permanent card with mana value 3 or less. Otherwise, put that card into your hand."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "6"
        artist = "Lius Lasahido"
    }
}
