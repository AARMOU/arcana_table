package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val WakeningSunsAvatar = card("Wakening Sun's Avatar") {
    manaCost = "{5}{W}{W}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Dinosaur Avatar"
    power = 7
    toughness = 7
    oracleText = "When Wakening Sun's Avatar enters the battlefield, if you cast it from your hand, destroy all non-Dinosaur creatures."

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.WasCastFromHand
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.notSubtype(Subtype("Dinosaur"))),
            Effects.Destroy(EffectTarget.IterationEntity),
        )
        description = "If you cast this from your hand, destroy all non-Dinosaur creatures."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "44"
        imageUri = "https://cards.scryfall.io/normal/front/5/8/58df2f80-5edf-46d2-9476-5ec84d7055c8.jpg?1562560184"
    }
}
