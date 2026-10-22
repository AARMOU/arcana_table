package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Great Unclean One — The Ruinous Powers.
 *
 * First, every opponent loses 2 life. The qualifying-opponent comparison
 * happens AFTER that loss, using each opponent's current life total versus
 * the original ability controller's life total. CountPlayersWith rebinds
 * Player.You to each opponent, while ControllerOfSource stays the controller.
 */
val GreatUncleanOne = card("Great Unclean One") {
    manaCost = "{4}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Demon"
    power = 4
    toughness = 5
    oracleText = "Reverberating Summons — At the beginning of your end step, each opponent loses 2 life. " +
        "Then for each opponent who has less life than you, create a 1/3 black Demon creature token " +
        "named Plaguebearer of Nurgle."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        effect = Effects.ForEachPlayer(
            players = Player.EachOpponent,
            effect = Effects.LoseLife(2, target = EffectTarget.Controller),
        ) then
            Effects.CreateToken(
                count = DynamicAmounts.countPlayersWith(
                    scope = Player.EachOpponent,
                    condition = Conditions.CompareAmounts(
                        left = DynamicAmounts.lifeTotal(Player.You),
                        operator = ComparisonOperator.LT,
                        right = DynamicAmounts.lifeTotal(Player.ControllerOfSource),
                    ),
                ),
                power = 1,
                toughness = 3,
                colors = setOf(Color.BLACK),
                creatureTypes = setOf("Demon"),
                name = "Plaguebearer of Nurgle",
            )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "35"
        artist = "Helge C. Balzer"
    }
}
