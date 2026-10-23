package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val WakkaDevotedGuardian = card("Wakka, Devoted Guardian") {
    manaCost = "{2}{G}{W}"
    colorIdentity = "GW"
    typeLine = "Legendary Creature — Human Warrior"
    power = 4
    toughness = 4
    oracleText = "Reach, trample\n" +
        "Whenever Wakka deals combat damage to a player, destroy up to one target artifact that player controls " +
        "and put a +1/+1 counter on Wakka.\n" +
        "Blitzball Captain — At the beginning of your end step, if a counter was put on Wakka this turn, " +
        "put a +1/+1 counter on each other creature you control."

    keywords(Keyword.REACH, Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        val artifact = target(
            TargetFilter(
                GameObjectFilter.Artifact.targetPlayerControls(EffectTarget.PlayerRef(Player.TriggeringPlayer))
            ),
            optional = true,
        )
        effect = Effects.Destroy(artifact) then
            Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Whenever Wakka deals combat damage to a player, destroy up to one target artifact that player controls and put a +1/+1 counter on Wakka."
    }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        interveningIf = Conditions.SourceReceivedCounterThisTurn()
        effect = Effects.ForEachInGroup(
            GroupFilter.OtherCreaturesYouControl,
            Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.IterationEntity),
        )
        description = "At the beginning of your end step, if a counter was put on Wakka this turn, put a +1/+1 counter on each other creature you control."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "477"
        artist = "Kieran Yanner"
        imageUri = "https://api.scryfall.com/cards/fic/477?format=image&version=normal"
    }
}
