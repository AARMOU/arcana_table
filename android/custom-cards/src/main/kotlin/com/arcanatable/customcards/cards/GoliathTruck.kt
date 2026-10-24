package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.targets.TargetOther

/**
 * Goliath Truck — Warhammer 40,000 Commander #158.
 *
 * Crew is an Argentum vehicle action, not a generic tap ability. The attack trigger targets
 * ANOTHER attacker at trigger placement (the Truck cannot put its counters on itself).
 */
val GoliathTruck = card("Goliath Truck") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 4
    toughness = 4
    oracleText = "Stowage — Whenever this Vehicle attacks, put two +1/+1 counters on another target attacking creature.\n" +
        "Crew 2"

    keywordAbility(KeywordAbility.crew(2))
    triggeredAbility {
        trigger = Triggers.self.attacks()
        val otherAttacker = target(
            TargetOther(baseRequirement = TargetObject(filter = TargetFilter.AttackingCreature)),
        )
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 2, otherAttacker)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "158"
        artist = "Diego Gisbert"
    }
}
