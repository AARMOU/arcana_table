package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Shard of the Nightbringer — Warhammer 40,000 Commander #55 (Necron Dynasties). {5}{B}{B}{B}
 * Creature — C'tan, 8/8.
 *
 * Flying
 * Drain Life — When this creature enters, if you cast it, target opponent loses half their life,
 * rounded up. You gain life equal to the life lost this way.
 *
 * "If you cast it" is The One Ring's intervening-if, `Conditions.WasCast` (CR 603.4): reanimated or
 * otherwise put onto the battlefield, it doesn't trigger. The amount is
 * `Divide(LifeTotal(Player.TargetOpponent), 2, roundUp = true)`, read on resolution from the chosen
 * opponent (the evaluator resolves `TargetOpponent` to the ability's player target). `DrainLife`
 * (Exsanguinate's primitive) with `from` = that target gains exactly the life the opponent actually
 * lost: an opponent who can't lose life (CR 119.8) loses none, and you gain none.
 * No official rulings are published for this card (Scryfall, checked 2026-11-03).
 */
val ShardOfTheNightbringer = card("Shard of the Nightbringer") {
    manaCost = "{5}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Creature — C'tan"
    power = 8
    toughness = 8
    oracleText = "Flying\nDrain Life — When this creature enters, if you cast it, target opponent loses half their life, rounded up. You gain life equal to the life lost this way."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.WasCast
        val opponent = target(Targets.Opponent)
        effect = Effects.DrainLife(
            DynamicAmount.Divide(DynamicAmounts.lifeTotal(Player.TargetOpponent), DynamicAmount.Fixed(2), roundUp = true),
            from = opponent,
        )
        description = "Drain Life — When this creature enters, if you cast it, target opponent loses half their life, rounded up. You gain life equal to the life lost this way."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "55"
        artist = "Pierre Loyvet"
        imageUri = "https://cards.scryfall.io/normal/front/2/1/21e4140e-22b3-479f-a259-0f56ed02b8ad.jpg?1783920909"
    }
}
