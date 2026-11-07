package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val CommissarSeverinaRaine = card("Commissar Severina Raine") {
    manaCost = "{1}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Legendary Creature — Human Soldier"
    power = 2
    toughness = 2
    oracleText = "Leading from the Front — Whenever Commissar Severina Raine attacks, each opponent loses X life, where X is the number of other attacking creatures.\n" +
        "Summary Execution — {2}, Sacrifice another creature: You gain 2 life and draw a card."

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.LoseLife(
            DynamicAmount.Add(
                DynamicAmounts.battlefield(Player.Each, GameObjectFilter.Creature.attacking()).count(),
                DynamicAmount.Fixed(-1),
            ),
            EffectTarget.PlayerRef(Player.EachOpponent),
        )
    }

    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{2}"),
            Costs.SacrificeAnother(GameObjectFilter.Creature.youControl()),
        )
        effect = Effects.GainLife(2) then Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "112"
        artist = "David Rapoza"
    }
}
