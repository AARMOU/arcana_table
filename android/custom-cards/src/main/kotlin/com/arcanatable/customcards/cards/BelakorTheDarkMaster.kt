package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val BelakorTheDarkMaster = card("Be'lakor, the Dark Master") {
    manaCost = "{3}{U}{B}{R}"
    colorIdentity = "UBR"
    typeLine = "Legendary Creature — Demon"
    power = 6
    toughness = 5
    oracleText = "Flying\nPrince of Chaos — When Be'lakor enters, you draw X cards and you lose X life, " +
        "where X is the number of Demons you control.\n" +
        "Lord of Torment — Whenever another Demon you control enters, it deals damage equal to its power to any target."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.enters()
        val demons = DynamicAmounts.battlefield(
            Player.You,
            GameObjectFilter.Creature.withSubtype("Demon"),
        ).count()
        effect = Effects.DrawCards(demons) then Effects.LoseLife(demons, EffectTarget.Controller)
    }

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature.withSubtype("Demon").youControl()).enters()
        val target = target(Targets.Any)
        effect = Effects.DealDamage(DynamicAmounts.triggeringPower(), target)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "6"
        artist = "Daarken"
    }
}
