package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.predicates.StatePredicate
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

val BronzebeakForagers = card("Bronzebeak Foragers") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Dinosaur"
    power = 3
    toughness = 4
    oracleText = "When this creature enters, for each opponent, exile up to one target nonland " +
        "permanent that player controls until this creature leaves the battlefield.\n" +
        "{X}{W}: Put target card with mana value X exiled with this creature into its owner's graveyard."

    triggeredAbility {
        trigger = Triggers.self.enters()
        targets(
            TargetFilter.NonlandPermanentOpponentControls,
            minCount = 0,
            dynamicMaxCount = DynamicAmounts.playerCount(Player.EachOpponent),
            differentControllers = true,
        )
        effect = Effects.ForEachTarget(Effects.ExileUntilLeaves(EffectTarget.ContextTarget(0)))
        description = "For each opponent, exile up to one target nonland permanent that player controls until this creature leaves the battlefield."
    }

    triggeredAbility {
        trigger = Triggers.self.leaves()
        effect = Effects.ReturnLinkedExileUnderOwnersControl()
        description = "When this creature leaves the battlefield, return the exiled cards under their owners' control."
    }

    activatedAbility {
        cost = Costs.Mana("{X}{W}")
        val exiledCard = target(
            TargetFilter(
                baseFilter = GameObjectFilter.Any
                    .withStatePredicate(StatePredicate.ExiledWithSource)
                    .manaValueEqualsDynamic(DynamicAmounts.xValue()),
                zone = Zone.EXILE,
            ),
        )
        effect = MoveToZoneEffect(
            target = exiledCard,
            destination = Zone.GRAVEYARD,
            fromZone = Zone.EXILE,
        )
        description = "Put target card with mana value X exiled with this creature into its owner's graveyard."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "69"
        artist = "Zoltan Boros"
        imageUri = "https://api.scryfall.com/cards/named?exact=Bronzebeak%20Foragers&format=image"
    }
}
