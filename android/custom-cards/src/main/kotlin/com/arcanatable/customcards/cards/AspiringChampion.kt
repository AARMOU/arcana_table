package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.SacrificeSelfEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private fun ruinousAscensionEffect() = Effects.Pipeline {
    val (found, revealed) = gatherUntilMatch(GameObjectFilter.Creature)
    reveal(revealed)
    move(found, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You))
    run(Effects.ShuffleLibrary())
    run(
        Effects.If(
            Conditions.CollectionContainsMatch(
                found.key,
                GameObjectFilter.Creature.withSubtype("Demon"),
            ),
            Effects.DealDamage(
                amount = DynamicAmounts.powerOf(found.asTarget(0)),
                target = EffectTarget.PlayerRef(Player.EachOpponent),
                damageSource = found.asTarget(0),
            ),
        ),
    )
}

val AspiringChampion = card("Aspiring Champion") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 3
    oracleText = "Menace\nRuinous Ascension — When this creature deals combat damage to a player, sacrifice it. If you do, reveal cards from the top of your library until you reveal a creature card. Put that card onto the battlefield, then shuffle the rest into your library. If that creature is a Demon, it deals damage equal to its power to each opponent."

    keywords(Keyword.MENACE)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.IfYouDo(
            action = SacrificeSelfEffect,
            then = ruinousAscensionEffect(),
            successCriterion = SuccessCriterion.PermanentsSacrificed,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "71"
        artist = "Miguel Sacristan"
    }
}
