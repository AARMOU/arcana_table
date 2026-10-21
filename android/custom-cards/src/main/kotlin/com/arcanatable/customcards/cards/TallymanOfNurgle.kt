package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player

/** Tallyman of Nurgle — Warhammer 40,000 Commander #60. */
val TallymanOfNurgle = card("Tallyman of Nurgle") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Astartes Warrior"
    power = 2
    toughness = 3
    oracleText = "Lifelink\nThe Seven-fold Chant — At the beginning of your end step, if a creature died " +
        "this turn, you draw a card and you lose 1 life. If seven or more creatures died this " +
        "turn, instead you draw seven cards and you lose 7 life."

    keywords(Keyword.LIFELINK)
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        interveningIf = Conditions.CompareAmounts(
            DynamicAmounts.creaturesDiedThisTurn(Player.Each),
            ComparisonOperator.GTE,
            1
        )
        effect = Effects.If(
            condition = Conditions.CompareAmounts(
                DynamicAmounts.creaturesDiedThisTurn(Player.Each),
                ComparisonOperator.GTE,
                7
            ),
            then = Effects.DrawCards(7) then Effects.LoseLife(7),
            otherwise = Effects.DrawCards(1) then Effects.LoseLife(1)
        )
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "60"
        artist = "Games Workshop"
    }
}
