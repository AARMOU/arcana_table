package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val SavaiThundermane = card("Savai Thundermane") {
    manaCost = "{R}{W}"
    colorIdentity = "RW"
    typeLine = "Creature — Elemental Cat"
    power = 3
    toughness = 2
    oracleText = "Whenever you cycle a card, you may pay {2}. When you do, this creature deals 2 damage to target creature and you gain 2 life."

    triggeredAbility {
        trigger = Triggers.you.cycles()
        effect = Effects.MayPay(
            ManaCost.parse("{2}"),
            Effects.ReflexiveTrigger(
                action = Effects.Nothing,
                optional = true,
                descriptionOverride = "When you do, this creature deals 2 damage to target creature and you gain 2 life.",
            ) {
                val creature = target(TargetFilter(GameObjectFilter.Creature))
                effect = Effects.DealDamage(2, creature, EffectTarget.Self) then Effects.GainLife(2)
            },
            Effects.Nothing,
        )
        description = "Whenever you cycle a card, you may pay {2}. When you do, this creature deals 2 damage to target creature and you gain 2 life."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "205"
        imageUri = "https://api.scryfall.com/cards/named?exact=Savai%20Thundermane&format=image"
    }
}
