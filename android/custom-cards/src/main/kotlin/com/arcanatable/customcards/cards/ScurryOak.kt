package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ScurryOak = card("Scurry Oak") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Treefolk"
    power = 1
    toughness = 2
    oracleText = "Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)\n" +
        "Whenever one or more +1/+1 counters are put on this creature, you may create a 1/1 green Squirrel creature token."
    evolve()
    triggeredAbility {
        trigger = Triggers.self.getsCounters(CounterType.PLUS_ONE_PLUS_ONE)
        optional = true
        effect = Effects.CreateToken(1, 1, setOf(Color.GREEN), setOf("Squirrel"))
        description = "Whenever one or more +1/+1 counters are put on this creature, you may create a 1/1 green Squirrel creature token."
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "172"
        imageUri = "https://cards.scryfall.io/normal/front/0/f/0fb48c2e-ee0f-4fae-9c22-247870c10d5b.jpg?1783926825"
    }
}
