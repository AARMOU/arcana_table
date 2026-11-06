package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val BoneSabres = card("Bone Sabres") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Artifact — Equipment"
    oracleText = "Whenever equipped creature attacks, put four +1/+1 counters on it.\nEquip {3}"

    triggeredAbility {
        trigger = Triggers.attached.attacks()
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 4, EffectTarget.TriggeringEntity)
    }

    equipAbility("{3}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "88"
        artist = "David Auden Nash"
    }
}
