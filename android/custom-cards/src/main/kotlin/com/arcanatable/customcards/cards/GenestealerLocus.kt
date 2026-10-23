package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Each declared attacker gets its own trigger; attacking a planeswalker never qualifies. */
val GenestealerLocus = card("Genestealer Locus") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Tyranid Human"
    power = 3
    toughness = 3
    oracleText = "Neurotraumal Rod — Whenever a creature attacks you, it gets -1/-0 until end of turn.\n" +
        "Whenever a creature attacks one of your opponents, it gets +0/+1 until end of turn."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.attackingPlayer(EffectTarget.Controller)).attacks()
        effect = Effects.ModifyStats(-1, 0, EffectTarget.TriggeringEntity)
    }
    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.attackingAnOpponent()).attacks()
        effect = Effects.ModifyStats(0, 1, EffectTarget.TriggeringEntity)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "21"
        artist = "Diego Gisbert"
    }
}
