package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Ravenous uses cast-time X for entry counters and the X>=5 draw condition. */
val TyrantGuard = card("Tyrant Guard") {
    manaCost = "{X}{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Tyranid"
    power = 3
    toughness = 3
    oracleText = "Ravenous (This creature enters with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters.)\n" +
        "Shieldwall — Sacrifice this creature: Creatures you control with counters on them gain hexproof and indestructible until end of turn."

    ravenous()

    activatedAbility {
        cost = Costs.SacrificeSelf
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl().withAnyCounter()),
            Effects.GrantKeyword(Keyword.HEXPROOF, EffectTarget.IterationEntity) then
                Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "103"
        artist = "Xavier Ribeiro"
    }
}
