package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.predicates.ControllerPredicate
import com.wingedsheep.sdk.scripting.predicates.StatePredicate
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val SpittingDilophosaurus = card("Spitting Dilophosaurus") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Dinosaur"
    power = 3
    toughness = 2
    oracleText = "Whenever this creature enters or attacks, put a -1/-1 counter on up to one target creature.\n" +
        "Creatures your opponents control with -1/-1 counters on them can't block."

    val counteredOpponentCreatures = GroupFilter(
        GameObjectFilter(
            cardPredicates = listOf(CardPredicate.IsCreature),
            statePredicates = listOf(StatePredicate.HasCounter(CounterType.MINUS_ONE_MINUS_ONE)),
            controllerPredicate = ControllerPredicate.ControlledByOpponent,
        ),
    )
    staticAbility {
        ability = CantBlock(counteredOpponentCreatures)
    }

    triggeredAbility {
        trigger = Triggers.or(Triggers.self.enters(), Triggers.self.attacks())
        val creature = target(TargetFilter(GameObjectFilter.Creature), optional = true)
        effect = Effects.AddCounters(CounterType.MINUS_ONE_MINUS_ONE, 1, creature)
        description = "Whenever this creature enters or attacks, put a -1/-1 counter on up to one target creature."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "3"
        artist = "Francisco Badilla"
        imageUri = "https://cards.scryfall.io/normal/front/f/4/f4f0c479-5fd1-45c2-af3f-15b6e5811b0f.jpg?1783913640"
    }
}
