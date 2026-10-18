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

val QuestForRenewal = card("Quest for Renewal") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "Whenever a creature you control becomes tapped, you may put a quest counter on this " +
        "enchantment.\nAs long as there are four or more quest counters on this enchantment, untap all " +
        "creatures you control during each other player's untap step."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl()).becomesTapped()
        optional = true
        effect = Effects.AddCounters(CounterType.QUEST, 1, EffectTarget.Self)
        description = "Whenever a creature you control becomes tapped, you may put a quest counter on this enchantment."
    }

    // "Untap all creatures you control during each other player's untap step" while the quest has
    // four counters. Arcana's pinned engine doesn't apply untap-step statics behind a condition, so
    // this untaps them at the start of each opponent's upkeep instead (straight after their untap step).
    triggeredAbility {
        trigger = Triggers.anOpponent.beginningOf(Step.UPKEEP)
        interveningIf = Conditions.SourceCounterCountAtLeast(CounterType.QUEST, 4)
        effect = Effects.ForEachInGroup(GroupFilter(GameObjectFilter.Creature.youControl()), Effects.Untap(EffectTarget.IterationEntity))
        description = "As long as there are four or more quest counters on this enchantment, untap all creatures you control during each other player's untap step."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "180"
        imageUri = "https://cards.scryfall.io/normal/front/4/1/41b7ad82-1115-4f80-886a-3f59f568423f.jpg?1783942043"
        artist = "Jason Chan"
    }
}
