package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val CloudExSoldier = card("Cloud, Ex-SOLDIER") {
    manaCost = "{2}{R}{G}{W}"
    colorIdentity = "RGW"
    typeLine = "Legendary Creature — Human Mercenary Soldier"
    power = 4
    toughness = 4
    oracleText = "Haste\n" +
        "When Cloud enters, attach up to one target Equipment you control to it.\n" +
        "Whenever Cloud attacks, draw a card for each equipped attacking creature you control. Then if Cloud has " +
        "power 7 or greater, create two Treasure tokens."

    keywords(Keyword.HASTE)

    triggeredAbility {
        trigger = Triggers.self.enters()
        val equipment = target(
            TargetFilter(GameObjectFilter.Artifact.withSubtype("Equipment").youControl()),
            optional = true,
        )
        effect = Effects.AttachTargetEquipmentToCreature(equipment, EffectTarget.Self)
        description = "When Cloud enters, attach up to one target Equipment you control to it."
    }

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = Effects.DrawCards(
            DynamicAmounts.battlefield(
                Player.You,
                GameObjectFilter.Creature.attacking().equipped(),
            ).count(),
        ) then Effects.If(
            Conditions.CompareAmounts(DynamicAmounts.sourcePower(), ComparisonOperator.GTE, 7),
            Effects.CreateTreasure(2),
        )
        description = "Whenever Cloud attacks, draw a card for each equipped attacking creature you control. Then if Cloud has power 7 or greater, create two Treasure tokens."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2"
        artist = "S. Makimura"
        imageUri = "https://api.scryfall.com/cards/fic/2?format=image&version=normal"
    }
}
