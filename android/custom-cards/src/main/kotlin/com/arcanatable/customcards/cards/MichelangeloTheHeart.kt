package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.PlayerAttackedWithCreaturesThisTurn
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*

val MichelangeloTheHeart = card("Michelangelo, the Heart") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Mutant Ninja Turtle"
    power = 2
    toughness = 1
    oracleText = "Trample\nRaid (the Fridge) — At the beginning of your second main phase, if you attacked this turn, " +
        "put a +1/+1 counter on target creature and create a Food token.\n" +
        "Partner—Character select (You can have two commanders if both have this ability.)"

    keywords(Keyword.TRAMPLE)
    keywordAbility(KeywordAbility.Partner(PartnerKind.Named("Character select")))

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.POSTCOMBAT_MAIN)
        interveningIf = PlayerAttackedWithCreaturesThisTurn(Player.You, GameObjectFilter.Creature, 1)
        val creature = target(TargetFilter.Creature)
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, creature) then Effects.CreateFood()
        description = "At the beginning of your second main phase, if you attacked this turn, put a +1/+1 counter " +
            "on target creature and create a Food token."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "5"
        artist = "Néstor Ossandón Leal"
        imageUri = "https://cards.scryfall.io/normal/front/b/a/bac2d744-db65-4b56-8634-c87fd00c090e.jpg?1783904173"
    }
}
