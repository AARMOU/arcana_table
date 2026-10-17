package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val IrmaPartTimeMutant = card("Irma, Part-Time Mutant") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Creature — Human Mutant Shapeshifter"
    power = 1
    toughness = 1
    oracleText = "At the beginning of combat on your turn, Irma becomes a copy of up to one other target creature you " +
        "control, except her name is Irma, Part-Time Mutant and she has this ability. Then put a +1/+1 counter on her."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        val model = target(TargetFilter(GameObjectFilter.Creature.youControl().notSourceItself()), optional = true)
        effect = Effects.EachPermanentBecomesCopyOfTarget(
            target = model,
            affected = EffectTarget.Self,
            duration = Duration.Permanent,
            exceptions = CopyExceptions(nameOverride = "Irma, Part-Time Mutant", retainResolvingTriggeredAbility = true),
        ) then Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "At the beginning of combat on your turn, Irma becomes a copy of up to one other target creature " +
            "you control, except her name is Irma, Part-Time Mutant and she has this ability. Then put a +1/+1 counter on her."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "12"
        artist = "Mirko Failoni"
    }
}
