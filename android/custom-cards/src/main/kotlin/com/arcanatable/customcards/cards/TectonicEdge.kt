package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Tectonic Edge — Eldrazi Incursion.
 * The four-land threshold is checked on EACH opponent separately, not by
 * summing all opponents' lands; any one qualifying opponent enables activation.
 * Once enabled, the targeted nonbasic land may belong to any player.
 */
val TectonicEdge = card("Tectonic Edge") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{1}, {T}, Sacrifice this land: Destroy target nonbasic land. " +
        "Activate only if an opponent controls four or more lands."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap, Costs.SacrificeSelf)
        val targetLand = target(TargetFilter.NonbasicLand)
        effect = Effects.Destroy(targetLand)
        restrictions = listOf(ActivationRestriction.OnlyIfCondition(
            Conditions.CompareAmounts(
                DynamicAmounts.countPlayersWith(
                    scope = Player.EachOpponent,
                    condition = Conditions.CompareAmounts(
                        DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).count(),
                        ComparisonOperator.GTE,
                        4
                    )
                ),
                ComparisonOperator.GTE,
                1,
            )
        ))
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "384"
        artist = "Vincent Proce"
    }
}
